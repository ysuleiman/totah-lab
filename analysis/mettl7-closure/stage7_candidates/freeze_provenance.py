#!/usr/bin/env python3
"""Freeze and validate the pre-redocking historical candidate provenance."""

from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent
FILES = ("candidate-provenance.csv",)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def rows(name: str) -> list[dict[str, str]]:
    with (ROOT / name).open(newline="") as handle:
        return list(csv.DictReader(handle))


def main() -> None:
    historical = rows(FILES[0])
    historical_ids = {row["immutable_ligand_identity_sha256"] for row in historical}
    if len(historical) != 200 or len(historical_ids) != 200:
        raise RuntimeError("corrected universe must contain 200 unique identities")
    manifest = {
        "schema": "mettl7_prospective_candidate_provenance_lock_v2",
        "candidate_direction": "METTL7B_FAVORED_COMPUTATIONAL_CANDIDATES",
        "paired_historical_corpus_count": 7716,
        "historical_universe": {
            "count": 200,
            "rule": {"historical_engine_output_7b_lt": -5.5, "exclude_label_prefix": "WH", "order": "historical_delta_7a_minus_7b DESC", "limit": 200},
        },
        "evidence_level": "COMPUTATIONAL_CANDIDATE",
        "files": [
            {"path": name, "bytes": (ROOT / name).stat().st_size, "sha256": sha256(ROOT / name)}
            for name in FILES
        ],
        "frozen_boundaries": {
            "stages_0_4_modified": False,
            "sealed_experimental_representation_modified": False,
            "redocking_started": False,
        },
    }
    (ROOT / "provenance-manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n"
    )
    print(json.dumps(manifest, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
