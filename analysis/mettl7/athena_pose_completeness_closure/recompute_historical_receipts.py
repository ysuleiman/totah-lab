#!/usr/bin/env python3
"""Create versioned coordinate-authoritative receipts without altering docking evidence."""
import csv, hashlib, json, re
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent / "historical_receipt_recomputation_v2"

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def models(path): return len(re.findall(r"(?m)^MODEL\s+", path.read_text(errors="replace")))

sources = {}
for table in (REPO / "analysis").rglob("*.csv"):
    try:
        rows = list(csv.DictReader(table.open(errors="replace")))
    except Exception:
        continue
    if not rows or not {"run_id", "pose_model", "receipt_path"}.issubset(rows[0]):
        continue
    grouped = defaultdict(list)
    for row in rows:
        if row.get("receipt_path"):
            grouped[(row["run_id"], row["receipt_path"])].append(row)
    for (run_id, receipt_text), pose_rows in grouped.items():
        receipt_path = Path(receipt_text)
        if not receipt_path.is_absolute(): receipt_path = REPO / receipt_path
        if not receipt_path.is_file(): continue
        receipt = json.loads(receipt_path.read_text())
        pose_path = receipt_path.parent / "poses.pdbqt"
        if not pose_path.is_file(): continue
        expected, emitted = models(pose_path), len(pose_rows)
        old = receipt.get("parsedPoseCount", receipt.get("parsed_pose_count"))
        if old == expected == emitted: continue
        candidate = (receipt_path, table, expected, emitted, old)
        prior = sources.get(run_id)
        if prior and (prior[2], prior[3], prior[4], sha(prior[0])) != (expected, emitted, old, sha(receipt_path)):
            raise RuntimeError(f"conflicting evidence for {run_id}")
        sources[run_id] = candidate

if len(sources) != 66:
    raise RuntimeError(f"frozen historical scope changed: expected 66 unique runs, got {len(sources)}")

OUT.mkdir(parents=True, exist_ok=True)
manifest = []
for run_id, (old_path, table, expected, emitted, old_count) in sorted(sources.items()):
    old_receipt = json.loads(old_path.read_text())
    classification = "METADATA_CORRECTED" if expected == emitted else "REQUIRES_REPROCESSING"
    revised = dict(old_receipt)
    revised["parsedPoseCount"] = expected
    revised["receiptVersion"] = "COORDINATE_AUTHORITATIVE_V2"
    revised["supersedesReceiptPath"] = str(old_path.relative_to(REPO))
    revised["supersedesReceiptSha256"] = sha(old_path)
    revised["recomputationClassification"] = classification
    revised["expectedPoseCount"] = expected
    revised["profiledPoseCount"] = emitted
    revised["emittedPoseCount"] = emitted
    revised["redockingPerformed"] = False
    target = OUT / run_id / "receipt.coordinate-authoritative.v2.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(revised, indent=2) + "\n")
    manifest.append({
        "run_id": run_id, "classification": classification,
        "old_receipt_path": str(old_path.relative_to(REPO)), "old_receipt_sha256": sha(old_path),
        "new_receipt_path": str(target.relative_to(REPO)), "new_receipt_sha256": sha(target),
        "old_recorded_count": old_count, "coordinate_model_count": expected,
        "profiled_count": emitted, "emitted_count": emitted,
        "poses_sha256": sha(old_path.parent / "poses.pdbqt"), "redocking_performed": "false",
        "source_table": str(table.relative_to(REPO))
    })

fields = list(manifest[0])
with (OUT / "HISTORICAL_66_RUN_RECOMPUTATION_MANIFEST.csv").open("w", newline="") as handle:
    writer = csv.DictWriter(handle, fieldnames=fields); writer.writeheader(); writer.writerows(manifest)
receipt = {
    "version": "COORDINATE_AUTHORITATIVE_V2", "unique_runs": len(manifest),
    "classifications": {key: sum(row["classification"] == key for row in manifest)
                        for key in ("UNCHANGED_EVIDENCE", "METADATA_CORRECTED", "REQUIRES_REPROCESSING")},
    "all_exact_after_recomputation": all(row["coordinate_model_count"] == row["profiled_count"] == row["emitted_count"] for row in manifest),
    "old_receipts_modified": False, "redocking_performed": False
}
(OUT / "HISTORICAL_66_RUN_RECOMPUTATION_RECEIPT.json").write_text(json.dumps(receipt, indent=2) + "\n")
print(json.dumps(receipt, indent=2))
