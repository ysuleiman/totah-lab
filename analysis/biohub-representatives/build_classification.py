#!/usr/bin/env python3
"""Build the representative classification table from declared evidence rules."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path


ASSIGNMENTS = {
    "CLUSTER_01_REP": ("METTL7A-like", "high", "TMT1A annotation; strongest reviewed hit is human TMT1A; groups with cluster 2 near the TMT1A branch."),
    "CLUSTER_02_REP": ("METTL7A-like", "high", "TMT1A annotation; strongest reviewed hit is human TMT1A; groups with cluster 1 near the TMT1A branch."),
    "CLUSTER_03_REP": ("METTL7B-like", "moderate", "TMT1B-like annotation; strongest reviewed hit is mouse TMT1B; supported sister relationship with cluster 10."),
    "CLUSTER_04_REP": ("METTL7B-like", "very high", "Near-identical primate TMT1B sequence and strongest reviewed hit is human TMT1B; 21/21 pocket positions identical."),
    "CLUSTER_05_REP": ("METTL7B-like", "moderate", "TMT1B-like annotation and strongest reviewed hit is rat TMT1B, but pocket divergence and long branch reduce confidence."),
    "CLUSTER_06_REP": ("METTL7A-like", "moderate", "TMT1A-like annotation and stronger human TMT1A than TMT1B similarity; long branch warrants caution."),
    "CLUSTER_07_REP": ("METTL7A-like", "moderate", "METTL7A annotation, stronger TMT1A similarity, and a strongly supported sister relationship with cluster 8."),
    "CLUSTER_08_REP": ("Other SAM-dependent methyltransferase", "moderate", "Generic SAM-dependent methyltransferase annotation; no stable UniProt identity; TMT1A-like sequence signal is insufficient for subtype assignment."),
    "CLUSTER_09_REP": ("Other SAM-dependent methyltransferase", "high", "ubiE/COQ5 annotation, weak partial METTL7 similarity, long branch, and absence of the vicinal-cysteine motif."),
    "CLUSTER_10_REP": ("METTL7B-like", "moderate", "METTL7B-like annotation, strongest reviewed hit is rat TMT1B, and a supported sister relationship with cluster 3."),
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("analysis_dir", type=Path)
    args = parser.parse_args()
    hits = {}
    for line in (args.analysis_dir / "reciprocal_uniprot_hits.tsv").read_text().splitlines():
        f = line.split("\t")
        hits.setdefault(f[0], []).append({"subject": f[1], "title": f[2], "identity": f[3], "coverage": f[7], "evalue": f[8], "bitscore": f[9]})
    pocket = list(csv.DictReader((args.analysis_dir / "pocket_residue_conservation.csv").open()))
    cys = list(csv.DictReader((args.analysis_dir / "cysteine_motif_summary.csv").open()))
    rows = []
    for sequence_id, (classification, confidence, rationale) in ASSIGNMENTS.items():
        top = hits[sequence_id][0]
        pocket_rows = [r for r in pocket if r["sequence_id"] == sequence_id]
        identical = sum(r["classification"] == "identical" for r in pocket_rows)
        aligned_cc = any(r["sequence_id"] == sequence_id and r["motif"] == "CC" and r["aligns_with_mettl7b_cys202_cys203_region"] == "yes" for r in cys)
        rows.append({
            "sequence_id": sequence_id,
            "classification": classification,
            "confidence": confidence,
            "top_reviewed_uniprot_hit": top["subject"],
            "top_hit_description": top["title"],
            "top_hit_percent_identity": top["identity"],
            "top_hit_query_coverage_percent": top["coverage"],
            "top_hit_evalue": top["evalue"],
            "top_hit_bitscore": top["bitscore"],
            "mettl7b_pocket_identical_positions_of_21": identical,
            "aligned_vicinal_cc": "yes" if aligned_cc else "no",
            "rationale": rationale,
        })
    output = args.analysis_dir / "representative_classification.csv"
    with output.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    main()
