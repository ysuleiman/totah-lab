#!/usr/bin/env python3
"""Apply METTL7B hypothesis enrichment to a completed Stage-1 audit."""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import math
import statistics
from collections import Counter
from pathlib import Path

from rdkit import Chem, DataStructs, rdBase
from rdkit.Chem import rdFingerprintGenerator, rdMolDescriptors
from rdkit.Chem.Scaffolds import MurckoScaffold


OUTPUT_FIELDS = [
    "source_index", "mcule_id", "canonical_smiles",
    "stage1_drug_like", "stage1_fragment", "stage2_drug_like",
    "stage2_fragment", "stage2_union", "stage2_drug_reasons",
    "stage2_fragment_reasons", "directional_polar_feature",
    "flat_nonpolar_nuisance",
    "fused_aromatic_ring_system_max", "heterocycle_count",
    "aliphatic_ring_count", "stereocenter_count", "compact_3d_preferred",
    "fragment_aromatic_preferred", "fragment_fsp3_preferred",
]
DRUG_GATES = [
    "mw_180_340", "aromatic_rings_le_2", "rotatable_bonds_le_4",
    "fraction_sp3_ge_0.35", "tpsa_25_80", "clogp_1.0_3.5",
    "hbd_0_2", "hba_1_5", "no_fused_polyaromatic_system",
    "directional_polar_feature",
]
FRAGMENT_GATES = ["rotatable_bonds_le_3", "aromatic_rings_le_1",
                  "directional_polar_feature", "not_flat_nonpolar_nuisance"]


def truth(value: str) -> bool:
    return value.lower() == "true"


def fused_aromatic_system_max(mol: Chem.Mol) -> int:
    rings = [set(ring) for ring in mol.GetRingInfo().BondRings()
             if all(mol.GetBondWithIdx(index).GetIsAromatic() for index in ring)]
    if not rings:
        return 0
    seen = set()
    maximum = 1
    for start in range(len(rings)):
        if start in seen:
            continue
        stack = [start]
        component = set()
        while stack:
            current = stack.pop()
            if current in component:
                continue
            component.add(current)
            seen.add(current)
            stack.extend(other for other in range(len(rings))
                         if other not in component and rings[current] & rings[other])
        maximum = max(maximum, len(component))
    return maximum


def evaluate(row: dict[str, str]) -> tuple[dict[str, object], dict[str, bool], dict[str, bool]]:
    mol = Chem.MolFromSmiles(row["canonical_smiles"])
    if mol is None:
        raise ValueError(f'Invalid canonical SMILES for {row["mcule_id"]}')
    mw = float(row["mw"])
    aromatic = int(row["aromatic_rings"])
    rotors = int(row["rotatable_bonds"])
    fsp3 = float(row["fraction_sp3"])
    tpsa = float(row["tpsa"])
    clogp = float(row["clogp"])
    hbd = int(row["hbd"])
    hba = int(row["hba"])
    fused = fused_aromatic_system_max(mol)
    directional = hbd > 0 or hba > 0
    drug = {
        "mw_180_340": 180 <= mw <= 340,
        "aromatic_rings_le_2": aromatic <= 2,
        "rotatable_bonds_le_4": rotors <= 4,
        "fraction_sp3_ge_0.35": fsp3 >= .35,
        "tpsa_25_80": 25 <= tpsa <= 80,
        "clogp_1.0_3.5": 1 <= clogp <= 3.5,
        "hbd_0_2": hbd <= 2,
        "hba_1_5": 1 <= hba <= 5,
        "no_fused_polyaromatic_system": fused <= 1,
        "directional_polar_feature": directional,
    }
    aromatic_atom_fraction = (sum(atom.GetIsAromatic() for atom in mol.GetAtoms())
                              / mol.GetNumHeavyAtoms())
    flat_nonpolar = aromatic > 0 and fsp3 == 0 and aromatic_atom_fraction >= .60 \
        and not directional
    fragment = {
        "rotatable_bonds_le_3": rotors <= 3,
        "aromatic_rings_le_1": aromatic <= 1,
        "directional_polar_feature": directional,
        "not_flat_nonpolar_nuisance": not flat_nonpolar,
    }
    heterocycles = rdMolDescriptors.CalcNumHeterocycles(mol)
    aliphatic_rings = rdMolDescriptors.CalcNumAliphaticRings(mol)
    stereocenters = len(Chem.FindMolChiralCenters(
        mol, includeUnassigned=True, includeCIP=True))
    compact_3d = (fsp3 >= .50 or aliphatic_rings > 0 or stereocenters > 0) \
        and aromatic_atom_fraction <= .60
    stage1_drug = truth(row["drug_like_passes"])
    stage1_fragment = truth(row["fragment_passes"])
    stage2_drug = stage1_drug and all(drug.values())
    stage2_fragment = stage1_fragment and all(fragment.values())
    output = {
        "source_index": row["source_index"], "mcule_id": row["mcule_id"],
        "canonical_smiles": row["canonical_smiles"],
        "stage1_drug_like": stage1_drug, "stage1_fragment": stage1_fragment,
        "stage2_drug_like": stage2_drug, "stage2_fragment": stage2_fragment,
        "stage2_union": stage2_drug or stage2_fragment,
        "stage2_drug_reasons": ";".join(name for name, passed in drug.items()
                                         if not passed),
        "stage2_fragment_reasons": (";".join(name for name, passed in fragment.items()
                                              if not passed)
                                     if stage1_fragment else "NOT_STAGE1_FRAGMENT"),
        "directional_polar_feature": directional,
        "flat_nonpolar_nuisance": flat_nonpolar,
        "fused_aromatic_ring_system_max": fused,
        "heterocycle_count": heterocycles,
        "aliphatic_ring_count": aliphatic_rings,
        "stereocenter_count": stereocenters,
        "compact_3d_preferred": compact_3d,
        "fragment_aromatic_preferred": aromatic <= 1,
        "fragment_fsp3_preferred": fsp3 >= .30,
    }
    return output, drug, fragment


