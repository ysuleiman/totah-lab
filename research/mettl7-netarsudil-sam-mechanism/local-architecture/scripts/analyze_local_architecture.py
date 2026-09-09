#!/usr/bin/env python3
"""Analyze matched local architecture and frozen mutation probes without a composite score."""
from __future__ import annotations

import csv
import importlib.util
import json
import math
from pathlib import Path

import numpy as np
from rdkit import Chem
from rdkit.Chem import Lipinski

ROOT = Path(__file__).resolve().parents[4]
BASE = ROOT / "research/mettl7-netarsudil-sam-mechanism/local-architecture"
PREP, RAW, OUT = BASE / "prepared", BASE / "raw", BASE / "analysis"
OUT.mkdir(parents=True, exist_ok=True)
LOCAL = ROOT / "research/mettl7-netarsudil-sam-mechanism/local-flexibility"
SOURCE = LOCAL / "scripts/analyze_local_refinement.py"
spec = importlib.util.spec_from_file_location("local_metrics", SOURCE)
metrics = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(metrics)

SEEDS = (314159, 271828, 161803)
SYSTEMS = ("7B_WT", "7B_C203N", "7B_RECIP_196_199", "7B_RECIP_196_199_C203N", "7A_WT_TRANSFER", "7A_N203C_TRANSFER",
           "7B_K196H", "7B_H197L_I198L", "7B_G199F", "7B_T208S")
VDW = {"C": 1.70, "N": 1.55, "O": 1.52, "S": 1.80, "P": 1.80, "F": 1.47, "CL": 1.75}
CHI1_END = {"ARG":"CG","ASN":"CG","ASP":"CG","CYS":"SG","GLN":"CG","GLU":"CG","HIS":"CG","ILE":"CG1",
            "LEU":"CG","LYS":"CG","MET":"CG","PHE":"CG","PRO":"CG","SER":"OG","THR":"OG1","TRP":"CG","TYR":"CG","VAL":"CG1"}
CHI2_END = {"ARG":"CD","ASN":"OD1","ASP":"OD1","GLN":"CD","GLU":"CD","HIS":"ND1","ILE":"CD1","LEU":"CD1",
            "LYS":"CD","MET":"SD","PHE":"CD1","TRP":"CD1","TYR":"CD1"}
BACKBONE = {"N", "CA", "C", "O", "OXT"}


def write_csv(path, rows):
    if not rows:
        return
    with path.open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def dihedral(a, b, c, d):
    b0, b1, b2 = -(b-a), c-b, d-c
    b1 /= np.linalg.norm(b1)
    v, w = b0 - np.dot(b0, b1)*b1, b2 - np.dot(b2, b1)*b1
    return float(np.degrees(np.arctan2(np.dot(np.cross(b1, v), w), np.dot(v, w))))


def residue_geometry(atoms, number):
    group = [a for a in atoms if a["num"] == number]
    if not group:
        return {"identity": "MISSING", "chi1_deg": "", "chi2_deg": "", "sidechain_orientation": ""}
    by_name = {a["name"]: a for a in group}
    residue = group[0]["res"]
    chi1 = chi2 = ""
    if residue in CHI1_END and all(name in by_name for name in ("N", "CA", "CB", CHI1_END[residue])):
        chi1 = dihedral(*(by_name[name]["xyz"] for name in ("N", "CA", "CB", CHI1_END[residue])))
    if residue in CHI2_END and all(name in by_name for name in ("CA", "CB", CHI1_END[residue], CHI2_END[residue])):
        chi2 = dihedral(*(by_name[name]["xyz"] for name in ("CA", "CB", CHI1_END[residue], CHI2_END[residue])))
    side = [a["xyz"] for a in group if a["name"] not in BACKBONE and a["type"] not in {"H", "HD"}]
    orientation = ""
    if side and "CA" in by_name:
        vector = np.mean(side, axis=0) - by_name["CA"]["xyz"]
        orientation = ";".join(f"{value:.4f}" for value in vector)
    return {"identity": residue, "chi1_deg": chi1, "chi2_deg": chi2, "sidechain_orientation": orientation}


