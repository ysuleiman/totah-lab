#!/usr/bin/env python3
"""Geometry-graph and symmetric secant-Hessian atlas study without new QM."""

from __future__ import annotations

import csv
import hashlib
import heapq
import importlib.util
import json
import math
from collections import defaultdict
from pathlib import Path

import numpy as np


HERE = Path(__file__).resolve().parent
CAMPAIGN = HERE.parent
UNIT = CAMPAIGN.parent
POOL_MANIFEST = UNIT / "gpu-qm-preparation/manifests/EXISTING_TSL_RSH_GEOMETRIES.csv"
CHARACTERIZATION = CAMPAIGN / "GPU_BATCH_60_CHARACTERIZATION.csv"
RESULTS = CAMPAIGN / "completed-batch-60/gpu_qm_results"
OUT = HERE / "SECANT_HESSIAN_MANIFOLD_ATLAS_RESULT_CORRECTED_V2.json"
PREDICTIONS = HERE / "SECANT_HESSIAN_MANIFOLD_ATLAS_PREDICTIONS_CORRECTED_V2.json"
COVERAGE = HERE / "GPU783_MANIFOLD_SUPPORT_COVERAGE_CORRECTED_V2.csv"
EH_TO_KCAL = 627.5094740631
BOHR_TO_ANGSTROM = 0.529177210903
FORCE_CONVERSION = EH_TO_KCAL / BOHR_TO_ANGSTROM
LOCAL = np.array([1, 7, 8, 9, 10, 25, 36, 55])
GRAPH_NEIGHBORS = 12
TANGENT_NEIGHBORS = 24
TANGENT_DIMENSION = 6
CHART_NEIGHBORS = 8
SECANT_NEIGHBORS = 12
OFF_MANIFOLD_PENALTY = 4.0


