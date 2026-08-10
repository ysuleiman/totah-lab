#!/usr/bin/env python3
"""Materialize Stage 0 inventories from retained artifacts; run no searches or docking."""
from __future__ import annotations

import csv
import hashlib
import json
import platform
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def ca_map(path: Path) -> dict[tuple[str, int], np.ndarray]:
    result = {}
    for line in path.read_text().splitlines():
        if line.startswith("ATOM  ") and line[12:16].strip() == "CA":
            result[(line[21].strip() or "A", int(line[22:26]))] = np.array(
                [float(line[30:38]), float(line[38:46]), float(line[46:54])]
            )
    return result


def kabsch(mobile: np.ndarray, reference: np.ndarray) -> tuple[np.ndarray, np.ndarray, float]:
    mobile_center = mobile.mean(axis=0)
    reference_center = reference.mean(axis=0)
    covariance = (mobile - mobile_center).T @ (reference - reference_center)
    u, _, vt = np.linalg.svd(covariance)
    rotation = u @ vt
    if np.linalg.det(rotation) < 0:
        u[:, -1] *= -1
        rotation = u @ vt
    translation = reference_center - mobile_center @ rotation
    fitted = mobile @ rotation + translation
    rmsd = float(np.sqrt(np.mean(np.sum((fitted - reference) ** 2, axis=1))))
    return rotation, translation, rmsd


def materialize_superpocket() -> dict:
    receptor_a = ROOT / "resources/shared-resources/src/main/resources/Q9H8H3/Q9H8H3_TMT1A_HUMAN.pdb"
    receptor_b = ROOT / "experiments/METTL7B-v6_diffdock/target_protein.pdb"
    source = ROOT / "resources/shared-resources/src/main/resources/Q6UX53/fpocket/pockets/pocket2_vert.pqr"
    destination = HERE / "METTL7A_homologous_197_sphere_SAM_superpocket.pqr"
    a, b = ca_map(receptor_a), ca_map(receptor_b)
    keys = sorted(set(a) & set(b))
    rotation, translation, rmsd = kabsch(
        np.array([b[key] for key in keys]), np.array([a[key] for key in keys])
    )
    output = []
    count = 0
    for line in source.read_text().splitlines():
        if line.startswith(("ATOM  ", "HETATM")):
            xyz = np.array([float(line[30:38]), float(line[38:46]), float(line[46:54])])
            x, y, z = xyz @ rotation + translation
            line = f"{line[:30]}{x:8.3f}{y:8.3f}{z:8.3f}{line[54:]}"
            count += 1
        output.append(line)
    destination.write_text("\n".join(output) + "\n")
    return {
        "source": str(source.relative_to(ROOT)),
        "source_sha256": sha256(source),
        "destination": str(destination.relative_to(ROOT)),
        "destination_sha256": sha256(destination),
        "matched_ca": len(keys),
        "fit_rmsd_angstrom": rmsd,
        "rotation": rotation.tolist(),
        "translation": translation.tolist(),
        "sphere_count": count,
        "coordinate_rounding_angstrom": 0.001,
    }


def dcmb_inventories() -> tuple[int, int]:
    campaign = ROOT / "analysis/dcmb/controlled_campaign"
    families = list(csv.DictReader((campaign / "family_results.csv").open()))
    poses = list(csv.DictReader((campaign / "pose_results.csv").open()))
    family_rows = []
    for row in families:
        if not row["condition"].endswith("SAM_BOUND"):
            continue
        source = campaign / "raw" / (
            f'{row["condition"]}_{row["enantiomer"]}_s{row["representative_seed"]}.pdbqt'
        )
        family_rows.append({
            "condition": row["condition"], "enantiomer": row["enantiomer"],
            "family": row["family"], "population": row["population"],
            "status": "ACCEPTED_ON_SITE_SAM_BOUND",
            "representative_source": str(source.relative_to(ROOT)),
            "representative_model": row["representative_mode"],
            "representative_source_sha256": sha256(source),
            "superpocket_atom_fraction": row["superpocket_atom_fraction"],
            "sam_compatibility": row["sam_compatibility"],
            "sam_min_distance_A": row["sam_min_distance_A"],
            "engine_score_min": row["score_min"],
            "engine_score_mean": row["score_mean"],
            "engine_score_max": row["score_max"],
            "scientific_score_interpretation": "NONE",
        })
    pose_rows = []
    for row in poses:
        if not row["condition"].endswith("SAM_BOUND"):
            continue
        source = campaign / "raw" / f'{row["condition"]}_{row["enantiomer"]}_s{row["seed"]}.pdbqt'
        accepted = row["site_assigned"].lower() == "true"
        pose_rows.append({
            "condition": row["condition"], "enantiomer": row["enantiomer"],
            "seed": row["seed"], "model": row["mode"],
            "status": "ACCEPTED_ON_SITE" if accepted else "REJECTED_OUTSIDE_CANONICAL_SITE",
            "family": row["family"] if accepted else "",
            "source": str(source.relative_to(ROOT)), "source_sha256": sha256(source),
            "sam_compatibility": row["sam_compatibility"],
            "sam_min_distance_A": row["sam_min_distance_A"],
            "engine_score": row["score"], "scientific_score_interpretation": "NONE",
        })
    write_csv(HERE / "dcmb_family_inventory.csv", family_rows)
    write_csv(HERE / "dcmb_pose_inventory.csv", pose_rows)
    return len(family_rows), len(pose_rows)