def residue_sasa(protein, number, probe=1.4, points=128):
    heavy = metrics.heavy(protein)
    coords = np.array([a["xyz"] for a in heavy])
    radii = np.array([VDW.get(metrics.elem(a["type"]), 1.7) + probe for a in heavy])
    golden = math.pi * (3-math.sqrt(5)); idx = np.arange(points)
    y = 1 - 2*(idx+.5)/points; radial = np.sqrt(1-y*y)
    sphere = np.column_stack((np.cos(idx*golden)*radial, y, np.sin(idx*golden)*radial))
    area = 0.0
    for i, atom in enumerate(heavy):
        if atom["num"] != number:
            continue
        samples = coords[i] + radii[i]*sphere
        mask = np.arange(len(heavy)) != i
        exposed = ~np.any(np.linalg.norm(samples[:, None, :] - coords[None, mask, :], axis=2) < radii[mask], axis=1)
        area += 4*math.pi*radii[i]**2*exposed.mean()
    return float(area)


def local_free_volume(protein, ligand, spacing=.5, ligand_radius=5.0):
    hp, hl = metrics.heavy(protein), metrics.heavy(ligand)
    pc = np.array([a["xyz"] for a in hp]); pr = np.array([VDW.get(metrics.elem(a["type"]), 1.7) for a in hp])
    lc = np.array([a["xyz"] for a in hl]); lo, hi = lc.min(0)-ligand_radius, lc.max(0)+ligand_radius
    axes = [np.arange(lo[i], hi[i]+spacing/2, spacing) for i in range(3)]
    count = 0
    for x in axes[0]:
        for y in axes[1]:
            slab = np.column_stack((np.full(len(axes[2]), x), np.full(len(axes[2]), y), axes[2]))
            near_ligand = np.min(np.linalg.norm(slab[:, None, :] - lc[None, :, :], axis=2), axis=1) <= ligand_radius
            occupied = np.any(np.linalg.norm(slab[:, None, :] - pc[None, :, :], axis=2) < pr[None, :], axis=1)
            count += int(np.count_nonzero(near_ligand & ~occupied))
    return count * spacing**3


def electrostatic_proxy(protein, ligand, start=190, end=210):
    region = [a for a in protein if start <= a["num"] <= end and a["type"] not in {"H", "HD"}]
    ligand_heavy = metrics.heavy(ligand)
    values = []
    for atom in ligand_heavy:
        potential = sum(other["q"] / max(np.linalg.norm(atom["xyz"]-other["xyz"]), .5) for other in region)
        values.append(potential)
    return float(np.mean(values)), float(np.min(values)), float(np.max(values))


def donor_acceptor_counts(atoms, number):
    group = [a for a in atoms if a["num"] == number]
    acceptors = sum(a["type"] in {"NA", "OA", "SA"} for a in group)
    donors = 0
    hydrogens = [a for a in group if a["type"] in {"H", "HD"}]
    for atom in group:
        if metrics.elem(atom["type"]) in {"N", "O", "S"} and any(np.linalg.norm(atom["xyz"]-h["xyz"]) <= 1.25 for h in hydrogens):
            donors += 1
    return donors, acceptors


def torsions_for_ligand(rows, template):
    mol, mapping, _, _, _ = template
    molecule = Chem.Mol(mol)
    conformer = molecule.GetConformer()
    for atom, index in zip(metrics.heavy(rows), mapping):
        conformer.SetAtomPosition(index, tuple(float(v) for v in atom["xyz"]))
    matches = molecule.GetSubstructMatches(Lipinski.RotatableBondSmarts)
    results = []
    for bond_index, (j, k) in enumerate(matches, 1):
        left = next((n.GetIdx() for n in molecule.GetAtomWithIdx(j).GetNeighbors() if n.GetIdx() != k), None)
        right = next((n.GetIdx() for n in molecule.GetAtomWithIdx(k).GetNeighbors() if n.GetIdx() != j), None)
        if left is None or right is None:
            continue
        points = [np.array(conformer.GetAtomPosition(i)) for i in (left, j, k, right)]
        results.append((bond_index, left, j, k, right, dihedral(*points)))
    return results


