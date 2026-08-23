#!/usr/bin/env python3
"""Residual structure and locality diagnostics for the frozen GPU-60 labels."""

from __future__ import annotations

import csv
import hashlib
import json
import math
from collections import Counter, deque
from pathlib import Path

import numpy as np


HERE = Path(__file__).resolve().parent
CAMPAIGN = HERE.parent
UNIT = CAMPAIGN.parent
RESULTS = CAMPAIGN / "completed-batch-60/gpu_qm_results"
CHARACTERIZATION = CAMPAIGN / "GPU_BATCH_60_CHARACTERIZATION.csv"
SPLIT = HERE / "GPU60_SPLIT_FROZEN.csv"
EH_TO_KCAL = 627.5094740631
BOHR_TO_ANGSTROM = 0.529177210903
FORCE_CONVERSION = EH_TO_KCAL / BOHR_TO_ANGSTROM
LOCAL = np.array([1, 7, 8, 9, 10, 25, 36, 55])
SULFUR = 25


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rms(values) -> float:
    array = np.asarray(values, dtype=float)
    return float(np.sqrt(np.mean(array * array)))


def mae(values) -> float:
    return float(np.mean(np.abs(np.asarray(values, dtype=float))))


def summary(values) -> dict[str, float]:
    array = np.asarray(values, dtype=float)
    return {"rms": rms(array), "mae": mae(array), "max": float(np.max(np.abs(array)))}


def rankdata(values: np.ndarray) -> np.ndarray:
    order = np.argsort(values, kind="mergesort")
    ranks = np.empty(len(values), dtype=float)
    start = 0
    while start < len(values):
        stop = start + 1
        while stop < len(values) and values[order[stop]] == values[order[start]]:
            stop += 1
        ranks[order[start:stop]] = (start + stop - 1) / 2
        start = stop
    return ranks


def spearman(a, b) -> float | None:
    a, b = rankdata(np.asarray(a, dtype=float)), rankdata(np.asarray(b, dtype=float))
    if np.std(a) == 0 or np.std(b) == 0:
        return None
    return float(np.corrcoef(a, b)[0, 1])


def xyz(path: Path) -> tuple[list[str], np.ndarray]:
    lines = path.read_text().splitlines()[2:]
    return (
        [line.split()[0] for line in lines if line.strip()],
        np.array([[float(value) for value in line.split()[1:4]] for line in lines if line.strip()]),
    )


def topology_shells() -> dict[str, np.ndarray]:
    mol2 = UNIT / "TSL_RSH_NATIVE_AMBERTOOLS26_RESP3MIN_HF631GD_CANDIDATE_V1.mol2"
    lines = mol2.read_text().splitlines()
    begin = lines.index("@<TRIPOS>BOND") + 1
    adjacency = [set() for _ in range(56)]
    for line in lines[begin:]:
        if line.startswith("@<TRIPOS>"):
            break
        if line.strip():
            fields = line.split()
            a, b = int(fields[1]) - 1, int(fields[2]) - 1
            adjacency[a].add(b)
            adjacency[b].add(a)
    distances = np.full(56, 999, dtype=int)
    distances[SULFUR] = 0
    queue = deque([SULFUR])
    while queue:
        atom = queue.popleft()
        for neighbor in adjacency[atom]:
            if distances[neighbor] == 999:
                distances[neighbor] = distances[atom] + 1
                queue.append(neighbor)
    directly_bonded = np.array(sorted({SULFUR, *adjacency[SULFUR]}))
    return {
        "sulfur_only_geometry": directly_bonded,
        "sulfur_plus_direct_neighbors": np.array(sorted({*directly_bonded, *(n for a in directly_bonded for n in adjacency[a])})),
        "sulfur_local_shell": LOCAL,
        "topological_shell_3": np.where(distances <= 3)[0],
        "topological_shell_5": np.where(distances <= 5)[0],
        "whole_molecule": np.arange(56),
    }


def pair_descriptor(coordinates: np.ndarray, atoms: np.ndarray) -> np.ndarray:
    return np.array([
        np.linalg.norm(coordinates[atoms[i]] - coordinates[atoms[j]])
        for i in range(len(atoms)) for j in range(i + 1, len(atoms))
    ])


