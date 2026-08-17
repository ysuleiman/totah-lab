#!/usr/bin/env python3
"""Stage-3 scaffold-aware ECFP4 diversity reduction for MCULE cohorts."""

from __future__ import annotations

import argparse
import csv
import gzip
import json
from collections import Counter, defaultdict
from pathlib import Path

from rdkit import Chem, DataStructs, rdBase
from rdkit.Chem import rdFingerprintGenerator, rdMolDescriptors
from rdkit.Chem.Scaffolds import MurckoScaffold
from rdkit.ML.Cluster import Butina


DEFAULT_SIMILARITY_COVERAGE = .65
RARE_SCAFFOLD_MAXIMUM = 3


def truth(value: str) -> bool:
    return value.lower() == "true"


def canonicalize(smiles: str) -> tuple[str, Chem.Mol]:
    mol = Chem.MolFromSmiles(smiles)
    if mol is None:
        raise ValueError(f"Invalid SMILES: {smiles}")
    return Chem.MolToSmiles(mol, canonical=True, isomericSmiles=True), mol


def scaffold_smiles(mol: Chem.Mol) -> str:
    scaffold = MurckoScaffold.GetScaffoldForMol(mol)
    return (Chem.MolToSmiles(scaffold, canonical=True, isomericSmiles=True)
            if scaffold.GetNumAtoms() else "[ACYCLIC]")


def distances(fingerprints) -> list[float]:
    result = []
    for index in range(1, len(fingerprints)):
        result.extend(1.0 - similarity for similarity in
                      DataStructs.BulkTanimotoSimilarity(
                          fingerprints[index], fingerprints[:index]))
    return result


def preference(row: dict[str, str], cohort: str) -> tuple[int, int]:
    fsp3 = float(row["fraction_sp3"])
    if cohort == "fragment":
        return (int(fsp3 >= .30), int(fsp3 >= .20))
    return (int(fsp3 >= .50), int(fsp3 >= .35))


def representative(cluster, group, fingerprints, cohort: str) -> int:
    best = cluster[0]
    best_key = None
    for candidate in cluster:
        similarities = [DataStructs.TanimotoSimilarity(
            fingerprints[candidate], fingerprints[other]) for other in cluster]
        key = (*preference(group[candidate], cohort),
               sum(similarities) / len(similarities),
               group[candidate]["canonical_smiles"], group[candidate]["mcule_id"])
        if best_key is None or key > best_key:
            best, best_key = candidate, key
    return best


def select(cohort: str, source_rows: list[dict[str, str]], generator,
           similarity_coverage: float):
    exact = {}
    duplicate_records = 0
    for source in source_rows:
        canonical, mol = canonicalize(source["canonical_smiles"])
        row = dict(source)
        row["canonical_smiles"] = canonical
        row["_mol"] = mol
        if canonical in exact:
            duplicate_records += 1
            continue
        exact[canonical] = row
    groups = defaultdict(list)
    for row in exact.values():
        row["_scaffold"] = scaffold_smiles(row["_mol"])
        groups[row["_scaffold"]].append(row)

    selected = []
    scaffold_stats = []
    for scaffold in sorted(groups):
        group = sorted(groups[scaffold], key=lambda row: row["canonical_smiles"])
        if len(group) <= RARE_SCAFFOLD_MAXIMUM:
            chosen = group
            clusters = tuple((index,) for index in range(len(group)))
        else:
            fingerprints = [generator.GetFingerprint(row["_mol"]) for row in group]
            clusters = Butina.ClusterData(
                distances(fingerprints), len(fingerprints),
                1.0 - similarity_coverage, isDistData=True, reordering=True)
            chosen = [group[representative(cluster, group, fingerprints, cohort)]
                      for cluster in clusters]
        selected.extend(chosen)
        scaffold_stats.append({
            "scaffold": scaffold, "input": len(group), "selected": len(chosen),
            "reduction": len(group) - len(chosen), "clusters": len(clusters),
        })
    return list(exact.values()), selected, scaffold_stats, duplicate_records


def composition(rows):
    counts = Counter()
    for row in rows:
        scaffold = MurckoScaffold.GetScaffoldForMol(row["_mol"])
        if scaffold.GetNumAtoms() == 0:
            counts["acyclic"] += 1
            continue
        aromatic = any(atom.GetIsAromatic() for atom in scaffold.GetAtoms())
        hetero = any(atom.GetAtomicNum() not in (1, 6) for atom in scaffold.GetAtoms())
        if aromatic and hetero:
            counts["heteroaromatic"] += 1
        elif aromatic:
            counts["carbocyclic_aromatic"] += 1
        else:
            counts["saturated_nonaromatic"] += 1
    return dict(counts)


