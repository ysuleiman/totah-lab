#!/usr/bin/env python3
"""Verify the receipt-backed bundle and write its outer checksum manifest."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    required = [
        "INTERNAL_COORDINATE_DEFINITIONS.json", "RESIDUAL_INTERNAL_FORCE_PROJECTIONS.csv",
        "TRAIN_COUPLING_ANALYSIS.json", "CANDIDATE_CROSS_TERMS.json", "CROSS_TERM_MODEL_SPEC.json",
        "TRAINING_FIT_REQUEST.json", "FIT_RECEIPT.json", "VALIDATION_COMPARISON.json",
        "STRESS_TEST_COMPARISON.json", "REPRESENTABILITY_DECISION.json",
        "CROSS_TERM_REPRESENTABILITY_REPORT.md", "cross_common.py", "discover_and_prepare.py",
        "evaluate_frozen_cross_term.py", "correct_post_open_reporting.py", "finalize_cross_coupling.py",
    ]
    required += [str(path.relative_to(HERE)) for path in sorted((HERE / "FIT_ARTIFACT").iterdir()) if path.is_file()]
    for name in required:
        if not (HERE / name).is_file():
            raise ValueError(f"missing required bundle file: {name}")
    receipt = json.loads((HERE / "FIT_RECEIPT.json").read_text())
    if receipt.get("receipt_verified") is not True or sha256(HERE / "FIT_ARTIFACT/SHA256SUMS") != receipt["artifact_sha256"]:
        raise ValueError("fit receipt failed final verification")
    validation = json.loads((HERE / "VALIDATION_COMPARISON.json").read_text())
    if validation.get("validation_open_count") != 1 or validation.get("validation_used_during_fit") is not False:
        raise ValueError("validation provenance is not frozen")
    lines = [f"{sha256(HERE / name)}  {name}" for name in required]
    (HERE / "SHA256SUMS").write_text("\n".join(lines) + "\n")
    print(json.dumps({"files": len(required), "receipt_verified": True, "validation_open_count": 1}, indent=2))


if __name__ == "__main__":
    main()