def quantile(values: list[float], probability: float) -> float | None:
    if not values:
        return None
    values = sorted(values)
    position = (len(values) - 1) * probability
    low, high = math.floor(position), math.ceil(position)
    return values[low] if low == high else values[low] + \
        (values[high] - values[low]) * (position - low)


def distribution(values: list[float]) -> dict[str, object]:
    return {"n": len(values), "min": min(values, default=None),
            "p10": quantile(values, .1), "p25": quantile(values, .25),
            "median": quantile(values, .5), "p75": quantile(values, .75),
            "p90": quantile(values, .9), "max": max(values, default=None),
            "mean": statistics.fmean(values) if values else None}


def diversity(rows: list[dict[str, str]]) -> dict[str, object]:
    scaffolds = Counter()
    fps = []
    generator = rdFingerprintGenerator.GetMorganGenerator(radius=2, fpSize=2048)
    for row in rows:
        mol = Chem.MolFromSmiles(row["canonical_smiles"])
        scaffold = MurckoScaffold.MurckoScaffoldSmiles(mol=mol,
                                                       includeChirality=True)
        scaffolds[scaffold or "[ACYCLIC]"] += 1
        if len(fps) < 3000:
            fps.append(generator.GetFingerprint(mol))
    nearest = [0.0] * len(fps)
    for index, fingerprint in enumerate(fps):
        for offset, similarity in enumerate(DataStructs.BulkTanimotoSimilarity(
                fingerprint, fps[index + 1:]), index + 1):
            nearest[index] = max(nearest[index], similarity)
            nearest[offset] = max(nearest[offset], similarity)
    return {
        "molecules": len(rows),
        "unique_bemis_murcko_scaffolds": len(scaffolds),
        "scaffold_singletons": sum(value == 1 for value in scaffolds.values()),
        "largest_scaffold_count": max(scaffolds.values(), default=0),
        "top_scaffolds": [{"scaffold": key, "count": value}
                          for key, value in scaffolds.most_common(15)],
        "nearest_neighbor_ecfp4_tanimoto": distribution(nearest),
        "fingerprint_sample_size": len(fps),
    }


