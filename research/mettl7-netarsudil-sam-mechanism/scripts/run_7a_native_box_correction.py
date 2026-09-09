#!/usr/bin/env python3
"""Correct the METTL7A arm using its established native-frame pocket box."""
from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "research/mettl7-netarsudil-sam-mechanism/vina-matched"
BASE = ROOT / "research/mettl7-netarsudil-sam-mechanism/vina-7a-native-box-correction"
PREP, CONFIGS, RAW, LOGS = (BASE / name for name in ("prepared", "configs", "raw", "logs"))
VINA = Path("/Users/yazan/bin/vina")
SEEDS = (172904, 483271, 806519)
BOX_7A = {
    "center_x": 1.8020,
    "center_y": -3.9254,
    "center_z": -6.7763,
    "size_x": 28.452,
    "size_y": 22.0,
    "size_z": 26.506,
}


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    for directory in (PREP, CONFIGS, RAW, LOGS):
        directory.mkdir(parents=True, exist_ok=True)

    copied_inputs = {}
    for name in (
        "METTL7A_SAM_receptor.pdbqt",
        "METTL7B_SAM_receptor.pdbqt",
        "netarsudil_neutral.pdbqt",
        "netarsudil_monocation.pdbqt",
        "netarsudil_CID66599893_neutral.sdf",
        "netarsudil_CID66599893_monocation_pH7.4.sdf",
    ):
        source = SOURCE / "prepared" / name
        target = PREP / name
        shutil.copy2(source, target)
        copied_inputs[name] = {"source": str(source.relative_to(ROOT)), "sha256": sha(target)}

    runs = []
    receptor = PREP / "METTL7A_SAM_receptor.pdbqt"
    for state in ("neutral", "monocation"):
        ligand = PREP / f"netarsudil_{state}.pdbqt"
        for seed in SEEDS:
            key = f"7A_{state}_seed{seed}"
            config = CONFIGS / f"{key}.txt"
            settings = {
                "receptor": receptor,
                "ligand": ligand,
                **BOX_7A,
                "exhaustiveness": 32,
                "num_modes": 20,
                "seed": seed,
                "cpu": 2,
            }
            config.write_text("\n".join(f"{key} = {value}" for key, value in settings.items()) + "\n")
            output, log = RAW / f"{key}.pdbqt", LOGS / f"{key}.log"
            command = [str(VINA), "--config", str(config), "--out", str(output)]
            with log.open("w") as stream:
                subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, check=True)
            runs.append({
                "key": key,
                "enzyme": "7A",
                "state": state,
                "seed": seed,
                "origin": "NEW_7A_NATIVE_BOX_CORRECTION",
                "config": str(config.relative_to(ROOT)),
                "config_sha256": sha(config),
                "receptor": str(receptor.relative_to(ROOT)),
                "receptor_sha256": sha(receptor),
                "ligand": str(ligand.relative_to(ROOT)),
                "ligand_sha256": sha(ligand),
                "output": str(output.relative_to(ROOT)),
                "output_sha256": sha(output),
                "log": str(log.relative_to(ROOT)),
                "log_sha256": sha(log),
                "command": command,
            })
            print(f"completed {key}", flush=True)

    reused_7b = []
    for state in ("neutral", "monocation"):
        for seed in SEEDS:
            key = f"7B_{state}_seed{seed}"
            entry = {"key": key, "origin": "REUSED_UNCHANGED_VALID_7B_ARM"}
            for folder, suffix in (("raw", ".pdbqt"), ("logs", ".log"), ("configs", ".txt")):
                source = SOURCE / folder / f"{key}{suffix}"
                target = BASE / folder / f"{key}{suffix}"
                shutil.copy2(source, target)
                entry[f"{folder}_source"] = str(source.relative_to(ROOT))
                entry[f"{folder}_sha256"] = sha(target)
            reused_7b.append(entry)

    manifest = {
        "study": "METTL7_NETARSUDIL_7A_NATIVE_BOX_CORRECTION_2026_08_30",
        "reason": "Original 7A arm used 7B numerical box in a distinct native receptor frame.",
        "vina_version": subprocess.check_output([str(VINA), "--version"], text=True).strip(),
        "corrected_7a_box": BOX_7A,
        "established_box_source": "analysis/mettl7-closure/stage1/protocol.json",
        "original_campaign_preserved": str(SOURCE.relative_to(ROOT)),
        "settings": {"exhaustiveness": 32, "num_modes": 20, "seeds": SEEDS, "cpu_per_job": 2},
        "copied_inputs": copied_inputs,
        "new_7a_runs": runs,
        "reused_7b_runs": reused_7b,
    }
    (BASE / "run_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
