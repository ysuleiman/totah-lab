#!/usr/bin/env python3
"""Derive alignment, pocket, motif, identity, and classification tables.

Only the 12-sequence representative panel is processed.  Canonical residue
positions are one-based positions in human UniProt Q6UX53 (METTL7B).
"""

from __future__ import annotations

import argparse
import csv
import math
import re
from collections import Counter
from pathlib import Path


POCKET = [
    (33, "K"), (36, "F"), (44, "T"), (55, "K"), (77, "L"),
    (78, "G"), (79, "C"), (127, "G"), (144, "T"), (145, "L"),
    (148, "C"), (149, "S"), (151, "Q"), (175, "H"), (195, "W"),
    (196, "K"), (199, "G"), (200, "D"), (201, "G"), (202, "C"),
    (203, "C"),
]

# Deliberately coarse physicochemical groups; documented in the report.
GROUPS = [set("AVLIM"), set("FWY"), set("STNQ"), set("KRH"), set("DE")]


def read_fasta(path: Path) -> dict[str, str]:
    records: dict[str, str] = {}
    current = None
    for raw in path.read_text().splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith(">"):
            current = line[1:].split()[0]
            if current in records:
                raise ValueError(f"duplicate FASTA identifier: {current}")
            records[current] = ""
        elif current is None:
            raise ValueError("sequence before first FASTA header")
        else:
            records[current] += line.upper()
    lengths = {len(sequence) for sequence in records.values()}
    if len(lengths) != 1:
        raise ValueError(f"input is not aligned: lengths={sorted(lengths)}")
    return records


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, object]]) -> None:
    with path.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def position_columns(reference: str) -> dict[int, int]:
    result = {}
    residue_number = 0
    for column, residue in enumerate(reference):
        if residue != "-":
            residue_number += 1
            result[residue_number] = column
    return result


def substitution(reference: str, observed: str) -> str:
    if observed == "-":
        return "insertion/deletion"
    if observed == reference:
        return "identical"
    if any(reference in group and observed in group for group in GROUPS):
        return "conservative substitution"
    return "non-conservative substitution"


def pairwise_identity(a: str, b: str) -> tuple[float, int]:
    comparable = [(x, y) for x, y in zip(a, b) if x != "-" and y != "-"]
    if not comparable:
        return 0.0, 0
    matches = sum(x == y for x, y in comparable)
    return 100.0 * matches / len(comparable), len(comparable)


