#!/usr/bin/env python3
"""Freeze the GPU-60 development/validation split without baseline residuals."""

from __future__ import annotations

import csv
import hashlib
import json
import math
from pathlib import Path


HERE = Path(__file__).resolve().parent
CAMPAIGN = HERE.parent
SOURCE = CAMPAIGN / "GPU_BATCH_60_CHARACTERIZATION.csv"
OUT_CSV = HERE / "GPU60_SPLIT_FROZEN.csv"
OUT_JSON = HERE / "GPU60_SPLIT_FROZEN.json"

CONTINUOUS = (
    "total_energy_hartree",
    "sulfur_local_force_rms_kcal_mol_a",
    "sc_distance_a",
    "sh_distance_a",
    "c_s_h_angle_deg",
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def scaled_features(rows: list[dict[str, str]]) -> dict[str, tuple[float, ...]]:
    columns: dict[str, list[float]] = {}
    for key in CONTINUOUS:
        values = [float(row[key]) for row in rows]
        lo, hi = min(values), max(values)
        columns[key] = [(value - lo) / (hi - lo) if hi > lo else 0.0 for value in values]
    families = sorted({row["family"] for row in rows})
    result = {}
    for index, row in enumerate(rows):
        numeric = [columns[key][index] for key in CONTINUOUS]
        # Periodic torsions enter as sine/cosine pairs; family is categorical.
        periodic = []
        for key in ("phi_deg", "psi_deg"):
            angle = math.radians(float(row[key]))
            periodic.extend((math.sin(angle), math.cos(angle)))
        categorical = [1.0 if row["family"] == family else 0.0 for family in families]
        result[row["campaign_id"]] = tuple(numeric + periodic + categorical)
    return result


def squared_distance(a: tuple[float, ...], b: tuple[float, ...]) -> float:
    return sum((x - y) ** 2 for x, y in zip(a, b, strict=True))


def choose_validation(rows: list[dict[str, str]], count: int) -> set[str]:
    """Deterministic farthest-point coverage within one source minimum."""
    features = scaled_features(rows)
    family_rows = {
        family: [row for row in rows if row["family"] == family]
        for family in sorted({row["family"] for row in rows})
    }
    # Proportional family quotas, while keeping every singleton in development
    # and at least one development member of each non-singleton family.
    quotas = {family: min(len(group) - 1, int(count * len(group) / len(rows))) for family, group in family_rows.items()}
    while sum(quotas.values()) < count:
        eligible = [family for family, group in family_rows.items() if quotas[family] < len(group) - 1]
        family = max(
            eligible,
            key=lambda item: (count * len(family_rows[item]) / len(rows) - quotas[item], item),
        )
        quotas[family] += 1
    # Start from the point nearest the multivariate centre, checksum as tie-break.
    dimensions = len(next(iter(features.values())))
    centre = tuple(sum(v[i] for v in features.values()) / len(rows) for i in range(dimensions))
    ranked = sorted(
        rows,
        key=lambda row: (squared_distance(features[row["campaign_id"]], centre), row["geometry_sha256"]),
    )
    first = next(row for row in ranked if quotas[row["family"]] > 0)
    selected = [first["campaign_id"]]
    used = {family: 0 for family in family_rows}
    used[first["family"]] = 1
    while len(selected) < count:
        candidates = [
            row for row in rows
            if row["campaign_id"] not in selected and used[row["family"]] < quotas[row["family"]]
        ]
        chosen = max(
            candidates,
            key=lambda row: (
                min(squared_distance(features[row["campaign_id"]], features[item]) for item in selected),
                row["geometry_sha256"],
            ),
        )
        selected.append(chosen["campaign_id"])
        used[chosen["family"]] += 1
    return set(selected)


def main() -> None:
    HERE.mkdir(parents=True, exist_ok=True)
    with SOURCE.open(newline="") as handle:
        rows = list(csv.DictReader(handle))
    if len(rows) != 60 or len({row["geometry_sha256"] for row in rows}) != 60:
        raise ValueError("Expected exactly 60 unique frozen geometries")

    validation: set[str] = set()
    for minimum in ("MIN01", "MIN02", "MIN04"):
        group = [row for row in rows if row["source_minimum"] == minimum]
        if len(group) != 20:
            raise ValueError(f"Expected 20 rows for {minimum}, found {len(group)}")
        validation.update(choose_validation(group, 5))

    output_rows = []
    for row in sorted(rows, key=lambda item: item["campaign_id"]):
        output_rows.append({
            "campaign_id": row["campaign_id"],
            "partition": "SEALED_VALIDATION" if row["campaign_id"] in validation else "DEVELOPMENT",
            "source_minimum": row["source_minimum"],
            "perturbation_family": row["family"],
            "geometry_sha256": row["geometry_sha256"],
            "result_sha256": row["result_sha256"],
        })
    with OUT_CSV.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=output_rows[0].keys())
        writer.writeheader()
        writer.writerows(output_rows)

    counts: dict[str, dict[str, int]] = {}
    for row in output_rows:
        key = f'{row["source_minimum"]}/{row["perturbation_family"]}'
        counts.setdefault(key, {"DEVELOPMENT": 0, "SEALED_VALIDATION": 0})[row["partition"]] += 1
    record = {
        "schema": "tsl-rsh-gpu60-split-v1",
        "source": str(SOURCE.relative_to(CAMPAIGN.parent.parent.parent.parent)),
        "source_sha256": sha256(SOURCE),
        "algorithm": "within-minimum deterministic farthest-point coverage",
        "features_used": [
            "source_minimum", "perturbation_family", "total_energy_rank",
            "sulfur_local_force_magnitude", "S-C", "S-H", "C-S-H", "sin/cos(phi)", "sin/cos(psi)",
        ],
        "features_explicitly_not_used": ["any baseline residual", "any fitted-model prediction"],
        "development_count": 45,
        "sealed_validation_count": 15,
        "validation_policy": "May be reported for this preregistered baseline diagnostic; prohibited for later model selection.",
        "stratum_counts": counts,
        "csv_sha256": sha256(OUT_CSV),
    }
    OUT_JSON.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n")
    print(json.dumps(record, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