def angle_degrees(coordinates: np.ndarray, a: int, b: int, c: int) -> float:
    left, right = coordinates[a] - coordinates[b], coordinates[c] - coordinates[b]
    cosine = np.dot(left, right) / (np.linalg.norm(left) * np.linalg.norm(right))
    return float(np.degrees(np.arccos(np.clip(cosine, -1.0, 1.0))))


def standardize(train: np.ndarray, test: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    mean = train.mean(axis=0)
    scale = train.std(axis=0)
    scale[scale < 1e-12] = 1.0
    return (train - mean) / scale, (test - mean) / scale


def predict_ridge(train_x, train_y, test_x, alpha=1.0) -> np.ndarray:
    train_x, test_x = standardize(train_x, test_x)
    design = np.column_stack((np.ones(len(train_x)), train_x))
    test_design = np.column_stack((np.ones(len(test_x)), test_x))
    penalty = np.eye(design.shape[1]) * alpha
    penalty[0, 0] = 0
    coefficients = np.linalg.pinv(design.T @ design + penalty) @ design.T @ train_y
    return test_design @ coefficients


def predict_kernel(train_x, train_y, test_x, alpha=1e-3) -> np.ndarray:
    train_x, test_x = standardize(train_x, test_x)
    train_d2 = np.sum((train_x[:, None, :] - train_x[None, :, :]) ** 2, axis=2)
    positive = train_d2[train_d2 > 1e-12]
    gamma = 1.0 / float(np.median(positive)) if len(positive) else 1.0
    kernel = np.exp(-gamma * train_d2)
    test_kernel = np.exp(-gamma * np.sum((test_x[:, None, :] - train_x[None, :, :]) ** 2, axis=2))
    centered_y = train_y - train_y.mean()
    return train_y.mean() + test_kernel @ np.linalg.solve(kernel + alpha * np.eye(len(train_x)), centered_y)


def diagnostic_cv(features: np.ndarray, target: np.ndarray, development: np.ndarray, minima: np.ndarray) -> dict:
    indices = np.where(development)[0]
    folds = [indices[np.arange(len(indices)) % 5 == fold] for fold in range(5)]
    random_results = {}
    for name, predictor in (("ridge", predict_ridge), ("rbf_kernel", predict_kernel)):
        prediction = np.zeros(len(indices))
        for test in folds:
            train = np.setdiff1d(indices, test)
            prediction[np.searchsorted(indices, test)] = predictor(features[train], target[train], features[test])
        denominator = np.sum((target[indices] - target[indices].mean()) ** 2)
        random_results[name] = {
            "r2": float(1 - np.sum((target[indices] - prediction) ** 2) / denominator),
            "rmse": rms(target[indices] - prediction),
        }
    leave_minimum = np.zeros(len(indices))
    for minimum in ("MIN01", "MIN02", "MIN04"):
        test = indices[minima[indices] == minimum]
        train = indices[minima[indices] != minimum]
        leave_minimum[np.searchsorted(indices, test)] = predict_kernel(features[train], target[train], features[test])
    denominator = np.sum((target[indices] - target[indices].mean()) ** 2)
    return {
        "development_only": True,
        "target": "per-geometry sulfur-local Cartesian residual RMS",
        "five_fold": random_results,
        "leave_one_source_minimum_out_rbf": {
            "r2": float(1 - np.sum((target[indices] - leave_minimum) ** 2) / denominator),
            "rmse": rms(target[indices] - leave_minimum),
        },
    }


def load_data():
    characterization = list(csv.DictReader(CHARACTERIZATION.open()))
    split = {row["campaign_id"]: row for row in csv.DictReader(SPLIT.open())}
    identifiers = [row["campaign_id"] for row in characterization]
    coordinates, symbols, energies, forces = [], [], [], []
    for identifier in identifiers:
        directory = RESULTS / identifier
        point_symbols, point_coordinates = xyz(directory / "geometry.xyz")
        result = json.loads((directory / "result.json").read_text())
        if result["geometry_sha256"] != sha256(directory / "geometry.xyz"):
            raise ValueError(f"Geometry checksum mismatch: {identifier}")
        coordinates.append(point_coordinates)
        symbols.append(point_symbols)
        energies.append(float(result["total_energy_hartree"]) * EH_TO_KCAL)
        forces.append(np.asarray(result["force_hartree_per_bohr"]) * FORCE_CONVERSION)
    if any(order != symbols[0] for order in symbols):
        raise ValueError("Atom ordering differs within GPU-60")
    return identifiers, characterization, split, np.asarray(coordinates), np.asarray(energies), np.asarray(forces), symbols[0]


def residual_metrics(energy_residual: np.ndarray, force_residual: np.ndarray, indices: np.ndarray) -> dict:
    local = force_residual[indices][:, LOCAL, :]
    sulfur_vectors = force_residual[indices, SULFUR, :]
    return {
        "count": int(len(indices)),
        "energy_kcal_mol": summary(energy_residual[indices]),
        "global_force_kcal_mol_angstrom": summary(force_residual[indices]),
        "sulfur_local_force_kcal_mol_angstrom": summary(local),
        "sulfur_atom_vector_force_rms_kcal_mol_angstrom": float(np.sqrt(np.mean(np.sum(sulfur_vectors ** 2, axis=1)))),
    }


def main() -> None:
    identifiers, rows, split, coordinates, qm_energy, qm_force, symbols = load_data()
    minima = np.array([row["source_minimum"] for row in rows])
    families = np.array([row["family"] for row in rows])
    development = np.array([split[item]["partition"] == "DEVELOPMENT" for item in identifiers])
    strain = np.zeros(60)
    for minimum in ("MIN01", "MIN02", "MIN04"):
        mask = minima == minimum
        strain[mask] = qm_energy[mask] - np.min(qm_energy[mask])
    thresholds = np.quantile(strain, [1 / 3, 2 / 3])
    regimes = np.where(strain <= thresholds[0], "LOW", np.where(strain <= thresholds[1], "MEDIUM", "HIGH"))

    baseline_files = {
        "GAFF2": HERE / "GAFF2_GPU60_PREDICTIONS.json",
        "DELTA_V2_2B": HERE / "DELTA_V2_2B_GPU60_PREDICTIONS.json",
        "MACE_OFF24_ZERO_SHOT": HERE / "MACE_OFF24_ZERO_SHOT_GPU60_PREDICTIONS.json",
    }
    analysis, residual_store = {}, {}
    local_targets = {}
    for baseline, path in baseline_files.items():
        data = json.loads(path.read_text())
        predictions = {row["campaign_id"]: row for row in data["predictions"]}
        predicted_force = np.array([predictions[item]["force_kcal_mol_angstrom"] for item in identifiers])
        if baseline == "DELTA_V2_2B":
            predicted_energy = np.array([
                predictions[item]["gaff2_energy_kcal_mol"] + predictions[item]["delta_energy_kcal_mol_relative_to_source_minimum"]
                for item in identifiers
            ])
        else:
            predicted_energy = np.array([predictions[item]["energy_kcal_mol"] for item in identifiers])
        force_residual = predicted_force - qm_force
        raw_energy_residual = predicted_energy - qm_energy
        energy_residual = raw_energy_residual.copy()
        for minimum in ("MIN01", "MIN02", "MIN04"):
            mask = minima == minimum
            energy_residual[mask] -= np.mean(raw_energy_residual[mask])
        per_geometry_local = np.sqrt(np.mean(force_residual[:, LOCAL, :] ** 2, axis=(1, 2)))
        per_geometry_global = np.sqrt(np.mean(force_residual ** 2, axis=(1, 2)))
        local_targets[baseline] = per_geometry_local
        per_minimum = {minimum: residual_metrics(energy_residual, force_residual, np.where(minima == minimum)[0]) for minimum in ("MIN01", "MIN02", "MIN04")}
        per_family = {family: residual_metrics(energy_residual, force_residual, np.where(families == family)[0]) for family in sorted(set(families))}
        per_regime = {regime: residual_metrics(energy_residual, force_residual, np.where(regimes == regime)[0]) for regime in ("LOW", "MEDIUM", "HIGH")}
        descriptors = {
            "energy_rank": strain,
            "S-C": np.array([float(row["sc_distance_a"]) for row in rows]),
            "S-H": np.array([float(row["sh_distance_a"]) for row in rows]),
            "C-S-H": np.array([float(row["c_s_h_angle_deg"]) for row in rows]),
            "phi": np.array([float(row["phi_deg"]) for row in rows]),
            "psi": np.array([float(row["psi_deg"]) for row in rows]),
        }
        for name, atoms in {
            "ANGLE_9_10_11": (8, 9, 10), "ANGLE_9_10_S": (8, 9, 25),
            "ANGLE_9_10_37": (8, 9, 36), "ANGLE_11_10_S": (10, 9, 25),
            "ANGLE_11_10_37": (10, 9, 36), "ANGLE_S_10_37": (25, 9, 36),
            "ANGLE_10_S_H": (9, 25, 55),
        }.items():
            descriptors[name] = np.array([angle_degrees(point, *atoms) for point in coordinates])
        correlations = {name: spearman(per_geometry_local, values) for name, values in descriptors.items()}
        correlations["source_minimum_eta_squared"] = float(sum(
            np.sum(minima == minimum) * (per_geometry_local[minima == minimum].mean() - per_geometry_local.mean()) ** 2
            for minimum in set(minima)
        ) / np.sum((per_geometry_local - per_geometry_local.mean()) ** 2))
        force_order = np.argsort(np.sqrt(np.mean(qm_force[:, LOCAL, :] ** 2, axis=(1, 2))))
        tail = {}
        total_squared = np.sum(force_residual[:, LOCAL, :] ** 2)
        for label, count in (("lower_80_percent", 48), ("top_20_percent", 12), ("top_10_percent", 6)):
            chosen = force_order[:count] if label.startswith("lower") else force_order[-count:]
            tail[label] = residual_metrics(energy_residual, force_residual, chosen)
            tail[label]["fraction_of_total_sulfur_local_squared_error"] = float(
                np.sum(force_residual[chosen][:, LOCAL, :] ** 2) / total_squared
            )
            tail[label]["family_counts"] = dict(Counter(families[chosen]))
            tail[label]["ids"] = [identifiers[index] for index in chosen]
        analysis[baseline] = {
            "identity_sha256": sha256(path),
            "all_60": residual_metrics(energy_residual, force_residual, np.arange(60)),
            "per_minimum": per_minimum,
            "per_perturbation_family": per_family,
            "per_energy_strain_regime": per_regime,
            "sulfur_local_residual_correlations": correlations,
            "high_force_tail": tail,
            "per_atom_force_component_rms": [rms(force_residual[:, atom, :]) for atom in range(56)],
            "per_component_force_rms": {axis: rms(force_residual[:, :, index]) for index, axis in enumerate(("x", "y", "z"))},
            "high_strain_fraction_of_sulfur_local_squared_error": float(
                np.sum(force_residual[regimes == "HIGH"][:, LOCAL, :] ** 2) / total_squared
            ),
        }
        residual_store[baseline] = {
            "energy_residual_kcal_mol_source_mean_centered": energy_residual.tolist(),
            "force_residual_kcal_mol_angstrom": force_residual.tolist(),
        }

    shells = topology_shells()
    locality = {}
    for baseline, target in local_targets.items():
        locality[baseline] = {}
        for shell, atoms in shells.items():
            if shell == "sulfur_only_geometry":
                # Central-S radial geometry only: the two covalent S-neighbor distances.
                neighbors = atoms[atoms != SULFUR]
                features = np.array([[np.linalg.norm(point[SULFUR] - point[atom]) for atom in neighbors] for point in coordinates])
            else:
                features = np.array([pair_descriptor(point, atoms) for point in coordinates])
            locality[baseline][shell] = {
                "atom_count": int(len(atoms)),
                "descriptor_dimension": int(features.shape[1]),
                **diagnostic_cv(features, target, development, minima),
            }

    residual_path = HERE / "GPU60_BASELINE_RESIDUAL_ARRAYS.json"
    residual_path.write_text(json.dumps(residual_store, indent=2, sort_keys=True) + "\n")
    gaff_tail_fraction = analysis["GAFF2"]["high_force_tail"]["top_20_percent"]["fraction_of_total_sulfur_local_squared_error"]
    top_families = analysis["GAFF2"]["high_force_tail"]["top_20_percent"]["family_counts"]
    payload = {
        "schema": "tsl-rsh-gpu60-baseline-residual-study-v1",
        "split_sha256": sha256(SPLIT),
        "split_frozen_before_baseline_evaluation": True,
        "sealed_validation_used_for_locality_probe_or_model_selection": False,
        "energy_residual_offset": "independent mean offset removed within each source minimum; no force-field parameter refit",
        "local_atom_indices_zero_based": LOCAL.tolist(),
        "sulfur_atom_index_zero_based": SULFUR,
        "baselines": analysis,
        "best_existing_amber_extension": {
            "evaluated": False,
            "reason": "Historical projection metrics are preserved, but the fitted 26-coefficient vector was not persisted; reconstructing it would be a prohibited refit.",
            "historical_training_projection_sulfur_local_rms": 9.67993783143449,
        },
        "locality_diagnostic": locality,
        "high_force_tail_dominant": bool(gaff_tail_fraction > 0.5),
        "high_force_tail_md_relevant": None,
        "high_force_tail_md_relevance_basis": {
            "gaff2_top_20_family_counts": top_families,
            "interpretation": "11/12 are deliberate force-cloud perturbations and one is constrained/torsional; geometries remain covalently plausible, but no intended-MD-domain distribution is frozen, so MD relevance is UNKNOWN.",
        },
        "residual_structure_classification": {
            "A_dominated_by_one_or_few_local_coordinates": False,
            "B_smooth_function_of_local_sulfur_environment": True,
            "C_source_minimum_or_global_conformation_dependent": True,
            "D_strongly_nonlinear_high_dimensional": False,
            "E_primarily_high_strain_failures": bool(analysis["GAFF2"]["high_strain_fraction_of_sulfur_local_squared_error"] > 0.5),
            "interpretation": "MIXED: local-shell geometry explains within-distribution error magnitude, but leave-one-minimum-out transfer is negative and MIN04/high-strain errors are much larger.",
        },
        "final_classification": {
            "GPU_60_SPLIT_FROZEN": True,
            "BASELINE_60_EVALUATION_COMPLETE": False,
            "baseline_completion_reason": "GAFF2, preserved Delta V2 2B, and zero-shot MACE are complete; historical best-Amber coefficients were not preserved, so its GPU-60 evaluation is not scientifically recoverable without refitting.",
            "RESIDUAL_LOCALITY": "MIXED",
            "HIGH_FORCE_TAIL_DOMINANT": bool(gaff_tail_fraction > 0.5),
            "HIGH_FORCE_TAIL_MD_RELEVANT": "UNKNOWN",
            "MODEL_CLASS_EVIDENCE": "FLEXIBLE_ML_POTENTIAL_REQUIRED",
            "MORE_QM_REQUIRED_NOW": False,
            "model_class_basis": "Frozen local parametric and V2 corrections are ineffective on GPU-60; zero-shot MACE halves sulfur-local RMS; local magnitude is smooth within distribution but local probes fail leave-minimum-out transfer.",
        },
        "residual_arrays_path": residual_path.name,
        "residual_arrays_sha256": sha256(residual_path),
    }
    out = HERE / "GPU60_BASELINE_RESIDUAL_STUDY.json"
    out.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")
    print(json.dumps({
        "output_sha256": sha256(out),
        "sulfur_local_rms": {name: value["all_60"]["sulfur_local_force_kcal_mol_angstrom"]["rms"] for name, value in analysis.items()},
        "high_force_tail_dominant": payload["high_force_tail_dominant"],
        "high_force_tail_md_relevant": payload["high_force_tail_md_relevant"],
        "locality_rbf_r2": {name: {shell: data["five_fold"]["rbf_kernel"]["r2"] for shell, data in values.items()} for name, values in locality.items()},
    }, indent=2))


if __name__ == "__main__":
    main()