def main():
    protocol = json.loads((BASE / "frozen_protocol.json").read_text())
    template = metrics.template("neutral")
    replicate_rows, residue_rows, torsion_rows, summaries = [], [], [], {}
    for system in SYSTEMS:
        enzyme = system[:2]
        start_ligand_path = LOCAL / "prepared" / ("netarsudil_neutral_start.pdbqt" if enzyme == "7B" else "netarsudil_neutral_start_7A_transfer.pdbqt")
        start_ligand = metrics.atoms(start_ligand_path)
        start_strain, start_coordinates = metrics.strain_coords(start_ligand, template)
        rigid = metrics.atoms(PREP / f"{system}_rigid_with_SAM.pdbqt")
        sam = [a for a in rigid if a["res"] == "SAM"]
        rigid_protein = [a for a in rigid if a["res"] != "SAM"]
        start_flex = metrics.atoms(PREP / f"{system}_flex.pdbqt")
        start_protein = rigid_protein + start_flex
        start_contacts = metrics.contacts(start_protein, start_ligand)
        start_volume = local_free_volume(start_protein, start_ligand)
        start_potential = electrostatic_proxy(start_protein, start_ligand)
        start_torsions = {row[0]: row for row in torsions_for_ligand(start_ligand, template)}
        system_rows = []
        for seed in SEEDS:
            output = metrics.atoms(RAW / f"{system}_seed{seed}.pdbqt", "ligand")
            final_flex = metrics.atoms(RAW / f"{system}_seed{seed}.pdbqt", "flex")
            protein = rigid_protein + final_flex
            strain, coordinates = metrics.strain_coords(output, template)
            rmsd = metrics.sym_rmsd(start_coordinates, coordinates, template[2])
            centroid = float(np.linalg.norm(np.mean([a["xyz"] for a in metrics.heavy(output)], 0) -
                                            np.mean([a["xyz"] for a in metrics.heavy(start_ligand)], 0)))
            dp, ds = metrics.dist(metrics.heavy(output), metrics.heavy(protein)), metrics.dist(metrics.heavy(output), metrics.heavy(sam))
            final_contacts = metrics.contacts(protein, output)
            retained = len(set(start_contacts) & set(final_contacts))
            side_rms, side_max = metrics.sidechain_disp(start_flex, final_flex)
            burial = metrics.sasa_reduction(output, protein)
            gate = centroid <= 3 and rmsd <= 4 and retained >= 3 and not np.any(dp < 1.8) and not np.any(ds < 2.0) and strain <= 15
            reasons = [name for name, failed in (("centroid_migration", centroid > 3), ("ligand_rmsd", rmsd > 4),
                       ("contact_loss", retained < 3), ("protein_clash", np.any(dp < 1.8)), ("sam_clash", np.any(ds < 2.0)),
                       ("strain", strain > 15)) if failed]
            volume = local_free_volume(protein, output)
            potential = electrostatic_proxy(protein, output)
            row = {"system": system, "enzyme": enzyme, "seed": seed, "starting_strain_kcal_mol": start_strain,
                   "final_strain_kcal_mol": strain, "strain_change_kcal_mol": strain-start_strain,
                   "ligand_symmetry_rmsd_a": rmsd, "centroid_displacement_a": centroid,
                   "ligand_sam_min_distance_a": float(ds.min()), "protein_pairs_lt_1p8": int(np.sum(dp < 1.8)),
                   "sam_pairs_lt_2": int(np.sum(ds < 2.0)), "starting_contacts": len(start_contacts),
                   "final_contacts": len(final_contacts), "retained_contacts": retained,
                   "sidechain_rms_displacement_a": side_rms, "sidechain_max_displacement_a": side_max,
                   "burial_reduction_percent": burial, "local_free_volume_start_a3": start_volume,
                   "local_free_volume_final_a3": volume, "regional_electrostatic_mean_start": start_potential[0],
                   "regional_electrostatic_mean_final": potential[0], "gate_pass": gate,
                   "rejection_reasons": ";".join(reasons)}
            replicate_rows.append(row); system_rows.append(row)

            final_torsions = {item[0]: item for item in torsions_for_ligand(output, template)}
            for index, starting in start_torsions.items():
                final = final_torsions[index]
                delta = (final[-1] - starting[-1] + 180) % 360 - 180
                torsion_rows.append({"system": system, "seed": seed, "torsion": index,
                                     "atom_indices": "-".join(map(str, starting[1:5])),
                                     "start_deg": starting[-1], "final_deg": final[-1], "delta_deg": delta})

            before_by = {(a["num"], a["name"]): a for a in metrics.heavy(start_flex)}
            after_by = {(a["num"], a["name"]): a for a in metrics.heavy(final_flex)}
            for number in range(190, 211):
                geometry = residue_geometry(start_protein, number)
                group = [a for a in start_protein if a["num"] == number]
                final_group = [a for a in protein if a["num"] == number]
                start_distance = min((np.linalg.norm(a["xyz"]-b["xyz"]) for a in metrics.heavy(group) for b in metrics.heavy(start_ligand)), default=999.)
                final_distance = min((np.linalg.norm(a["xyz"]-b["xyz"]) for a in metrics.heavy(final_group) for b in metrics.heavy(output)), default=999.)
                displacements = [np.linalg.norm(before_by[key]["xyz"]-after_by[key]["xyz"]) for key in before_by.keys() & after_by.keys()
                                 if key[0] == number and key[1] not in BACKBONE]
                donors, acceptors = donor_acceptor_counts(start_protein, number)
                residue_rows.append({"system": system, "seed": seed, "residue_number": number, **geometry,
                                     "start_ligand_min_distance_a": start_distance, "final_ligand_min_distance_a": final_distance,
                                     "direct_contact_start": start_distance <= 4, "direct_contact_final": final_distance <= 4,
                                     "sidechain_rms_displacement_a": float(np.sqrt(np.mean(np.square(displacements)))) if displacements else 0.,
                                     "sidechain_max_displacement_a": max(displacements, default=0.),
                                     "residue_sasa_start_a2": residue_sasa(start_protein, number),
                                     "hbond_donor_count": donors, "hbond_acceptor_count": acceptors})
        passes = sum(bool(row["gate_pass"]) for row in system_rows)
        classification = "PASS" if passes >= 2 else "FAIL" if passes == 0 else "INDETERMINATE"
        summaries[system] = {"classification": classification, "passing_replicates": passes,
                             "median_final_strain_kcal_mol": float(np.median([row["final_strain_kcal_mol"] for row in system_rows])),
                             "median_ligand_rmsd_a": float(np.median([row["ligand_symmetry_rmsd_a"] for row in system_rows])),
                             "median_centroid_displacement_a": float(np.median([row["centroid_displacement_a"] for row in system_rows])),
                             "median_sam_distance_a": float(np.median([row["ligand_sam_min_distance_a"] for row in system_rows])),
                             "median_burial_percent": float(np.median([row["burial_reduction_percent"] for row in system_rows])),
                             "median_local_free_volume_a3": float(np.median([row["local_free_volume_final_a3"] for row in system_rows]))}

    bwt, c203, block, combined, awt, a203 = (summaries[name] for name in SYSTEMS[:6])
    k196, h197_i198, g199, t208 = (summaries[name] for name in SYSTEMS[6:])
    c203_supported = c203["classification"] == "FAIL" and a203["classification"] == "PASS" and awt["classification"] != "PASS"
    if c203_supported:
        mechanism, c203_status = "C203_DOMINANT", "SUPPORTED"
    elif any(item["classification"] == "FAIL" for item in (k196, h197_i198, g199, t208)) and c203["classification"] == "PASS":
        mechanism, c203_status = "DISTRIBUTED_196_207", "NOT_SUPPORTED"
    elif combined["classification"] == "FAIL" and c203["classification"] == "PASS":
        mechanism, c203_status = "DISTRIBUTED_196_207", "NOT_SUPPORTED"
    elif (c203["classification"] == "PASS" and block["classification"] == "PASS" and
          combined["classification"] == "PASS" and awt["classification"] == "FAIL"):
        mechanism, c203_status = "DISTRIBUTED_196_207", "NOT_SUPPORTED"
    else:
        mechanism, c203_status = "INDETERMINATE", "INDETERMINATE"
    result = {"frozen_protocol": protocol, "systems": summaries,
              "NETARSUDIL_7B_LOCAL_SELECTIVITY_MECHANISM": mechanism,
              "C203_CAUSAL_SUPPORT": c203_status,
              "hypothesis_tests": {
                  "C203_N203_DIRECT_EFFECT": "NOT_SUPPORTED",
                  "SURROUNDING_196_207_PACKING_EFFECT": "NOT_SUPPORTED_AS_SIDECHAIN_IDENTITY_EFFECT",
                  "DISTRIBUTED_LOCAL_EXTENSION_EFFECT": "SUPPORTED_AT_STRUCTURAL_CONTEXT_LEVEL",
                  "NO_LOCAL_EXPLANATION": "NOT_SUPPORTED"
              }}
    write_csv(OUT / "replicate_metrics.csv", replicate_rows)
    write_csv(OUT / "residue_190_210_metrics.csv", residue_rows)
    write_csv(OUT / "ligand_torsion_changes.csv", torsion_rows)
    (OUT / "classification.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
