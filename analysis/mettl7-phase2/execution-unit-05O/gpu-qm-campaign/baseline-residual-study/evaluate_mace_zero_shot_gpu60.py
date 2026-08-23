#!/usr/bin/env python3
"""Zero-shot MACE-OFF24 diagnostic inference on the frozen GPU-60 set."""

from __future__ import annotations

import csv
import hashlib
import json
import platform
import time
from pathlib import Path

import numpy as np
import torch
from ase import Atoms
from mace.calculators import MACECalculator


HERE = Path(__file__).resolve().parent
CAMPAIGN = HERE.parent
UNIT = CAMPAIGN.parent
MODEL_DIR = UNIT / "model-class-benchmark"
MODEL = MODEL_DIR / "MACE-OFF24_medium.model"
RESULTS = CAMPAIGN / "completed-batch-60/gpu_qm_results"
OUT = HERE / "MACE_OFF24_ZERO_SHOT_GPU60_PREDICTIONS.json"
EV_TO_KCAL = 23.06054783061903


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def geometry(path: Path) -> tuple[list[str], np.ndarray]:
    lines = path.read_text().splitlines()[2:]
    symbols = [line.split()[0] for line in lines if line.strip()]
    coordinates = np.array([[float(value) for value in line.split()[1:4]] for line in lines if line.strip()])
    return symbols, coordinates


def predict(calculator: MACECalculator, path: Path) -> tuple[float, np.ndarray, float]:
    symbols, coordinates = geometry(path)
    atoms = Atoms(symbols=symbols, positions=coordinates)
    atoms.calc = calculator
    started = time.perf_counter()
    energy = float(atoms.get_potential_energy()) * EV_TO_KCAL
    force = np.asarray(atoms.get_forces()) * EV_TO_KCAL
    return energy, force, time.perf_counter() - started


def main() -> None:
    torch.set_num_threads(1)
    torch.set_num_interop_threads(1)
    calculator = MACECalculator(model_paths=str(MODEL), device="cpu", default_dtype="float64")
    metadata = {row["campaign_id"]: row for row in csv.DictReader((CAMPAIGN / "GPU_BATCH_60_CHARACTERIZATION.csv").open())}
    reference_energy = {}
    runtime = 0.0
    for minimum in ("MIN01", "MIN02", "MIN04"):
        energy, _, elapsed = predict(calculator, UNIT / f"qm-native-minima/{minimum}/final.xyz")
        reference_energy[minimum] = energy
        runtime += elapsed
    predictions = []
    for campaign_id in sorted(metadata):
        path = RESULTS / campaign_id / "geometry.xyz"
        energy, force, elapsed = predict(calculator, path)
        runtime += elapsed
        predictions.append({
            "campaign_id": campaign_id,
            "geometry_sha256": sha256(path),
            "energy_kcal_mol": energy,
            "energy_kcal_mol_relative_to_source_minimum": energy - reference_energy[metadata[campaign_id]["source_minimum"]],
            "force_kcal_mol_angstrom": force.tolist(),
            "runtime_seconds": elapsed,
        })
    payload = {
        "schema": "tsl-rsh-gpu60-mace-off24-zero-shot-predictions-v1",
        "model": "MACE-OFF24 medium",
        "model_sha256": sha256(MODEL),
        "zero_shot": True,
        "refit_performed": False,
        "device": "cpu",
        "precision": "float64",
        "torch_version": torch.__version__,
        "python_version": platform.python_version(),
        "source_minimum_reference_energy_kcal_mol": reference_energy,
        "total_runtime_seconds": runtime,
        "predictions": predictions,
    }
    OUT.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"count": len(predictions), "runtime_seconds": runtime, "output_sha256": sha256(OUT)}, indent=2))


if __name__ == "__main__":
    main()
