#!/usr/bin/env python3
"""Profile global cross-scaffold ECFP4 coverage after Stage-2 enrichment."""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import math
import statistics
from collections import Counter, defaultdict
from pathlib import Path

from rdkit import Chem, DataStructs, rdBase
from rdkit.Chem import rdFingerprintGenerator, rdMolDescriptors
from rdkit.Chem.Scaffolds import MurckoScaffold
from rdkit.ML.Cluster import Butina


THRESHOLDS = (.50, .45, .40, .35, .30)
WITHIN_FAMILY_SIMILARITY = .35
RARE_MAXIMUM = 3


def truth(value: str) -> bool:
    return value.lower() == "true"


def quantile(values: list[float], probability: float):
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


def scaffold(mol: Chem.Mol, generic: bool = False) -> str:
    value = MurckoScaffold.GetScaffoldForMol(mol)
    if value.GetNumAtoms() == 0:
        return "[ACYCLIC]"
    if generic:
        value = MurckoScaffold.MakeScaffoldGeneric(value)
    return Chem.MolToSmiles(value, canonical=True)


def butina_clusters(fingerprints, similarity: float):
    distances = []
    for index in range(1, len(fingerprints)):
        distances.extend(1.0 - value for value in
                         DataStructs.BulkTanimotoSimilarity(
                             fingerprints[index], fingerprints[:index]))
    return Butina.ClusterData(distances, len(fingerprints), 1.0 - similarity,
                              isDistData=True, reordering=True)


def load_cohorts(stage1_path: Path, stage2_path: Path):
    with gzip.open(stage1_path, "rt", newline="") as stream:
        stage1 = {row["mcule_id"]: row for row in csv.DictReader(stream)}
    with gzip.open(stage2_path, "rt", newline="") as stream:
        stage2 = list(csv.DictReader(stream))
    result = {}
    for cohort, field in (("drug_like", "stage2_drug_like"),
                          ("fragment", "stage2_fragment")):
        exact = {}
        for stage2_row in stage2:
            if not truth(stage2_row[field]):
                continue
            row = dict(stage1[stage2_row["mcule_id"]])
            mol = Chem.MolFromSmiles(row["canonical_smiles"])
            canonical = Chem.MolToSmiles(mol, canonical=True, isomericSmiles=True)
            if canonical in exact:
                continue
            row["canonical_smiles"], row["_mol"] = canonical, mol
            row["_exact_scaffold"] = scaffold(mol)
            row["_generic_scaffold"] = scaffold(mol, generic=True)
            exact[canonical] = row
        result[cohort] = list(exact.values())
    return result


def protected_indices(rows, cohort: str) -> set[int]:
    sizes = Counter(row["_exact_scaffold"] for row in rows)
    protected = {index for index, row in enumerate(rows)
                 if sizes[row["_exact_scaffold"]] <= RARE_MAXIMUM}
    if cohort == "fragment":
        for index, row in enumerate(rows):
            mol = row["_mol"]
            polar = int(row["hbd"]) > 0 or int(row["hba"]) > 0
            heteroaromatic = rdMolDescriptors.CalcNumAromaticHeterocycles(mol) > 0
            if polar and heteroaromatic and float(row["fraction_sp3"]) < .30:
                protected.add(index)
    return protected


def family_reduce(rows, fingerprints, protected, mode: str):
    field = "_exact_scaffold" if mode == "exact" else "_generic_scaffold"
    groups = defaultdict(list)
    for index, row in enumerate(rows):
        groups[row[field]].append(index)
    retained = set()
    for members in groups.values():
        local_fps = [fingerprints[index] for index in members]
        for cluster in butina_clusters(local_fps, WITHIN_FAMILY_SIMILARITY):
            global_cluster = [members[index] for index in cluster]
            protected_members = [index for index in global_cluster
                                 if index in protected]
            if protected_members:
                retained.update(protected_members)
            else:
                retained.add(global_cluster[0])
    return sorted(retained)


def global_trajectory(pool, fingerprints, protected):
    chosen = [index for index in pool if index in protected]
    if not chosen:
        chosen.append(pool[0])
    chosen_set = set(chosen)
    remaining = [index for index in pool if index not in chosen_set]
    maximum = {index: 0.0 for index in remaining}
    for seed in chosen:
        values = DataStructs.BulkTanimotoSimilarity(
            fingerprints[seed], [fingerprints[index] for index in remaining])
        for index, value in zip(remaining, values):
            maximum[index] = max(maximum[index], value)
    snapshots = {}
    thresholds = sorted(THRESHOLDS)
    while remaining:
        least = min(remaining, key=lambda index: (maximum[index], index))
        minimum_similarity = maximum[least]
        for threshold in thresholds:
            if threshold not in snapshots and minimum_similarity >= threshold:
                snapshots[threshold] = list(chosen)
        if len(snapshots) == len(thresholds):
            break
        chosen.append(least)
        remaining.remove(least)
        values = DataStructs.BulkTanimotoSimilarity(
            fingerprints[least], [fingerprints[index] for index in remaining])
        for index, value in zip(remaining, values):
            maximum[index] = max(maximum[index], value)
    for threshold in thresholds:
        snapshots.setdefault(threshold, list(chosen))
    return snapshots


