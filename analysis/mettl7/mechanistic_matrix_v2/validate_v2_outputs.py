#!/usr/bin/env python3
"""Read-only V2 run auditor plus deterministic final checksum emitter.

The utility never changes inputs, poses, logs, or receipts. It writes only the
top-level technical audit and, after full PASS, the final checksum manifest.
"""

from __future__ import annotations

import csv
import hashlib
import json
import os
import tempfile
from collections import Counter
from pathlib import Path
from validation_artifact_lifecycle import invalidate_success_manifest


ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[2]
LEDGER = ROOT / "AUTHORITATIVE_RUN_LEDGER.csv"
RUNS = ROOT / "production" / "runs"
AUDIT = ROOT / "OUTPUT_COMPLETENESS_AUDIT.json"
SUMS = ROOT / "FINAL_SHA256SUMS"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def resolve(value: str) -> Path:
    path = Path(value)
    return (path if path.is_absolute() else REPO / path).resolve()


def atomic_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            handle.write(text)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def pose_count(path: Path) -> int:
    count = sum(line.startswith("MODEL") for line in path.read_text(errors="replace").splitlines())
    return count if count else int(path.stat().st_size > 0)


ledger_rows = list(csv.DictReader(LEDGER.open(newline="", encoding="utf-8")))
ledger_by_id = {row["run_id"]: row for row in ledger_rows}
errors: list[dict[str, str]] = []
status_counts: Counter[str] = Counter()
completed_ids: set[str] = set()
predeclared_technical_ids: set[str] = {
    row["run_id"] for row in ledger_rows
    if row["technical_status"] == "TECHNICAL_FAILURE"
}

if len(ledger_rows) != 2064:
    errors.append({"scope": "ledger", "error": f"expected 2064 rows; found {len(ledger_rows)}"})
if len(ledger_by_id) != len(ledger_rows):
    errors.append({"scope": "ledger", "error": "duplicate run_id"})

# Verify every frozen input against every ledger row. Cache hashes, but compare
# each row so a ledger-level mismatch cannot hide behind path deduplication.
hash_cache: dict[Path, str] = {}
for row in ledger_rows:
    for kind in ("receptor", "ligand"):
        path = resolve(row[f"{kind}_path"])
        if not path.is_file():
            errors.append({"run_id": row["run_id"], "error": f"missing {kind}: {path}"})
            continue
        actual = hash_cache.setdefault(path, sha256(path))
        if actual != row[f"{kind}_sha256"]:
            errors.append({"run_id": row["run_id"], "error": f"{kind} SHA256 mismatch"})

for run_id, row in ledger_by_id.items():
    if run_id in predeclared_technical_ids:
        status_counts["PREDECLARED_TECHNICAL_FAILURE"] += 1
        continue
    directory = RUNS / run_id
    receipt_path = directory / "receipt.json"
    pose_path = directory / "poses.pdbqt"
    log_path = directory / "vina.log"
    if not receipt_path.is_file():
        status_counts["PENDING"] += 1
        continue
    try:
        receipt = json.loads(receipt_path.read_text())
    except Exception as exception:
        status_counts["MALFORMED_RECEIPT"] += 1
        errors.append({"run_id": run_id, "error": f"malformed receipt: {exception}"})
        continue
    status = receipt.get("status", "MISSING_STATUS")
    status_counts[status] += 1
    if receipt.get("runId") != run_id:
        errors.append({"run_id": run_id, "error": "receipt runId mismatch"})
    if receipt.get("seed") != int(row["seed"]):
        errors.append({"run_id": run_id, "error": "receipt seed mismatch"})
    if receipt.get("receptorSha256") != row["receptor_sha256"]:
        errors.append({"run_id": run_id, "error": "receipt receptor hash mismatch"})
    if receipt.get("ligandSha256") != row["ligand_sha256"]:
        errors.append({"run_id": run_id, "error": "receipt ligand hash mismatch"})
    if status == "COMPLETED_VALID":
        errors_before_run_validation = len(errors)
        if receipt.get("vinaExitCode") != 0:
            errors.append({"run_id": run_id, "error": "valid receipt has nonzero Vina exit"})
        if not pose_path.is_file() or not log_path.is_file():
            errors.append({"run_id": run_id, "error": "valid receipt missing poses or log"})
            continue
        actual_pose_sha = sha256(pose_path)
        if actual_pose_sha != receipt.get("posesSha256"):
            errors.append({"run_id": run_id, "error": "pose SHA256 mismatch"})
        actual_pose_count = pose_count(pose_path)
        # parsedPoseCount is the count of coordinate models actually emitted
        # to the checksum-bound PDBQT, not the larger stdout score table.
        expected_pose_count = receipt.get("parsedPoseCount")
        if (not isinstance(expected_pose_count, int)
                or not 1 <= actual_pose_count <= 9
                or actual_pose_count != expected_pose_count):
            errors.append({"run_id": run_id, "error": f"pose count mismatch/invalid: {actual_pose_count}"})
        if len(errors) == errors_before_run_validation:
            completed_ids.add(run_id)

