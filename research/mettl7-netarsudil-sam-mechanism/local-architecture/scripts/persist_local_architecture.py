#!/usr/bin/env python3
"""Create hashes and SQL persistence for the local-architecture study."""
from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
PROJECT = ROOT / "research/mettl7-netarsudil-sam-mechanism"
BASE = PROJECT / "local-architecture"
OUT = BASE / "analysis"
REPORT = PROJECT / "METTL7_NETARSUDIL_LOCAL_ARCHITECTURE.md"
CANONICAL = PROJECT / "METTL7_NETARSUDIL_SAM_MATCHED_VINA.md"
RUN = "METTL7_NETARSUDIL_LOCAL_ARCHITECTURE_2026_08_30"


def sha(path: Path): return hashlib.sha256(path.read_bytes()).hexdigest()
def quote(value): return "'" + str(value).replace("'", "''") + "'"


files = []
for path in sorted(BASE.rglob("*")):
    if path.is_file() and path.name not in {"artifact_hashes.json", "persist.sql"}:
        files.append({"path": str(path.relative_to(ROOT)), "sha256": sha(path), "bytes": path.stat().st_size})
for path in (REPORT, CANONICAL):
    files.append({"path": str(path.relative_to(ROOT)), "sha256": sha(path), "bytes": path.stat().st_size})
(BASE / "artifact_hashes.json").write_text(json.dumps({"run_key": RUN, "artifacts": files}, indent=2) + "\n")

classification = json.loads((OUT / "classification.json").read_text())
protocol = {
    "primary": json.loads((BASE / "frozen_protocol.json").read_text()),
    "minimal_probe": json.loads((BASE / "minimal_probe_protocol.json").read_text()),
    "k196_correction": json.loads((BASE / "k196_rotamer_correction_protocol.json").read_text()),
    "preparation": json.loads((BASE / "preparation_manifest.json").read_text()),
    "run_manifests": [str((BASE / name).relative_to(ROOT)) for name in
                      ("run_manifest.json", "minimal_probe_run_manifest.json", "k196_rotamer_correction_run_manifest.json")],
    "classification": classification,
    "artifact_manifest": str((BASE / "artifact_hashes.json").relative_to(ROOT)),
}

replicates = list(csv.DictReader((OUT / "replicate_metrics.csv").open()))
residues = list(csv.DictReader((OUT / "residue_190_210_metrics.csv").open()))
sql = ["BEGIN;", """CREATE TABLE IF NOT EXISTS docking.mettl7_netarsudil_local_architecture_residue (
run_key varchar(100) NOT NULL REFERENCES docking.mettl7_computational_run(run_key), system_id varchar(48) NOT NULL,
seed integer NOT NULL, residue_number integer NOT NULL, residue_name varchar(8) NOT NULL,
start_ligand_distance_a double precision NOT NULL, final_ligand_distance_a double precision NOT NULL,
direct_contact_start boolean NOT NULL, direct_contact_final boolean NOT NULL,
sidechain_rmsd_a double precision NOT NULL, sidechain_max_displacement_a double precision NOT NULL,
residue_sasa_a2 double precision NOT NULL, chi1_deg double precision, chi2_deg double precision,
hbond_donors integer NOT NULL, hbond_acceptors integer NOT NULL,
PRIMARY KEY(run_key,system_id,seed,residue_number));"""]
conclusion = ("C203 is not supported as causal. All clash-qualified 7B reciprocal probes pass, whereas native/transferred 7A fails; "
              "the bounded model supports a distributed 196-207 structural-context effect rather than a single side-chain identity.")
sql.append("INSERT INTO docking.mettl7_computational_run"
           "(run_key,title,method,method_version,classification,report_path,input_path,completed_on,protocol,conclusion) VALUES (" +
           ",".join((quote(RUN), quote("METTL7 netarsudil/SAM local-architecture analysis"),
                     quote("Matched static architecture plus Vina local_only reciprocal sensitivity probes"),
                     quote("Vina v1.2.5 / Meeko 0.8.0"),
                     quote("DISTRIBUTED_196_207; C203 NOT_SUPPORTED"), quote(str(REPORT.relative_to(ROOT))),
                     quote(str((BASE / "frozen_protocol.json").relative_to(ROOT))), "'2026-08-30'",
                     quote(json.dumps(protocol, separators=(",", ":"))) + "::jsonb", quote(conclusion))) +
           ") ON CONFLICT(run_key) DO UPDATE SET classification=EXCLUDED.classification,report_path=EXCLUDED.report_path,"
           "input_path=EXCLUDED.input_path,protocol=EXCLUDED.protocol,conclusion=EXCLUDED.conclusion;")
sql.extend((f"DELETE FROM docking.mettl7_netarsudil_local_refinement WHERE run_key={quote(RUN)};",
            f"DELETE FROM docking.mettl7_netarsudil_local_architecture_residue WHERE run_key={quote(RUN)};"))
for row in replicates:
    values = [RUN, row["system"], int(row["seed"]), row["gate_pass"].lower(), row["rejection_reasons"],
              float(row["starting_strain_kcal_mol"]), float(row["final_strain_kcal_mol"]),
              float(row["ligand_symmetry_rmsd_a"]), float(row["centroid_displacement_a"]),
              float(row["ligand_sam_min_distance_a"]), int(row["protein_pairs_lt_1p8"]), int(row["sam_pairs_lt_2"]),
              0.0, float(row["sidechain_rms_displacement_a"]), float(row["sidechain_max_displacement_a"]),
              int(row["retained_contacts"]), float(row["burial_reduction_percent"]), "NULL", "", "", 
              str((BASE / "raw" / f"{row['system']}_seed{row['seed']}.pdbqt").relative_to(ROOT))]
    rendered = []
    for value in values:
        if value == "NULL": rendered.append("NULL")
        elif isinstance(value, (int, float)) or value in {"true", "false"}: rendered.append(str(value))
        else: rendered.append(quote(value))
    sql.append("INSERT INTO docking.mettl7_netarsudil_local_refinement VALUES (" + ",".join(rendered) + ");")
for row in residues:
    values = [RUN, row["system"], int(row["seed"]), int(row["residue_number"]), row["identity"],
              float(row["start_ligand_min_distance_a"]), float(row["final_ligand_min_distance_a"]),
              row["direct_contact_start"].lower(), row["direct_contact_final"].lower(),
              float(row["sidechain_rms_displacement_a"]), float(row["sidechain_max_displacement_a"]),
              float(row["residue_sasa_start_a2"]), row["chi1_deg"], row["chi2_deg"],
              int(row["hbond_donor_count"]), int(row["hbond_acceptor_count"])]
    rendered = []
    for index, value in enumerate(values):
        if index in {12, 13} and value == "": rendered.append("NULL")
        elif isinstance(value, (int, float)) or value in {"true", "false"}: rendered.append(str(value))
        else: rendered.append(quote(value))
    sql.append("INSERT INTO docking.mettl7_netarsudil_local_architecture_residue VALUES (" + ",".join(rendered) + ");")
sql.append("COMMIT;")
(BASE / "persist.sql").write_text("\n".join(sql) + "\n")
print(json.dumps({"run_key": RUN, "artifacts": len(files), "replicates": len(replicates), "residue_rows": len(residues)}, indent=2))
