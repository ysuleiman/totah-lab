#!/usr/bin/env python3
# DEPRECATED FOR V2 USE (2026-09-05): near-attack geometry in this script is superseded by
# athena.tmt.NearAttackGeometry / NearAttackAssessor / EnsembleNacAnalyzer (Java).
# See ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md. Retained for historical regression reproduction only.
"""Run the locked productive-TSL feasibility workflow across all eight systems."""
from __future__ import annotations

import csv
import hashlib
import json
import math
import sys
from pathlib import Path

import numpy as np
from rdkit import Chem
from rdkit.Chem import AllChem

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
STAGE2 = ROOT / "analysis/mettl7-closure/stage2/prepared"
DCMB = ROOT / "analysis/dcmb"
sys.path[:0] = [str(DCMB), str(DCMB / "tsl_catalytic_geometry")]
import same_site_pose_analysis as base  # noqa: E402
import reconstruct_tsl as rt  # noqa: E402
import tsl_conformational_response as response  # noqa: E402

BACKBONE = {"N", "CA", "C", "O", "OXT"}
SYSTEMS = ["7A_WT", "7B_WT", "7A_F43L", "7A_F199G", "7A_F43L_F199G",
           "7B_L43F", "7B_G199F", "7B_L43F_G199F"]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_csv(path: Path, rows: list[dict]) -> None:
    if not rows:
        return
    with path.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader(); writer.writerows(rows)


def make_ligand():
    mol = Chem.AddHs(Chem.MolFromSmiles(rt.SMILES))
    params = AllChem.ETKDGv3(); params.randomSeed = rt.SEED; params.pruneRmsThresh = 0.35
    ids = AllChem.EmbedMultipleConfs(mol, numConfs=12, params=params)
    for conformer_id in ids:
        AllChem.MMFFOptimizeMolecule(mol, confId=conformer_id, maxIters=500)
    heavy = Chem.RemoveHs(mol)
    sulfur_index = next(atom.GetIdx() for atom in heavy.GetAtoms() if atom.GetSymbol() == "S")
    conformers = []
    for conformer_id in range(heavy.GetNumConformers()):
        conformer = heavy.GetConformer(conformer_id)
        raw = np.array([[conformer.GetAtomPosition(i).x, conformer.GetAtomPosition(i).y,
                         conformer.GetAtomPosition(i).z] for i in range(heavy.GetNumAtoms())])
        conformers.append(raw - raw[sulfur_index])
    return heavy, sulfur_index, conformers


def spheres(system: str) -> np.ndarray:
    path = ROOT / ("analysis/mettl7-closure/stage0/METTL7A_homologous_197_sphere_SAM_superpocket.pqr"
                   if system.startswith("7A") else
                   "resources/shared-resources/src/main/resources/Q6UX53/fpocket/pockets/pocket2_vert.pqr")
    return base.spheres(path)


def build_candidates(system: str, conformers: list[np.ndarray], sulfur_index: int):
    receptor = STAGE2 / f"{system}_receptor.pdb"
    bound = STAGE2 / f"{system}_SAM_BOUND.pdb"
    protein_atoms = rt.receptor(receptor)
    sam_atoms = rt.sam(bound)
    sam_s = next(atom[5] for atom in sam_atoms if atom[0] == "SD")
    methyl = next(atom[5] for atom in sam_atoms if atom[0] == "CE")
    axis = (methyl - sam_s) / np.linalg.norm(methyl - sam_s)
    protein_xyz = np.array([atom[5] for atom in protein_atoms])
    local_mask = np.linalg.norm(protein_xyz - methyl, axis=1) < 16.0
    local_xyz = protein_xyz[local_mask]
    local_atoms = [atom for atom, keep in zip(protein_atoms, local_mask) if keep]
    sam_xyz = np.array([atom[5] for atom in sam_atoms])
    cloud = spheres(system)
    rng = np.random.default_rng(rt.SEED)
    candidates = []
    for conformer_id, centered in enumerate(conformers):
        for distance in rt.DISTANCES:
            for rotation_index in range(rt.ROTATIONS):
                trial = rng.normal(size=3)
                perpendicular = trial - axis * float(trial @ axis)
                perpendicular /= np.linalg.norm(perpendicular)
                theta = math.acos(rng.uniform(math.cos(math.radians(30)), 1.0))
                direction = axis * math.cos(theta) + perpendicular * math.sin(theta)
                xyz = centered @ rt.random_rotation(rng) + methyl + direction * distance
                metrics = rt.evaluate(xyz, local_xyz, sam_xyz, cloud, sulfur_index, sam_s, methyl)
                distances = np.linalg.norm(xyz[:, None, :] - local_xyz[None, :, :], axis=2)
                clashes = np.argwhere(distances < 2.0)
                residues = {(local_atoms[j][2], local_atoms[j][3], local_atoms[j][1]) for _, j in clashes}
                candidates.append({"conformer": conformer_id, "rotation": rotation_index,
                                   "distance": distance, "xyz": xyz, "metrics": metrics,
                                   "residues": residues})
    return receptor, bound, protein_atoms, sam_atoms, candidates


