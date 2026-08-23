#!/usr/bin/env python3
"""Validate, deterministically package, and checksum the preparation bundle."""

from __future__ import annotations

import csv
import hashlib
import json
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ZIP = HERE / "TSL_RSH_CURVATURE_GPU_CAMPAIGN.zip"
PACKAGE_SUMS = HERE / "PACKAGE_SHA256SUMS"
OUTER_SUMS = HERE / "SHA256SUMS"
CORE = [
    "CURVATURE_COORDINATE_DEFINITIONS.json", "CURVATURE_PAIR_PANEL.json",
    "DISPLACEMENT_PROTOCOL.json", "CURVATURE_GEOMETRY_MANIFEST.csv",
    "GEOMETRY_CONSTRUCTION_AUDIT.json", "FROZEN_GPU_QM_PROTOCOL.json",
    "CURVATURE_ANALYSIS_PROTOCOL.json", "run_curvature_gpu_campaign.py",
    "prepare_curvature_campaign.py", "finalize_curvature_package.py",
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    rows = list(csv.DictReader((HERE / "CURVATURE_GEOMETRY_MANIFEST.csv").open(newline="")))
    if len(rows) != 76:
        raise ValueError("manifest must contain exactly 76 frozen points")
    geometry_names = []
    for row in rows:
        name = row["geometry_path"]; path = HERE / name
        if not path.is_file() or sha256(path) != row["geometry_sha256"]:
            raise ValueError(f"geometry checksum failure: {name}")
        geometry_names.append(name)
    audit = json.loads((HERE / "GEOMETRY_CONSTRUCTION_AUDIT.json").read_text())
    required_passes = ("connectivity_check_pass", "chirality_check_pass", "non_target_drift_check_pass", "duplicate_check_pass")
    if audit["unique_geometry_count"] != 76 or audit["rejected_geometry_count"] != 0 or not all(audit[key] for key in required_passes):
        raise ValueError("geometry audit did not pass")
    protocol = json.loads((HERE / "FROZEN_GPU_QM_PROTOCOL.json").read_text())
    analysis = json.loads((HERE / "CURVATURE_ANALYSIS_PROTOCOL.json").read_text())
    if protocol["qm_protocol_matches_gpu60"] is not True or protocol["qm_executed"] is not False or analysis["frozen_before_qm"] is not True:
        raise ValueError("protocol freeze failure")
    payload = CORE + geometry_names
    PACKAGE_SUMS.write_text("".join(f"{sha256(HERE / name)}  {name}\n" for name in payload))
    packaged = payload + [PACKAGE_SUMS.name]
    with zipfile.ZipFile(ZIP, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in packaged:
            data = (HERE / name).read_bytes()
            info = zipfile.ZipInfo(name, date_time=(2026, 8, 23, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    outer = payload + [PACKAGE_SUMS.name, ZIP.name]
    OUTER_SUMS.write_text("".join(f"{sha256(HERE / name)}  {name}\n" for name in outer))
    print(json.dumps({"payload_files": len(payload), "zip_sha256": sha256(ZIP), "unique_geometries": 76}, indent=2))


if __name__ == "__main__":
    main()