def load_first_order_module():
    path = HERE / "run_conservative_local_qm_atlas.py"
    spec = importlib.util.spec_from_file_location("frozen_first_order_atlas", path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rms(values) -> float:
    values = np.asarray(values)
    return float(np.sqrt(np.mean(values * values)))


def rankdata(values):
    values = np.asarray(values)
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


def spearman(a, b):
    a, b = rankdata(a), rankdata(b)
    return float(np.corrcoef(a, b)[0, 1]) if np.std(a) and np.std(b) else None


def dijkstra(graph, source):
    distances = np.full(len(graph), np.inf)
    distances[source] = 0.0
    heap = [(0.0, source)]
    while heap:
        distance, node = heapq.heappop(heap)
        if distance != distances[node]:
            continue
        for neighbor, weight in graph[node]:
            candidate = distance + weight
            if candidate < distances[neighbor]:
                distances[neighbor] = candidate
                heapq.heappush(heap, (candidate, neighbor))
    return distances


def symmetric_secant_fit(displacements, gradient_secants):
    dimension = displacements.shape[1]
    pairs = [(a, b) for a in range(dimension) for b in range(a, dimension)]
    design = np.zeros((len(displacements) * dimension, len(pairs)))
    target = gradient_secants.reshape(-1)
    for observation, displacement in enumerate(displacements):
        for output in range(dimension):
            row = observation * dimension + output
            for column, (a, b) in enumerate(pairs):
                if output == a:
                    design[row, column] += displacement[b]
                if b != a and output == b:
                    design[row, column] += displacement[a]
    normal = design.T @ design
    ridge = 1.0e-8 * max(float(np.trace(normal) / len(pairs)), 1.0)
    coefficients = np.linalg.solve(normal + ridge * np.eye(len(pairs)), design.T @ target)
    hessian = np.zeros((dimension, dimension))
    for coefficient, (a, b) in zip(coefficients, pairs, strict=True):
        hessian[a, b] = hessian[b, a] = coefficient
    residual = design @ coefficients - target
    return hessian, rms(residual), float(np.linalg.cond(normal + ridge * np.eye(len(pairs))))


def metric_delta(delta, tangent):
    projection_coefficients = delta @ tangent
    projection = projection_coefficients @ tangent.T
    perpendicular = delta - projection
    distance_squared = float(projection_coefficients @ projection_coefficients + OFF_MANIFOLD_PENALTY * (perpendicular @ perpendicular))
    metric_vector = projection + OFF_MANIFOLD_PENALTY * perpendicular
    return distance_squared, metric_vector, projection_coefficients


def predict_one(query, training, anchor_graph_distances, z, jacobians, energies, covectors, tangents, hessians, bandwidth, second_order):
    training = np.asarray(training, dtype=int)
    chosen = training[np.argsort(anchor_graph_distances[query, training], kind="mergesort")[: min(CHART_NEIGHBORS, len(training))]]
    distances_squared, metric_vectors, tangent_delta, chart_energy, chart_gradient = [], [], [], [], []
    for anchor in chosen:
        delta = z[query] - z[anchor]
        distance_squared, metric_vector, projected = metric_delta(delta, tangents[anchor])
        hessian = hessians[anchor] if second_order else np.zeros_like(hessians[anchor])
        energy = energies[anchor] + covectors[anchor] @ delta + 0.5 * projected @ hessian @ projected
        gradient = covectors[anchor] + tangents[anchor] @ (hessian @ projected)
        distances_squared.append(distance_squared)
        metric_vectors.append(metric_vector)
        tangent_delta.append(projected)
        chart_energy.append(energy)
        chart_gradient.append(gradient)
    distances_squared = np.asarray(distances_squared)
    metric_vectors = np.asarray(metric_vectors)
    chart_energy = np.asarray(chart_energy)
    chart_gradient = np.asarray(chart_gradient)
    raw_weights = np.exp(-0.5 * distances_squared / (bandwidth * bandwidth))
    weights = raw_weights / np.sum(raw_weights)
    energy = float(weights @ chart_energy)
    gradient_log_weight = -metric_vectors / (bandwidth * bandwidth)
    gradient = np.einsum("i,ij->j", weights, chart_gradient)
    gradient += np.einsum("i,i,ij->j", weights, chart_energy - energy, gradient_log_weight)
    force = -(jacobians[query].T @ gradient).reshape(56, 3)
    chart_forces = np.array([-(jacobians[query].T @ gradient_i).reshape(56, 3) for gradient_i in chart_gradient])
    disagreement = float(np.sqrt(np.einsum("i,iaj,iaj->", weights, chart_forces - force, chart_forces - force) / 168))
    return energy, force, {
        "nearest_graph_support_distance": float(anchor_graph_distances[query, chosen[0]]),
        "nearest_local_metric_distance": float(math.sqrt(np.min(distances_squared))),
        "chart_force_disagreement_kcal_mol_angstrom": disagreement,
        "neighbor_anchor_indices": chosen.tolist(),
        "neighbor_weights": weights.tolist(),
    }


def metric_summary(records):
    energy = np.array([record["energy_error_kcal_mol"] for record in records])
    force = np.array([record["force_error_kcal_mol_angstrom"] for record in records])
    return {
        "count": len(records),
        "energy_rms_kcal_mol": rms(energy),
        "global_force_rms_kcal_mol_angstrom": rms(force),
        "sulfur_local_force_rms_kcal_mol_angstrom": rms(force[:, LOCAL, :]),
        "force_max_component_kcal_mol_angstrom": float(np.max(np.abs(force))),
    }


def evaluate_scheme(label, training_sets, anchor_graph_distances, z, jacobians, energies, forces, covectors, tangents, hessian_sets, bandwidth, identifiers, minima, second_order):
    records = []
    for query, training in enumerate(training_sets):
        training = np.asarray(training, dtype=int)
        training_energy_origin = float(np.mean(energies[training]))
        fold_energies = energies - training_energy_origin
        energy, force, diagnostic = predict_one(query, training,
                anchor_graph_distances, z, jacobians, fold_energies,
                covectors, tangents, hessian_sets[query], bandwidth,
                second_order)
        force_error = force - forces[query]
        records.append({
            "campaign_id": identifiers[query],
            "source_minimum": minima[query],
            "training_energy_origin_kcal_mol": training_energy_origin,
            "predicted_energy_kcal_mol": energy,
            "energy_error_kcal_mol": float(energy - fold_energies[query]),
            "predicted_force_kcal_mol_angstrom": force.tolist(),
            "force_error_kcal_mol_angstrom": force_error.tolist(),
            "sulfur_local_force_error_rms_kcal_mol_angstrom": rms(force_error[LOCAL]),
            **diagnostic,
        })
    support = np.array([record["nearest_graph_support_distance"] for record in records])
    force_error = np.array([record["sulfur_local_force_error_rms_kcal_mol_angstrom"] for record in records])
    disagreement = np.array([record["chart_force_disagreement_kcal_mol_angstrom"] for record in records])
    summary = {
        "scheme": label,
        "aggregate": metric_summary(records),
        "per_minimum": {minimum: metric_summary([record for record in records if record["source_minimum"] == minimum]) for minimum in ("MIN01", "MIN02", "MIN04")},
        "support_correlations": {
            "graph_distance_vs_sulfur_local_force_error_spearman": spearman(support, force_error),
            "chart_disagreement_vs_sulfur_local_force_error_spearman": spearman(disagreement, force_error),
        },
    }
    cutoff_support, cutoff_error = np.quantile(support, .90), np.quantile(force_error, .75)
    high = force_error >= cutoff_error
    summary["failure_separation"] = {
        "support_cutoff_p90": float(cutoff_support),
        "error_cutoff_p75": float(cutoff_error),
        "extrapolation_count": int(np.sum(high & (support > cutoff_support))),
        "interpolation_count": int(np.sum(high & (support <= cutoff_support))),
        "interpolation_ids": [identifiers[index] for index in np.where(high & (support <= cutoff_support))[0]],
    }
    return records, summary


def isotonic_threshold(distances, errors, target=7.5):
    order = np.argsort(distances, kind="mergesort")
    groups = [{"weight": 1, "value": float(errors[index]), "lo": float(distances[index]), "hi": float(distances[index])} for index in order]
    position = 0
    while position < len(groups) - 1:
        if groups[position]["value"] <= groups[position + 1]["value"]:
            position += 1
            continue
        left, right = groups[position], groups[position + 1]
        weight = left["weight"] + right["weight"]
        merged = {"weight": weight, "value": (left["weight"] * left["value"] + right["weight"] * right["value"]) / weight, "lo": left["lo"], "hi": right["hi"]}
        groups[position:position + 2] = [merged]
        position = max(0, position - 1)
    acceptable = [group for group in groups if group["value"] <= target]
    threshold = max((group["hi"] for group in acceptable), default=None)
    return threshold, groups


def fold_hessians(training, anchor_graph_distances, label_z, covectors, tangents):
    """Fit curvature using only labels present in one validation training fold."""
    training = np.asarray(training, dtype=int)
    hessians = np.zeros((60, TANGENT_DIMENSION, TANGENT_DIMENSION))
    for anchor in training:
        candidates = training[training != anchor]
        neighbors = candidates[np.argsort(anchor_graph_distances[anchor, candidates], kind="mergesort")[: min(SECANT_NEIGHBORS, len(candidates))]]
        tangent = tangents[anchor]
        displacement = (label_z[neighbors] - label_z[anchor]) @ tangent
        gradient_secants = (covectors[neighbors] - covectors[anchor]) @ tangent
        hessians[anchor] = symmetric_secant_fit(displacement, gradient_secants)[0]
    return hessians


def main():
    first = load_first_order_module()
    mol2, bonds, angles, dihedrals, nonbonded = first.graph_definition()
    descriptor = lambda x: first.internal_coordinates(x, bonds, angles, dihedrals, nonbonded)

    manifest = list(csv.DictReader(POOL_MANIFEST.open()))
    compatible_rows = [row for row in manifest if row["atom_count"] == "56" and row["composition"] == "C22H30O3S1"]
    incompatible_rows = [row for row in manifest if row not in compatible_rows]
    unique_by_sha = {}
    duplicate_rows = defaultdict(list)
    for row_index, row in enumerate(compatible_rows):
        duplicate_rows[row["geometry_sha256"]].append(row_index)
        unique_by_sha.setdefault(row["geometry_sha256"], row)
    pool_rows = list(unique_by_sha.values())
    pool_coordinates, pool_symbols = [], []
    for row in pool_rows:
        symbols, coordinates = first.xyz(Path(row["source_path"]))
        if len(symbols) != 56:
            raise RuntimeError(f'Compatible manifest row has wrong atom count: {row["source_path"]}')
        pool_symbols.append(symbols)
        pool_coordinates.append(coordinates)
    if any(symbols != pool_symbols[0] for symbols in pool_symbols):
        raise RuntimeError("Compatible pool atom ordering is not homogeneous")
    pool_raw = np.array([descriptor(coordinates) for coordinates in pool_coordinates])

    label_rows = list(csv.DictReader(CHARACTERIZATION.open()))
    identifiers = [row["campaign_id"] for row in label_rows]
    minima = np.array([row["source_minimum"] for row in label_rows])
    label_coordinates, energies, forces = [], [], []
    pool_sha_index = {row["geometry_sha256"]: index for index, row in enumerate(pool_rows)}
    anchor_pool_indices = []
    for row in label_rows:
        if row["geometry_sha256"] not in pool_sha_index:
            raise RuntimeError(f'Missing labeled geometry in compatible pool: {row["campaign_id"]}')
        anchor_pool_indices.append(pool_sha_index[row["geometry_sha256"]])
        directory = RESULTS / row["campaign_id"]
        _, coordinates = first.xyz(directory / "geometry.xyz")
        result = json.loads((directory / "result.json").read_text())
        label_coordinates.append(coordinates)
        energies.append(float(result["total_energy_hartree"]) * EH_TO_KCAL)
        forces.append(np.asarray(result["force_hartree_per_bohr"]) * FORCE_CONVERSION)
    anchor_pool_indices = np.asarray(anchor_pool_indices)
    label_coordinates, energies, forces = np.asarray(label_coordinates), np.asarray(energies), np.asarray(forces)
    # Preserve absolute immutable labels.  Each validation fold computes an
    # energy origin from training labels only in evaluate_scheme().

    block_dimensions = [len(bonds), len(angles), 2 * len(dihedrals), len(nonbonded)]
    floors = [0.02, 0.02, 0.05, 0.005]
    mean = np.mean(pool_raw, axis=0)
    scale = np.empty(pool_raw.shape[1])
    start = 0
    for dimension, floor in zip(block_dimensions, floors, strict=True):
        stop = start + dimension
        scale[start:stop] = np.maximum(np.std(pool_raw[:, start:stop], axis=0), floor) * math.sqrt(dimension)
        start = stop
    pool_z = (pool_raw - mean) / scale
    label_z = pool_z[anchor_pool_indices]

    squared = np.sum(pool_z * pool_z, axis=1)[:, None] + np.sum(pool_z * pool_z, axis=1)[None, :] - 2 * pool_z @ pool_z.T
    pairwise = np.sqrt(np.maximum(squared, 0.0))
    graph = [[] for _ in pool_rows]
    for node in range(len(pool_rows)):
        neighbors = np.argsort(pairwise[node], kind="mergesort")[1:GRAPH_NEIGHBORS + 1]
        for neighbor in neighbors:
            weight = float(pairwise[node, neighbor])
            graph[node].append((int(neighbor), weight))
            graph[neighbor].append((node, weight))
    anchor_to_pool_graph = np.array([dijkstra(graph, int(source)) for source in anchor_pool_indices])
    if not np.all(np.isfinite(anchor_to_pool_graph)):
        raise RuntimeError("Geometry graph is disconnected at k=12")
    anchor_graph_distances = anchor_to_pool_graph[:, anchor_pool_indices]

    tangents = []
    for anchor_pool in anchor_pool_indices:
        neighbors = np.argsort(pairwise[anchor_pool], kind="mergesort")[1:TANGENT_NEIGHBORS + 1]
        displacements = pool_z[neighbors] - pool_z[anchor_pool]
        _, _, vt = np.linalg.svd(displacements, full_matrices=False)
        tangents.append(vt[:TANGENT_DIMENSION].T)
    tangents = np.asarray(tangents)

    raw_jacobians = np.array([first.finite_difference_jacobian(x, descriptor, pool_raw.shape[1]) for x in label_coordinates])
    jacobians = raw_jacobians / scale[:, None]
    covectors, pullback = [], []
    for jacobian, force in zip(jacobians, forces, strict=True):
        gradient = -force.reshape(-1)
        covector = jacobian @ (np.linalg.pinv(jacobian.T @ jacobian, rcond=1e-10) @ gradient)
        covectors.append(covector)
        pullback.append(jacobian.T @ covector - gradient)
    covectors = np.asarray(covectors)

    local_anchor_distances = np.full((60, 60), np.inf)
    for query in range(60):
        for anchor in range(60):
            if query != anchor:
                local_anchor_distances[query, anchor] = math.sqrt(metric_delta(label_z[query] - label_z[anchor], tangents[anchor])[0])
    bandwidth = float(np.median(np.sort(local_anchor_distances, axis=1)[:, 5]))
    all_indices = np.arange(60)
    loo_training = [all_indices[all_indices != query] for query in all_indices]
    lomo_training = [all_indices[minima != minima[query]] for query in all_indices]
    # Curvature is never fitted globally.  Every validation prediction receives
    # a Hessian set constructed strictly from that fold's training labels.
    zero_hessians = np.zeros((60, TANGENT_DIMENSION, TANGENT_DIMENSION))
    zero_sets = [zero_hessians] * 60
    loo_hessian_sets = [fold_hessians(training, anchor_graph_distances, label_z, covectors, tangents) for training in loo_training]
    lomo_by_minimum = {
        minimum: fold_hessians(all_indices[minima != minimum], anchor_graph_distances, label_z, covectors, tangents)
        for minimum in ("MIN01", "MIN02", "MIN04")
    }
    lomo_hessian_sets = [lomo_by_minimum[minima[query]] for query in all_indices]

    first_loo_records, first_loo = evaluate_scheme("MANIFOLD_FIRST_ORDER_LOO", loo_training, anchor_graph_distances, label_z, jacobians, energies, forces, covectors, tangents, zero_sets, bandwidth, identifiers, minima, False)
    second_loo_records, second_loo = evaluate_scheme("MANIFOLD_SECOND_ORDER_LOO", loo_training, anchor_graph_distances, label_z, jacobians, energies, forces, covectors, tangents, loo_hessian_sets, bandwidth, identifiers, minima, True)
    first_lomo_records, first_lomo = evaluate_scheme("MANIFOLD_FIRST_ORDER_LOMO", lomo_training, anchor_graph_distances, label_z, jacobians, energies, forces, covectors, tangents, zero_sets, bandwidth, identifiers, minima, False)
    second_lomo_records, second_lomo = evaluate_scheme("MANIFOLD_SECOND_ORDER_LOMO", lomo_training, anchor_graph_distances, label_z, jacobians, energies, forces, covectors, tangents, lomo_hessian_sets, bandwidth, identifiers, minima, True)

    prediction_payload = {
        "schema": "tsl-rsh-secants-manifold-atlas-predictions-corrected-v2",
        "validation_geometry_protocol": "TRANSDUCTIVE_LABEL_FREE_783_GEOMETRY_GRAPH",
        "manifold_first_order_loo": first_loo_records,
        "manifold_second_order_loo": second_loo_records,
        "manifold_first_order_lomo": first_lomo_records,
        "manifold_second_order_lomo": second_lomo_records,
    }
    PREDICTIONS.write_text(json.dumps(prediction_payload, indent=2, sort_keys=True) + "\n")

    support = np.min(anchor_to_pool_graph, axis=0)
    loo_support = np.array([record["nearest_graph_support_distance"] for record in second_loo_records])
    loo_error = np.array([record["sulfur_local_force_error_rms_kcal_mol_angstrom"] for record in second_loo_records])
    support_threshold, isotonic_groups = isotonic_threshold(loo_support, loo_error, 7.5)

    # Greedy graph k-center coverage using only geometry. These are recommended
    # IDs, not generated labels. Duplicate manifest rows inherit one geometry.
    selected_pool_indices = []
    working_support = support.copy()
    if support_threshold is not None:
        while float(np.max(working_support)) > support_threshold:
            selected = int(np.argmax(working_support))
            selected_pool_indices.append(selected)
            working_support = np.minimum(working_support, dijkstra(graph, selected))
    selection_ids = [f'POOL-{index + 1:04d}:{pool_rows[index]["authoritative_id"]}' for index in selected_pool_indices]

    with COVERAGE.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["pool_index", "geometry_id", "source_path", "geometry_sha256", "nearest_anchor_graph_distance", "selected_next_qm"])
        writer.writeheader()
        for index, row in enumerate(pool_rows):
            writer.writerow({
                "pool_index": index,
                "geometry_id": f'POOL-{index + 1:04d}:{row["authoritative_id"]}',
                "source_path": row["source_path"],
                "geometry_sha256": row["geometry_sha256"],
                "nearest_anchor_graph_distance": support[index],
                "selected_next_qm": index in selected_pool_indices,
            })

    result = {
        "schema": "tsl-rsh-secants-manifold-atlas-corrected-v2",
        "validation_geometry_protocol": "TRANSDUCTIVE_LABEL_FREE_783_GEOMETRY_GRAPH",
        "restrictions": {"new_qm": False, "neural_model": False, "analytic_hessian": False},
        "pool_audit": {
            "manifest_rows": len(manifest),
            "compatible_rows": len(compatible_rows),
            "compatible_unique_geometries": len(pool_rows),
            "incompatible_rows": len(incompatible_rows),
            "incompatible_composition_atom_count": sorted({(row["composition"], row["atom_count"]) for row in incompatible_rows}),
            "duplicate_compatible_rows": len(compatible_rows) - len(pool_rows),
            "all_60_anchors_found": len(anchor_pool_indices) == 60,
            "manifest_sha256": sha256(POOL_MANIFEST),
        },
        "manifold": {
            "descriptor_dimension": pool_raw.shape[1],
            "graph_nodes": len(pool_rows),
            "knn": GRAPH_NEIGHBORS,
            "connected": True,
            "tangent_neighbors": TANGENT_NEIGHBORS,
            "tangent_dimension": TANGENT_DIMENSION,
            "off_manifold_penalty": OFF_MANIFOLD_PENALTY,
            "metric_scaling_source": "all compatible unlabeled geometries; no QM labels",
            "scale": scale.tolist(),
            "mean": mean.tolist(),
        },
        "secant_hessians": {
            "symmetric": True,
            "secant_neighbors": SECANT_NEIGHBORS,
            "analytic_hessians_computed": False,
            "validation_label_leakage": False,
            "validation_rule": "LOO curvature excludes the query gradient; leave-minimum-out curvature excludes every gradient from the held-out minimum.",
            "diagnostics": "fold-scoped; see each frozen validation prediction provenance",
        },
        "gradient_pullback_rms_kcal_mol_angstrom": rms(pullback),
        "bandwidth": bandwidth,
        "manifold_first_order": {"loo": first_loo, "lomo": first_lomo},
        "manifold_second_order": {"loo": second_loo, "lomo": second_lomo},
        "coverage": {
            "compatible_max_current_support_distance": float(np.max(support)),
            "compatible_support_quantiles": {str(q): float(np.quantile(support, q)) for q in (0, .25, .5, .75, .9, .95, 1)},
            "incompatible_rows_support_distance": "UNDEFINED_DIFFERENT_MOLECULAR_GRAPH",
            "support_distance_for_7_5_force_error": support_threshold,
            "isotonic_support_error_groups": isotonic_groups,
            "estimated_additional_qm_points": len(selected_pool_indices) if support_threshold is not None else None,
            "next_qm_geometry_ids": selection_ids,
            "coverage_csv": COVERAGE.name,
            "coverage_csv_sha256": sha256(COVERAGE),
        },
        "predictions": {"path": PREDICTIONS.name, "sha256": sha256(PREDICTIONS)},
        "comparisons": {
            "interpolation_failures_first_order_manifold": first_loo["failure_separation"]["interpolation_count"],
            "interpolation_failures_second_order_manifold": second_loo["failure_separation"]["interpolation_count"],
            "second_order_minus_first_order_interpolation_failures": second_loo["failure_separation"]["interpolation_count"] - first_loo["failure_separation"]["interpolation_count"],
        },
    }
    OUT.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps({
        "pool_audit": result["pool_audit"],
        "first_loo": first_loo,
        "second_loo": second_loo,
        "first_lomo": first_lomo,
        "second_lomo": second_lomo,
        "coverage": result["coverage"],
        "comparisons": result["comparisons"],
        "output_sha256": sha256(OUT),
    }, indent=2))


if __name__ == "__main__":
    main()
