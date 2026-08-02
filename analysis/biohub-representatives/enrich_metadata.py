#!/usr/bin/env python3
"""Enrich the representative metadata from authoritative UniProt services."""

from __future__ import annotations

import argparse
import csv
import json
import time
import urllib.error
import urllib.request
from pathlib import Path


def fetch_json(url: str, cache: Path):
    if cache.exists():
        return json.loads(cache.read_text())
    request = urllib.request.Request(url, headers={"User-Agent": "totah-lab-representative-analysis/1.0"})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            data = json.load(response)
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            data = None
        else:
            raise
    cache.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")
    time.sleep(0.1)
    return data


def protein_name(entry) -> str:
    description = entry.get("proteinDescription", {})
    recommended = description.get("recommendedName", {})
    value = recommended.get("fullName", {}).get("value")
    if value:
        return value
    submitted = description.get("submissionNames", [])
    return submitted[0].get("fullName", {}).get("value", "") if submitted else ""


def function_text(entry) -> str:
    values = []
    for comment in entry.get("comments", []):
        if comment.get("commentType") == "FUNCTION":
            values.extend(text.get("value", "") for text in comment.get("texts", []))
    return " | ".join(value for value in values if value)


def domains(entry) -> str:
    allowed = {"Pfam", "InterPro", "CDD"}
    values = []
    for reference in entry.get("uniProtKBCrossReferences", []):
        if reference.get("database") in allowed:
            values.append(f"{reference['database']}:{reference['id']}")
    return "; ".join(values)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("analysis_dir", type=Path)
    args = parser.parse_args()
    metadata_path = args.analysis_dir / "cluster_representatives_metadata.csv"
    raw_dir = args.analysis_dir / "raw"
    rows = list(csv.DictReader(metadata_path.open()))
    structural = []
    for row in rows:
        accession = row["uniprot_accession"]
        entry = None
        if accession:
            entry = fetch_json(
                f"https://rest.uniprot.org/uniprotkb/{accession}.json",
                raw_dir / f"{accession}.json",
            )
        if entry:
            organism = entry.get("organism", {})
            lineage = organism.get("lineage", [])
            if organism.get("scientificName"):
                row["organism"] = organism["scientificName"]
            if lineage:
                row["taxonomy"] = "; ".join(lineage)
            row["predicted_protein_name"] = protein_name(entry) or row["predicted_protein_name"]
            row["predicted_function"] = function_text(entry) or row["predicted_function"]
            entry_domains = domains(entry)
            if entry_domains:
                row["conserved_domain_annotations"] = entry_domains
            pdb_ids = sorted({x["id"] for x in entry.get("uniProtKBCrossReferences", []) if x.get("database") == "PDB"})
        else:
            pdb_ids = []
        alphafold = None
        if accession:
            alphafold = fetch_json(
                f"https://alphafold.ebi.ac.uk/api/prediction/{accession}",
                raw_dir / f"{accession}.alphafold.json",
            )
        af_ids = []
        if isinstance(alphafold, list):
            af_ids = sorted({x.get("entryId", "") for x in alphafold if x.get("entryId")})
        structural.append({
            "sequence_id": row["sequence_id"],
            "uniprot_accession": accession,
            "experimental_pdb_available": "yes" if pdb_ids else "no",
            "pdb_identifiers": ";".join(pdb_ids),
            "alphafold_model_available": "yes" if af_ids else "no",
            "alphafold_identifiers": ";".join(af_ids),
            "esmfold_model_available": "not established",
            "esmfold_identifier": "",
            "esmfold_note": "No stable ESMFold accession is present in the supplied BioHub or UniProt records; absence was not inferred from sequence.",
        })
    with metadata_path.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    with (args.analysis_dir / "structural_availability.csv").open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=structural[0].keys())
        writer.writeheader()
        writer.writerows(structural)


if __name__ == "__main__":
    main()