actual_directories = {path.name for path in RUNS.iterdir() if path.is_dir()} if RUNS.is_dir() else set()
extra_directories = sorted(actual_directories - set(ledger_by_id))
temporary_files = sorted(str(path.relative_to(REPO)) for path in ROOT.rglob("*.tmp"))
if extra_directories:
    errors.append({"scope": "runs", "error": f"extra run directories: {len(extra_directories)}"})

resolved_count = len(completed_ids) + len(predeclared_technical_ids)
complete = resolved_count == len(ledger_rows)
status = "PASS" if complete and not errors and not temporary_files else ("IN_PROGRESS" if not complete and not errors else "FAIL")
audit = {
    "audit_version": "METTL7_V2_OUTPUT_COMPLETENESS_V1",
    "status": status,
    "biological_interpretation_authorized": False,
    "ledger_path": str(LEDGER.relative_to(REPO)),
    "ledger_sha256": sha256(LEDGER),
    "expected": len(ledger_rows),
    "completed_valid": len(completed_ids),
    "predeclared_technical_failure": len(predeclared_technical_ids),
    "resolved_total": resolved_count,
    "failed_or_invalid": sum(count for name, count in status_counts.items() if name not in ("COMPLETED_VALID", "PENDING")),
    "remaining": len(ledger_rows) - resolved_count,
    "receipt_status_counts": dict(sorted(status_counts.items())),
    "input_files_checked": len(hash_cache),
    "input_hash_consistency": "PASS" if not any("SHA256 mismatch" in e["error"] for e in errors) else "FAIL",
    "pose_receipt_consistency": "PASS" if not errors else "FAIL",
    "extra_run_directories": extra_directories,
    "temporary_files": temporary_files,
    "errors": errors,
}
atomic_text(AUDIT, json.dumps(audit, indent=2) + "\n")

if status == "PASS":
    # Exclude self-referential/generated checksum outputs. Sort by repository-
    # relative POSIX path to make the manifest byte-deterministic.
    files = [
        path for path in ROOT.rglob("*") if path.is_file()
        and path not in (AUDIT, SUMS)
        and not path.name.endswith(".tmp")
        and "g199f_candidates" not in path.parts
        and "s47y_candidates" not in path.parts
        and not path.name.endswith("_PROTEUS_ORIGINAL.pdbqt")
    ]
    lines = [f"{sha256(path)}  {path.relative_to(REPO).as_posix()}" for path in sorted(files, key=lambda p: p.relative_to(REPO).as_posix())]
    atomic_text(SUMS, "\n".join(lines) + "\n")
else:
    invalidate_success_manifest(SUMS, ROOT / "VALIDATION_FAILURE_RECEIPT.json",
                                [entry.get("error", str(entry)) for entry in errors]
                                + [f"temporary file: {path}" for path in temporary_files])

print(f"STATUS={status} COMPLETED_VALID={len(completed_ids)} PREDECLARED_TECHNICAL_FAILURE={len(predeclared_technical_ids)} EXPECTED={len(ledger_rows)} REMAINING={len(ledger_rows)-resolved_count} ERRORS={len(errors)}")