def nearest_selected(pool, selected, fingerprints):
    selected_set = set(selected)
    discarded = [index for index in pool if index not in selected_set]
    values = []
    selected_fps = [fingerprints[index] for index in selected]
    for index in discarded:
        values.append(max(DataStructs.BulkTanimotoSimilarity(
            fingerprints[index], selected_fps), default=0.0))
    return distribution(values)


def enforce_global_coverage(selected, fingerprints, threshold: float):
    selected = list(selected)
    selected_set = set(selected)
    remaining = [index for index in range(len(fingerprints))
                 if index not in selected_set]
    selected_fps = [fingerprints[index] for index in selected]
    maximum = {
        index: max(DataStructs.BulkTanimotoSimilarity(
            fingerprints[index], selected_fps), default=0.0)
        for index in remaining
    }
    while remaining:
        least = min(remaining, key=lambda index: (maximum[index], index))
        if maximum[least] >= threshold:
            break
        selected.append(least)
        remaining.remove(least)
        values = DataStructs.BulkTanimotoSimilarity(
            fingerprints[least], [fingerprints[index] for index in remaining])
        for index, value in zip(remaining, values):
            maximum[index] = max(maximum[index], value)
    return selected


def result(rows, pool, selected, fingerprints, protected):
    selected_rows = [rows[index] for index in selected]
    exact_all = {row["_exact_scaffold"] for row in rows}
    exact_selected = {row["_exact_scaffold"] for row in selected_rows}
    generic_selected = Counter(row["_generic_scaffold"] for row in selected_rows)
    heteroaromatic = sum(rdMolDescriptors.CalcNumAromaticHeterocycles(
        row["_mol"]) > 0 for row in selected_rows)
    fsp3 = [float(row["fraction_sp3"]) for row in selected_rows]
    return {
        "pre_global_pool": len(pool), "selected": len(selected),
        "retained_percent_of_stage2_unique": 100 * len(selected) / len(rows),
        "protected_selected": sum(index in protected for index in selected),
        "exact_scaffolds_covered": len(exact_selected),
        "exact_scaffold_coverage_percent": 100 * len(exact_selected) / len(exact_all),
        "heteroaromatic_selected": heteroaromatic,
        "heteroaromatic_percent": 100 * heteroaromatic / len(selected),
        "fsp3_distribution": distribution(fsp3),
        "discarded_nearest_selected_ecfp4": nearest_selected(
            range(len(rows)), selected, fingerprints),
        "top_generic_scaffold_families": [
            {"family": family, "selected": count}
            for family, count in generic_selected.most_common(15)],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage1-audit", type=Path, required=True)
    parser.add_argument("--stage2-audit", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    cohorts = load_cohorts(args.stage1_audit, args.stage2_audit)
    generator = rdFingerprintGenerator.GetMorganGenerator(radius=2, fpSize=2048)
    report = {
        "schema": "mcule_stage3b_global_cross_scaffold_profile_v1",
        "rdkit_version": rdBase.rdkitVersion,
        "thresholds": THRESHOLDS,
        "within_family_similarity": WITHIN_FAMILY_SIMILARITY,
        "protection": ["exact Murcko scaffold size <=3",
                       "fragment polar heteroaromatic with Fsp3 <0.30"],
        "fixed_output_quota": None,
        "profiles": {},
        "combined": {},
    }
    selected_structures = defaultdict(dict)
    for cohort, rows in cohorts.items():
        fingerprints = [generator.GetFingerprint(row["_mol"]) for row in rows]
        protected = protected_indices(rows, cohort)
        stage2_heteroaromatic = sum(
            rdMolDescriptors.CalcNumAromaticHeterocycles(row["_mol"]) > 0
            for row in rows)
        report["profiles"][cohort] = {
            "stage2_unique": len(rows), "protected": len(protected),
            "stage2_heteroaromatic": stage2_heteroaromatic,
            "stage2_fsp3_distribution": distribution(
                [float(row["fraction_sp3"]) for row in rows]),
            "modes": {}}
        for mode in ("exact", "generic"):
            pool = family_reduce(rows, fingerprints, protected, mode)
            snapshots = global_trajectory(pool, fingerprints, protected)
            mode_results = {}
            for threshold in THRESHOLDS:
                selected = enforce_global_coverage(
                    snapshots[threshold], fingerprints, threshold)
                selected_structures[(mode, threshold)][cohort] = {
                    rows[index]["canonical_smiles"] for index in selected}
                mode_results[str(threshold)] = result(
                    rows, pool, selected, fingerprints, protected)
            report["profiles"][cohort]["modes"][mode] = mode_results
    for (mode, threshold), values in selected_structures.items():
        drug = values["drug_like"]
        fragment = values["fragment"]
        report["combined"].setdefault(mode, {})[str(threshold)] = {
            "drug_like": len(drug), "fragment": len(fragment),
            "exact_structure_overlap": len(drug & fragment),
            "exact_structure_union": len(drug | fragment),
        }
    (args.output / "PROFILE.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
