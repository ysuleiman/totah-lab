#!/usr/bin/env python3
"""Profile a completed MCULE Stage-1 audit without changing filter policy."""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import math
import statistics
from collections import Counter
from pathlib import Path

from rdkit import Chem, DataStructs
from rdkit.Chem import rdFingerprintGenerator
from rdkit.Chem.Scaffolds import MurckoScaffold


DESCRIPTORS = (
    "mw", "formal_charge", "hbd", "hba", "rotatable_bonds", "tpsa",
    "clogp", "aromatic_rings", "heavy_atoms", "fraction_sp3",
    "dcmb_tanimoto",
)


def truth(value: str) -> bool:
    return value.lower() == "true"


def quantile(values: list[float], probability: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * probability
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def distribution(values: list[float]) -> dict[str, float | int | None]:
    return {
        "n": len(values),
        "min": min(values) if values else None,
        "p05": quantile(values, .05),
        "p10": quantile(values, .10),
        "p25": quantile(values, .25),
        "median": quantile(values, .50),
        "p75": quantile(values, .75),
        "p90": quantile(values, .90),
        "p95": quantile(values, .95),
        "max": max(values) if values else None,
        "mean": statistics.fmean(values) if values else None,
    }


def drug_gates(row: dict[str, str]):
    reasons = set(filter(None, row["drug_like_rejection_reasons"].split(";")))
    liabilities = bool(row["liability_findings"] or row["pains_matches"])
    return [
        ("valid_structure", truth(row["valid"])),
        ("single_connected_structure", "DISCONNECTED_STRUCTURE" not in reasons),
        ("mw_150_425", "MW_OUTSIDE_150_425" not in reasons),
        ("formal_charge_-1_0_1", "FORMAL_CHARGE_OUTSIDE_-1_0_1" not in reasons),
        ("hbd_0_3", "HBD_OUTSIDE_0_3" not in reasons),
        ("hba_1_7", "HBA_OUTSIDE_1_7" not in reasons),
        ("rotatable_bonds_le_6", "ROTATABLE_BONDS_GT_6" not in reasons),
        ("tpsa_20_100", "TPSA_OUTSIDE_20_100" not in reasons),
        ("clogp_0.5_4.5", "CLOGP_OUTSIDE_0.5_4.5" not in reasons),
        ("aromatic_rings_le_3", "AROMATIC_RINGS_GT_3" not in reasons),
        ("heavy_atoms_10_30", "HEAVY_ATOMS_OUTSIDE_10_30" not in reasons),
        ("no_flagged_liability", not liabilities),
        ("dcmb_tanimoto_le_0.75", float(row["dcmb_tanimoto"]) <= .75),
    ]


def fragment_gates(row: dict[str, str]):
    reasons = set(filter(None, row["fragment_rejection_reasons"].split(";")))
    liabilities = bool(row["liability_findings"] or row["pains_matches"])
    return [
        ("valid_structure", truth(row["valid"])),
        ("single_connected_structure", "DISCONNECTED_STRUCTURE" not in reasons),
        ("formal_charge_-1_0_1", "FORMAL_CHARGE_OUTSIDE_-1_0_1" not in reasons),
        ("ro3_mw_le_300", "RO3_MW_GT_300" not in reasons),
        ("ro3_clogp_le_3", "RO3_CLOGP_GT_3" not in reasons),
        ("ro3_hbd_le_3", "RO3_HBD_GT_3" not in reasons),
        ("ro3_hba_le_3", "RO3_HBA_GT_3" not in reasons),
        ("no_flagged_liability", not liabilities),
        ("dcmb_tanimoto_le_0.75", float(row["dcmb_tanimoto"]) <= .75),
    ]


def diversity(rows: list[dict[str, str]], fingerprint_sample_limit: int = 3000) -> dict[str, object]:
    generator = rdFingerprintGenerator.GetMorganGenerator(radius=2, fpSize=2048)
    scaffolds = Counter()
    fingerprints = []
    invalid = 0
    parsed = 0
    for row in rows:
        mol = Chem.MolFromSmiles(row["canonical_smiles"])
        if mol is None:
            invalid += 1
            continue
        parsed += 1
        scaffold = MurckoScaffold.MurckoScaffoldSmiles(mol=mol,
                                                       includeChirality=True)
        scaffolds[scaffold or "[ACYCLIC]"] += 1
        if len(fingerprints) < fingerprint_sample_limit:
            fingerprints.append(generator.GetFingerprint(mol))
    nearest = []
    for index, fingerprint in enumerate(fingerprints):
        similarities = DataStructs.BulkTanimotoSimilarity(
            fingerprint, fingerprints[index + 1:])
        for offset, value in enumerate(similarities, index + 1):
            if len(nearest) <= index:
                nearest.append(value)
            elif value > nearest[index]:
                nearest[index] = value
            while len(nearest) <= offset:
                nearest.append(value)
            if value > nearest[offset]:
                nearest[offset] = value
    return {
        "molecules": parsed,
        "invalid": invalid,
        "unique_bemis_murcko_scaffolds": len(scaffolds),
        "scaffold_singletons": sum(count == 1 for count in scaffolds.values()),
        "largest_scaffold_count": max(scaffolds.values(), default=0),
        "top_scaffolds": [{"scaffold": key, "count": count}
                          for key, count in scaffolds.most_common(15)],
        "nearest_neighbor_ecfp4_tanimoto": distribution(nearest),
        "fingerprint_sample_size": len(fingerprints),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    with gzip.open(args.audit, "rt", newline="") as stream:
        rows = list(csv.DictReader(stream))
    valid = [row for row in rows if truth(row["valid"])]
    eligible = [row for row in rows if row["status"] != "REJECTED_BEFORE_DOCKING"]
    rejected = [row for row in rows
                if row["status"] == "REJECTED_BEFORE_DOCKING"]
    fragments = [row for row in rows if truth(row["fragment_pool"])]
    drug_eligible = [row for row in valid if truth(row["drug_like_passes"])]
    fragment_eligible = [row for row in fragments if truth(row["fragment_passes"])]
    overlap = [row for row in valid if truth(row["drug_like_passes"])
               and truth(row["fragment_passes"])]

    gate_profiles = {}
    failure_combinations = Counter()
    rejection_reasons = Counter()
    for branch, population, gate_function in (
            ("drug_like", valid, drug_gates),
            ("fragment", fragments, fragment_gates)):
        independent = Counter()
        cumulative = Counter()
        branch_combinations = Counter()
        gate_names = [name for name, _ in gate_function(population[0])] if population else []
        for row in population:
            alive = True
            failures = []
            for name, passed in gate_function(row):
                if passed:
                    independent[name] += 1
                else:
                    failures.append(name)
                alive = alive and passed
                if alive:
                    cumulative[name] += 1
            branch_combinations[";".join(failures) or "PASS_ALL"] += 1
        profile_rows = []
        previous = len(population)
        for name in gate_names:
            current = cumulative[name]
            profile_rows.append({
                "branch": branch, "gate": name,
                "population": len(population),
                "independent_pass": independent[name],
                "independent_pass_percent": 100 * independent[name] / len(population),
                "cumulative_pass": current,
                "cumulative_pass_percent": 100 * current / len(population),
                "incremental_loss": previous - current,
            })
            previous = current
        gate_profiles[branch] = profile_rows
        failure_combinations[branch] = branch_combinations.most_common(30)
    for row in valid:
        rejection_reasons.update(filter(None, row["rejection_reasons"].split(";")))

    cohorts = {
        "all_valid": valid,
        "eligible_union": eligible,
        "drug_like_eligible": drug_eligible,
        "rejected": rejected,
        "fragment_pool": fragments,
        "fragment_eligible": fragment_eligible,
    }
    descriptor_profiles = {
        cohort: {descriptor: distribution([
            float(row[descriptor]) for row in subset if row[descriptor] != ""
        ]) for descriptor in DESCRIPTORS}
        for cohort, subset in cohorts.items()
    }
    profile = {
        "source_audit": str(args.audit),
        "counts": {
            "records": len(rows), "valid": len(valid),
            "eligible_union": len(eligible), "rejected": len(rejected),
            "drug_like_eligible": len(drug_eligible),
            "fragment_pool": len(fragments),
            "fragment_eligible": len(fragment_eligible),
            "branch_overlap": len(overlap),
            "drug_like_only": len(drug_eligible) - len(overlap),
            "fragment_only": len(fragment_eligible) - len(overlap),
            "lipinski_preferred": sum(truth(row["lipinski_preferred"])
                                       for row in valid),
            "ro3_applicable": sum(truth(row["ro3_applicable"]) for row in valid),
            "ro3_passes": sum(truth(row["ro3_passes"])
                              for row in valid if row["ro3_passes"]),
            "dcmb_primary": sum(row["dcmb_bucket"] == "PRIMARY_NOVEL_CHEMOTYPE"
                                for row in valid),
            "dcmb_control": sum(row["dcmb_bucket"] == "DCMB_NEIGHBORHOOD_CONTROL"
                                for row in valid),
            "dcmb_too_similar": sum(row["dcmb_bucket"] == "TOO_SIMILAR_TO_DCMB"
                                    for row in valid),
        },
        "gate_profiles": gate_profiles,
        "descriptor_profiles": descriptor_profiles,
        "rejection_reasons": rejection_reasons.most_common(),
        "failure_combinations": failure_combinations,
        "diversity": {
            "all_valid": diversity(valid),
            "eligible_union": diversity(eligible),
            "drug_like_eligible": diversity(drug_eligible),
            "fragment_eligible": diversity(fragment_eligible),
            "branch_overlap": diversity(overlap),
        },
    }
    (args.output / "profile.json").write_text(json.dumps(profile, indent=2) + "\n")
    with (args.output / "gate-survival.csv").open("w", newline="") as stream:
        combined_gates = gate_profiles["drug_like"] + gate_profiles["fragment"]
        writer = csv.DictWriter(stream, fieldnames=combined_gates[0].keys())
        writer.writeheader()
        writer.writerows(combined_gates)
    with (args.output / "descriptor-distributions.csv").open("w", newline="") as stream:
        fields = ["cohort", "descriptor", "n", "min", "p05", "p10", "p25",
                  "median", "p75", "p90", "p95", "max", "mean"]
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for cohort, descriptors in descriptor_profiles.items():
            for descriptor, values in descriptors.items():
                writer.writerow({"cohort": cohort, "descriptor": descriptor, **values})
    print(json.dumps(profile["counts"], indent=2))


if __name__ == "__main__":
    main()
