#!/usr/bin/env python3
"""Prepare the second leg of reciprocal BLAST from frozen database files."""

from __future__ import annotations

import argparse
from pathlib import Path


def fasta(path: Path):
    header = None
    sequence = []
    for line in path.read_text().splitlines():
        if line.startswith(">"):
            if header is not None:
                yield header, "".join(sequence)
            header, sequence = line[1:], []
        else:
            sequence.append(line.strip())
    if header is not None:
        yield header, "".join(sequence)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("forward_hits", type=Path)
    parser.add_argument("swissprot_fasta", type=Path)
    parser.add_argument("panel_fasta", type=Path)
    parser.add_argument("output_dir", type=Path)
    args = parser.parse_args()

    top_subjects = {}
    for line in args.forward_hits.read_text().splitlines():
        fields = line.split("\t")
        top_subjects.setdefault(fields[0], fields[1])
    wanted = set(top_subjects.values())
    found = {}
    for header, sequence in fasta(args.swissprot_fasta):
        identifier = header.split()[0]
        if identifier in wanted:
            found[identifier] = (header, sequence)
    missing = wanted - set(found)
    if missing:
        raise ValueError(f"top Swiss-Prot sequences not found: {sorted(missing)}")
    with (args.output_dir / "reciprocal_top_swissprot.fasta").open("w") as handle:
        for identifier in sorted(found):
            header, sequence = found[identifier]
            handle.write(f">{identifier} {header}\n{sequence}\n")
    with (args.output_dir / "representatives_only.fasta").open("w") as handle:
        for header, sequence in fasta(args.panel_fasta):
            identifier = header.split()[0]
            if identifier.startswith("CLUSTER_"):
                handle.write(f">{identifier}\n{sequence}\n")


if __name__ == "__main__":
    main()
