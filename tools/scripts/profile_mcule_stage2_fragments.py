#!/usr/bin/env python3
"""Profile candidate Stage-2 enrichment policies for Stage-1 MCULE fragments."""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import math
import statistics
from collections import Counter
from pathlib import Path

from rdkit import Chem, rdBase
from rdkit.Chem import rdMolDescriptors
from rdkit.Chem.Scaffolds import MurckoScaffold


DESCRIPTORS = ("mw", "clogp", "hbd", "hba", "rotatable_bonds", "tpsa",
               "aromatic_rings", "heavy_atoms", "fraction_sp3")
PRIMITIVES = (
    "aromatic_rings_le_1", "rotors_le_3", "rotors_le_2", "fsp3_ge_0.20",
    "fsp3_ge_0.30", "fsp3_ge_0.40", "directional_polar_feature",
    "not_flat_nonpolar",
)
POLICIES = {
    "permissive": ("rotors_le_3", "directional_polar_feature", "not_flat_nonpolar"),
    "balanced": ("aromatic_rings_le_1", "rotors_le_3",
                 "directional_polar_feature", "not_flat_nonpolar"),
    "aggressive": ("aromatic_rings_le_1", "rotors_le_2", "fsp3_ge_0.30",
                   "directional_polar_feature", "not_flat_nonpolar"),
    "combo_arom1_rot3_fsp20": ("aromatic_rings_le_1", "rotors_le_3",
                               "fsp3_ge_0.20"),
    "combo_arom1_rot3_fsp30": ("aromatic_rings_le_1", "rotors_le_3",
                               "fsp3_ge_0.30"),
    "combo_arom1_rot2_fsp40": ("aromatic_rings_le_1", "rotors_le_2",
                               "fsp3_ge_0.40"),
}


def truth(value: str) -> bool:
    return value.lower() == "true"


def quantile(values: list[float], probability: float) -> float | None:
    if not values:
        return None
    values = sorted(values)
    position = (len(values) - 1) * probability
    low, high = math.floor(position), math.ceil(position)
    return values[low] if low == high else values[low] + \
        (values[high] - values[low]) * (position - low)


def distribution(values: list[float]) -> dict[str, float | int | None]:
    return {
        "n": len(values), "min": min(values, default=None),
        "p10": quantile(values, .10), "p25": quantile(values, .25),
        "median": quantile(values, .50), "p75": quantile(values, .75),
        "p90": quantile(values, .90), "max": max(values, default=None),
        "mean": statistics.fmean(values) if values else None,
    }


def scaffold_category(scaffold: Chem.Mol | None) -> str:
    if scaffold is None or scaffold.GetNumAtoms() == 0:
        return "acyclic"
    aromatic = any(atom.GetIsAromatic() for atom in scaffold.GetAtoms())
    hetero = any(atom.GetAtomicNum() not in (1, 6) for atom in scaffold.GetAtoms())
    nonaromatic_ring = any(
        not all(scaffold.GetBondWithIdx(index).GetIsAromatic() for index in ring)
        for ring in scaffold.GetRingInfo().BondRings())
    if aromatic and hetero:
        return "heteroaromatic_mixed" if nonaromatic_ring else "heteroaromatic"
    if aromatic:
        return "carbocyclic_aromatic_mixed" if nonaromatic_ring else "carbocyclic_aromatic"
    return "saturated_nonaromatic"


def diversity(rows: list[dict[str, str]]) -> dict[str, object]:
    scaffolds = Counter()
    categories = Counter()
    scaffold_categories = {}
    for row in rows:
        mol = Chem.MolFromSmiles(row["canonical_smiles"])
        scaffold = MurckoScaffold.GetScaffoldForMol(mol)
        smiles = Chem.MolToSmiles(scaffold, isomericSmiles=True) if scaffold.GetNumAtoms() else "[ACYCLIC]"
        scaffolds[smiles] += 1
        category = scaffold_category(scaffold)
        categories[category] += 1
        scaffold_categories[smiles] = category
    return {
        "murcko_scaffold_count": len(scaffolds),
        "singleton_scaffold_count": sum(count == 1 for count in scaffolds.values()),
        "largest_scaffold_size": max(scaffolds.values(), default=0),
        "scaffold_composition_molecules": dict(categories),
        "scaffold_composition_unique": dict(Counter(scaffold_categories.values())),
        "top_scaffolds": [{"scaffold": scaffold, "count": count}
                          for scaffold, count in scaffolds.most_common(10)],
    }


