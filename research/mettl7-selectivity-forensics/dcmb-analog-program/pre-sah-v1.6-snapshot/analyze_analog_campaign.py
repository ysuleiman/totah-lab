#!/usr/bin/env python3
# DEPRECATED FOR V2 USE (2026-09-05): near-attack geometry in this script is superseded by
# athena.tmt.NearAttackGeometry / NearAttackAssessor / EnsembleNacAnalyzer (Java).
# See ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md. Retained for historical regression reproduction only.
"""Analyze the focused DCMB analog campaign without changing or rerunning docking."""
from __future__ import annotations

import csv
import hashlib
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
from rdkit import Chem, DataStructs
from rdkit.Chem import AllChem, Descriptors, Lipinski, rdMolDescriptors

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
CAMPAIGN = HERE / "matched-ab-campaign-v1"
OUT = HERE / "analysis-v1"
OUT.mkdir(parents=True, exist_ok=True)
SEEDS = (1, 7, 42)
ANCHORS = {
    "7A": {33, 43, 47, 99, 149, 151, 196, 199, 200, 201},
    "7B": {36, 39, 40, 43, 47, 144, 145, 149, 195, 199, 200, 203},
}


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="") as handle:
        return list(csv.DictReader(handle))


def write_csv(name: str, rows: list[dict[str, object]]) -> None:
    if not rows:
        return
    with (OUT / name).open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def atoms(path: Path, models: bool = False) -> list[dict[str, object]]:
    output, current, score, model = [], [], math.nan, 0
    for line in path.read_text().splitlines():
        if line.startswith("MODEL"):
            current, score = [], math.nan
            model = int(line.split()[1])
        elif line.startswith("REMARK VINA RESULT:"):
            score = float(line.split()[3])
        elif line.startswith(("ATOM  ", "HETATM")):
            atom_type = line.split()[-1]
            element = ({"A": "C", "C": "C", "NA": "N", "N": "N", "OA": "O", "O": "O",
                        "SA": "S", "S": "S", "Cl": "Cl", "F": "F", "HD": "H", "H": "H"}.get(atom_type)
                       or line[12:16].strip()[0])
            if element.upper() != "H":
                current.append({"serial": int(line[6:11]), "name": line[12:16].strip(),
                                "resname": line[17:20].strip(), "chain": line[21:22].strip(),
                                "resnum": int(line[22:26]), "element": element,
                                "xyz": np.array([float(line[30:38]), float(line[38:46]), float(line[46:54])])})
        elif models and line.startswith("ENDMDL") and current:
            output.append({"model": model, "score": score, "atoms": current})
            current = []
    if not models:
        return current
    return output


def rmsd(a: list[dict[str, object]], b: list[dict[str, object]]) -> float:
    xa = np.array([x["xyz"] for x in a]); xb = np.array([x["xyz"] for x in b])
    return float(np.sqrt(np.mean(np.sum((xa - xb) ** 2, axis=1))))


def clusters(records: list[dict[str, object]], cutoff: float = 2.0) -> list[list[int]]:
    groups = [[i] for i in range(len(records))]
    while True:
        choices = []
        for i in range(len(groups)):
            for j in range(i + 1, len(groups)):
                d = max(rmsd(records[a]["atoms"], records[b]["atoms"]) for a in groups[i] for b in groups[j])
                if d <= cutoff:
                    choices.append((d, i, j))
        if not choices:
            break
        _, i, j = min(choices)
        groups[i].extend(groups[j]); groups.pop(j)
    return sorted(groups, key=lambda g: (-len(g), min(g)))


def receptor(condition: str) -> list[dict[str, object]]:
    names = {"7A_APO": "7A_WT_APO.pdbqt", "7A_SAM": "7A_WT_SAM_BOUND.pdbqt",
             "7B_APO": "7B_WT_APO.pdbqt", "7B_SAM": "7B_WT_SAM_BOUND.pdbqt"}
    return atoms(ROOT / "analysis/dcmb/controlled_campaign/prepared" / names[condition])


def contact_rows(analog: str, condition: str, family: int, pose: list[dict[str, object]], rec: list[dict[str, object]]) -> list[dict[str, object]]:
    groups: dict[tuple[str, int, str], list[np.ndarray]] = defaultdict(list)
    for atom in rec:
        if atom["resname"] != "SAM":
            groups[(str(atom["chain"]), int(atom["resnum"]), str(atom["resname"]))].append(atom["xyz"])
    lig = np.array([x["xyz"] for x in pose])
    rows = []
    for (chain, number, residue), xyz in groups.items():
        distance = float(np.min(np.linalg.norm(np.array(xyz)[:, None, :] - lig[None, :, :], axis=2)))
        if distance <= 4.5:
            rows.append({"analog_id": analog, "condition": condition, "paralog": condition[:2],
                         "state": condition.split("_")[-1], "family": family, "chain": chain,
                         "residue_number": number, "residue_name": residue,
                         "minimum_distance_A": round(distance, 4),
                         "predefined_selectivity_sector": number in ANCHORS[condition[:2]]})
    return sorted(rows, key=lambda row: (row["minimum_distance_A"], row["residue_number"]))