def complete_linkage(candidates: list[dict]) -> list[list[dict]]:
    groups = [[candidate] for candidate in candidates]
    while True:
        choice = None
        for i in range(len(groups)):
            for j in range(i + 1, len(groups)):
                maximum = max(base.rmsd(a["xyz"], b["xyz"]) for a in groups[i] for b in groups[j])
                if maximum <= 2.0 and (choice is None or maximum < choice[0]):
                    choice = (maximum, i, j)
        if choice is None:
            break
        _, i, j = choice
        groups[i].extend(groups[j]); groups.pop(j)
    groups.sort(key=lambda group: (-len(group), min(item["conformer"] for item in group),
                                   min(item["rotation"] for item in group)))
    return groups


def medoid(group: list[dict]) -> dict:
    return min(group, key=lambda candidate: (
        np.mean([base.rmsd(candidate["xyz"], other["xyz"]) for other in group]),
        candidate["conformer"], candidate["rotation"]))


def near_misses(candidates: list[dict], count: int = 5) -> list[dict]:
    favorable = [candidate for candidate in candidates
                 if candidate["metrics"]["sam_pairs_lt_2A"] == 0
                 and candidate["metrics"]["superpocket_atom_fraction"] >= 0.70]
    ordered = sorted(favorable, key=lambda candidate: (
        candidate["metrics"]["protein_pairs_lt_2A"],
        -candidate["metrics"]["protein_min_A"],
        -candidate["metrics"]["superpocket_atom_fraction"],
        abs(candidate["metrics"]["attack_angle_TSL_S_Cmethyl_SAM_S_deg"] - 180),
        candidate["conformer"], candidate["rotation"]))
    selected = []
    for candidate in ordered:
        if all(base.rmsd(candidate["xyz"], prior["xyz"]) >= 2.0 for prior in selected):
            selected.append(candidate)
        if len(selected) == count:
            break
    return selected


def response_pass(metrics: dict) -> bool:
    return (metrics["protein_pairs_lt_2A"] == 0 and metrics["sam_pairs_lt_2A"] == 0
            and metrics["max_bond_deviation_A"] <= 0.02
            and metrics["backbone_rmsd_A"] <= 0.25
            and metrics["max_atom_displacement_A"] <= 1.50)


def controlled_relax(protein_atoms, ligand, sam_xyz, sidechain_residues, backbone_residues,
                     initial_xyz=None, steps=2500):
    reference = np.array([atom[5] for atom in protein_atoms])
    xyz = reference.copy() if initial_xyz is None else initial_xyz.copy()
    mobile = np.array([index for index, atom in enumerate(protein_atoms)
                       if ((atom[2], atom[3], atom[1]) in sidechain_residues and atom[0] not in BACKBONE)
                       or ((atom[2], atom[3], atom[1]) in backbone_residues)], dtype=int)
    fixed = np.array([index for index in range(len(protein_atoms)) if index not in set(mobile)], dtype=int)
    bonds = []
    for offset, i in enumerate(mobile):
        for j in mobile[offset + 1:]:
            if protein_atoms[i][2:4] == protein_atoms[j][2:4] and np.linalg.norm(reference[i] - reference[j]) < 1.9:
                bonds.append((i, j, float(np.linalg.norm(reference[i] - reference[j]))))
    momentum = np.zeros_like(xyz); variance = np.zeros_like(xyz); learning_rate = 0.006
    for _ in range(steps):
        gradient = np.zeros_like(xyz)
        gradient[mobile] += 4 * (xyz[mobile] - reference[mobile])
        for environment, force in ((ligand, 80.0), (sam_xyz, 80.0), (xyz[fixed], 50.0)):
            delta = xyz[mobile, None, :] - environment[None, :, :]
            distance = np.linalg.norm(delta, axis=2); safe = np.maximum(distance, 1e-4); mask = distance < 2.1
            gradient[mobile] += np.sum(np.where(
                mask[:, :, None], -2 * force * (2.1 - distance)[:, :, None] * delta / safe[:, :, None], 0), axis=1)
        for i, j, distance0 in bonds:
            delta = xyz[i] - xyz[j]; distance = max(np.linalg.norm(delta), 1e-6)
            bond_gradient = 120 * (distance - distance0) * delta / distance
            gradient[i] += bond_gradient; gradient[j] -= bond_gradient
        momentum = 0.9 * momentum + 0.1 * gradient
        variance = 0.999 * variance + 0.001 * gradient * gradient
        xyz[mobile] -= learning_rate * (momentum[mobile] / 0.1) / (np.sqrt(variance[mobile] / 0.001) + 1e-8)
    protein_distance = np.linalg.norm(ligand[:, None, :] - xyz[None, :, :], axis=2)
    sam_distance = np.linalg.norm(ligand[:, None, :] - sam_xyz[None, :, :], axis=2)
    displacement = np.linalg.norm(xyz - reference, axis=1)
    max_bond = max((abs(np.linalg.norm(xyz[i] - xyz[j]) - distance0) for i, j, distance0 in bonds), default=0)
    result = {"protein_min_A": float(protein_distance.min()),
              "protein_pairs_lt_2A": int(np.sum(protein_distance < 2.0)),
              "sam_min_A": float(sam_distance.min()), "sam_pairs_lt_2A": int(np.sum(sam_distance < 2.0)),
              "mobile_atoms": len(mobile),
              "sidechain_rmsd_A": float(np.sqrt(np.mean(displacement[mobile] ** 2))) if len(mobile) else 0,
              "max_atom_displacement_A": float(displacement[mobile].max()) if len(mobile) else 0,
              "backbone_rmsd_A": float(np.sqrt(np.mean(displacement[[i for i, atom in enumerate(protein_atoms)
                                                                       if atom[0] in BACKBONE]] ** 2))),
              "max_bond_deviation_A": max_bond}
    return xyz, result, protein_distance