def gate_profile(population, evaluations, gates, branch):
    rows = []
    alive = [True] * len(population)
    for gate in gates:
        values = [evaluation[gate] for evaluation in evaluations]
        alive = [prior and current for prior, current in zip(alive, values)]
        rows.append({"branch": branch, "gate": gate,
                     "population": len(population),
                     "individual_pass": sum(values),
                     "individual_pass_percent": 100 * sum(values) / len(values),
                     "cumulative_pass": sum(alive),
                     "cumulative_pass_percent": 100 * sum(alive) / len(alive)})
    return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage1-audit", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    with gzip.open(args.stage1_audit, "rt", newline="") as stream:
        source = list(csv.DictReader(stream))
    stage1_drug = [row for row in source if truth(row["drug_like_passes"])]
    stage1_fragment = [row for row in source if truth(row["fragment_passes"])]
    evaluated = {}
    drug_eval = []
    fragment_eval = []
    with gzip.open(args.output / "stage2-audit.csv.gz", "wt", newline="") as stream, \
            gzip.open(args.output / "stage2-survivors.smi.gz", "wt") as survivors:
        writer = csv.DictWriter(stream, fieldnames=OUTPUT_FIELDS)
        writer.writeheader()
        for row in source:
            if not truth(row["drug_like_passes"]) and not truth(row["fragment_passes"]):
                continue
            result, drug, fragment = evaluate(row)
            evaluated[row["mcule_id"]] = result
            writer.writerow(result)
            if truth(row["drug_like_passes"]): drug_eval.append(drug)
            if truth(row["fragment_passes"]): fragment_eval.append(fragment)
            if result["stage2_union"]:
                survivors.write(f'{row["canonical_smiles"]}\t{row["mcule_id"]}\t'
                                f'{result["stage2_drug_like"]}\t'
                                f'{result["stage2_fragment"]}\n')
    drug_survivors = [row for row in stage1_drug
                      if evaluated[row["mcule_id"]]["stage2_drug_like"]]
    fragment_survivors = [row for row in stage1_fragment
                          if evaluated[row["mcule_id"]]["stage2_fragment"]]
    overlap_ids = {row["mcule_id"] for row in drug_survivors} & \
        {row["mcule_id"] for row in fragment_survivors}
    union = {row["mcule_id"]: row for row in drug_survivors + fragment_survivors}
    gate_rows = gate_profile(stage1_drug, drug_eval, DRUG_GATES, "drug_like") + \
        gate_profile(stage1_fragment, fragment_eval, FRAGMENT_GATES, "fragment")
    with (args.output / "gate-survival.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=gate_rows[0].keys())
        writer.writeheader(); writer.writerows(gate_rows)
    descriptors = ("mw", "clogp", "hbd", "hba", "rotatable_bonds", "tpsa",
                   "aromatic_rings", "heavy_atoms", "fraction_sp3", "dcmb_tanimoto")
    profiles = {}
    for name, rows in (("stage1_drug", stage1_drug),
                       ("stage2_drug", drug_survivors),
                       ("stage1_fragment", stage1_fragment),
                       ("stage2_fragment", fragment_survivors),
                       ("stage2_union", list(union.values()))):
        profiles[name] = {descriptor: distribution([
            float(row[descriptor]) for row in rows]) for descriptor in descriptors}
    counts = {
        "sample": len(source), "stage1_drug": len(stage1_drug),
        "stage1_fragment": len(stage1_fragment),
        "stage2_drug": len(drug_survivors),
        "stage2_fragment": len(fragment_survivors),
        "stage2_overlap": len(overlap_ids), "stage2_union": len(union),
        "stage2_drug_percent_of_stage1_drug": 100 * len(drug_survivors) / len(stage1_drug),
        "stage2_fragment_percent_of_stage1_fragment": 100 * len(fragment_survivors) / len(stage1_fragment),
        "stage2_union_percent_of_sample": 100 * len(union) / len(source),
        "stage2_compact_3d_preferred": sum(
            evaluated[key]["compact_3d_preferred"] for key in union),
        "stage2_fragment_aromatic_preferred": sum(
            evaluated[row["mcule_id"]]["fragment_aromatic_preferred"]
            for row in fragment_survivors),
        "stage2_fragment_fsp3_preferred": sum(
            evaluated[row["mcule_id"]]["fragment_fsp3_preferred"]
            for row in fragment_survivors),
    }
    report = {
        "schema": "mcule_mettl7b_stage2_enrichment_profile_v1",
        "stage1_audit": str(args.stage1_audit),
        "rdkit_version": rdBase.rdkitVersion,
        "policy": {"drug_like_hard_gates": DRUG_GATES,
                   "fragment_hard_gates": FRAGMENT_GATES,
                   "fragment_preferences": ["fraction_sp3_ge_0.30", "fraction_sp3_ge_0.20"],
                   "note": "No DCMB/TSL similarity requirement and no amine requirement."},
        "counts": counts, "gate_profile": gate_rows,
        "descriptor_profiles": profiles,
        "diversity": {
            "stage2_drug": diversity(drug_survivors),
            "stage2_fragment": diversity(fragment_survivors),
            "stage2_union": diversity(list(union.values())),
        },
    }
    (args.output / "PROFILE.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(counts, indent=2))


if __name__ == "__main__":
    main()