def sam_geometry(pose: list[dict[str, object]], rec: list[dict[str, object]]) -> dict[str, object]:
    sam = {str(a["name"]): a["xyz"] for a in rec if a["resname"] == "SAM"}
    if not sam:
        return {"sam_min_distance_A": "", "candidate_acceptor_atom": "", "acceptor_methyl_distance_A": "",
                "acceptor_methyl_sulfur_angle_deg": "", "near_attack_geometry": "NOT_APPLICABLE"}
    sam_xyz = np.array(list(sam.values())); lig_xyz = np.array([a["xyz"] for a in pose])
    candidate = [a for a in pose if str(a["element"]).upper() in {"N", "S", "O"}]
    methyl_name = "CE" if "CE" in sam else "C9"
    sulfur_name = "SD" if "SD" in sam else "S8"
    if not candidate or methyl_name not in sam or sulfur_name not in sam:
        return {"sam_min_distance_A": round(float(np.min(np.linalg.norm(lig_xyz[:, None, :] - sam_xyz[None, :, :], axis=2))), 4),
                "candidate_acceptor_atom": "NONE", "acceptor_methyl_distance_A": "",
                "acceptor_methyl_sulfur_angle_deg": "", "near_attack_geometry": "UNRESOLVED"}
    chosen = min(candidate, key=lambda a: float(np.linalg.norm(a["xyz"] - sam[methyl_name])))
    va = chosen["xyz"] - sam[methyl_name]; vs = sam[sulfur_name] - sam[methyl_name]
    angle = math.degrees(math.acos(np.clip(float(np.dot(va, vs) / np.linalg.norm(va) / np.linalg.norm(vs)), -1, 1)))
    distance = float(np.linalg.norm(va))
    return {"sam_min_distance_A": round(float(np.min(np.linalg.norm(lig_xyz[:, None, :] - sam_xyz[None, :, :], axis=2))), 4),
            "candidate_acceptor_atom": f"{chosen['name']}:{chosen['element']}",
            "acceptor_methyl_distance_A": round(distance, 4),
            "acceptor_methyl_sulfur_angle_deg": round(angle, 3),
            "near_attack_geometry": "CANDIDATE" if distance <= 3.5 and angle >= 150 else "NOT_OBSERVED"}


def chemistry(panel: list[dict[str, str]]) -> list[dict[str, object]]:
    dcmb = Chem.MolFromSmiles(next(r["smiles"] for r in panel if r["analog_id"] == "DCMB_R"))
    dcmb_fp = AllChem.GetMorganFingerprintAsBitVect(dcmb, 2, nBits=2048)
    rows = []
    for row in panel:
        mol = Chem.MolFromSmiles(row["smiles"]); fp = AllChem.GetMorganFingerprintAsBitVect(mol, 2, nBits=2048)
        halogens = Counter(a.GetSymbol() for a in mol.GetAtoms() if a.GetSymbol() in {"F", "Cl", "Br", "I"})
        primary_amines = len(mol.GetSubstructMatches(Chem.MolFromSmarts("[NX3;H2;!$(NC=O)]")))
        rows.append({"analog_id": row["analog_id"], "canonical_smiles": Chem.MolToSmiles(mol, isomericSmiles=True),
                     "panel_role": row["panel_role"], "stereochemistry": row["stereochemistry"],
                     "morgan_similarity_to_DCMB_R": round(DataStructs.TanimotoSimilarity(dcmb_fp, fp), 4),
                     "molecular_weight": round(Descriptors.MolWt(mol), 4), "heavy_atoms": mol.GetNumHeavyAtoms(),
                     "rings": Lipinski.RingCount(mol), "aromatic_rings": Lipinski.NumAromaticRings(mol),
                     "rotatable_bonds": Lipinski.NumRotatableBonds(mol), "hbd": Lipinski.NumHDonors(mol),
                     "hba": Lipinski.NumHAcceptors(mol), "tpsa_A2": round(rdMolDescriptors.CalcTPSA(mol), 3),
                     "cl_count": halogens["Cl"], "f_count": halogens["F"], "primary_amine_count": primary_amines,
                     "free_thiol_count": len(mol.GetSubstructMatches(Chem.MolFromSmarts("[SX2H]"))),
                     "plausible_METTL7_methyl_acceptor": "YES" if mol.HasSubstructMatch(Chem.MolFromSmarts("[SX2H]")) else "NO_EVIDENCE_FOR_AMINE_AS_METTL7_ACCEPTOR"})
    return rows


