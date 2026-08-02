#!/usr/bin/env python3
"""Build the 12-sequence BioHub representative analysis input set."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path


def fasta_records(path: Path):
    header = None
    chunks: list[str] = []
    with path.open() as stream:
        for raw in stream:
            line = raw.strip()
            if not line:
                continue
            if line.startswith(">"):
                if header is not None:
                    yield header, "".join(chunks)
                header, chunks = line[1:], []
            else:
                chunks.append(line)
    if header is not None:
        yield header, "".join(chunks)


def write_fasta(records, path: Path):
    with path.open("w") as stream:
        for identifier, description, sequence in records:
            stream.write(f">{identifier} {description}\n")
            for start in range(0, len(sequence), 80):
                stream.write(sequence[start:start + 80] + "\n")


def uniprot_metadata(path: Path):
    record = json.loads(path.read_text())
    organism = record.get("organism", {})
    names = record.get("proteinDescription", {})
    recommended = names.get("recommendedName", {}).get("fullName", {}).get("value")
    return {
        "organism": organism.get("scientificName", "Homo sapiens"),
        "taxonomy": "; ".join(organism.get("lineage", [])),
        "protein_name": recommended or "",
        "function": " | ".join(
            comment.get("texts", [{}])[0].get("value", "")
            for comment in record.get("comments", [])
            if comment.get("commentType") == "FUNCTION"
        ),
        "domains": "; ".join(
            f"{xref.get('database')}:{xref.get('id')}"
            for xref in record.get("uniProtKBCrossReferences", [])
            if xref.get("database") in {"Pfam", "InterPro", "CDD"}
        ),
    }


def uniparc_metadata(path: Path):
    record = json.loads(path.read_text())
    crossrefs = record.get("uniParcCrossReferences", [])
    active = [item for item in crossrefs if item.get("active")]
    preferred = next(
        (item for item in active if item.get("database") in {"UniProtKB/Swiss-Prot", "UniProtKB/TrEMBL"}),
        active[0] if active else {},
    )
    organism = preferred.get("organism", {}) or {}
    uniprot = preferred.get("id", "") if preferred.get("database", "").startswith("UniProtKB") else ""
    return {
        "organism": organism.get("scientificName", ""),
        "taxonomy": str(organism.get("taxonId", "")),
        "uniprot": uniprot,
        "protein_name": preferred.get("proteinName", ""),
        "annotation_source": preferred.get("database", "UniParc"),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--clusters", type=Path, required=True)
    parser.add_argument("--raw", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    manifest = json.loads((args.clusters / "manifest.json").read_text())
    selection = json.loads((args.clusters / "cluster-selection.json").read_text())
    accessions: dict[str, tuple[str, str, int]] = {}
    with (args.clusters / "cluster-members.csv").open() as stream:
        for row in csv.DictReader(stream):
            accessions[row["member_hash"]] = (
                row["accession"], row["source"], int(row["sequence_length"])
            )
    sequences = {}
    for header, sequence in fasta_records(args.clusters / "all-clusters.fasta"):
        tokens = header.split("|")
        sequences[tokens[2]] = sequence

    fasta = []
    rows = []
    for accession, label in (("Q6UX53", "HUMAN_METTL7B"), ("Q9H8H3", "HUMAN_METTL7A")):
        _, sequence = next(fasta_records(args.raw / f"{accession}.fasta"))
        meta = uniprot_metadata(args.raw / f"{accession}.json")
        fasta.append((label, f"accession={accession} organism=Homo_sapiens", sequence))
        rows.append({
            "sequence_id": label, "cluster_id": "reference", "representative_hash": "",
            "representative_accession": accession, "organism": meta["organism"],
            "taxonomy": meta["taxonomy"], "sequence_length": len(sequence),
            "annotation_source": "UniProtKB reviewed", "uniprot_accession": accession,
            "predicted_protein_name": meta["protein_name"], "predicted_function": meta["function"],
            "predicted_methyltransferase_family": "METTL7/TMT",
            "conserved_domain_annotations": meta["domains"], "sequence": sequence,
        })

    selected = {item["id"]: item for item in selection}
    for cluster in manifest["clusters"]:
        cid = cluster["id"]
        rep_hash = cluster["representativeHash"]
        sequence = sequences[rep_hash]
        if hashlib.md5(sequence.encode()).hexdigest() != rep_hash:
            raise ValueError(f"MD5 mismatch for cluster {cid}")
        accession, source, expected_length = accessions[rep_hash]
        if len(sequence) != expected_length:
            raise ValueError(f"Length mismatch for cluster {cid}")
        cluster_json = json.loads((args.clusters / cluster["directory"] / "cluster.json").read_text())
        uniparc = uniparc_metadata(args.raw / f"{accession}.json") if accession.startswith("UPI") else {}
        pfam = cluster_json.get("cluster_top_pfam_domains", {})
        domains = "; ".join(
            f"Pfam:{accession} {details.get('name', '')} (cluster count={details.get('count', '')})".strip()
            for accession, details in pfam.items()
        )
        sequence_id = f"CLUSTER_{cid:02d}_REP"
        fasta.append((sequence_id, f"accession={accession} hash={rep_hash} source={source}", sequence))
        rows.append({
            "sequence_id": sequence_id, "cluster_id": cid, "representative_hash": rep_hash,
            "representative_accession": accession, "organism": uniparc.get("organism", ""),
            "taxonomy": uniparc.get("taxonomy", ""), "sequence_length": len(sequence),
            "annotation_source": uniparc.get("annotation_source", source),
            "uniprot_accession": uniparc.get("uniprot", ""),
            "predicted_protein_name": cluster.get("representativeName", cluster_json.get("protein_name", "")),
            "predicted_function": "", "predicted_methyltransferase_family": selected[cid]["category"],
            "conserved_domain_annotations": domains, "sequence": sequence,
        })

    fields = list(rows[0])
    with (args.output / "cluster_representatives_metadata.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    write_fasta(fasta, args.output / "cluster_representatives.fasta")


if __name__ == "__main__":
    main()
