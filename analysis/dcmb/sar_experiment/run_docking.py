#!/usr/bin/env python3
"""Run the bounded four-state WT SAR campaign with resumable outputs."""
from __future__ import annotations

import csv
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
VINA = Path("/Users/yazan/bin/vina")
SEED = 20260809
EXHAUSTIVENESS = 16
NUM_MODES = 12
CPU = 1
MAX_PARALLEL = 8
BOX = {
    "7A": (1.8020, -3.9254, -6.7763, 28.452, 22.0, 26.506),
    "7B": (2.8444, -2.1005, -4.2105, 25.334, 22.0, 23.923),
}
SOURCE_RECEPTORS = {
    "7A_APO": ROOT / "analysis/dcmb/controlled_campaign/prepared/7A_WT_APO.pdbqt",
    "7A_SAM": ROOT / "analysis/dcmb/controlled_campaign/prepared/7A_WT_SAM_BOUND.pdbqt",
    "7B_APO": ROOT / "analysis/dcmb/controlled_campaign/prepared/7B_WT_APO.pdbqt",
    "7B_SAM": ROOT / "analysis/dcmb/controlled_campaign/prepared/7B_WT_SAM_BOUND.pdbqt",
}


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    prep = HERE / "prepared_receptors"; raw = HERE / "raw"
    prep.mkdir(exist_ok=True); raw.mkdir(exist_ok=True)
    receptors = {}
    for state, source in SOURCE_RECEPTORS.items():
        target = prep / f"{state}.pdbqt"
        if not target.exists() or sha(target) != sha(source):
            shutil.copyfile(source, target)
        receptors[state] = target
    with (HERE / "sar_compounds.csv").open() as fh:
        compounds = [r["compound_id"] for r in csv.DictReader(fh)]
    jobs = []
    for state, receptor in receptors.items():
        paralog = state[:2]
        cx, cy, cz, sx, sy, sz = BOX[paralog]
        for compound in compounds:
            ligand = HERE / "ligands" / f"{compound}.pdbqt"
            out = raw / f"{state}__{compound}.pdbqt"
            log = raw / f"{state}__{compound}.log"
            if out.exists() and log.exists() and "Writing output" in log.read_text(errors="ignore"):
                continue
            cmd = [str(VINA), "--receptor", str(receptor), "--ligand", str(ligand),
                   "--center_x", str(cx), "--center_y", str(cy), "--center_z", str(cz),
                   "--size_x", str(sx), "--size_y", str(sy), "--size_z", str(sz),
                   "--exhaustiveness", str(EXHAUSTIVENESS), "--num_modes", str(NUM_MODES),
                   "--seed", str(SEED), "--cpu", str(CPU), "--out", str(out)]
            jobs.append((cmd, log))
    running = []
    for cmd, log in jobs:
        fh = log.open("w")
        running.append((subprocess.Popen(cmd, stdout=fh, stderr=subprocess.STDOUT), fh, cmd))
        if len(running) >= MAX_PARALLEL:
            proc, handle, launched = running.pop(0); rc = proc.wait(); handle.close()
            if rc:
                raise RuntimeError(f"Vina failed: {' '.join(launched)}")
    for proc, handle, launched in running:
        rc = proc.wait(); handle.close()
        if rc:
            raise RuntimeError(f"Vina failed: {' '.join(launched)}")
    version = subprocess.run([str(VINA), "--version"], text=True, capture_output=True, check=True).stdout.strip()
    manifest = {
        "engine": version, "seed": SEED, "exhaustiveness": EXHAUSTIVENESS,
        "num_modes_requested": NUM_MODES, "cpu_per_job": CPU, "boxes": BOX,
        "scope": "18 explicit prepared ligand variants x four WT receptor states; no broad screening",
        "receptors": {k: {"source": str(SOURCE_RECEPTORS[k]), "sha256": sha(v),
                           "sam_atom_records": sum(" SAM " in x for x in v.read_text().splitlines())}
                      for k, v in receptors.items()},
        "ligands": {c: sha(HERE / "ligands" / f"{c}.pdbqt") for c in compounds},
        "completed_outputs": len(list(raw.glob("*.pdbqt"))),
    }
    (HERE / "docking_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