def main() -> None:
    panel = read_csv(CAMPAIGN / "analog_panel.csv")
    chem = chemistry(panel); write_csv("analog_chemistry.csv", chem)
    pose_rows, family_rows, contacts, representative_metrics = [], [], [], []
    summaries: dict[tuple[str, str], dict[str, object]] = {}
    for condition in ("7A_APO", "7A_SAM", "7B_APO", "7B_SAM"):
        rec = receptor(condition)
        for analog in [r["analog_id"] for r in panel]:
            records = []
            for seed in SEEDS:
                path = CAMPAIGN / "raw" / f"{condition}__{analog}__s{seed}.pdbqt"
                for model in atoms(path, models=True):
                    model.update({"seed": seed, "path": str(path)}); records.append(model)
            groups = clusters(records)
            dominant = set(groups[0]); best = min(records, key=lambda x: x["score"])
            representative_index = min(groups[0], key=lambda i: records[i]["score"])
            representative = records[representative_index]
            seeds_in_dominant = len({records[i]["seed"] for i in groups[0]})
            geometry = sam_geometry(representative["atoms"], rec)
            protein = [a for a in rec if a["resname"] != "SAM"]
            pxyz = np.array([a["xyz"] for a in protein]); lxyz = np.array([a["xyz"] for a in representative["atoms"]])
            distances = np.linalg.norm(lxyz[:, None, :] - pxyz[None, :, :], axis=2)
            rep_contacts = contact_rows(analog, condition, 1, representative["atoms"], rec)
            contact_numbers = {int(x["residue_number"]) for x in rep_contacts}
            metrics = {"ligand_atom_burial_fraction_4p5A": round(float(np.mean(np.min(distances, axis=1) <= 4.5)), 4),
                       "protein_min_distance_A": round(float(np.min(distances)), 4),
                       "severe_clash_pairs_lt_2A": int(np.sum(distances < 2.0)),
                       "rear_195_203_contacts": ";".join(map(str, sorted(contact_numbers & set(range(195, 204))))),
                       "exit_228_237_contacts": ";".join(map(str, sorted(contact_numbers & set(range(228, 238))))),
                       "selectivity_sector_contacts": ";".join(f"{x['residue_name']}{x['residue_number']}" for x in rep_contacts if x["predefined_selectivity_sector"])}
            summaries[(condition, analog)] = {"best": best["score"], "seed_rank1": [records[i * 9]["score"] for i in range(3)],
                                               "dominant_fraction": len(groups[0]) / len(records),
                                               "dominant_seeds": seeds_in_dominant, "family_count": len(groups), **geometry, **metrics}
            representative_metrics.append({"analog_id": analog, "condition": condition, "paralog": condition[:2],
                                           "state": condition.split("_")[-1], **metrics, **geometry})
            for fi, group in enumerate(groups, 1):
                rep_index = min(group, key=lambda i: records[i]["score"]); rep = records[rep_index]
                family_rows.append({"analog_id": analog, "condition": condition, "paralog": condition[:2],
                                    "state": condition.split("_")[-1], "family": fi, "population": len(group),
                                    "population_fraction": round(len(group) / len(records), 6),
                                    "seed_count": len({records[i]["seed"] for i in group}),
                                    "representative_seed": rep["seed"], "representative_mode": rep["model"],
                                    "score_min": min(records[i]["score"] for i in group),
                                    "score_mean": round(float(np.mean([records[i]["score"] for i in group])), 5)})
                contacts.extend(contact_rows(analog, condition, fi, rep["atoms"], rec))
            for i, record in enumerate(records):
                pose_rows.append({"analog_id": analog, "condition": condition, "seed": record["seed"],
                                  "mode": record["model"], "score_kcal_mol": record["score"],
                                  "family": next(fi for fi, group in enumerate(groups, 1) if i in group),
                                  "dominant_family": i in dominant})
    write_csv("all_pose_results.csv", pose_rows); write_csv("pose_families.csv", family_rows)
    write_csv("representative_pose_contacts.csv", contacts)
    write_csv("representative_pose_metrics.csv", representative_metrics)

    matched = []
    for state in ("APO", "SAM"):
        for analog in [r["analog_id"] for r in panel]:
            a = summaries[(f"7A_{state}", analog)]; b = summaries[(f"7B_{state}", analog)]
            matched.append({"analog_id": analog, "state": state,
                            "vina_best_7A": a["best"], "vina_best_7B": b["best"],
                            "delta_7B_minus_7A_kcal_mol": round(float(b["best"] - a["best"]), 4),
                            "score_preference": "7A" if a["best"] < b["best"] else ("7B" if b["best"] < a["best"] else "TIE"),
                            "7A_seed_rank1_mean": round(float(np.mean(a["seed_rank1"])), 4),
                            "7B_seed_rank1_mean": round(float(np.mean(b["seed_rank1"])), 4),
                            "7A_seed_rank1_range": round(float(np.ptp(a["seed_rank1"])), 4),
                            "7B_seed_rank1_range": round(float(np.ptp(b["seed_rank1"])), 4),
                            "7A_dominant_family_fraction": round(float(a["dominant_fraction"]), 4),
                            "7B_dominant_family_fraction": round(float(b["dominant_fraction"]), 4),
                            "7A_dominant_family_seed_count": a["dominant_seeds"], "7B_dominant_family_seed_count": b["dominant_seeds"],
                            "7A_family_count": a["family_count"], "7B_family_count": b["family_count"]})
    write_csv("matched_ab_docking_results.csv", matched)

    geom = []
    for (condition, analog), values in summaries.items():
        geom.append({"analog_id": analog, "condition": condition, "paralog": condition[:2],
                     "state": condition.split("_")[-1], "plausible_METTL7_methyl_acceptor": next(x["plausible_METTL7_methyl_acceptor"] for x in chem if x["analog_id"] == analog),
                     "sam_min_distance_A": values["sam_min_distance_A"], "candidate_acceptor_atom": values["candidate_acceptor_atom"],
                     "acceptor_methyl_distance_A": values["acceptor_methyl_distance_A"],
                     "acceptor_methyl_sulfur_angle_deg": values["acceptor_methyl_sulfur_angle_deg"],
                     "near_attack_geometry": values["near_attack_geometry"]})
    write_csv("productive_nonproductive_geometry.csv", geom)

    contact_sets = defaultdict(set)
    for row in contacts:
        if int(row["family"]) == 1 and row["predefined_selectivity_sector"]:
            contact_sets[(row["condition"], row["analog_id"])].add(f"{row['residue_name']}{row['residue_number']}")
    dcmb_sets = {condition: contact_sets[(condition, "DCMB_R")] | contact_sets[(condition, "DCMB_S")]
                 for condition in ("7A_APO", "7A_SAM", "7B_APO", "7B_SAM")}
    signatures = []
    for analog in [r["analog_id"] for r in panel]:
        apos = next(x for x in matched if x["analog_id"] == analog and x["state"] == "APO")
        sams = next(x for x in matched if x["analog_id"] == analog and x["state"] == "SAM")
        overlaps = []
        for condition in dcmb_sets:
            current, reference = contact_sets[(condition, analog)], dcmb_sets[condition]
            overlaps.append(len(current & reference) / len(current | reference) if current | reference else 0.0)
        chem_row = next(x for x in chem if x["analog_id"] == analog)
        reproducible = min(apos["7A_dominant_family_seed_count"], apos["7B_dominant_family_seed_count"],
                           sams["7A_dominant_family_seed_count"], sams["7B_dominant_family_seed_count"]) >= 2
        same_gap_direction = apos["score_preference"] == next(x for x in matched if x["analog_id"] == "DCMB_R" and x["state"] == "APO")["score_preference"]
        resemblance = "HIGH" if float(chem_row["morgan_similarity_to_DCMB_R"]) >= .5 and np.mean(overlaps) >= .5 and reproducible and same_gap_direction else ("PARTIAL" if float(chem_row["morgan_similarity_to_DCMB_R"]) >= .3 or np.mean(overlaps) >= .35 else "LOW")
        signatures.append({"analog_id": analog, "chemical_similarity_to_DCMB_R": chem_row["morgan_similarity_to_DCMB_R"],
                           "apo_delta_7B_minus_7A": apos["delta_7B_minus_7A_kcal_mol"],
                           "sam_delta_7B_minus_7A": sams["delta_7B_minus_7A_kcal_mol"],
                           "same_APO_score_preference_as_DCMB_R": same_gap_direction,
                           "mean_DCMB_sector_contact_jaccard": round(float(np.mean(overlaps)), 4),
                           "pose_family_reproduced_in_at_least_2_seeds_all_conditions": reproducible,
                           "METTL7_productive_near_attack_observed": any(x["analog_id"] == analog and x["near_attack_geometry"] == "CANDIDATE" and x["plausible_METTL7_methyl_acceptor"] == "YES" for x in geom),
                           "rule_based_DCMB_signature_resemblance": resemblance})
    write_csv("dcmb_calibrated_signature.csv", signatures)

    protocol = json.loads((CAMPAIGN / "protocol.json").read_text())
    summary = {"analog_species": len(panel), "docking_jobs": len(pose_rows) // 9,
               "poses": len(pose_rows), "protocol": protocol,
               "interpretation_limits": ["Vina scores are not binding free energies", "R/S are separate modeled species",
                                         "near-attack geometry is geometric screening, not evidence of catalysis"]}
    (OUT / "analysis_summary.json").write_text(json.dumps(summary, indent=2) + "\n")


if __name__ == "__main__":
    main()