def summarize(input_rows, unique_rows, selected, stats, duplicates):
    scaffold_sizes = Counter(row["_scaffold"] for row in unique_rows)
    selected_sizes = Counter(row["_scaffold"] for row in selected)
    return {
        "input_records": len(input_rows),
        "unique_exact_structures": len(unique_rows),
        "exact_duplicate_records_removed": duplicates,
        "input_scaffolds": len(scaffold_sizes),
        "input_singleton_scaffolds": sum(value == 1 for value in scaffold_sizes.values()),
        "selected_structures": len(selected),
        "retention_percent_of_unique": 100 * len(selected) / len(unique_rows),
        "selected_scaffolds": len(selected_sizes),
        "selected_singleton_scaffolds": sum(value == 1 for value in selected_sizes.values()),
        "largest_input_scaffold": max(scaffold_sizes.values(), default=0),
        "largest_selected_scaffold": max(selected_sizes.values(), default=0),
        "selected_composition": composition(selected),
        "top_reduced_scaffolds": sorted(stats, key=lambda row: row["reduction"],
                                         reverse=True)[:20],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage1-audit", type=Path, required=True)
    parser.add_argument("--stage2-audit", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--similarity-coverage", type=float,
                        default=DEFAULT_SIMILARITY_COVERAGE)
    args = parser.parse_args()
    if not 0.0 < args.similarity_coverage <= 1.0:
        parser.error("--similarity-coverage must be in (0, 1]")
    args.output.mkdir(parents=True, exist_ok=True)
    with gzip.open(args.stage1_audit, "rt", newline="") as stream:
        stage1 = {row["mcule_id"]: row for row in csv.DictReader(stream)}
    with gzip.open(args.stage2_audit, "rt", newline="") as stream:
        stage2 = list(csv.DictReader(stream))
    cohorts = {
        "drug_like": [stage1[row["mcule_id"]] for row in stage2
                      if truth(row["stage2_drug_like"])],
        "fragment": [stage1[row["mcule_id"]] for row in stage2
                     if truth(row["stage2_fragment"])],
    }
    generator = rdFingerprintGenerator.GetMorganGenerator(radius=2, fpSize=2048)
    report = {
        "schema": "mcule_stage3_scaffold_ecfp4_diversity_v1",
        "rdkit_version": rdBase.rdkitVersion,
        "policy": {
            "canonicalization": "RDKit canonical isomeric SMILES",
            "deduplication": "exact canonical structure within each cohort",
            "scaffold": "Bemis-Murcko including chirality in canonical structure",
            "rare_scaffolds": f"preserve all when scaffold family size <= {RARE_SCAFFOLD_MAXIMUM}",
            "redundancy_rule": f"one representative per within-scaffold ECFP4 cluster at Tanimoto >= {args.similarity_coverage}",
            "cohort_control": "drug-like and fragment cohorts selected independently",
            "fragment_representative_preference": "Fsp3 >=0.30, then >=0.20; preference never excludes a cluster",
            "fixed_output_target": None,
        },
        "cohorts": {},
    }
    selected_by_cohort = {}
    for cohort, rows in cohorts.items():
        unique, selected, stats, duplicates = select(
            cohort, rows, generator, args.similarity_coverage)
        selected_by_cohort[cohort] = selected
        report["cohorts"][cohort] = summarize(
            rows, unique, selected, stats, duplicates)
        with gzip.open(args.output / f"{cohort}-selected.smi.gz", "wt") as stream:
            for row in selected:
                stream.write(f'{row["canonical_smiles"]}\t{row["mcule_id"]}\t{row["_scaffold"]}\n')
        with (args.output / f"{cohort}-scaffolds.csv").open("w", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=stats[0].keys())
            writer.writeheader(); writer.writerows(stats)
    drug_structures = {row["canonical_smiles"] for row in selected_by_cohort["drug_like"]}
    fragment_structures = {row["canonical_smiles"] for row in selected_by_cohort["fragment"]}
    report["selected_overlap_exact_structures"] = len(drug_structures & fragment_structures)
    report["selected_union_exact_structures"] = len(drug_structures | fragment_structures)
    (args.output / "PROFILE.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