def run_system(system: str, heavy, sulfur_index: int, conformers: list[np.ndarray]):
    out = HERE / system
    out.mkdir(parents=True, exist_ok=True)
    receptor, bound, protein_atoms, sam_atoms, candidates = build_candidates(system, conformers, sulfur_index)
    passing = [candidate for candidate in candidates
               if candidate["metrics"]["protein_pairs_lt_2A"] == 0
               and candidate["metrics"]["sam_pairs_lt_2A"] == 0
               and candidate["metrics"]["superpocket_atom_fraction"] >= 0.70]
    groups = complete_linkage(passing)
    selected = [medoid(group) for group in groups] if groups else near_misses(candidates)
    sam_xyz = np.array([atom[5] for atom in sam_atoms])
    state_rows, attempt_rows, artifacts = [], [], []
    original_xyz = np.array([atom[5] for atom in protein_atoms])
    for rank, candidate in enumerate(selected, 1):
        final_xyz = original_xyz.copy()
        if groups:
            result = {"protein_min_A": candidate["metrics"]["protein_min_A"],
                      "protein_pairs_lt_2A": 0,
                      "sam_min_A": candidate["metrics"]["sam_min_nonreactive_A"],
                      "sam_pairs_lt_2A": 0, "mobile_atoms": 0, "sidechain_rmsd_A": 0.0,
                      "max_atom_displacement_A": 0.0, "backbone_rmsd_A": 0.0,
                      "max_bond_deviation_A": 0.0}
            stage, passed, moved = "STATIC", True, set()
        else:
            moved = set(candidate["residues"])
            movable_sidechain_exists = any((atom[2], atom[3], atom[1]) in moved and atom[0] not in BACKBONE
                                           for atom in protein_atoms)
            if movable_sidechain_exists:
                final_xyz, result, _ = controlled_relax(protein_atoms, candidate["xyz"], sam_xyz, moved, set())
                passed = response_pass(result)
            else:
                result = {"protein_min_A": candidate["metrics"]["protein_min_A"],
                          "protein_pairs_lt_2A": candidate["metrics"]["protein_pairs_lt_2A"],
                          "sam_min_A": candidate["metrics"]["sam_min_nonreactive_A"],
                          "sam_pairs_lt_2A": candidate["metrics"]["sam_pairs_lt_2A"],
                          "mobile_atoms": 0, "sidechain_rmsd_A": 0.0,
                          "max_atom_displacement_A": 0.0, "backbone_rmsd_A": 0.0,
                          "max_bond_deviation_A": 0.0}
                passed = False
            stage = "SIDECHAIN_ONLY"
            attempt_rows.append({"system": system, "candidate": rank, "stage": stage,
                                 "passed": passed, "moved_residues": ";".join(f"{x[0]}:{x[2]}{x[1]}" for x in sorted(moved, key=lambda z: z[1])), **result})
            if not passed:
                near = {(atom[2], atom[3], atom[1]) for atom in protein_atoms
                        if np.min(np.linalg.norm(candidate["xyz"] - atom[5], axis=1)) < 4.5}
                residual_distances = np.linalg.norm(candidate["xyz"][:, None, :] - final_xyz[None, :, :], axis=2)
                residual_indices = set(np.argwhere(residual_distances < 2.0)[:, 1].tolist())
                backbone_residues = {(protein_atoms[index][2], protein_atoms[index][3], protein_atoms[index][1])
                                     for index in residual_indices if protein_atoms[index][0] in BACKBONE}
                moved = near | backbone_residues
                final_xyz, result, _ = controlled_relax(protein_atoms, candidate["xyz"], sam_xyz,
                                                         near, backbone_residues, final_xyz)
                stage, passed = "LIMITED_LOCAL_BACKBONE", response_pass(result)
                attempt_rows.append({"system": system, "candidate": rank, "stage": stage,
                                     "passed": passed, "moved_residues": ";".join(f"{x[0]}:{x[2]}{x[1]}" for x in sorted(moved, key=lambda z: z[1])), **result})
        if passed:
            path = out / f"{system}_SAM_TSL_{rank}.pdb"
            response.write_complex(receptor, protein_atoms, final_xyz, bound, heavy, candidate["xyz"], path)
            artifacts.append({"path": str(path.relative_to(ROOT)), "sha256": sha256(path)})
        state_rows.append({"system": system, "state": rank,
                           "status": "PASS" if passed else "NOT_PRODUCTIVE_UNDER_LIMITED_RESPONSE",
                           "phase": stage, "conformer": candidate["conformer"],
                           "rotation": candidate["rotation"],
                           "catalytic_distance_A": candidate["metrics"]["tsl_s_to_sam_methyl_A"],
                           "attack_angle_deg": candidate["metrics"]["attack_angle_TSL_S_Cmethyl_SAM_S_deg"],
                           "superpocket_atom_fraction": candidate["metrics"]["superpocket_atom_fraction"],
                           "protein_min_A": result["protein_min_A"],
                           "protein_pairs_lt_2A": result["protein_pairs_lt_2A"],
                           "sam_min_A": result["sam_min_A"], "sam_pairs_lt_2A": result["sam_pairs_lt_2A"],
                           "mobile_atoms": result["mobile_atoms"],
                           "backbone_rmsd_A": result["backbone_rmsd_A"],
                           "max_atom_displacement_A": result["max_atom_displacement_A"],
                           "max_bond_deviation_A": result["max_bond_deviation_A"],
                           "moved_residues": ";".join(f"{x[0]}:{x[2]}{x[1]}" for x in sorted(moved, key=lambda z: z[1]))})
    write_csv(out / "states.csv", state_rows)
    write_csv(out / "response_attempts.csv", attempt_rows)
    summary = {"system": system, "status": "PASS" if artifacts else "NOT_PRODUCTIVE_UNDER_LIMITED_RESPONSE",
               "candidates_tested": len(candidates), "static_passing_candidates": len(passing),
               "static_families": len(groups), "selected_for_response": 0 if groups else len(selected),
               "accepted_states": len(artifacts), "receptor": str(receptor.relative_to(ROOT)),
               "receptor_sha256": sha256(receptor), "sam_complex": str(bound.relative_to(ROOT)),
               "sam_complex_sha256": sha256(bound), "artifacts": artifacts}
    (out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    return summary, state_rows, attempt_rows


def main() -> None:
    heavy, sulfur_index, conformers = make_ligand()
    summaries, states, attempts = [], [], []
    for system in SYSTEMS:
        summary_path = HERE / system / "summary.json"
        states_path = HERE / system / "states.csv"
        if summary_path.exists() and states_path.exists():
            summary = json.loads(summary_path.read_text())
            system_states = list(csv.DictReader(states_path.open()))
            attempts_path = HERE / system / "response_attempts.csv"
            system_attempts = list(csv.DictReader(attempts_path.open())) if attempts_path.exists() else []
            print(system, "RESUMED", summary["status"], summary["static_passing_candidates"], summary["accepted_states"])
        else:
            summary, system_states, system_attempts = run_system(system, heavy, sulfur_index, conformers)
        summaries.append(summary); states.extend(system_states); attempts.extend(system_attempts)
        if not summary_path.exists():
            print(system, summary["status"], summary["static_passing_candidates"], summary["accepted_states"])
    write_csv(HERE / "matrix_summary.csv", [{key: value for key, value in summary.items() if key != "artifacts"}
                                              for summary in summaries])
    write_csv(HERE / "all_states.csv", states)
    write_csv(HERE / "all_response_attempts.csv", attempts)
    manifest = {"protocol": "analysis/mettl7-closure/stage1/protocol.json",
                "protocol_sha256": sha256(ROOT / "analysis/mettl7-closure/stage1/protocol.json"),
                "ligand_smiles": rt.SMILES, "seed": rt.SEED, "embedded_conformers": len(conformers),
                "attack_distances_A": list(rt.DISTANCES), "rotations_per_distance": rt.ROTATIONS,
                "systems": summaries}
    (HERE / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
