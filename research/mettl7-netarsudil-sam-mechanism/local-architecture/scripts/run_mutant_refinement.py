#!/usr/bin/env python3
"""Run the frozen neutral-state local-architecture sensitivity batch."""
from __future__ import annotations

import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
BASE = ROOT / "research/mettl7-netarsudil-sam-mechanism/local-architecture"
PREP, CONFIG, RAW, LOGS = (BASE / name for name in ("prepared", "configs", "raw", "logs"))
for directory in (CONFIG, RAW, LOGS):
    directory.mkdir(parents=True, exist_ok=True)
VINA = Path("/Users/yazan/bin/vina")
SEEDS = (314159, 271828, 161803)
SYSTEMS = ("7B_WT", "7B_C203N", "7B_RECIP_196_199", "7B_RECIP_196_199_C203N", "7A_WT_TRANSFER", "7A_N203C_TRANSFER")
MINIMAL_SYSTEMS = ("7B_K196H", "7B_H197L_I198L", "7B_G199F", "7B_T208S")
LOCAL_PREP = ROOT / "research/mettl7-netarsudil-sam-mechanism/local-flexibility/prepared"


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    runs = []
    for system in SYSTEMS:
        enzyme = system[:2]
        ligand = LOCAL_PREP / ("netarsudil_neutral_start.pdbqt" if enzyme == "7B" else "netarsudil_neutral_start_7A_transfer.pdbqt")
        rigid = PREP / f"{system}_rigid_with_SAM.pdbqt"
        flex = PREP / f"{system}_flex.pdbqt"
        for seed in SEEDS:
            key = f"{system}_seed{seed}"
            config, output, log = CONFIG / f"{key}.txt", RAW / f"{key}.pdbqt", LOGS / f"{key}.log"
            config.write_text("\n".join((f"receptor = {rigid}", f"flex = {flex}", f"ligand = {ligand}",
                                          "local_only = true", "autobox = true", f"seed = {seed}", "cpu = 1", "num_modes = 1")) + "\n")
            command = [str(VINA), "--config", str(config), "--out", str(output)]
            with log.open("w") as stream:
                subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, check=True)
            runs.append({"key": key, "system": system, "enzyme": enzyme, "seed": seed,
                         "config": str(config.relative_to(ROOT)), "config_sha256": sha(config),
                         "rigid_receptor": str(rigid.relative_to(ROOT)), "rigid_sha256": sha(rigid),
                         "flex_receptor": str(flex.relative_to(ROOT)), "flex_sha256": sha(flex),
                         "ligand": str(ligand.relative_to(ROOT)), "ligand_sha256": sha(ligand),
                         "output": str(output.relative_to(ROOT)), "output_sha256": sha(output),
                         "log": str(log.relative_to(ROOT)), "log_sha256": sha(log), "command": command})
            print("completed", key, flush=True)
    manifest = {"run_key": "METTL7_NETARSUDIL_LOCAL_ARCHITECTURE_2026_08_30",
                "vina_version": subprocess.check_output([str(VINA), "--version"], text=True).strip(),
                "method": "local_only", "global_redocking": False, "seeds": SEEDS, "runs": runs}
    (BASE / "run_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


def minimal():
    runs = []
    ligand = LOCAL_PREP / "netarsudil_neutral_start.pdbqt"
    for system in MINIMAL_SYSTEMS:
        rigid, flex = PREP / f"{system}_rigid_with_SAM.pdbqt", PREP / f"{system}_flex.pdbqt"
        for seed in SEEDS:
            key = f"{system}_seed{seed}"
            config, output, log = CONFIG / f"{key}.txt", RAW / f"{key}.pdbqt", LOGS / f"{key}.log"
            config.write_text("\n".join((f"receptor = {rigid}", f"flex = {flex}", f"ligand = {ligand}",
                                          "local_only = true", "autobox = true", f"seed = {seed}", "cpu = 1", "num_modes = 1")) + "\n")
            command = [str(VINA), "--config", str(config), "--out", str(output)]
            with log.open("w") as stream:
                subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, check=True)
            runs.append({"key": key, "system": system, "enzyme": "7B", "seed": seed,
                         "config": str(config.relative_to(ROOT)), "config_sha256": sha(config),
                         "rigid_receptor": str(rigid.relative_to(ROOT)), "rigid_sha256": sha(rigid),
                         "flex_receptor": str(flex.relative_to(ROOT)), "flex_sha256": sha(flex),
                         "ligand": str(ligand.relative_to(ROOT)), "ligand_sha256": sha(ligand),
                         "output": str(output.relative_to(ROOT)), "output_sha256": sha(output),
                         "log": str(log.relative_to(ROOT)), "log_sha256": sha(log), "command": command})
            print("completed", key, flush=True)
    manifest = {"run_key": "METTL7_NETARSUDIL_LOCAL_ARCHITECTURE_MINIMAL_PROBES_2026_08_30",
                "vina_version": subprocess.check_output([str(VINA), "--version"], text=True).strip(),
                "method": "local_only", "global_redocking": False, "seeds": SEEDS, "runs": runs}
    (BASE / "minimal_probe_run_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


def k196_corrected():
    systems = ("7B_K196H", "7B_RECIP_196_199", "7B_RECIP_196_199_C203N")
    ligand = LOCAL_PREP / "netarsudil_neutral_start.pdbqt"; runs = []
    for system in systems:
        rigid, flex = PREP / f"{system}_rigid_with_SAM.pdbqt", PREP / f"{system}_flex.pdbqt"
        for seed in SEEDS:
            key = f"{system}_seed{seed}"; config = CONFIG / f"{key}.txt"; output = RAW / f"{key}.pdbqt"; log = LOGS / f"{key}.log"
            config.write_text("\n".join((f"receptor = {rigid}", f"flex = {flex}", f"ligand = {ligand}", "local_only = true",
                                          "autobox = true", f"seed = {seed}", "cpu = 1", "num_modes = 1")) + "\n")
            command = [str(VINA), "--config", str(config), "--out", str(output)]
            with log.open("w") as stream: subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, check=True)
            runs.append({"key": key, "system": system, "seed": seed, "config_sha256": sha(config), "rigid_sha256": sha(rigid),
                         "flex_sha256": sha(flex), "ligand_sha256": sha(ligand), "output_sha256": sha(output), "log_sha256": sha(log), "command": command})
            print("completed", key, flush=True)
    (BASE / "k196_rotamer_correction_run_manifest.json").write_text(json.dumps({"run_key": "METTL7_NETARSUDIL_K196_ROTAMER_CORRECTION_2026_08_30",
        "supersedes_overlapped_outputs": True, "vina_version": subprocess.check_output([str(VINA), "--version"], text=True).strip(), "runs": runs}, indent=2) + "\n")


if __name__ == "__main__":
    import sys
    k196_corrected() if "--k196-corrected" in sys.argv else minimal() if "--minimal" in sys.argv else main()
