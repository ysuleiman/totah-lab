#!/usr/bin/env python3
# DEPRECATED FOR V2 USE (2026-09-05): near-attack geometry in this script is superseded by
# athena.tmt.NearAttackGeometry / NearAttackAssessor / EnsembleNacAnalyzer (Java).
# See ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md. Retained for historical regression reproduction only.
"""Evaluate restraint sensitivity and residual clashes in HADDOCK3 RNA runs."""

from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path

import numpy as np


def parse_atoms(path: Path) -> list[dict[str, object]]:
    result = []
    for line in path.read_text().splitlines():
        if not line.startswith(("ATOM  ", "HETATM")):
            continue
        result.append({"name": line[12:16].strip(), "resname": line[17:20].strip(),
                       "chain": line[21:22], "resid": int(line[22:26]),
                       "element": (line[76:78].strip() or line[12:14].strip()[0]).upper(),
                       "xyz": np.array([float(line[30:38]), float(line[38:46]), float(line[46:54])])})
    return result


def main() -> None:
    parser = argparse.ArgumentParser(); parser.add_argument("--root", type=Path, required=True); args = parser.parse_args()
    rows = []
    for run_dir in sorted(args.root.glob("METTL7?_*")):
        capri_path = run_dir / "run/5_caprieval/capri_ss.tsv"
        if not capri_path.exists():
            continue
        scores = {Path(r["model"]).name: r for r in csv.DictReader(capri_path.open(), delimiter="\t")}
        enzyme, site_id = run_dir.name.split("_", 1)
        acceptor_name = "N7" if site_id.startswith(("KLF4", "NFKBIA")) else "N6"
        for pdb in sorted((run_dir / "run/4_flexref").glob("flexref_*.pdb")):
            atoms = parse_atoms(pdb)
            protein = [a for a in atoms if a["chain"] not in ("R", "S") and a["element"] != "H"]
            rna = [a for a in atoms if a["chain"] == "R" and a["element"] != "H"]
            anchors = {(int(a["resid"]), str(a["name"])): a for a in atoms if a["chain"] == "S"}
            acceptor = next(a for a in rna if a["resid"] == 3 and a["name"] == acceptor_name)
            ce, sd = anchors[(1, "SHA")]["xyz"], anchors[(2, "SHA")]["xyz"]
            target = acceptor["xyz"]
            ce_distance, sd_distance = float(np.linalg.norm(target-ce)), float(np.linalg.norm(target-sd))
            v1, v2 = target-ce, sd-ce
            angle = math.degrees(math.acos(float(np.dot(v1, v2) / (np.linalg.norm(v1)*np.linalg.norm(v2)))))
            distances = np.linalg.norm(np.array([a["xyz"] for a in protein])[:,None,:] - np.array([a["xyz"] for a in rna])[None,:,:], axis=2)
            capri = scores[pdb.name]
            rows.append({
                "enzyme": enzyme, "site_id": site_id, "model": pdb.name,
                "caprieval_rank": capri["caprieval_rank"], "haddock_score": capri["score"],
                "target_ce_distance_A": round(ce_distance, 3), "target_sd_distance_A": round(sd_distance, 3),
                "acceptor_methyl_sulfur_angle_deg": round(angle, 2),
                "distance_window_satisfied": 2.8 <= ce_distance <= 3.4,
                "approx_angle_window_satisfied": 150.0 <= angle <= 180.0,
                "severe_atom_pairs_lt_1_8A": int(np.count_nonzero(distances < 1.8)),
                "close_atom_pairs_lt_2_4A": int(np.count_nonzero(distances < 2.4)),
                "contact_atom_pairs_le_4_5A": int(np.count_nonzero(distances <= 4.5)),
                "interpretation_limit": "restrained flexible-refinement result; score is not affinity",
                "model_path": str(pdb),
            })
    output = args.root / "flexible_refinement_evaluation.csv"
    with output.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(rows)
    print(f"evaluated {len(rows)} flexibly refined structures")


if __name__ == "__main__":
    main()
