#!/usr/bin/env python3
"""Prepare, validate, and run the frozen matched netarsudil/SAM Vina study."""
from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT / "research/mettl7-netarsudil-sam-mechanism/vina-matched"
PREP, CONFIGS, RAW, LOGS = (BASE / p for p in ("prepared", "configs", "raw", "logs"))
HEPH = ROOT / "software/modules/hephaestus/target/hephaestus-1.0-SNAPSHOT-standalone.jar"
VINA = Path("/Users/yazan/bin/vina")
SOURCE_LIG = ROOT / "artifacts/mettl7-netarsudil-docking-inputs/netarsudil_pubchem_cid66599893_3d.sdf"
RECEPTORS = {
    "7A": ROOT / "analysis/dcmb/controlled_campaign/prepared/7A_WT_SAM_BOUND.pdbqt",
    "7B": ROOT / "analysis/dcmb/controlled_campaign/prepared/7B_WT_SAM_BOUND.pdbqt",
}
CANONICAL = {
    "7A": ROOT / "analysis/dcmb/sam_state/validated/WT_METTL7A_SAM_BOUND.pdb",
    "7B": ROOT / "analysis/dcmb/sam_state/validated/WT_METTL7B_SAM_BOUND.pdb",
}
SEEDS = (172904, 483271, 806519)
BOX = {"center_x": 2.844, "center_y": -2.100, "center_z": -4.211,
       "size_x": 25.334, "size_y": 19.263, "size_z": 23.923}


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def atoms(path: Path, residue: str | None = None):
    out = []
    for line in path.read_text().splitlines():
        if not line.startswith(("ATOM", "HETATM")):
            continue
        if residue and line[17:20].strip() != residue:
            continue
        out.append({"name": line[12:16].strip(), "x": float(line[30:38]),
                    "y": float(line[38:46]), "z": float(line[46:54]), "line": line})
    return out


def pdbqt_sam(path: Path):
    rows = []
    for line in path.read_text().splitlines():
        if line.startswith(("ATOM", "HETATM")) and line[17:20].strip() == "SAM":
            parts = line.split()
            rows.append({"name": line[12:16].strip(), "x": float(line[30:38]),
                         "y": float(line[38:46]), "z": float(line[46:54]),
                         "charge": float(parts[-2]), "type": parts[-1]})
    return rows


