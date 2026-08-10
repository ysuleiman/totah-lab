#!/usr/bin/env python3
"""Run the preregistered SAM-present WT 7B/7A candidate campaign."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import subprocess
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
ARTIFACT_ROOT = Path("/Users/yazan/projects/chemflow/backend/artifact-storage")
VINA = Path("/Users/yazan/bin/vina")
PROTOCOL = json.loads((HERE / "protocol.json").read_text())
RAW = HERE / "raw"
LIGANDS = HERE / "prepared-ligands"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def artifact_path(uri: str) -> Path:
    return ARTIFACT_ROOT / uri.removeprefix("local://artifact-storage/")


def first_model(content: str) -> str:
    lines = content.splitlines()
    if not lines or not lines[0].startswith("MODEL"):
        return content.rstrip() + "\n"
    selected = []
    for line in lines[1:]:
        if line.startswith("ENDMDL"):
            break
        selected.append(line)
    return "\n".join(selected).rstrip() + "\n"


def prepare_ligands(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    LIGANDS.mkdir(parents=True, exist_ok=True)
    prepared = []
    for row in rows:
        identity = row["immutable_ligand_identity_sha256"]
        source = artifact_path(row["historical_pose_uri_7b"])
        content = first_model(source.read_text(errors="strict")).encode()
        destination = LIGANDS / f"{identity}.pdbqt"
        destination.write_bytes(content)
        prepared.append({
            "identity": identity,
            "historical_rank": row["historical_rank"],
            "path": str(destination),
            "source_uri": row["historical_pose_uri_7b"],
            "source_sha256": sha256(source.read_bytes()),
            "prepared_sha256": sha256(content),
        })
    return prepared


def command(job: dict) -> list[str]:
    target = job["target"]
    box = PROTOCOL["docking"]["boxes"][target]
    receptor = (HERE / PROTOCOL["receptors"][target]).resolve()
    center, size = box["center_A"], box["size_A"]
    return [
        str(VINA), "--receptor", str(receptor), "--ligand", job["ligand"],
        "--center_x", str(center[0]), "--center_y", str(center[1]), "--center_z", str(center[2]),
        "--size_x", str(size[0]), "--size_y", str(size[1]), "--size_z", str(size[2]),
        "--exhaustiveness", str(PROTOCOL["docking"]["exhaustiveness"]),
        "--num_modes", str(PROTOCOL["docking"]["modes_per_seed"]),
        "--seed", str(job["seed"]), "--cpu", str(PROTOCOL["docking"]["cpu_per_process"]),
        "--out", job["output"],
    ]


def run_job(job: dict) -> dict:
    output = Path(job["output"])
    log = Path(job["log"])
    if output.exists() and "REMARK VINA RESULT:" in output.read_text(errors="replace"):
        return {**job, "status": "EXISTING", "exit_code": 0}
    result = subprocess.run(command(job), capture_output=True, text=True, check=False)
    log.write_text(result.stdout + "\nSTDERR\n" + result.stderr)
    return {**job, "status": "PASS" if result.returncode == 0 else "FAIL", "exit_code": result.returncode}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workers", type=int, default=10)
    args = parser.parse_args()
    manifest = json.loads((HERE / "provenance-manifest.json").read_text())
    expected = next(item["sha256"] for item in manifest["files"] if item["path"] == "candidate-provenance.csv")
    if sha256((HERE / "candidate-provenance.csv").read_bytes()) != expected:
        raise RuntimeError("candidate provenance hash mismatch")
    with (HERE / "candidate-provenance.csv").open(newline="") as handle:
        rows = list(csv.DictReader(handle))
    if len(rows) != 200:
        raise RuntimeError("candidate universe is not the corrected non-WH top 200")
    prepared = prepare_ligands(rows)
    RAW.mkdir(parents=True, exist_ok=True)
    jobs = []
    for ligand in prepared:
        for target in ("7B", "7A"):
            for seed in PROTOCOL["docking"]["seeds"]:
                stem = f"{ligand['identity']}_{target}_s{seed}"
                jobs.append({
                    "identity": ligand["identity"], "historical_rank": ligand["historical_rank"],
                    "target": target, "seed": seed, "ligand": ligand["path"],
                    "output": str(RAW / f"{stem}.pdbqt"), "log": str(RAW / f"{stem}.log"),
                })
    results = []
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = {executor.submit(run_job, job): job for job in jobs}
        for index, future in enumerate(as_completed(futures), 1):
            result = future.result()
            results.append(result)
            if index % 25 == 0 or result["status"] == "FAIL":
                print(f"completed={index}/{len(jobs)} failures={sum(x['status']=='FAIL' for x in results)}", flush=True)
    results.sort(key=lambda row: (int(row["historical_rank"]), row["target"], row["seed"]))
    with (HERE / "docking-run-validation.csv").open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(results[0]))
        writer.writeheader(); writer.writerows(results)
    if any(row["status"] == "FAIL" for row in results):
        raise RuntimeError("one or more docking jobs failed")
    (HERE / "ligand-preparation-manifest.json").write_text(json.dumps(prepared, indent=2) + "\n")
    print(json.dumps({"jobs": len(results), "failed": 0, "candidates": len(prepared)}, indent=2))


if __name__ == "__main__":
    main()
