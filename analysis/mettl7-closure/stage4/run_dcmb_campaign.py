#!/usr/bin/env python3
"""Run the locked SAM-present DCMB campaign for the eight Stage 2 systems."""

from __future__ import annotations

import hashlib
import json
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
PROTOCOL = ROOT / "analysis/mettl7-closure/stage1/protocol.json"
STAGE2 = ROOT / "analysis/mettl7-closure/stage2/prepared"
HEPHAESTUS = ROOT / "software/modules/hephaestus/target/hephaestus-1.0-SNAPSHOT-standalone.jar"
VINA = Path("/Users/yazan/bin/vina")
LIGANDS = {
    "R": ROOT / "analysis/dcmb/artifacts/DCMB_R.pdbqt",
    "S": ROOT / "analysis/dcmb/artifacts/DCMB_S.pdbqt",
}
SAM_TEMPLATES = {
    "7A": ROOT / "analysis/dcmb/controlled_campaign/prepared/7A_SAM.pdbqt",
    "7B": ROOT / "analysis/dcmb/controlled_campaign/prepared/7B_SAM.pdbqt",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def atom_lines(path: Path) -> list[str]:
    return [line for line in path.read_text().splitlines() if line.startswith(("ATOM  ", "HETATM"))]


def append_fixed_sam(protein: Path, sam: Path, output: Path) -> None:
    protein_lines = atom_lines(protein)
    serial = max(int(line[6:11]) for line in protein_lines)
    sam_lines = atom_lines(sam)
    fixed = [line[:6] + f"{index:5d}" + line[11:] for index, line in enumerate(sam_lines, serial + 1)]
    output.write_text("\n".join(protein_lines + fixed) + "\n")


def prepare(protocol: dict) -> list[dict]:
    prepared = HERE / "prepared"
    prepared.mkdir(parents=True, exist_ok=True)
    conditions = []
    for item in protocol["systems"]:
        system = item["id"]
        paralog = system[:2]
        receptor_pdb = STAGE2 / f"{system}_receptor.pdb"
        protein_pdbqt = prepared / f"{system}_protein.pdbqt"
        bound_pdbqt = prepared / f"{system}_SAM_BOUND.pdbqt"
        subprocess.run(
            [
                "java", "-jar", str(HEPHAESTUS), "prepare-receptor",
                "--input", str(receptor_pdb), "--output", str(protein_pdbqt),
                "--remove-waters", "--overwrite",
            ],
            check=True,
        )
        append_fixed_sam(protein_pdbqt, SAM_TEMPLATES[paralog], bound_pdbqt)
        sam_records = sum(" SAM " in line for line in atom_lines(bound_pdbqt))
        if sam_records != protocol["coordinate_policy"]["sam_prepared_atom_records_expected"]:
            raise RuntimeError(f"{system}: expected 49 fixed-SAM atom records, observed {sam_records}")
        conditions.append({
            "system": system,
            "paralog": paralog,
            "receptor_pdb": str(receptor_pdb.relative_to(ROOT)),
            "receptor_pdb_sha256": sha256(receptor_pdb),
            "protein_pdbqt": str(protein_pdbqt.relative_to(ROOT)),
            "protein_pdbqt_sha256": sha256(protein_pdbqt),
            "receptor_pdbqt": str(bound_pdbqt.relative_to(ROOT)),
            "receptor_pdbqt_sha256": sha256(bound_pdbqt),
            "sam_template": str(SAM_TEMPLATES[paralog].relative_to(ROOT)),
            "sam_template_sha256": sha256(SAM_TEMPLATES[paralog]),
            "sam_atom_records": sam_records,
        })
    return conditions


def dock(protocol: dict, conditions: list[dict]) -> None:
    docking = protocol["dcmb_docking"]
    raw = HERE / "raw"
    raw.mkdir(parents=True, exist_ok=True)
    jobs = []
    for condition in conditions:
        target = "METTL7A" if condition["paralog"] == "7A" else "METTL7B"
        box = docking["boxes"][target]
        for enantiomer, ligand in LIGANDS.items():
            for seed in docking["seeds"]:
                stem = f'{condition["system"]}_{enantiomer}_s{seed}'
                output = raw / f"{stem}.pdbqt"
                log = raw / f"{stem}.log"
                command = [
                    str(VINA), "--receptor", str(ROOT / condition["receptor_pdbqt"]),
                    "--ligand", str(ligand),
                    "--center_x", str(box["center_A"][0]), "--center_y", str(box["center_A"][1]),
                    "--center_z", str(box["center_A"][2]), "--size_x", str(box["size_A"][0]),
                    "--size_y", str(box["size_A"][1]), "--size_z", str(box["size_A"][2]),
                    "--exhaustiveness", str(docking["exhaustiveness"]),
                    "--num_modes", str(docking["modes_requested_per_seed"]),
                    "--seed", str(seed), "--out", str(output),
                ]
                jobs.append((command, log))

    running = []
    for command, log in jobs:
        handle = log.open("w")
        running.append((subprocess.Popen(command, stdout=handle, stderr=subprocess.STDOUT), handle, command))
        if len(running) >= 8:
            process, handle, completed_command = running.pop(0)
            return_code = process.wait()
            handle.close()
            if return_code:
                raise RuntimeError(f"Docking failed: {completed_command}")
    for process, handle, completed_command in running:
        return_code = process.wait()
        handle.close()
        if return_code:
            raise RuntimeError(f"Docking failed: {completed_command}")


def main() -> None:
    HERE.mkdir(parents=True, exist_ok=True)
    protocol = json.loads(PROTOCOL.read_text())
    conditions = prepare(protocol)
    dock(protocol, conditions)
    manifest = {
        "protocol": str(PROTOCOL.relative_to(ROOT)),
        "protocol_sha256": sha256(PROTOCOL),
        "physical_state": "protein_plus_fixed_SAM",
        "engine": protocol["dcmb_docking"]["engine"],
        "seeds": protocol["dcmb_docking"]["seeds"],
        "exhaustiveness": protocol["dcmb_docking"]["exhaustiveness"],
        "modes_requested_per_seed": protocol["dcmb_docking"]["modes_requested_per_seed"],
        "boxes": protocol["dcmb_docking"]["boxes"],
        "ligands": {key: {"path": str(value.relative_to(ROOT)), "sha256": sha256(value)} for key, value in LIGANDS.items()},
        "conditions": conditions,
    }
    (HERE / "campaign_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