def tsl_inventory() -> int:
    rows = []
    pocket_a = []
    for line in (HERE / "METTL7A_homologous_197_sphere_SAM_superpocket.pqr").read_text().splitlines():
        if line.startswith(("ATOM  ", "HETATM")):
            pocket_a.append([float(line[30:38]), float(line[38:46]), float(line[46:54])])
    pocket_a_xyz = np.array(pocket_a)
    relaxation = list(csv.DictReader((ROOT / "analysis/dcmb/tsl_conformational_response/relaxation_metrics.csv").open()))
    accepted_a = {int(row["rank"]): row for row in relaxation if row["passed"] == "True"}
    for rank, metric in sorted(accepted_a.items()):
        path = ROOT / f"analysis/dcmb/tsl_conformational_response/WT_METTL7A_SAM_TSL_relaxed_{rank}.pdb"
        ligand = []
        for line in path.read_text().splitlines():
            if line.startswith("HETATM") and line[17:20].strip() == "TSL" and line[76:78].strip().upper() != "H":
                ligand.append([float(line[30:38]), float(line[38:46]), float(line[46:54])])
        ligand_xyz = np.array(ligand)
        containment = float(np.mean(np.min(
            np.linalg.norm(ligand_xyz[:, None, :] - pocket_a_xyz[None, :, :], axis=2), axis=1
        ) <= 4.0))
        rows.append({
            "target": "METTL7A", "state_id": f"7A-TSL-{rank}", "status": "ACCEPTED",
            "response_mode": metric["stage"], "path": str(path.relative_to(ROOT)), "sha256": sha256(path),
            "catalytic_distance_A": metric["catalytic_distance_A"], "attack_angle_deg": metric["attack_angle_deg"],
            "protein_min_A": metric["protein_min_A"], "protein_pairs_lt_2A": metric["protein_pairs_lt_2A"],
            "sam_min_A": metric["sam_min_A"], "sam_pairs_lt_2A": metric["sam_pairs_lt_2A"],
            "superpocket_atom_fraction": containment,
            "backbone_rmsd_A": metric["backbone_rmsd_A"], "max_atom_displacement_A": metric["max_atom_displacement_A"],
        })
    selected_b = list(csv.DictReader((ROOT / "analysis/dcmb/mettl7b_selectivity/selected_tsl_states.csv").open()))
    for rank, metric in enumerate(selected_b, 1):
        path = ROOT / f"analysis/dcmb/mettl7b_selectivity/WT_METTL7B_SAM_TSL_fixed_{rank}.pdb"
        rows.append({
            "target": "METTL7B", "state_id": f"7B-TSL-{rank}", "status": "ACCEPTED",
            "response_mode": "fixed_receptor", "path": str(path.relative_to(ROOT)), "sha256": sha256(path),
            "catalytic_distance_A": metric["tsl_s_to_sam_methyl_A"], "attack_angle_deg": metric["attack_angle_TSL_S_Cmethyl_SAM_S_deg"],
            "protein_min_A": metric["protein_min_A"], "protein_pairs_lt_2A": metric["protein_pairs_lt_2A"],
            "sam_min_A": metric["sam_min_nonreactive_A"], "sam_pairs_lt_2A": metric["sam_pairs_lt_2A"],
            "superpocket_atom_fraction": metric["superpocket_atom_fraction"],
            "backbone_rmsd_A": "0", "max_atom_displacement_A": "0",
        })
    write_csv(HERE / "productive_tsl_inventory.csv", rows)
    return len(rows)


def main() -> None:
    pocket = materialize_superpocket()
    families, poses = dcmb_inventories()
    tsl_states = tsl_inventory()
    runtime = {
        "generated_by": str(Path(__file__).relative_to(ROOT)),
        "generated_by_sha256": sha256(Path(__file__)),
        "python": sys.version,
        "implementation": platform.python_implementation(),
        "platform": platform.platform(),
        "numpy": np.__version__,
        "no_new_docking_or_search": True,
        "source_protocols": {
            "docking": "analysis/dcmb/controlled_campaign/campaign_manifest.json",
            "mettl7a_tsl": "analysis/dcmb/tsl_conformational_response/summary.json",
            "mettl7b_tsl": "analysis/dcmb/mettl7b_selectivity/static_gate.json"
        },
    }
    (HERE / "superpocket_transfer.json").write_text(json.dumps(pocket, indent=2) + "\n")
    (HERE / "runtime_provenance.json").write_text(json.dumps(runtime, indent=2) + "\n")
    selection = {
        "system": "METTL7B L43F and L43F/G199F",
        "selection_stage": "Stage 2",
        "rule_frozen_at_stage0": True,
        "rule": [
            "Enumerate F43 chi1/chi2 states and local L229 chi1 offsets using the existing rotamer protocol.",
            "Reject any state with a receptor heavy-atom pair below 2.0 angstrom.",
            "Do not move backbone atoms or residues other than positions 43 and 229.",
            "Rank viable states first by steric score, then minimum environment distance, then reproducible deterministic torsion ordering.",
            "Retain tied geometrically distinct viable states; do not select using DCMB or TSL results."
        ],
        "prior_candidate": "analysis/dcmb/reciprocal_mutation/rotamer_analysis/SELECTED_L43F_trans_L229_repacked.pdb",
        "prior_candidate_status": "INPUT_TO_STAGE2_REVALIDATION_NOT_CANONICAL",
    }
    (HERE / "l43f_selection_rule.json").write_text(json.dumps(selection, indent=2) + "\n")
    print(json.dumps({"superpocket_spheres": pocket["sphere_count"], "families": families,
                      "poses": poses, "productive_tsl_states": tsl_states}, sort_keys=True))


if __name__ == "__main__":
    main()
