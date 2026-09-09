#!/usr/bin/env python3
"""Compare the corrected lowest-strain 7A pose with accepted 7B family representative."""
from __future__ import annotations

import csv
import importlib.util
import json
from pathlib import Path

import numpy as np
from rdkit import Chem
from rdkit.Chem import AllChem

ROOT = Path(__file__).resolve().parents[4]
BASE = ROOT / "research/mettl7-netarsudil-sam-mechanism/local-architecture"
OUT = BASE / "analysis"
LOCAL_ANALYZER = BASE / "scripts/analyze_local_architecture.py"
spec = importlib.util.spec_from_file_location("architecture", LOCAL_ANALYZER)
architecture = importlib.util.module_from_spec(spec); assert spec.loader is not None; spec.loader.exec_module(architecture)
metrics = architecture.metrics


def extract_mode(path: Path, mode: int):
    models = []
    current = []; take = False
    for line in path.read_text().splitlines():
        if line.startswith("MODEL"):
            take = int(line.split()[1]) == mode; current = []
        elif take and line.startswith("ENDMDL"):
            break
        elif take:
            current.append(line)
    temporary = BASE / "prepared/corrected_7A_lowest_strain_mode15.pdbqt"
    temporary.write_text("\n".join(current) + "\n")
    return temporary, metrics.atoms(temporary)


def optimized_reference_torsions(template):
    mol, _, _, props, _ = template
    reference = Chem.Mol(mol)
    AllChem.MMFFOptimizeMolecule(reference, mmffVariant="MMFF94s", maxIters=1000)
    rows = []
    conformer = reference.GetConformer()
    matches = reference.GetSubstructMatches(architecture.Lipinski.RotatableBondSmarts)
    for index, (j, k) in enumerate(matches, 1):
        left = next((n.GetIdx() for n in reference.GetAtomWithIdx(j).GetNeighbors() if n.GetIdx() != k), None)
        right = next((n.GetIdx() for n in reference.GetAtomWithIdx(k).GetNeighbors() if n.GetIdx() != j), None)
        if left is None or right is None: continue
        points = [np.array(conformer.GetAtomPosition(i)) for i in (left, j, k, right)]
        rows.append((index, left, j, k, right, architecture.dihedral(*points)))
    return {row[0]: row for row in rows}


def main():
    corrected = ROOT / "research/mettl7-netarsudil-sam-mechanism/vina-7a-native-box-correction"
    pose_path, ligand_a = extract_mode(corrected / "raw/7A_neutral_seed172904.pdbqt", 15)
    ligand_b = metrics.atoms(ROOT / "research/mettl7-netarsudil-sam-mechanism/local-flexibility/prepared/netarsudil_neutral_start.pdbqt")
    receptor_a = metrics.atoms(corrected / "prepared/METTL7A_SAM_receptor.pdbqt")
    receptor_b = metrics.atoms(ROOT / "research/mettl7-netarsudil-sam-mechanism/vina-matched/prepared/METTL7B_SAM_receptor.pdbqt")
    template = metrics.template("neutral"); reference = optimized_reference_torsions(template)
    rows = []; torsions = []
    for label, enzyme, ligand, receptor in (("7A_CORRECTED_LOWEST_STRAIN", "7A", ligand_a, receptor_a),
                                             ("7B_ACCEPTED_REPRESENTATIVE", "7B", ligand_b, receptor_b)):
        protein = [a for a in receptor if a["res"] != "SAM"]
        sam = [a for a in receptor if a["res"] == "SAM"]
        strain, _ = metrics.strain_coords(ligand, template)
        contact_map = metrics.contacts(protein, ligand)
        potential = architecture.electrostatic_proxy(protein, ligand)
        for number in range(190, 211):
            group = [a for a in protein if a["num"] == number]
            distance = min((np.linalg.norm(a["xyz"]-b["xyz"]) for a in metrics.heavy(group) for b in metrics.heavy(ligand)), default=999.)
            geometry = architecture.residue_geometry(protein, number)
            donors, acceptors = architecture.donor_acceptor_counts(protein, number)
            rows.append({"reference": label, "enzyme": enzyme, "residue_number": number, **geometry,
                         "ligand_min_distance_a": distance, "direct_contact": distance <= 4,
                         "residue_sasa_a2": architecture.residue_sasa(protein, number),
                         "hbond_donor_count": donors, "hbond_acceptor_count": acceptors})
        for item in architecture.torsions_for_ligand(ligand, template):
            ref = reference[item[0]][-1]; delta = (item[-1]-ref+180) % 360-180
            torsions.append({"reference": label, "torsion": item[0], "atom_indices": "-".join(map(str, item[1:5])),
                             "pose_deg": item[-1], "mmff_reference_deg": ref, "absolute_deviation_deg": abs(delta)})
        summary = {"reference": label, "enzyme": enzyme, "strain_kcal_mol": strain,
                   "sam_min_distance_a": float(metrics.dist(metrics.heavy(ligand), metrics.heavy(sam)).min()),
                   "burial_reduction_percent": metrics.sasa_reduction(ligand, protein),
                   "local_free_volume_a3": architecture.local_free_volume(protein, ligand),
                   "regional_electrostatic_mean": potential[0],
                   "contacts": [f"{res}{num}" for chain, num, res in sorted(contact_map)]}
        (OUT / f"{label.lower()}_summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    architecture.write_csv(OUT / "corrected_7a_vs_accepted_7b_residues.csv", rows)
    architecture.write_csv(OUT / "corrected_7a_vs_accepted_7b_ligand_torsions.csv", torsions)
    print((OUT / "7a_corrected_lowest_strain_summary.json").read_text())
    print((OUT / "7b_accepted_representative_summary.json").read_text())


if __name__ == "__main__": main()
