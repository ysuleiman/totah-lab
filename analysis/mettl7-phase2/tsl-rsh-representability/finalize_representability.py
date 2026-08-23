#!/usr/bin/env python3
"""Verify the completed representability bundle and write its outer checksum manifest."""

from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    receipt = json.loads((HERE / "FIT_RECEIPT.json").read_text())
    artifact = HERE / "FIT_ARTIFACT"
    for line in (artifact / "SHA256SUMS").read_text().splitlines():
        expected, name = line.split(maxsplit=1)
        path = artifact / name
        if not path.is_file() or sha256(path) != expected:
            raise ValueError(f"fit artifact checksum failure: {name}")
    if sha256(artifact / "SHA256SUMS") != receipt["artifact_sha256"]:
        raise ValueError("receipt does not identify verified fit artifact")
    metrics = ["TRAIN_METRICS.json", "VALIDATION_METRICS.json", "STRESS_TEST_METRICS.json"]
    for name in metrics:
        payload = json.loads((HERE / name).read_text())
        if not payload or payload["count"] <= 0:
            raise ValueError(f"invalid metric artifact: {name}")
    rows = list(csv.DictReader((HERE / "FITTABLE_PARAMETER_MANIFEST.csv").open(newline="")))
    if any(row["VALUE"] == "TO_BE_FITTED_FROM_39_TRAIN_IDS" for row in rows):
        raise ValueError("fitted parameter manifest still contains placeholders")
    names = [
        "ADDITIVE_CLASSICAL_MODEL_SPEC.json", "FITTABLE_PARAMETER_MANIFEST.csv",
        "FIT_RECEIPT.json", "TRAINING_FIT_REQUEST.json", "BASELINE_PREDICTIONS.json",
        "TRAIN_METRICS.json", "VALIDATION_METRICS.json", "STRESS_TEST_METRICS.json",
        "RESIDUAL_DIAGNOSTICS.csv", "REPRESENTABILITY_DECISION.json",
        "ADDITIVE_CLASSICAL_REPRESENTABILITY_REPORT.md", "classical_common.py",
        "prepare_additive_fit.py", "evaluate_frozen_additive_fit.py",
        "finalize_representability.py",
    ]
    names.extend("FIT_ARTIFACT/" + path.name for path in sorted(artifact.iterdir()) if path.is_file())
    with (HERE / "SHA256SUMS").open("w") as stream:
        for name in names:
            path = HERE / name
            if not path.is_file():
                raise FileNotFoundError(path)
            stream.write(f"{sha256(path)}  {name}\n")
    print(json.dumps({"bundle_files": len(names), "fit_receipt_verified": True, "outer_checksums_written": True}, indent=2))


if __name__ == "__main__":
    main()
