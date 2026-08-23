#!/usr/bin/env python3
"""Evaluate the preserved Delta V2 2B coefficients on GPU-60 without refitting."""

from __future__ import annotations

import csv
import hashlib
import importlib.util
import json
from pathlib import Path

import numpy as np


HERE = Path(__file__).resolve().parent
CAMPAIGN = HERE.parent
UNIT = CAMPAIGN.parent
DESIGN = UNIT / "delta-potential/design"
V2 = UNIT / "delta-potential/v2"
TRAINING = UNIT / "delta-potential/training-v2/V2_TRAINING_RESULTS.json"
GAFF = HERE / "GAFF2_GPU60_PREDICTIONS.json"
RESULTS = CAMPAIGN / "completed-batch-60/gpu_qm_results"
OUT = HERE / "DELTA_V2_2B_GPU60_PREDICTIONS.json"


def load_stage1():
    spec = importlib.util.spec_from_file_location("preserved_delta_stage1", DESIGN / "run_stage1.py")
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def transform_matrix() -> np.ndarray:
    raw_rows = list(csv.DictReader((DESIGN / "BASIS_CHANNEL_INVENTORY.csv").open()))
    mapping: dict[str, int] = {}
    index = 0
    for row in raw_rows:
        if row["body_order"] == "2":
            for order in range(4):
                mapping[f'{row["channel_id"]}:n{order}'] = index
                index += 1
        elif row["body_order"] == "3":
            for order in (1, 2):
                mapping[f'{row["channel_id"]}:l{order}'] = index
                index += 1
    rows = [row for row in csv.DictReader((V2 / "V2_EXACT_BASIS.csv").open()) if row["model"] == "MODEL_V2_2B"]
    matrix = np.zeros((602, len(rows)))
    for column, row in enumerate(rows):
        for key, value in json.loads(row["orthogonal_expansion"]).items():
            matrix[mapping[key], column] = value
    return matrix


def main() -> None:
    stage1 = load_stage1()
    frozen = json.loads(TRAINING.read_text())
    model = frozen["models"]["MODEL_V2_2B"]
    coefficients = np.asarray(model["full_training_coefficients"], dtype=float)
    transform = transform_matrix()
    if transform.shape[1] != len(coefficients):
        raise ValueError("Frozen V2 coefficient/basis dimension mismatch")
    types, distances = stage1.topology()
    pairs, triples = stage1.inventory()
    reference_features = {}
    for minimum in ("MIN01", "MIN02", "MIN04"):
        coordinates = stage1.xyz(UNIT / f"qm-native-minima/{minimum}/final.xyz")
        reference_features[minimum] = stage1.features(coordinates, types, distances, pairs, triples)[0]

    metadata = {row["campaign_id"]: row for row in csv.DictReader((CAMPAIGN / "GPU_BATCH_60_CHARACTERIZATION.csv").open())}
    gaff = json.loads(GAFF.read_text())
    gaff_predictions = {row["campaign_id"]: row for row in gaff["predictions"]}
    predictions = []
    for campaign_id in sorted(metadata):
        directory = RESULTS / campaign_id
        coordinates = stage1.xyz(directory / "geometry.xyz")
        values, gradients = stage1.features(coordinates, types, distances, pairs, triples)
        minimum = metadata[campaign_id]["source_minimum"]
        delta_energy = float(((values - reference_features[minimum]) @ transform) @ coefficients)
        transformed_gradient = np.einsum("fak,fc->cak", gradients, transform)
        delta_force = -np.einsum("cak,c->ak", transformed_gradient, coefficients)
        base = gaff_predictions[campaign_id]
        predictions.append({
            "campaign_id": campaign_id,
            "geometry_sha256": sha256(directory / "geometry.xyz"),
            "gaff2_energy_kcal_mol": base["energy_kcal_mol"],
            "delta_energy_kcal_mol_relative_to_source_minimum": delta_energy,
            "force_kcal_mol_angstrom": (np.asarray(base["force_kcal_mol_angstrom"]) + delta_force).tolist(),
            "delta_force_kcal_mol_angstrom": delta_force.tolist(),
        })
    payload = {
        "schema": "tsl-rsh-gpu60-preserved-delta-v2-2b-predictions-v1",
        "model": "MODEL_V2_2B",
        "scientific_identity": "preserved full-training V2 2B adapter added to preserved GAFF2 baseline",
        "refit_performed": False,
        "historical_classification": frozen["classification"],
        "training_results_sha256": sha256(TRAINING),
        "basis_sha256": sha256(V2 / "V2_EXACT_BASIS.csv"),
        "coefficient_count": len(coefficients),
        "coefficient_sha256": hashlib.sha256(coefficients.astype("<f8").tobytes()).hexdigest(),
        "gaff2_predictions_sha256": sha256(GAFF),
        "predictions": predictions,
    }
    OUT.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"count": len(predictions), "output_sha256": sha256(OUT)}, indent=2))


if __name__ == "__main__":
    main()