def prepare() -> dict:
    for d in (PREP, CONFIGS, RAW, LOGS):
        d.mkdir(parents=True, exist_ok=True)
    copied = {}
    for enzyme, src in RECEPTORS.items():
        dst = PREP / f"METTL{enzyme}_SAM_receptor.pdbqt"
        shutil.copy2(src, dst)
        copied[enzyme] = dst

    neutral_sdf = PREP / "netarsudil_CID66599893_neutral.sdf"
    shutil.copy2(SOURCE_LIG, neutral_sdf)
    mono_sdf = PREP / "netarsudil_CID66599893_monocation_pH7.4.sdf"
    subprocess.run(["obabel", str(SOURCE_LIG), "-p", "7.4", "-O", str(mono_sdf)], check=True)
    ligands = {}
    for state, sdf in (("neutral", neutral_sdf), ("monocation", mono_sdf)):
        out = PREP / f"netarsudil_{state}.pdbqt"
        subprocess.run(["java", "-jar", str(HEPH), "prepare-ligand", "--input", str(sdf),
                        "--output", str(out), "--overwrite"], check=True)
        ligands[state] = out

    qc = {"criteria": {"sam_heavy_atoms": 27, "coordinate_tolerance_a": 0.002,
                        "valid_types": ["A", "C", "H", "HD", "N", "NA", "OA", "S"]},
          "receptors": {}, "ligands": {}}
    for enzyme, rec in copied.items():
        can = atoms(CANONICAL[enzyme], "SAM")
        got = pdbqt_sam(rec)
        heavy = [a for a in got if a["type"] not in ("H", "HD")]
        if len(can) != 27 or len(heavy) != 27:
            raise RuntimeError(f"{enzyme}: expected 27 canonical/final SAM heavy atoms")
        # PDBQT torsion-tree order differs from PDB order. Compare the coordinate
        # sets with a one-to-one minimum-distance assignment.
        unused = set(range(len(heavy)))
        deltas = []
        for a in can:
            j, delta = min(((j, ((a["x"]-heavy[j]["x"])**2 +
                                  (a["y"]-heavy[j]["y"])**2 +
                                  (a["z"]-heavy[j]["z"])**2)**0.5) for j in unused),
                           key=lambda item: item[1])
            unused.remove(j)
            deltas.append(delta)
        bad_types = sorted({a["type"] for a in got} - set(qc["criteria"]["valid_types"]))
        entry = {"source_complex": str(CANONICAL[enzyme].relative_to(ROOT)),
                 "source_complex_sha256": sha(CANONICAL[enzyme]),
                 "vina_receptor": str(rec.relative_to(ROOT)), "vina_receptor_sha256": sha(rec),
                 "sam_atoms_total": len(got), "sam_heavy_atoms": len(heavy),
                 "sam_atom_types": sorted({a["type"] for a in got}), "invalid_types": bad_types,
                 "sam_charge_sum": round(sum(a["charge"] for a in got), 6),
                 "coordinate_rmsd_a": (sum(d*d for d in deltas)/len(deltas))**0.5,
                 "coordinate_max_delta_a": max(deltas),
                 "passed": not bad_types and max(deltas) <= 0.002}
        if not entry["passed"]:
            raise RuntimeError(f"{enzyme}: SAM QC failed: {entry}")
        qc["receptors"][enzyme] = entry
    for state, lig in ligands.items():
        rows = [l for l in lig.read_text().splitlines() if l.startswith("ATOM")]
        charges = [float(l.split()[-2]) for l in rows]
        qc["ligands"][state] = {"sdf": str((PREP/f'netarsudil_CID66599893_{state if state == "neutral" else "monocation_pH7.4"}.sdf').relative_to(ROOT)),
                                "pdbqt": str(lig.relative_to(ROOT)), "pdbqt_sha256": sha(lig),
                                "atom_records": len(rows), "charge_sum": round(sum(charges), 6),
                                "nonzero_charge_atoms": sum(abs(q) > 1e-6 for q in charges)}
    (BASE / "sam_receptor_qc.json").write_text(json.dumps(qc, indent=2) + "\n")
    return {"receptors": copied, "ligands": ligands, "qc": qc}


def run(prepared: dict) -> None:
    runs = []
    for enzyme in ("7A", "7B"):
        for state in ("neutral", "monocation"):
            for seed in SEEDS:
                key = f"{enzyme}_{state}_seed{seed}"
                config = CONFIGS / f"{key}.txt"
                lines = [f"receptor = {prepared['receptors'][enzyme]}",
                         f"ligand = {prepared['ligands'][state]}"]
                lines += [f"{k} = {v}" for k, v in BOX.items()]
                lines += ["exhaustiveness = 32", "num_modes = 20", f"seed = {seed}", "cpu = 2"]
                config.write_text("\n".join(lines) + "\n")
                out, log = RAW / f"{key}.pdbqt", LOGS / f"{key}.log"
                cmd = [str(VINA), "--config", str(config), "--out", str(out)]
                with log.open("w") as stream:
                    subprocess.run(cmd, stdout=stream, stderr=subprocess.STDOUT, check=True)
                runs.append({"key": key, "enzyme": enzyme, "state": state, "seed": seed,
                             "config": str(config.relative_to(ROOT)), "config_sha256": sha(config),
                             "receptor": str(prepared["receptors"][enzyme].relative_to(ROOT)),
                             "receptor_sha256": sha(prepared["receptors"][enzyme]),
                             "ligand": str(prepared["ligands"][state].relative_to(ROOT)),
                             "ligand_sha256": sha(prepared["ligands"][state]),
                             "output": str(out.relative_to(ROOT)), "output_sha256": sha(out),
                             "log": str(log.relative_to(ROOT)), "log_sha256": sha(log),
                             "command": cmd})
                print(f"completed {key}", flush=True)
    manifest = {"study": "METTL7_NETARSUDIL_SAM_MECHANISM_MATCHED_VINA_2026_08_29",
                "vina_version": subprocess.check_output([str(VINA), "--version"], text=True).strip(),
                "box": BOX, "exhaustiveness": 32, "num_modes": 20, "seeds": SEEDS,
                "cpu_per_job": 2, "execution": "serial", "runs": runs}
    (BASE / "run_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    run(prepare())
