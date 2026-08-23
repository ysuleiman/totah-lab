#!/usr/bin/env python3
"""Training-only residual coupling discovery and frozen cross-term fit request."""

from __future__ import annotations

import csv
import hashlib
import json
import math
import subprocess
from itertools import combinations
from pathlib import Path

import numpy as np

from cross_common import (
    ADDITIVE_NAMES, COORDINATES, COORDINATE_BY_ID, HERE, LOCAL_ATOMS, MANDATORY_PAIRS,
    REPRESENTABILITY, ROOT, additive_parameters, additive_prediction, circular_center,
    coordinate_jacobian, coordinate_vector, cross_energy_features, cross_force_features,
    generalized_force, pair_term_definitions, rms, split,
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def ranks(values: np.ndarray) -> np.ndarray:
    order = np.argsort(values, kind="mergesort")
    result = np.empty(len(values), dtype=float)
    start = 0
    while start < len(values):
        end = start + 1
        while end < len(values) and values[order[end]] == values[order[start]]:
            end += 1
        result[order[start:end]] = (start + end - 1) / 2.0
        start = end
    return result


def correlation(a: np.ndarray, b: np.ndarray) -> float:
    a, b = a - np.mean(a), b - np.mean(b)
    denominator = np.linalg.norm(a) * np.linalg.norm(b)
    return float(np.dot(a, b) / denominator) if denominator > 0 else 0.0


def encoded(values: np.ndarray, definition: dict[str, object]) -> np.ndarray:
    if definition["kind"] == "torsion":
        return np.column_stack((np.sin(values), np.cos(values)))
    return values[:, None]


def fit_predict(design: np.ndarray, target: np.ndarray, weights: np.ndarray, train: np.ndarray, test: np.ndarray) -> np.ndarray:
    matrix = design[train] * np.sqrt(weights[train, None])
    vector = target[train] * np.sqrt(weights[train])
    scale = np.linalg.norm(matrix, axis=0)
    if np.any(scale < 1.0e-14):
        raise ValueError("zero-norm candidate column")
    coefficients, _, rank, _ = np.linalg.lstsq(matrix / scale, vector, rcond=1.0e-11)
    if rank != design.shape[1]:
        raise ValueError("candidate cross-term design is rank deficient")
    return design[test] @ (coefficients / scale)


def cv_loss(design: np.ndarray, target: np.ndarray, weights: np.ndarray, row_structure: np.ndarray, folds: int = 5) -> tuple[float, float, list[float]]:
    losses = []
    structures = np.unique(row_structure)
    for fold in range(folds):
        held_structures = structures[np.arange(len(structures)) % folds == fold]
        test = np.isin(row_structure, held_structures)
        train = ~test
        prediction = fit_predict(design, target, weights, train, test)
        losses.append(float(np.sum(weights[test] * (prediction - target[test]) ** 2) / np.sum(weights[test])))
    return float(np.mean(losses)), float(np.std(losses, ddof=1) / math.sqrt(folds)), losses


def synthetic_projection_tests() -> dict[str, object]:
    rng = np.random.default_rng(240824)
    xyz = rng.normal(size=(56, 3))
    # Put every relevant atom in a nonsingular, non-collinear geometry.
    for index, atom in enumerate(sorted({a for item in COORDINATES for a in item["atoms_zero_based"]})):
        xyz[atom] = [0.7 * index, math.sin(index + 0.3), math.cos(0.4 * index + 0.2)]
    direction = rng.normal(size=xyz.shape)
    jacobian = coordinate_jacobian(xyz)
    step = 2.0e-6
    plus, minus = coordinate_vector(xyz + step * direction), coordinate_vector(xyz - step * direction)
    finite = np.empty(len(COORDINATES))
    for i, definition in enumerate(COORDINATES):
        delta = math.atan2(math.sin(plus[i] - minus[i]), math.cos(plus[i] - minus[i])) if definition["kind"] == "torsion" else plus[i] - minus[i]
        finite[i] = delta / (2 * step)
    analytic = jacobian @ direction.reshape(-1)
    maximum = float(np.max(np.abs(finite - analytic)))
    known_q = rng.normal(size=len(COORDINATES))
    cartesian = (jacobian.T @ known_q).reshape(56, 3)
    recovered, _, rank = generalized_force(cartesian, jacobian)
    recovery = float(np.max(np.abs(recovered - known_q)))
    passed = maximum <= 2.0e-5 and recovery <= 2.0e-8 and rank == len(COORDINATES)
    if not passed:
        raise ValueError(f"internal-coordinate projection validation failed: derivative={maximum}, recovery={recovery}, rank={rank}")
    return {"passed": True, "finite_difference_step_angstrom": step, "max_directional_derivative_difference": maximum, "max_generalized_force_recovery_difference": recovery, "jacobian_rank": rank}


def main() -> None:
    HERE.mkdir(parents=True, exist_ok=True)
    frozen = split()
    train_ids = list(frozen["TRAIN_IDS"])
    if (len(train_ids), len(frozen["VALIDATION_IDS"]), len(frozen["STRESS_TEST_IDS"])) != (39, 11, 10):
        raise ValueError("frozen split changed")
    baseline = json.loads((REPRESENTABILITY / "BASELINE_PREDICTIONS.json").read_text())["predictions"]
    additive = additive_parameters()
    projection_test = synthetic_projection_tests()

    records = []
    coordinate_rows, generalized_rows = [], []
    energy_targets, force_targets = [], []
    geometries = []
    for artifact_id in train_ids:  # The only QM-label access in discovery.
        qm_energy, qm_force, xyz, additive_energy, additive_force = additive_prediction(artifact_id, baseline, additive)
        residual_force = qm_force - additive_force
        jacobian = coordinate_jacobian(xyz)
        generalized, explained, rank = generalized_force(residual_force, jacobian)
        values = coordinate_vector(xyz)
        if rank != len(COORDINATES):
            raise ValueError(f"coordinate Jacobian rank loss for {artifact_id}: {rank}")
        coordinate_rows.append(values); generalized_rows.append(generalized)
        energy_targets.append(qm_energy - additive_energy); force_targets.append(residual_force.reshape(-1)); geometries.append(xyz)
        row = {"ARTIFACT_ID": artifact_id, "CARTESIAN_RESIDUAL_NORM": float(np.linalg.norm(residual_force)), "COORDINATE_SPAN_VARIANCE_EXPLAINED": explained, "JACOBIAN_RANK": rank}
        for definition, value, force in zip(COORDINATES, values, generalized):
            row[definition["id"]] = value; row[definition["id"] + "_GENERALIZED_RESIDUAL_FORCE"] = force
        records.append(row)
    coordinates = np.stack(coordinate_rows)
    generalized = np.stack(generalized_rows)
    centers = {definition["id"]: (circular_center(coordinates[:, i]) if definition["kind"] == "torsion" else float(np.mean(coordinates[:, i]))) for i, definition in enumerate(COORDINATES)}

    definitions = {"schema": "tsl-rsh-internal-coordinate-definitions-v1", "atom_index_convention": "zero-based in computation; one-based persisted for chemical review", "coordinate_count": len(COORDINATES), "coordinates": COORDINATES, "training_centers": centers, "projection": {"equation": "Q = pinv(J.T) DeltaF, where J_i=dq_i/dR; J.T Q is the least-squares projection of Cartesian residual force into the internal-coordinate span", "force_sign": "DeltaF=F_QM-F_additive", "numerical_validation": projection_test}, "validation_labels_used": False}
    (HERE / "INTERNAL_COORDINATE_DEFINITIONS.json").write_text(json.dumps(definitions, indent=2, sort_keys=True) + "\n")
    with (HERE / "RESIDUAL_INTERNAL_FORCE_PROJECTIONS.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(records[0].keys()), lineterminator="\n")
        writer.writeheader(); writer.writerows(records)

    pairs = list(MANDATORY_PAIRS)
    for pair in combinations([item["id"] for item in COORDINATES], 2):
        if pair not in pairs:
            pairs.append(pair)
    pair_analysis = []
    energy_targets_array = np.asarray(energy_targets)
    force_targets_array = np.concatenate(force_targets)
    energy_scale = rms(energy_targets_array - np.mean(energy_targets_array))
    force_scale = rms(force_targets_array)
    energy_weight = 0.5 / len(train_ids) / energy_scale ** 2
    force_weight = 0.5 / len(force_targets_array) / force_scale ** 2
    target = np.concatenate((energy_targets_array, force_targets_array))
    weights = np.concatenate((np.full(len(train_ids), energy_weight), np.full(len(force_targets_array), force_weight)))
    row_structure = np.concatenate((np.arange(len(train_ids)), np.repeat(np.arange(len(train_ids)), 56 * 3)))
    constant = np.concatenate((np.ones((len(train_ids), 1)), np.zeros((len(force_targets_array), 1))))
    null_cv = cv_loss(constant, target, weights, row_structure)
    pair_designs = {}
    for left, right in pairs:
        li, ri = [next(i for i, d in enumerate(COORDINATES) if d["id"] == name) for name in (left, right)]
        terms = pair_term_definitions((left, right), centers)
        design = np.vstack((np.stack([cross_energy_features(xyz, terms, centers) for xyz in geometries]), np.vstack([cross_force_features(xyz, terms, centers).reshape(-1, 1 + len(terms)) for xyz in geometries])))
        try:
            mean_loss, se_loss, fold_losses = cv_loss(design, target, weights, row_structure)
            identifiable = True
        except ValueError:
            mean_loss, se_loss, fold_losses, identifiable = float("inf"), float("inf"), [], False
        x, y = coordinates[:, ri], generalized[:, li]
        x_encoded = encoded(x, COORDINATE_BY_ID[right])
        x_design = np.column_stack((np.ones(len(x)), x_encoded))
        prediction = x_design @ np.linalg.lstsq(x_design, y, rcond=None)[0]
        variance_explained = 1.0 - float(np.sum((y - prediction) ** 2) / np.sum((y - np.mean(y)) ** 2)) if np.var(y) else 0.0
        order = np.argsort(x)
        bins = np.array_split(order, 4)
        conditional = [{"coordinate_mean": float(np.mean(x[index])), "generalized_force_mean": float(np.mean(y[index])), "count": len(index)} for index in bins]
        result = {"pair": [left, right], "mandatory": (left, right) in MANDATORY_PAIRS, "term_count": len(terms), "terms": terms, "rank_correlation": correlation(ranks(x), ranks(y)), "pearson_correlation": correlation(x, y), "residual_variance_explained_by_partner": variance_explained, "conditional_means_quartiles": conditional, "cv_loss": mean_loss, "cv_standard_error": se_loss, "cv_fold_losses": fold_losses, "cv_improvement_fraction_vs_offset_only": 1.0 - mean_loss / null_cv[0] if math.isfinite(mean_loss) else None, "identifiable": identifiable}
        pair_analysis.append(result); pair_designs[(left, right)] = (design, terms)

    # Deterministic training-only forward path, at most three coordinate pairs.
    ranked = [item for item in sorted(pair_analysis, key=lambda item: item["cv_loss"]) if item["identifiable"]]
    path = [{"pairs": [], "cv_loss": null_cv[0], "cv_standard_error": null_cv[1], "terms": []}]
    selected_pairs: list[tuple[str, str]] = []
    remaining = [tuple(item["pair"]) for item in ranked[:20]]
    for _ in range(3):
        options = []
        for pair in remaining:
            trial_pairs = selected_pairs + [pair]
            trial_terms = [term for p in trial_pairs for term in pair_designs[p][1]]
            design = np.vstack((np.stack([cross_energy_features(xyz, trial_terms, centers) for xyz in geometries]), np.vstack([cross_force_features(xyz, trial_terms, centers).reshape(-1, 1 + len(trial_terms)) for xyz in geometries])))
            try:
                loss, se, folds = cv_loss(design, target, weights, row_structure)
            except ValueError:
                continue
            options.append((loss, pair, se, folds, trial_terms, design))
        if not options:
            break
        loss, pair, se, folds, trial_terms, design = min(options, key=lambda item: item[0])
        if loss >= path[-1]["cv_loss"]:
            break
        selected_pairs.append(pair); remaining.remove(pair)
        path.append({"pairs": [list(p) for p in selected_pairs], "cv_loss": loss, "cv_standard_error": se, "cv_fold_losses": folds, "terms": trial_terms})
    best_loss = min(item["cv_loss"] for item in path)
    best = next(item for item in path if item["cv_loss"] == best_loss)
    one_se_limit = best["cv_loss"] + best["cv_standard_error"]
    chosen = next(item for item in path if item["cv_loss"] <= one_se_limit)
    if not chosen["pairs"]:
        # A cross-term study with no train-supported improvement is an explicit zero-term negative result.
        selected_terms = []
    else:
        selected_terms = chosen["terms"]

    strong = [item for item in pair_analysis if item["identifiable"] and item["cv_improvement_fraction_vs_offset_only"] is not None and item["cv_improvement_fraction_vs_offset_only"] >= 0.10 and abs(item["rank_correlation"]) >= 0.35]
    classification = "STRONG_PAIRWISE_COUPLING" if strong else ("WEAK_PAIRWISE_COUPLING" if any(item["cv_improvement_fraction_vs_offset_only"] and item["cv_improvement_fraction_vs_offset_only"] > 0 for item in pair_analysis) else "HIGHER_ORDER_OR_NONLOCAL")
    analysis = {"schema": "tsl-rsh-training-coupling-analysis-v1", "training_count": 39, "validation_labels_used": False, "stress_labels_used": False, "null_offset_only_cv_loss": null_cv[0], "candidate_pair_count": len(pair_analysis), "strong_pair_definition": "training-only diagnostic: >=10% CV loss improvement versus energy-offset-only and |Spearman rank correlation|>=0.35; not a production acceptance threshold", "strong_pair_count": len(strong), "strong_pairs": [item["pair"] for item in strong], "classification": classification, "pair_analysis": pair_analysis, "forward_selection_path": path, "one_standard_error_rule": {"best_cv_loss": best_loss, "limit": one_se_limit, "selected_pair_count": len(chosen["pairs"]), "selected_pairs": chosen["pairs"]}}
    (HERE / "TRAIN_COUPLING_ANALYSIS.json").write_text(json.dumps(analysis, indent=2, sort_keys=True) + "\n")
    candidates = {"schema": "tsl-rsh-candidate-cross-terms-v1", "generated_from_training_only": True, "mandatory_pairs_inspected": [list(p) for p in MANDATORY_PAIRS], "all_candidates": [{"pair": item["pair"], "terms": item["terms"], "cv_loss": item["cv_loss"], "selected": item["pair"] in chosen["pairs"]} for item in pair_analysis], "selected_pairs": chosen["pairs"], "selected_terms": selected_terms}
    (HERE / "CANDIDATE_CROSS_TERMS.json").write_text(json.dumps(candidates, indent=2, sort_keys=True) + "\n")

    final_design = np.vstack((np.stack([cross_energy_features(xyz, selected_terms, centers) for xyz in geometries]), np.vstack([cross_force_features(xyz, selected_terms, centers).reshape(-1, 1 + len(selected_terms)) for xyz in geometries])))
    weighted = final_design * np.sqrt(weights[:, None])
    rank = int(np.linalg.matrix_rank(weighted))
    if rank != final_design.shape[1]:
        raise ValueError("selected fit design is rank deficient")
    parameter_names = ["CROSS_ENERGY_REFERENCE_OFFSET"] + [term["name"] for term in selected_terms]
    parameter_units = ["kcal/mol"] + ["kcal/mol divided by the product of the persisted coordinate basis units" for _ in selected_terms]
    source_checksums = {"frozen_split": sha256(ROOT / "analysis/mettl7-phase2/tsl-rsh-trusted-evidence/FROZEN_TRAIN_VALIDATION_SPLIT.json"), "frozen_additive_artifact": sha256(REPRESENTABILITY / "FIT_ARTIFACT/SHA256SUMS"), "baseline_predictions": sha256(REPRESENTABILITY / "BASELINE_PREDICTIONS.json"), "coordinate_definitions": sha256(HERE / "INTERNAL_COORDINATE_DEFINITIONS.json"), "coupling_analysis": sha256(HERE / "TRAIN_COUPLING_ANALYSIS.json"), "candidate_cross_terms": sha256(HERE / "CANDIDATE_CROSS_TERMS.json")}
    request = {"modelFamily": "FROZEN_AMBER_ADDITIVE_PLUS_LOW_ORDER_CROSS_TERMS", "modelVersion": "TSL_RSH_GPU60_CROSS_COUPLING_V1", "basisDefinition": json.dumps({"frozen_parent": "6d139cfb94130a660b8916fca280caa846883af2 additive fit", "frozen_parent_artifact_sha256": json.loads((REPRESENTABILITY / "FIT_RECEIPT.json").read_text())["artifact_sha256"], "centers": centers, "selected_terms": selected_terms}, sort_keys=True), "parameterNames": parameter_names, "parameterUnits": parameter_units, "designMatrix": final_design.tolist(), "targets": target.tolist(), "rowWeights": weights.tolist(), "frozenParameters": {"frozen_additive_parameter_count": float(len(ADDITIVE_NAMES))}, "objectiveDefinition": "Same frozen 0.5 normalized energy + 0.5 normalized all-Cartesian-force objective, now applied to residuals after the immutable additive model", "objectiveWeights": {"energy_block": 0.5, "force_block": 0.5, "energy_row_weight": energy_weight, "force_row_weight": force_weight}, "trainingIds": train_ids, "validationIds": list(frozen["VALIDATION_IDS"]), "normalizationState": {"energy_scale_kcal_mol": repr(energy_scale), "force_scale_kcal_mol_angstrom": repr(force_scale), "training_only": "true"}, "optimizerConfiguration": {"algorithm": "twice-reorthogonalized modified Gram-Schmidt QR", "regularization": "NONE", "rank": str(rank)}, "seed": 0, "sourceDatasetChecksums": source_checksums, "codeCommitSha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()}
    (HERE / "TRAINING_FIT_REQUEST.json").write_text(json.dumps(request, indent=2, sort_keys=True) + "\n")
    spec = {"schema": "tsl-rsh-cross-term-model-spec-v1", "frozen_split_counts": {"train": 39, "validation": 11, "stress": 10}, "validation_used_during_discovery": False, "validation_used_during_fit": False, "frozen_additive_model": "analysis/mettl7-phase2/tsl-rsh-representability/FIT_ARTIFACT", "selection_rule": "deterministic five-fold structure-grouped forward selection over at most three pairs; choose smallest forward-path model within one standard error of the minimum training CV loss", "selected_pairs": chosen["pairs"], "selected_terms": selected_terms, "selected_cross_term_count": len(selected_terms), "energy_conservative": True, "forces": "negative Cartesian gradient of scalar cross-term energy", "stability_policy": "reject scientific sufficiency if known local harmonic curvature is negative, any selected form is unbounded in a polynomial direction, or finite/smooth periodic evaluation fails over the observed development domain", "thresholds_changed": False, "new_qm": False, "neural_model": False}
    (HERE / "CROSS_TERM_MODEL_SPEC.json").write_text(json.dumps(spec, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"coordinate_count": len(COORDINATES), "strong_pairs": len(strong), "selected_pairs": chosen["pairs"], "selected_terms": len(selected_terms), "rank": rank, "validation_used": False}, indent=2))


if __name__ == "__main__":
    main()
