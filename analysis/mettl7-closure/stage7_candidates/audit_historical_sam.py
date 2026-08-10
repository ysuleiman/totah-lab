#!/usr/bin/env python3
"""Audit SAM status of historical 7A/7B candidate runs and pose artifacts."""

from __future__ import annotations

import csv
import json
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parent
PROVENANCE = ROOT / "candidate-provenance.csv"
ARTIFACT_ROOT = Path("/Users/yazan/projects/chemflow/backend/artifact-storage")
CANONICAL_SAM_ATOMS = 27


def database_run_rows(run_ids: set[str]) -> dict[str, dict[str, str]]:
    values = ",".join(f"('{value}'::uuid)" for value in sorted(run_ids))
    sql = f"""
    COPY (
      WITH requested(id) AS (VALUES {values})
      SELECT dr.id, dr.engine, dr.run_metadata::text,
             tsa.storage_uri AS target_structure_uri,
             tsa.artifact_metadata::text AS target_structure_metadata,
             ra.storage_uri AS receptor_uri,
             ra.artifact_metadata::text AS receptor_metadata
      FROM requested q
      JOIN docking_runs dr ON dr.id=q.id
      JOIN target_structures ts ON ts.id=dr.target_structure_id
      JOIN artifacts tsa ON tsa.id=ts.artifact_id
      LEFT JOIN artifacts ra ON ra.id=nullif(dr.run_metadata::jsonb->>'receptor_artifact_id','')::uuid
      ORDER BY dr.id
    ) TO STDOUT WITH (FORMAT csv, HEADER true)
    """
    result = subprocess.run(
        ["psql", "-h", "localhost", "-U", "postgres", "-d", "chemflow3", "-c", sql],
        check=True,
        capture_output=True,
        text=True,
    )
    return {row["id"]: row for row in csv.DictReader(result.stdout.splitlines())}


def artifact_path(uri: str) -> Path | None:
    if not uri:
        return None
    candidate = Path(uri)
    if candidate.is_absolute():
        return candidate
    relative = uri.removeprefix("local://artifact-storage/")
    relative = relative.removeprefix("artifact://").removeprefix("file://")
    return ARTIFACT_ROOT / relative


def sam_evidence(path: Path | None) -> tuple[str, int, str]:
    if path is None or not path.exists():
        return "UNKNOWN", 0, "source artifact unavailable"
    atom_count = 0
    residue_names: set[str] = set()
    for line in path.read_text(errors="replace").splitlines():
        if line.startswith(("ATOM  ", "HETATM")):
            residue = line[17:20].strip().upper()
            residue_names.add(residue)
            if residue in {"SAM", "AdoMet".upper()}:
                atom_count += 1
    if atom_count == CANONICAL_SAM_ATOMS:
        return "SAM_PRESENT_CANONICAL", atom_count, "27 SAM atoms present"
    if atom_count:
        return "SAM_PRESENT_NONCANONICAL", atom_count, "SAM-like residue has noncanonical atom count"
    return "PROTEIN_ONLY", 0, "no SAM residue in receptor/target artifact"


def main() -> None:
    with PROVENANCE.open(newline="") as handle:
        candidates = list(csv.DictReader(handle))
    run_ids = {
        row[key]
        for row in candidates
        for key in ("historical_run_7b", "historical_run_7a")
    }
    runs = database_run_rows(run_ids)
    output = []
    for candidate in candidates:
        for target in ("7B", "7A"):
            run_id = candidate[f"historical_run_{target.lower()}"]
            run = runs[run_id]
            receptor_path = artifact_path(run.get("receptor_uri", ""))
            target_path = artifact_path(run.get("target_structure_uri", ""))
            path = receptor_path if receptor_path and receptor_path.exists() else target_path
            status, atom_count, reason = sam_evidence(path)
            output.append({
                "immutable_ligand_identity_sha256": candidate["immutable_ligand_identity_sha256"],
                "historical_rank": candidate["historical_rank"],
                "target": target,
                "historical_run_id": run_id,
                "historical_pose_id": candidate[f"historical_pose_{target.lower()}"],
                "sam_status": status,
                "sam_atom_count": atom_count,
                "audit_reason": reason,
                "audited_artifact_uri": run.get("receptor_uri") or run.get("target_structure_uri") or "",
                "audited_artifact_exists": bool(path and path.exists()),
                "engine": run["engine"],
                "run_metadata": run["run_metadata"],
            })
    fields = list(output[0])
    with (ROOT / "sam-audit.csv").open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(output)
    summary: dict[str, int] = {}
    for row in output:
        summary[row["sam_status"]] = summary.get(row["sam_status"], 0) + 1
    print(json.dumps({"candidate_target_rows": len(output), "status_counts": summary}, indent=2))


if __name__ == "__main__":
    main()
