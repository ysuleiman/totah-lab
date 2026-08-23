#!/usr/bin/env python3
"""Evaluate the preserved, unmodified GAFF2 baseline on frozen GPU-60 geometries."""

from __future__ import annotations

import hashlib
import json
import platform
from pathlib import Path

import numpy as np
import parmed as pmd
import sander


HERE = Path(__file__).resolve().parent
CAMPAIGN = HERE.parent
UNIT = CAMPAIGN.parent
RESULTS = CAMPAIGN / "completed-batch-60/gpu_qm_results"
TOP = UNIT / "model-form-analysis/baseline/baseline.parm7"
OUT = HERE / "GAFF2_GPU60_PREDICTIONS.json"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def geometry(path: Path) -> tuple[list[str], np.ndarray]:
    lines = path.read_text().splitlines()
    symbols = [line.split()[0] for line in lines[2:] if line.strip()]
    coordinates = np.array([[float(value) for value in line.split()[1:4]] for line in lines[2:] if line.strip()])
    return symbols, coordinates


def energy_terms(energy) -> dict[str, float]:
    names = ("tot", "bond", "angle", "dihedral", "elec", "elec_14", "vdw", "vdw_14", "imp", "gb")
    return {name: float(getattr(energy, name)) for name in names}


def main() -> None:
    directories = sorted(path for path in RESULTS.iterdir() if path.is_dir() and path.name.startswith("TSLRSH-GPU-"))
    if len(directories) != 60:
        raise ValueError(f"Expected 60 result directories, found {len(directories)}")
    topology = pmd.load_file(str(TOP))
    symbols, first = geometry(directories[0] / "geometry.xyz")
    if len(symbols) != 56 or len(topology.atoms) != 56:
        raise ValueError("Atom count mismatch")
    atomic_numbers = [atom.atomic_number for atom in topology.atoms]
    expected = [{"H": 1, "C": 6, "O": 8, "S": 16}[symbol] for symbol in symbols]
    if atomic_numbers != expected:
        raise ValueError("Topology and GPU geometry atom ordering differ")

    options = sander.gas_input()
    options.cut = 999.0
    sander.setup(str(TOP), first, None, options)
    predictions = []
    try:
        for directory in directories:
            point_symbols, coordinates = geometry(directory / "geometry.xyz")
            if point_symbols != symbols:
                raise ValueError(f"Atom ordering mismatch: {directory.name}")
            result = json.loads((directory / "result.json").read_text())
            if result["campaign_id"] != directory.name or not result["scf_converged"]:
                raise ValueError(f"Invalid frozen result: {directory.name}")
            sander.set_positions(coordinates)
            energy, force = sander.energy_forces(as_numpy=True)
            predictions.append({
                "campaign_id": directory.name,
                "geometry_sha256": sha256(directory / "geometry.xyz"),
                "energy_kcal_mol": float(energy.tot),
                "energy_terms_kcal_mol": energy_terms(energy),
                "force_kcal_mol_angstrom": np.asarray(force).reshape(56, 3).tolist(),
            })
    finally:
        sander.cleanup()

    payload = {
        "schema": "tsl-rsh-gpu60-preserved-baseline-predictions-v1",
        "baseline": "accepted native AmberTools26 RESP charges + unmodified GAFF2 2.2.30 + original parmchk2 completion",
        "refit_performed": False,
        "topology_path": str(TOP.relative_to(UNIT)),
        "topology_sha256": sha256(TOP),
        "atom_order": symbols,
        "settings": {"periodic": False, "implicit_solvent": False, "cutoff_angstrom": 999.0},
        "software": {"AmberTools": "26.0", "ParmEd": pmd.__version__, "Python": platform.python_version()},
        "predictions": predictions,
    }
    OUT.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"count": len(predictions), "output_sha256": sha256(OUT)}, indent=2))


if __name__ == "__main__":
    main()
