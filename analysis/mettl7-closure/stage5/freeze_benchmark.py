#!/usr/bin/env python3
"""Create a content-addressed lock manifest for accepted METTL7 Stages 0-4."""

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
BASE = ROOT / "analysis/mettl7-closure"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    files = []
    for stage in range(5):
        directory = BASE / f"stage{stage}"
        for path in sorted(item for item in directory.rglob("*") if item.is_file()):
            files.append({
                "path": str(path.relative_to(ROOT)),
                "bytes": path.stat().st_size,
                "sha256": sha256(path),
            })
    payload = {
        "schema_version": "1.0.0",
        "status": "FROZEN_ACCEPTED_CANONICAL_BENCHMARK",
        "accepted_through_commit": "17d20c052",
        "locked_protocol": "analysis/mettl7-closure/stage1/protocol.json",
        "locked_protocol_sha256": sha256(BASE / "stage1/protocol.json"),
        "evidence_level": "COMPUTATIONAL_HYPOTHESIS",
        "accepted_hypothesis": "METTL7A shows accommodation-dependent productive TSL geometry and predominantly broad DCMB interference, whereas METTL7B retains static productive TSL configurations and DCMB escape families in every tested background. Positions 43 and 199 modulate these landscapes but do not constitute a reciprocal selectivity switch.",
        "prohibited_extensions": ["additional mutations", "additional docking campaigns", "rescoring", "mechanistic searches", "training on Vina scores", "use of METTL7A/7B predictions as experimental labels"],
        "benchmark_role": "Downstream out-of-training-distribution benchmark for whether an experimentally learned representation captures the frozen structural distinction.",
        "files": files,
    }
    HERE.mkdir(parents=True, exist_ok=True)
    (HERE / "benchmark-lock.json").write_text(json.dumps(payload, indent=2) + "\n")
    (HERE / "BENCHMARK_SHA256SUMS").write_text("".join(f'{item["sha256"]}  {item["path"]}\n' for item in files))
    print(f"Frozen {len(files)} Stage 0-4 files")


if __name__ == "__main__":
    main()
