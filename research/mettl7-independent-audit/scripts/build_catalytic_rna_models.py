#!/usr/bin/env python3
# DEPRECATED FOR V2 USE (2026-09-05): near-attack geometry in this script is superseded by
# athena.tmt.NearAttackGeometry / NearAttackAssessor / EnsembleNacAnalyzer (Java).
# See ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md. Retained for historical regression reproduction only.
"""Build non-minimized RNA pentamer poses constrained to SAM transfer geometry.

These are local catalytic-feasibility probes, not RNA-binding poses or docking
predictions. The acceptor atom is sampled across a pre-reactive distance/angle
window around the SAM methyl carbon; rigid-body orientations are then sampled
at each geometry.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
from dataclasses import dataclass, replace
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[3]
RNA_TEMPLATE = ROOT / "analysis/mettl7-phase2/execution-unit-05O/literature-comparator-sources/AmberClassic/benchmarks/tRNAphe/1ehz_RNA.pdb"
PROTEINS = {
    "METTL7A": ROOT / "analysis/dcmb/sam_state/validated/WT_METTL7A_SAM_BOUND.pdb",
    "METTL7B": ROOT / "analysis/dcmb/sam_state/validated/WT_METTL7B_SAM_BOUND.pdb",
}
MODELS = (
    ("BMP2_SITE_2", "AAACC", 2, "A", "N6"),
    ("BMP2_SITE_3", "AGACU", 2, "A", "N6"),
    ("FILIP1L_GGACT", "GGACU", 2, "A", "N6"),
    ("KLF4_G1171", "ACGAC", 2, "G", "N7"),
    ("NFKBIA_G246", "AAGGA", 2, "G", "N7"),
    ("NFKBIA_G282", "GUGCC", 2, "G", "N7"),
)
BACKBONE = {"P", "OP1", "OP2", "OP3", "O5'", "C5'", "C4'", "O4'", "C3'", "O3'", "C2'", "O2'", "C1'"}
CHARGED = {"ARG": 1, "LYS": 1, "HIS": 1, "ASP": -1, "GLU": -1}


@dataclass(frozen=True)
class Atom:
    record: str
    name: str
    resname: str
    chain: str
    resid: int
    xyz: np.ndarray
    element: str


def parse_pdb(path: Path) -> tuple[list[Atom], list[str]]:
    atoms, lines = [], path.read_text().splitlines()
    for line in lines:
        if not line.startswith(("ATOM  ", "HETATM")):
            continue
        atoms.append(Atom(line[:6].strip(), line[12:16].strip(), line[17:20].strip(), line[21:22], int(line[22:26]),
                          np.array([float(line[30:38]), float(line[38:46]), float(line[46:54])]),
                          (line[76:78].strip() or line[12:14].strip()[0]).upper()))
    return atoms, lines


def residues(atoms: list[Atom]) -> list[list[Atom]]:
    grouped: dict[tuple[str, int], list[Atom]] = {}
    for atom in atoms:
        grouped.setdefault((atom.chain, atom.resid), []).append(atom)
    return list(grouped.values())


def base_letter(resname: str) -> str:
    return resname.strip()[0]


def kabsch(source: np.ndarray, target: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    source_center, target_center = source.mean(axis=0), target.mean(axis=0)
    covariance = (source - source_center).T @ (target - target_center)
    u, _, vt = np.linalg.svd(covariance)
    rotation = vt.T @ u.T
    if np.linalg.det(rotation) < 0:
        vt[-1] *= -1
        rotation = vt.T @ u.T
    return rotation, target_center - rotation @ source_center


def graft_pentamer(sequence: str) -> list[Atom]:
    template_atoms, _ = parse_pdb(RNA_TEMPLATE)
    template_residues = residues(template_atoms)
    scaffold = template_residues[9:14]
    examples = {letter: next(residue for residue in template_residues if base_letter(residue[0].resname) == letter) for letter in "ACGU"}
    result: list[Atom] = []
    anchors = ("C1'", "C2'", "O4'")
    for index, (letter, destination) in enumerate(zip(sequence, scaffold), start=1):
        source = examples[letter]
        source_map, destination_map = ({atom.name: atom for atom in residue} for residue in (source, destination))
        rotation, translation = kabsch(np.array([source_map[name].xyz for name in anchors]), np.array([destination_map[name].xyz for name in anchors]))
        for atom in destination:
            if atom.name in BACKBONE or atom.element == "H":
                if atom.element != "H":
                    result.append(replace(atom, resname=letter, chain="R", resid=index))
        for atom in source:
            if atom.name not in BACKBONE and atom.element != "H":
                result.append(replace(atom, resname=letter, chain="R", resid=index, xyz=rotation @ atom.xyz + translation))
    return result


def fibonacci_rotations(count: int) -> list[np.ndarray]:
    rotations = []
    golden = math.pi * (3.0 - math.sqrt(5.0))
    for index in range(count):
        z = 1.0 - 2.0 * (index + 0.5) / count
        radius = math.sqrt(max(0.0, 1.0 - z * z))
        x, y = radius * math.cos(golden * index), radius * math.sin(golden * index)
        axis = np.array([x, y, z])
        angle = golden * index
        cross = np.array([[0, -axis[2], axis[1]], [axis[2], 0, -axis[0]], [-axis[1], axis[0], 0]])
        rotations.append(np.eye(3) * math.cos(angle) + (1 - math.cos(angle)) * np.outer(axis, axis) + math.sin(angle) * cross)
    return rotations


def acceptor_directions(sulfur_direction: np.ndarray, angle_degrees: float) -> list[np.ndarray]:
    """Directions from methyl carbon giving acceptor--methyl--sulfur angle."""
    reference = np.array([1.0, 0.0, 0.0])
    if abs(float(np.dot(reference, sulfur_direction))) > 0.9:
        reference = np.array([0.0, 1.0, 0.0])
    tangent_a = np.cross(sulfur_direction, reference)
    tangent_a /= np.linalg.norm(tangent_a)
    tangent_b = np.cross(sulfur_direction, tangent_a)
    theta = math.radians(angle_degrees)
    azimuths = (0.0,) if angle_degrees == 180.0 else tuple(2.0 * math.pi * i / 6.0 for i in range(6))
    return [
        math.cos(theta) * sulfur_direction
        + math.sin(theta) * (math.cos(phi) * tangent_a + math.sin(phi) * tangent_b)
        for phi in azimuths
    ]


def transformed(atoms: list[Atom], rotation: np.ndarray, origin: np.ndarray, destination: np.ndarray) -> list[Atom]:
    return [replace(atom, xyz=rotation @ (atom.xyz - origin) + destination) for atom in atoms]


def evaluate(rna: list[Atom], protein: list[Atom], target_resid: int, acceptor_name: str) -> dict[str, object]:
    protein_heavy = [atom for atom in protein if atom.element != "H" and atom.resname != "SAM"]
    rna_xyz = np.array([atom.xyz for atom in rna])
    protein_xyz = np.array([atom.xyz for atom in protein_heavy])
    distances = np.linalg.norm(rna_xyz[:, None, :] - protein_xyz[None, :, :], axis=2)
    severe = int(np.count_nonzero(distances < 1.8))
    close = int(np.count_nonzero(distances < 2.4))
    contacts = int(np.count_nonzero(distances <= 4.5))
    contact_residues: set[tuple[str, int, str]] = set()
    target_base_contacts: set[tuple[str, int, str]] = set()
    for rna_index, protein_index in np.argwhere(distances <= 4.5):
        ra, pa = rna[int(rna_index)], protein_heavy[int(protein_index)]
        key = (pa.chain, pa.resid, pa.resname)
        contact_residues.add(key)
        if ra.resid == target_resid and ra.name not in BACKBONE:
            target_base_contacts.add(key)
    ordered = sorted(contact_residues, key=lambda value: (value[0], value[1]))
    charge = sum(CHARGED.get(resname, 0) for _, _, resname in ordered)
    return {
        "severe_clashes_lt_1_8A": severe,
        "close_pairs_lt_2_4A": close,
        "contact_atom_pairs_le_4_5A": contacts,
        "contact_residues": ",".join(f"{resname}{resid}" for _, resid, resname in ordered),
        "target_base_contact_residues": ",".join(f"{resname}{resid}" for _, resid, resname in sorted(target_base_contacts)),
        "contact_positive_residues": sum(1 for _, _, name in ordered if CHARGED.get(name) == 1),
        "contact_negative_residues": sum(1 for _, _, name in ordered if CHARGED.get(name) == -1),
        "contact_charge_count_difference": charge,
    }


def pdb_atom_line(serial: int, atom: Atom) -> str:
    return f"{atom.record:<6}{serial:5d} {atom.name:^4} {atom.resname:>3} {atom.chain:1}{atom.resid:4d}    {atom.xyz[0]:8.3f}{atom.xyz[1]:8.3f}{atom.xyz[2]:8.3f}  1.00  0.00          {atom.element:>2}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--orientations", type=int, default=48)
    args = parser.parse_args()
    args.output_dir = args.output_dir.resolve()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    summaries = []
    sensitivity = []
    rotations = fibonacci_rotations(args.orientations)
    for enzyme, protein_path in PROTEINS.items():
        protein, protein_lines = parse_pdb(protein_path)
        sam = {atom.name: atom for atom in protein if atom.resname == "SAM"}
        sulfur_direction = sam["SD"].xyz - sam["CE"].xyz
        sulfur_direction /= np.linalg.norm(sulfur_direction)
        for site_id, sequence, target_index, target_base, acceptor_name in MODELS:
            rna = graft_pentamer(sequence)
            acceptor = next(atom for atom in rna if atom.resid == target_index + 1 and atom.name == acceptor_name)
            candidates = []
            for distance in (2.8, 3.0, 3.2, 3.4):
                for angle in (150.0, 160.0, 170.0, 180.0):
                    geometry_candidates = []
                    for direction_index, direction in enumerate(acceptor_directions(sulfur_direction, angle)):
                        destination = sam["CE"].xyz + distance * direction
                        for rotation_index, rotation in enumerate(rotations):
                            pose = transformed(rna, rotation, acceptor.xyz, destination)
                            metrics = evaluate(pose, protein, target_index + 1, acceptor_name)
                            entry = (metrics["severe_clashes_lt_1_8A"], metrics["close_pairs_lt_2_4A"], -metrics["contact_atom_pairs_le_4_5A"], distance, angle, direction_index, rotation_index, pose, metrics)
                            geometry_candidates.append(entry)
                            candidates.append(entry)
                    geometry_best = min(geometry_candidates, key=lambda value: value[:3])
                    sensitivity.append({
                        "enzyme": enzyme,
                        "site_id": site_id,
                        "distance_A": distance,
                        "angle_deg": angle,
                        "direction_samples": len(acceptor_directions(sulfur_direction, angle)),
                        "orientation_samples_per_direction": args.orientations,
                        "minimum_severe_clashes_lt_1_8A": geometry_best[0],
                        "minimum_close_pairs_lt_2_4A": geometry_best[1],
                    })
            _, _, _, distance, angle, direction_index, rotation_index, best_pose, metrics = min(candidates, key=lambda value: value[:3])
            model_path = args.output_dir / f"{enzyme}_{site_id}_constrained.pdb"
            retained = [line for line in protein_lines if not line.startswith(("TER", "END"))]
            serial = max(atom.resid for atom in []) if False else len(protein) + 1
            model_path.write_text("\n".join(retained + ["TER"] + [pdb_atom_line(serial + i, atom) for i, atom in enumerate(best_pose)] + ["TER", "END"]) + "\n")
            summaries.append({
                "enzyme": enzyme,
                "site_id": site_id,
                "pentamer": sequence,
                "target_residue_index": target_index + 1,
                "acceptor_atom": acceptor_name,
                "sam_acceptor_distance_A": distance,
                "acceptor_methyl_sulfur_angle_deg": angle,
                "direction_index": direction_index,
                "orientation_samples_per_direction": args.orientations,
                "selected_orientation": rotation_index,
                **metrics,
                "model_path": str(model_path.relative_to(ROOT)),
                "interpretation_limit": "rigid, non-minimized local catalytic-feasibility model; not an RNA-binding or docking pose",
            })
    (args.output_dir / "model_summary.json").write_text(json.dumps(summaries, indent=2) + "\n")
    with (args.output_dir / "model_summary.csv").open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(summaries[0]))
        writer.writeheader()
        writer.writerows(summaries)
    with (args.output_dir / "geometry_sensitivity.csv").open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(sensitivity[0]))
        writer.writeheader()
        writer.writerows(sensitivity)
    print(f"wrote {len(summaries)} models to {args.output_dir}")


if __name__ == "__main__":
    main()