def evaluate(row: dict[str, str]) -> tuple[dict[str, bool], dict[str, object]]:
    mol = Chem.MolFromSmiles(row["canonical_smiles"])
    aromatic = int(row["aromatic_rings"])
    rotors = int(row["rotatable_bonds"])
    fsp3 = float(row["fraction_sp3"])
    hbd, hba = int(row["hbd"]), int(row["hba"])
    directional = hbd > 0 or hba > 0
    aromatic_atoms = sum(atom.GetIsAromatic() for atom in mol.GetAtoms())
    heavy_atoms = mol.GetNumHeavyAtoms()
    flat_aromatic = aromatic > 0 and fsp3 == 0 and aromatic_atoms / heavy_atoms >= .60
    flags = {
        "aromatic_rings_le_1": aromatic <= 1,
        "rotors_le_3": rotors <= 3,
        "rotors_le_2": rotors <= 2,
        "fsp3_ge_0.20": fsp3 >= .20,
        "fsp3_ge_0.30": fsp3 >= .30,
        "fsp3_ge_0.40": fsp3 >= .40,
        "directional_polar_feature": directional,
        "not_flat_nonpolar": not (flat_aromatic and not directional),
    }
    annotations = {
        "heterocycle_count": rdMolDescriptors.CalcNumHeterocycles(mol),
        "heteroaromatic_ring_count": rdMolDescriptors.CalcNumAromaticHeterocycles(mol),
        "flat_aromatic": flat_aromatic,
    }
    return flags, annotations


def summarize(name: str, rows: list[dict[str, str]], drug_ids: set[str]) -> dict[str, object]:
    return {
        "name": name,
        "count": len(rows),
        "percent": None,
        "overlap_with_stage2_drug_count": sum(row["mcule_id"] in drug_ids for row in rows),
        "descriptor_distributions": {
            descriptor: distribution([float(row[descriptor]) for row in rows])
            for descriptor in DESCRIPTORS
        },
        "diversity": diversity(rows),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage1-audit", type=Path, required=True)
    parser.add_argument("--stage2-audit", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    with gzip.open(args.stage1_audit, "rt", newline="") as stream:
        fragments = [row for row in csv.DictReader(stream)
                     if truth(row["fragment_passes"])]
    with gzip.open(args.stage2_audit, "rt", newline="") as stream:
        drug_ids = {row["mcule_id"] for row in csv.DictReader(stream)
                    if truth(row["stage2_drug_like"])}

    evaluations = {}
    annotations = {}
    for row in fragments:
        evaluations[row["mcule_id"]], annotations[row["mcule_id"]] = evaluate(row)

    individual = []
    cohorts = {"stage1_fragments": fragments}
    for criterion in PRIMITIVES:
        survivors = [row for row in fragments if evaluations[row["mcule_id"]][criterion]]
        cohorts[f"individual_{criterion}"] = survivors
        individual.append({"criterion": criterion, "pass_count": len(survivors),
                           "pass_percent": 100 * len(survivors) / len(fragments)})

    cumulative = []
    for policy, gates in POLICIES.items():
        alive = fragments
        for gate in gates:
            alive = [row for row in alive if evaluations[row["mcule_id"]][gate]]
            cumulative.append({
                "policy": policy, "gate": gate, "pass_count": len(alive),
                "pass_percent": 100 * len(alive) / len(fragments),
            })
        cohorts[f"policy_{policy}"] = alive

    summaries = {}
    for name, rows in cohorts.items():
        summary = summarize(name, rows, drug_ids)
        summary["percent"] = 100 * len(rows) / len(fragments)
        summary["heteroaromatic_molecule_count"] = sum(
            annotations[row["mcule_id"]]["heteroaromatic_ring_count"] > 0 for row in rows)
        summary["heteroaromatic_molecule_percent"] = 100 * summary["heteroaromatic_molecule_count"] / len(rows) if rows else 0
        summaries[name] = summary

    with (args.output / "individual-criteria.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=individual[0].keys())
        writer.writeheader(); writer.writerows(individual)
    with (args.output / "cumulative-policies.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=cumulative[0].keys())
        writer.writeheader(); writer.writerows(cumulative)
    report = {
        "schema": "mcule_stage2_fragment_policy_profile_v1",
        "rdkit_version": rdBase.rdkitVersion,
        "population": len(fragments),
        "definitions": {
            "directional_polar_feature": "RDKit HBD > 0 or HBA > 0",
            "flat_nonpolar_exclusion": "reject aromatic, Fsp3=0, >=60% aromatic-heavy-atom compounds lacking directional polar feature",
            "policy_status": "candidate profiles only; no fragment policy selected",
        },
        "individual_criteria": individual,
        "cumulative_policies": cumulative,
        "cohorts": summaries,
    }
    (args.output / "PROFILE.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({
        "population": len(fragments),
        "individual": individual,
        "policy_final": [row for row in cumulative
                         if row["gate"] == POLICIES[row["policy"]][-1]],
    }, indent=2))


if __name__ == "__main__":
    main()