def motif_matches(sequence: str):
    patterns = [("CCC", "CCC"), ("CC", "CC"), ("CXC", "C.C"),
                ("CXXC", "C..C"), ("CXXXC", "C...C")]
    for label, pattern in patterns:
        for match in re.finditer(f"(?=({pattern}))", sequence):
            yield label, match.start() + 1, match.group(1)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("alignment", type=Path)
    parser.add_argument("output_dir", type=Path)
    args = parser.parse_args()
    seqs = read_fasta(args.alignment)
    if set(seqs) != {"HUMAN_METTL7A", "HUMAN_METTL7B", *[f"CLUSTER_{i:02d}_REP" for i in range(1, 11)]}:
        raise ValueError("alignment must contain exactly the two references and ten representatives")

    ref = seqs["HUMAN_METTL7B"]
    columns = position_columns(ref)
    for position, expected in POCKET:
        observed = ref[columns[position]]
        if observed != expected:
            raise ValueError(f"Q6UX53 position {position}: expected {expected}, observed {observed}")

    pocket_rows = []
    for position, expected in POCKET:
        column = columns[position]
        for sequence_id, sequence in seqs.items():
            observed = sequence[column]
            pocket_rows.append({
                "mettl7b_position": position,
                "mettl7b_residue": expected,
                "alignment_column_one_based": column + 1,
                "sequence_id": sequence_id,
                "observed_residue": observed,
                "classification": substitution(expected, observed),
            })
    write_csv(args.output_dir / "pocket_residue_conservation.csv",
              list(pocket_rows[0]), pocket_rows)

    c202_col, c203_col = columns[202], columns[203]
    motif_rows = []
    for sequence_id, aligned in seqs.items():
        ungapped = aligned.replace("-", "")
        ungapped_to_column = [i for i, aa in enumerate(aligned) if aa != "-"]
        for motif, start, match in motif_matches(ungapped):
            start_col = ungapped_to_column[start - 1]
            end_col = ungapped_to_column[start + len(match) - 2]
            left = max(0, start - 1 - 8)
            right = min(len(ungapped), start - 1 + len(match) + 8)
            overlaps = start_col <= c203_col and end_col >= c202_col
            motif_rows.append({
                "sequence_id": sequence_id,
                "motif": motif,
                "start_one_based": start,
                "end_one_based": start + len(match) - 1,
                "matched_sequence": match,
                "sequence_context": ungapped[left:right],
                "alignment_start_one_based": start_col + 1,
                "alignment_end_one_based": end_col + 1,
                "aligns_with_mettl7b_cys202_cys203_region": "yes" if overlaps else "no",
            })
    write_csv(args.output_dir / "cysteine_motif_summary.csv",
              list(motif_rows[0]), motif_rows)

    ids = list(seqs)
    identity_rows = []
    for a in ids:
        row: dict[str, object] = {"sequence_id": a}
        for b in ids:
            identity, comparable = pairwise_identity(seqs[a], seqs[b])
            row[b] = f"{identity:.2f}"
            row[f"{b}_comparable_sites"] = comparable
        identity_rows.append(row)
    identity_fields = ["sequence_id"] + [v for b in ids for v in (b, f"{b}_comparable_sites")]
    write_csv(args.output_dir / "pairwise_identity.csv", identity_fields, identity_rows)

    column_rows = []
    fully_conserved = []
    variable = []
    for column in range(len(ref)):
        residues = [sequence[column] for sequence in seqs.values()]
        non_gap = [r for r in residues if r != "-"]
        counts = Counter(non_gap)
        occupancy = len(non_gap) / len(residues)
        identity = max(counts.values()) / len(non_gap) if non_gap else 0.0
        entropy = -sum((n / len(non_gap)) * math.log2(n / len(non_gap)) for n in counts.values()) if non_gap else 0.0
        ref_position = sum(x != "-" for x in ref[:column + 1]) if ref[column] != "-" else ""
        row = {
            "alignment_column_one_based": column + 1,
            "mettl7b_position": ref_position,
            "mettl7b_residue": ref[column],
            "occupancy_fraction": f"{occupancy:.3f}",
            "consensus_residue": counts.most_common(1)[0][0] if counts else "-",
            "consensus_identity_fraction": f"{identity:.3f}",
            "shannon_entropy_bits": f"{entropy:.3f}",
            "residue_counts": ";".join(f"{aa}:{n}" for aa, n in sorted(counts.items())),
        }
        column_rows.append(row)
        if occupancy == 1.0 and identity == 1.0:
            fully_conserved.append(column + 1)
        if occupancy >= 0.75 and entropy >= 2.0:
            variable.append(column + 1)
    write_csv(args.output_dir / "alignment_column_conservation.csv",
              list(column_rows[0]), column_rows)

    summary = [
        "# Alignment conservation summary",
        "",
        f"- Sequences: {len(seqs)} (2 human references + 10 BioHub representatives)",
        f"- Alignment columns: {len(ref)}",
        f"- Fully conserved, gap-free columns: {len(fully_conserved)}",
        f"- Highly variable columns (occupancy >= 0.75; Shannon entropy >= 2.0 bits): {len(variable)}",
        f"- Fully conserved columns (one-based): {', '.join(map(str, fully_conserved)) or 'none'}",
        f"- Highly variable columns (one-based): {', '.join(map(str, variable)) or 'none'}",
        "",
        "Per-column occupancy, consensus, and entropy are in `alignment_column_conservation.csv`.",
    ]
    (args.output_dir / "alignment_conservation_summary.md").write_text("\n".join(summary) + "\n")


if __name__ == "__main__":
    main()
