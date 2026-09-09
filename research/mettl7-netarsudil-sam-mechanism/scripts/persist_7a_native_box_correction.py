#!/usr/bin/env python3
"""Hash and prepare database persistence for the 7A native-box correction."""
from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
PROJECT = ROOT / "research/mettl7-netarsudil-sam-mechanism"
BASE = PROJECT / "vina-7a-native-box-correction"
ANALYSIS = BASE / "analysis"
REPORT = PROJECT / "METTL7_NETARSUDIL_7A_NATIVE_BOX_CORRECTION.md"
RUN = "METTL7_NETARSUDIL_7A_NATIVE_BOX_CORRECTION_2026_08_30"


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def quote(value: object) -> str:
    return "'" + str(value).replace("'", "''") + "'"


files = []
for path in sorted(BASE.rglob("*")):
    if path.is_file() and path.name not in {"artifact_hashes.json", "persist.sql"}:
        files.append({"path": str(path.relative_to(ROOT)), "sha256": sha(path), "bytes": path.stat().st_size})
for path in (
    REPORT,
    PROJECT / "scripts/run_7a_native_box_correction.py",
    PROJECT / "scripts/analyze_7a_native_box_correction.py",
    PROJECT / "scripts/persist_7a_native_box_correction.py",
):
    files.append({"path": str(path.relative_to(ROOT)), "sha256": sha(path), "bytes": path.stat().st_size})
(BASE / "artifact_hashes.json").write_text(json.dumps({"run_key": RUN, "artifacts": files}, indent=2) + "\n")

manifest = json.loads((BASE / "run_manifest.json").read_text())
classification = json.loads((ANALYSIS / "classification.json").read_text())
families = list(csv.DictReader((ANALYSIS / "family_results.csv").open()))
contacts = list(csv.DictReader((ANALYSIS / "family_contacts.csv").open()))

sql = ["BEGIN;"]
protocol = {
    "correction_reason": manifest["reason"],
    "run_manifest": str((BASE / "run_manifest.json").relative_to(ROOT)),
    "classification": classification,
    "artifact_hash_manifest": str((BASE / "artifact_hashes.json").relative_to(ROOT)),
    "supersedes_7a_arm_of": "METTL7_NETARSUDIL_SAM_MATCHED_VINA_2026_08_29",
    "preserves_7b_arm_of": "METTL7_NETARSUDIL_SAM_MATCHED_VINA_2026_08_29",
}
conclusion = (
    "Corrected native-frame METTL7A docking returned no admissible neutral or monocation family; "
    "7A remains INDETERMINATE. The unchanged METTL7B neutral family remains SAM-compatible and ADJACENT."
)
sql.append(
    "INSERT INTO docking.mettl7_computational_run"
    "(run_key,title,method,method_version,classification,report_path,input_path,completed_on,protocol,conclusion) VALUES ("
    + ",".join((
        quote(RUN),
        quote("METTL7A native-frame box correction for explicit-SAM netarsudil Vina docking"),
        quote("AutoDock Vina bounded correction with frozen pose-family analysis"),
        quote(manifest["vina_version"]),
        quote("7A INDETERMINATE; unchanged 7B YES and ADJACENT to SAM"),
        quote(str(REPORT.relative_to(ROOT))),
        quote(str((BASE / "run_manifest.json").relative_to(ROOT))),
        "'2026-08-30'",
        quote(json.dumps(protocol, separators=(",", ":"))) + "::jsonb",
        quote(conclusion),
    ))
    + ") ON CONFLICT(run_key) DO UPDATE SET classification=EXCLUDED.classification,report_path=EXCLUDED.report_path,"
      "input_path=EXCLUDED.input_path,protocol=EXCLUDED.protocol,conclusion=EXCLUDED.conclusion;"
)
sql.extend((
    f"DELETE FROM docking.mettl7_netarsudil_vina_contact WHERE run_key={quote(RUN)};",
    f"DELETE FROM docking.mettl7_netarsudil_vina_family WHERE run_key={quote(RUN)};",
))
for row in families:
    values = [
        RUN, row["enzyme"], row["state"], int(row["family"]), int(row["population_all"]),
        int(row["physical_pass_population"]), int(row["physical_pass_seed_count"]), row["physical_pass_seeds"],
        row["admissible"].lower(), int(row["representative_seed"]), int(row["representative_mode"]),
        float(row["vina_score_min"]), float(row["vina_score_mean"]), float(row["sam_min_distance_a"]),
        row["site_relative_to_sam"], float(row["burial_reduction_percent"]), float(row["strain_kcal_mol"]),
        int(row["protein_pairs_lt_1p8"]), int(row["sam_pairs_lt_2"]),
    ]
    rendered = ",".join(quote(v) if isinstance(v, str) and v not in {"true", "false"} else str(v) for v in values)
    sql.append("INSERT INTO docking.mettl7_netarsudil_vina_family VALUES (" + rendered + ");")
for row in contacts:
    values = [RUN, row["enzyme"], row["state"], int(row["family"]), row["chain"] or "", int(row["residue_number"]),
              row["residue_name"], float(row["minimum_distance_a"]), row["focus_region"].lower()]
    rendered = ",".join(quote(v) if isinstance(v, str) and v not in {"true", "false"} else str(v) for v in values)
    sql.append("INSERT INTO docking.mettl7_netarsudil_vina_contact VALUES (" + rendered + ");")
sql.append("COMMIT;")
(BASE / "persist.sql").write_text("\n".join(sql) + "\n")
print(json.dumps({"run_key": RUN, "artifact_count": len(files), "sql": str((BASE / "persist.sql").relative_to(ROOT))}, indent=2))
