#!/usr/bin/env python3
"""Compare Vina poses with independently predicted BioHub complexes.

The BioHub protein is fitted to the original receptor with matching CA atoms.
Ligand RMSD is emitted only when both coordinate graphs are isomorphic to the
SMILES heavy-atom graph. Symmetry-equivalent graph mappings are enumerated and
the minimum post-fit RMSD is reported.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import subprocess
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import numpy as np
from rdkit import Chem


CONTACT_CUTOFF = 4.0
SAME_POCKET_CENTROID_CUTOFF = 6.0
MAX_GRAPH_MAPPINGS = 20_000


@dataclass(frozen=True)
class Atom:
    name: str
    element: str
    xyz: np.ndarray
    chain: str = ""
    residue_name: str = ""
    residue_number: int = 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--biohub-root", type=Path, required=True)
    parser.add_argument("--receptor", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=Path.cwd())
    parser.add_argument("--db-name", default="totah_lab_db")
    parser.add_argument("--db-user", default="postgres")
    parser.add_argument("--db-host", default="localhost")
    return parser.parse_args()


def element_from_name(name: str) -> str:
    letters = "".join(character for character in name if character.isalpha())
    if not letters:
        raise ValueError(f"Cannot derive element from atom name {name!r}")
    two = letters[:2].title()
    if two in {"Br", "Cl", "Si", "Se"}:
        return two
    return letters[0].upper()


def parse_pdb(path: Path) -> tuple[list[Atom], list[Atom]]:
    protein: list[Atom] = []
    ligand: list[Atom] = []
    with path.open() as handle:
        for line in handle:
            if not line.startswith(("ATOM  ", "HETATM")):
                continue
            name = line[12:16].strip()
            element = line[76:78].strip().title() or element_from_name(name)
            atom = Atom(
                name=name,
                element=element,
                xyz=np.array([
                    float(line[30:38]),
                    float(line[38:46]),
                    float(line[46:54]),
                ]),
                chain=line[21:22].strip(),
                residue_name=line[17:20].strip(),
                residue_number=int(line[22:26]),
            )
            (ligand if line.startswith("HETATM") and atom.chain == "L"
             else protein).append(atom)
    return protein, ligand


def parse_first_vina_model(path: Path) -> list[Atom]:
    atoms: list[Atom] = []
    with path.open() as handle:
        for line in handle:
            if line.startswith("ENDMDL"):
                break
            if not line.startswith(("ATOM  ", "HETATM")):
                continue
            name = line[12:16].strip()
            autodock_type = line[77:].strip().split()[0] if line[77:].strip() else ""
            element = {
                "A": "C", "C": "C", "NA": "N", "N": "N",
                "OA": "O", "O": "O", "SA": "S", "S": "S",
                "HD": "H", "H": "H", "F": "F", "Cl": "Cl",
                "Br": "Br", "I": "I", "P": "P",
            }.get(autodock_type, element_from_name(name))
            atoms.append(Atom(
                name=f"{name}:{int(line[6:11])}",
                element=element,
                xyz=np.array([
                    float(line[30:38]),
                    float(line[38:46]),
                    float(line[46:54]),
                ]),
            ))
    return [atom for atom in atoms if atom.element != "H"]


def fit_transform(reference: np.ndarray, mobile: np.ndarray):
    if reference.shape != mobile.shape or len(reference) < 3:
        raise ValueError("At least three matching CA atoms are required")
    reference_center = reference.mean(axis=0)
    mobile_center = mobile.mean(axis=0)
    covariance = (mobile - mobile_center).T @ (reference - reference_center)
    u, _, vt = np.linalg.svd(covariance)
    rotation = u @ vt
    if np.linalg.det(rotation) < 0:
        u[:, -1] *= -1
        rotation = u @ vt
    translation = reference_center - mobile_center @ rotation
    fitted = mobile @ rotation + translation
    rmsd = math.sqrt(float(np.mean(np.sum((fitted - reference) ** 2, axis=1))))
    return rotation, translation, rmsd


def matching_ca(receptor: list[Atom], biohub: list[Atom]):
    reference = {
        (atom.chain, atom.residue_number): atom.xyz
        for atom in receptor if atom.name == "CA"
    }
    mobile = {
        (atom.chain, atom.residue_number): atom.xyz
        for atom in biohub if atom.name == "CA"
    }
    keys = sorted(reference.keys() & mobile.keys())
    return (
        np.array([reference[key] for key in keys]),
        np.array([mobile[key] for key in keys]),
        keys,
    )


def coordinate_molecule(atoms: list[Atom]) -> Chem.Mol:
    editable = Chem.RWMol()
    conformer = Chem.Conformer(len(atoms))
    for index, atom in enumerate(atoms):
        editable.AddAtom(Chem.Atom(atom.element))
        conformer.SetAtomPosition(index, tuple(float(value) for value in atom.xyz))
    periodic_table = Chem.GetPeriodicTable()
    for first in range(len(atoms)):
        first_number = editable.GetAtomWithIdx(first).GetAtomicNum()
        first_radius = periodic_table.GetRcovalent(first_number)
        for second in range(first + 1, len(atoms)):
            second_number = editable.GetAtomWithIdx(second).GetAtomicNum()
            second_radius = periodic_table.GetRcovalent(second_number)
            distance = float(np.linalg.norm(atoms[first].xyz - atoms[second].xyz))
            # Coordinate files lack bond records. A modest tolerance around
            # the sum of covalent radii recovers connectivity without using
            # atom order or assigning potentially misleading bond orders.
            if 0.4 < distance <= 1.25 * (first_radius + second_radius):
                editable.AddBond(first, second, Chem.BondType.SINGLE)
    molecule = editable.GetMol()
    molecule.AddConformer(conformer)
    return molecule


def graph(molecule: Chem.Mol):
    labels = [atom.GetAtomicNum() for atom in molecule.GetAtoms()]
    adjacency = [set() for _ in labels]
    for bond in molecule.GetBonds():
        begin, end = bond.GetBeginAtomIdx(), bond.GetEndAtomIdx()
        adjacency[begin].add(end)
        adjacency[end].add(begin)
    return labels, adjacency


def graph_mappings(template: Chem.Mol, coordinate: Chem.Mol):
    template_labels, template_edges = graph(template)
    coordinate_labels, coordinate_edges = graph(coordinate)
    if Counter(template_labels) != Counter(coordinate_labels):
        return [], "heavy-atom elemental composition differs from SMILES"
    if sum(map(len, template_edges)) != sum(map(len, coordinate_edges)):
        return [], "inferred coordinate connectivity differs from SMILES"

    candidates: list[list[int]] = []
    for index, atomic_number in enumerate(template_labels):
        neighbor_labels = Counter(template_labels[n] for n in template_edges[index])
        options = [
            other for other, other_number in enumerate(coordinate_labels)
            if other_number == atomic_number
            and len(coordinate_edges[other]) == len(template_edges[index])
            and Counter(coordinate_labels[n] for n in coordinate_edges[other])
            == neighbor_labels
        ]
        if not options:
            return [], "no element/degree-compatible molecular graph mapping"
        candidates.append(options)

    order = sorted(range(len(template_labels)), key=lambda i: len(candidates[i]))
    mappings: list[tuple[int, ...]] = []
    assigned: dict[int, int] = {}
    used: set[int] = set()

    def visit(depth: int):
        if len(mappings) >= MAX_GRAPH_MAPPINGS:
            return
        if depth == len(order):
            mappings.append(tuple(assigned[i] for i in range(len(template_labels))))
            return
        template_index = order[depth]
        for coordinate_index in candidates[template_index]:
            if coordinate_index in used:
                continue
            compatible = True
            for assigned_template, assigned_coordinate in assigned.items():
                if ((assigned_template in template_edges[template_index]) !=
                        (assigned_coordinate in coordinate_edges[coordinate_index])):
                    compatible = False
                    break
            if compatible:
                assigned[template_index] = coordinate_index
                used.add(coordinate_index)
                visit(depth + 1)
                used.remove(coordinate_index)
                del assigned[template_index]

    visit(0)
    if not mappings:
        return [], "coordinate graph is not isomorphic to SMILES"
    if len(mappings) >= MAX_GRAPH_MAPPINGS:
        return [], f"molecular symmetry exceeds mapping limit {MAX_GRAPH_MAPPINGS}"
    return mappings, None


def mapped_rmsd(smiles: str, vina_atoms: list[Atom], bio_atoms: list[Atom]):
    template = Chem.RemoveHs(Chem.MolFromSmiles(smiles))
    if template is None:
        return None, "SMILES could not be parsed", 0
    try:
        vina_molecule = coordinate_molecule(vina_atoms)
        bio_molecule = coordinate_molecule(bio_atoms)
    except Exception as exception:
        return None, f"coordinate connectivity failed: {exception}", 0
    vina_maps, reason = graph_mappings(template, vina_molecule)
    if reason:
        return None, f"Vina mapping invalid: {reason}", 0
    bio_maps, reason = graph_mappings(template, bio_molecule)
    if reason:
        return None, f"BioHub mapping invalid: {reason}", 0
    vina_map = vina_maps[0]
    vina_xyz = np.array([vina_atoms[index].xyz for index in vina_map])
    best = math.inf
    for bio_map in bio_maps:
        bio_xyz = np.array([bio_atoms[index].xyz for index in bio_map])
        value = math.sqrt(float(np.mean(np.sum((vina_xyz - bio_xyz) ** 2, axis=1))))
        best = min(best, value)
    return best, None, len(bio_maps)


def residue_label(atom: Atom) -> str:
    return f"{atom.chain}:{atom.residue_name}{atom.residue_number}"


def contacts(protein: list[Atom], ligand: list[Atom], cutoff: float):
    ligand_xyz = np.array([atom.xyz for atom in ligand if atom.element != "H"])
    result: set[str] = set()
    for atom in protein:
        if atom.element == "H":
            continue
        if float(np.min(np.linalg.norm(ligand_xyz - atom.xyz, axis=1))) <= cutoff:
            result.add(residue_label(atom))
    return result


def nearest_to_sg(protein: list[Atom], ligand: list[Atom]):
    sg = next((atom for atom in protein
               if atom.chain == "A" and atom.residue_number == 202
               and atom.name == "SG"), None)
    if sg is None:
        return None, None
    pairs = [
        (float(np.linalg.norm(atom.xyz - sg.xyz)), atom.name)
        for atom in ligand if atom.element != "H"
    ]
    return min(pairs) if pairs else (None, None)


def query_pose_paths(entries: list[dict], arguments: argparse.Namespace):
    pose_ids = sorted({int(entry["candidate"]["poseIdPrimary"]) for entry in entries})
    sql = ("SELECT id,pose_file FROM docking.docking_pose WHERE id IN ("
           + ",".join(map(str, pose_ids)) + ") ORDER BY id")
    environment = os.environ.copy()
    command = [
        "psql", "-h", arguments.db_host, "-U", arguments.db_user,
        "-d", arguments.db_name, "-At", "-F", "\t", "-c", sql,
    ]
    completed = subprocess.run(command, check=True, capture_output=True,
                               text=True, env=environment)
    return {
        int(line.split("\t", 1)[0]): Path(line.split("\t", 1)[1])
        for line in completed.stdout.splitlines() if line.strip()
    }


def ligand_confidence(prediction_path: Path):
    root = json.loads(prediction_path.read_text())
    values = [token["confidence"] for token in root["prediction"]["tokens"]
              if token["chain"] == "L"]
    return sum(values) / len(values) if values else None


def classify(same_pocket: bool, overlap: float, vina_sg, bio_sg, rmsd,
             ligand_confidence_value, mapping_error):
    if mapping_error or ligand_confidence_value is None or ligand_confidence_value < 0.30:
        return "Indeterminate"
    cys_preserved = (vina_sg <= 4.0 and bio_sg <= 4.0) or (
        vina_sg <= 6.0 and bio_sg <= 6.0 and abs(vina_sg - bio_sg) <= 1.5
    )
    if same_pocket and cys_preserved and overlap >= 0.50 and rmsd <= 3.0:
        return "Strongly reproduced"
    if same_pocket:
        return "Partially reproduced"
    return "Not reproduced"


def format_residues(values: Iterable[str]) -> str:
    def key(value: str):
        digits = "".join(character for character in value if character.isdigit())
        return (int(digits or 0), value)
    return ";".join(sorted(values, key=key))


def main() -> None:
    arguments = parse_args()
    manifest = json.loads(arguments.manifest.read_text())
    entries = manifest["entries"]
    pose_paths = query_pose_paths(entries, arguments)
    receptor, _ = parse_pdb(arguments.receptor)
    arguments.output_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    assumptions = []

    for entry in entries:
        candidate = entry["candidate"]
        ligand_id = candidate["ligandId"]
        pose_id = int(candidate["poseIdPrimary"])
        pose_path = pose_paths.get(pose_id)
        prediction_pdb = arguments.biohub_root / entry["predictionPdb"]
        prediction_json = arguments.biohub_root / entry["predictionJson"]
        missing = [str(path) for path in (pose_path, prediction_pdb, prediction_json)
                   if path is None or not path.is_file()]
        if missing:
            rows.append({
                "ligand_id": ligand_id,
                "mettl7b_vina_score": candidate["scorePrimary"],
                "mettl7a_vina_score": candidate["scoreComparison"],
                "selectivity_delta_7a_minus_7b": candidate["delta"],
                "final_interpretation": "Indeterminate",
                "notes": "Missing: " + "; ".join(missing),
            })
            continue

        vina_ligand = parse_first_vina_model(pose_path)
        bio_protein, bio_ligand = parse_pdb(prediction_pdb)
        reference_ca, mobile_ca, ca_keys = matching_ca(receptor, bio_protein)
        rotation, translation, protein_rmsd = fit_transform(reference_ca, mobile_ca)
        aligned_bio_ligand = [
            Atom(atom.name, atom.element, atom.xyz @ rotation + translation)
            for atom in bio_ligand if atom.element != "H"
        ]

        vina_contacts = contacts(receptor, vina_ligand, CONTACT_CUTOFF)
        bio_contacts = contacts(bio_protein, bio_ligand, CONTACT_CUTOFF)
        shared = vina_contacts & bio_contacts
        union = vina_contacts | bio_contacts
        overlap = len(shared) / len(union) if union else 0.0
        vina_sg, vina_atom = nearest_to_sg(receptor, vina_ligand)
        bio_sg, bio_atom = nearest_to_sg(bio_protein, bio_ligand)
        centroid_distance = float(np.linalg.norm(
            np.mean([atom.xyz for atom in vina_ligand], axis=0)
            - np.mean([atom.xyz for atom in aligned_bio_ligand], axis=0)
        ))
        # A long ligand can rotate about the same Cys-facing anchor and move
        # its centroid substantially. Treat that as the same pocket only when
        # both poses remain in the Cys202 neighborhood and preserve at least
        # three residue contacts; it is still a different pose/orientation.
        same_pocket = (
            centroid_distance <= SAME_POCKET_CENTROID_CUTOFF and bool(shared)
        ) or (
            vina_sg <= 6.0 and bio_sg <= 6.0 and len(shared) >= 3
        )
        rmsd, mapping_error, mapping_count = mapped_rmsd(
            entry["smiles"], vina_ligand, aligned_bio_ligand
        )
        confidence = ligand_confidence(prediction_json)
        interpretation = classify(
            same_pocket, overlap, vina_sg, bio_sg, rmsd,
            confidence, mapping_error,
        )
        notes = []
        if mapping_error:
            notes.append(mapping_error)
        if pose_id != candidate["poseIdPrimary"]:
            notes.append("unexpected pose ID mismatch")
        rows.append({
            "ligand_id": ligand_id,
            "mettl7b_vina_score": candidate["scorePrimary"],
            "mettl7a_vina_score": candidate["scoreComparison"],
            "selectivity_delta_7a_minus_7b": candidate["delta"],
            "vina_pose_id": pose_id,
            "minimum_vina_ligand_to_cys202_sg_distance_a": vina_sg,
            "minimum_biohub_ligand_to_cys202_sg_distance_a": bio_sg,
            "vina_ligand_atom_nearest_cys202_sg": vina_atom,
            "biohub_ligand_atom_nearest_cys202_sg": bio_atom,
            "ligand_heavy_atom_rmsd_a": rmsd,
            "ligand_centroid_distance_a": centroid_distance,
            "same_pocket": same_pocket,
            "shared_contact_residue_fraction_jaccard": overlap,
            "vina_contact_residues_4a": format_residues(vina_contacts),
            "biohub_contact_residues_4a": format_residues(bio_contacts),
            "shared_contact_residues_4a": format_residues(shared),
            "biohub_ptm": entry["ptm"],
            "biohub_interface_ptm": entry["interfacePtm"],
            "biohub_ligand_confidence": confidence,
            "protein_ca_alignment_rmsd_a": protein_rmsd,
            "protein_ca_alignment_atom_count": len(ca_keys),
            "valid_ligand_graph_mappings": mapping_count,
            "final_interpretation": interpretation,
            "notes": "; ".join(notes),
        })

    fields = [
        "ligand_id", "mettl7b_vina_score", "mettl7a_vina_score",
        "selectivity_delta_7a_minus_7b", "vina_pose_id",
        "minimum_vina_ligand_to_cys202_sg_distance_a",
        "minimum_biohub_ligand_to_cys202_sg_distance_a",
        "vina_ligand_atom_nearest_cys202_sg",
        "biohub_ligand_atom_nearest_cys202_sg",
        "ligand_heavy_atom_rmsd_a", "ligand_centroid_distance_a",
        "same_pocket", "shared_contact_residue_fraction_jaccard",
        "vina_contact_residues_4a", "biohub_contact_residues_4a",
        "shared_contact_residues_4a", "biohub_ptm",
        "biohub_interface_ptm", "biohub_ligand_confidence",
        "protein_ca_alignment_rmsd_a", "protein_ca_alignment_atom_count",
        "valid_ligand_graph_mappings", "final_interpretation", "notes",
    ]
    csv_path = arguments.output_dir / "vina_vs_biohub_pose_comparison.csv"
    with csv_path.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)

    counts = Counter(row["final_interpretation"] for row in rows)
    report_path = arguments.output_dir / "vina_vs_biohub_pose_comparison.md"
    with report_path.open("w") as handle:
        handle.write("# Vina versus BioHub pose-consistency analysis\n\n")
        handle.write("BioHub was treated as an independent pose prediction, not as a docking score. ")
        handle.write("Confidence values are reported only as annotations.\n\n")
        handle.write("## Summary\n\n")
        handle.write(f"Analyzed {len(rows)} ligands. " + ", ".join(
            f"{name}: {count}" for name, count in sorted(counts.items())) + ".\n\n")
        handle.write("| Ligand | Vina 7B | Vina 7A | Delta | Vina SG | BioHub SG | RMSD | Centroid | Contact Jaccard | Interpretation |\n")
        handle.write("|---|---:|---:|---:|---:|---:|---:|---:|---:|---|\n")
        for row in rows:
            value = lambda key: "NA" if row.get(key) is None else (
                f"{row[key]:.2f}" if isinstance(row[key], float) else str(row[key]))
            handle.write(
                f"| {row['ligand_id']} | {value('mettl7b_vina_score')} | "
                f"{value('mettl7a_vina_score')} | {value('selectivity_delta_7a_minus_7b')} | "
                f"{value('minimum_vina_ligand_to_cys202_sg_distance_a')} | "
                f"{value('minimum_biohub_ligand_to_cys202_sg_distance_a')} | "
                f"{value('ligand_heavy_atom_rmsd_a')} | "
                f"{value('ligand_centroid_distance_a')} | "
                f"{value('shared_contact_residue_fraction_jaccard')} | "
                f"{row['final_interpretation']} |\n"
            )
        handle.write("\n## Method and thresholds\n\n")
        handle.write(
            f"- Protein contacts use a {CONTACT_CUTOFF:.1f} A heavy-atom cutoff.\n"
            "- Cys202 distances are measured directly to SG, independently of the contact list.\n"
            "- BioHub protein coordinates are fitted to matching receptor CA atoms with the Kabsch algorithm; the same transform is applied to its ligand.\n"
            "- Ligand coordinate connectivity must be graph-isomorphic to the supplied SMILES. Symmetry-equivalent mappings are enumerated and the minimum mapped heavy-atom RMSD is used. Invalid mappings produce no RMSD and an Indeterminate result.\n"
            f"- Same pocket means centroid displacement <= {SAME_POCKET_CENTROID_CUTOFF:.1f} A with a shared contact, or both poses within 6 A of Cys202 SG with at least three shared 4 A contact residues. The second rule recognizes a shared anchor while allowing a long ligand to adopt a different orientation.\n"
            "- Strongly reproduced requires the same pocket, preserved Cys202 geometry, contact Jaccard >= 0.50, and mapped RMSD <= 3.0 A. Partial reproduction requires the same pocket plus contact Jaccard >= 0.20 or preserved Cys202 geometry.\n"
        )
        handle.write("\n## Missing inputs and assumptions\n\n")
        handle.write("- The requested `/mnt/data` ZIP and summary CSV were not mounted. Equivalent BioHub artifacts and their manifest under `/Users/yazan/artifacts/targets/Q6UX53/biohub/top20_cys202_delta` were used.\n")
        handle.write("- The manifest's `poseIdPrimary` identifies the best overall METTL7B Vina pose. The report does not silently substitute another Cys202-contacting pose.\n")
        handle.write("- The canonical receptor `Q6UX53_TMT1B_HUMAN.pdb` was used; its Cys202 coordinates were verified against the docking database.\n")
        handle.write("- Contact Jaccard is intersection divided by union. Residue identity is chain, residue name, and residue number.\n")
        handle.write("- BioHub confidence does not establish affinity, inhibition, selectivity, or covalency. Only METTL7B was submitted, so BioHub cannot validate the Vina 7A-versus-7B delta.\n")


if __name__ == "__main__":
    main()
