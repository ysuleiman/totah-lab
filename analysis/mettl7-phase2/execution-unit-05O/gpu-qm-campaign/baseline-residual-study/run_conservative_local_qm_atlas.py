#!/usr/bin/env python3
"""Conservative first-order local QM-atlas feasibility study on GPU-60.

No parameters are fitted to forces as independent vectors.  Each reference i
defines a scalar chart

    L_i(z) = E_i + g_i . (z - z_i),

where g_i is the minimum-norm internal-coordinate covector whose Cartesian
pullback reproduces the QM energy gradient.  The atlas scalar is

    E_hat(z) = sum_i w_i(z) L_i(z),
    w_i = exp(-||z-z_i||^2/(2 h^2)) / sum_j exp(...).

Forces are always F_hat(x) = -J_z(x)^T grad_z E_hat.  The derivative includes
both chart slopes and the derivative of normalized chart weights.
"""

from __future__ import annotations

import csv
import hashlib
import json
import math
from collections import deque
from pathlib import Path

import numpy as np


HERE = Path(__file__).resolve().parent
CAMPAIGN = HERE.parent
UNIT = CAMPAIGN.parent
RESULTS = CAMPAIGN / "completed-batch-60/gpu_qm_results"
CHARACTERIZATION = CAMPAIGN / "GPU_BATCH_60_CHARACTERIZATION.csv"
OUT = HERE / "CONSERVATIVE_LOCAL_QM_ATLAS_RESULT_CORRECTED_V2.json"
PREDICTIONS = HERE / "CONSERVATIVE_LOCAL_QM_ATLAS_PREDICTIONS_CORRECTED_V2.json"
EH_TO_KCAL = 627.5094740631
BOHR_TO_ANGSTROM = 0.529177210903
FORCE_CONVERSION = EH_TO_KCAL / BOHR_TO_ANGSTROM
LOCAL = np.array([1, 7, 8, 9, 10, 25, 36, 55])
FD_STEP_ANGSTROM = 1.0e-5
NEIGHBOR_COUNT = 8
SUPPORT_CORRELATION_THRESHOLD = 0.30


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def xyz(path: Path) -> tuple[list[str], np.ndarray]:
    lines = path.read_text().splitlines()[2:]
    return (
        [line.split()[0] for line in lines if line.strip()],
        np.array([[float(value) for value in line.split()[1:4]] for line in lines if line.strip()]),
    )


def graph_definition():
    mol2 = UNIT / "TSL_RSH_NATIVE_AMBERTOOLS26_RESP3MIN_HF631GD_CANDIDATE_V1.mol2"
    lines = mol2.read_text().splitlines()
    begin = lines.index("@<TRIPOS>BOND") + 1
    bonds = []
    adjacency = [set() for _ in range(56)]
    for line in lines[begin:]:
        if line.startswith("@<TRIPOS>"):
            break
        if line.strip():
            fields = line.split()
            a, b = int(fields[1]) - 1, int(fields[2]) - 1
            bonds.append(tuple(sorted((a, b))))
            adjacency[a].add(b)
            adjacency[b].add(a)
    bonds = sorted(set(bonds))
    angles = sorted((a, center, c) for center in range(56) for a in adjacency[center] for c in adjacency[center] if a < c)
    dihedrals = set()
    for b, c in bonds:
        for a in adjacency[b] - {c}:
            for d in adjacency[c] - {b}:
                path = (a, b, c, d)
                dihedrals.add(min(path, path[::-1]))
    dihedrals = sorted(dihedrals)
    graph_distance = np.full((56, 56), 999, dtype=int)
    for root in range(56):
        graph_distance[root, root] = 0
        queue = deque([root])
        while queue:
            atom = queue.popleft()
            for neighbor in adjacency[atom]:
                if graph_distance[root, neighbor] == 999:
                    graph_distance[root, neighbor] = graph_distance[root, atom] + 1
                    queue.append(neighbor)
    nonbonded = [(a, b) for a in range(56) for b in range(a + 1, 56) if graph_distance[a, b] >= 3]
    return mol2, bonds, angles, dihedrals, nonbonded


def internal_coordinates(x, bonds, angles, dihedrals, nonbonded) -> np.ndarray:
    bond_values = [np.linalg.norm(x[a] - x[b]) for a, b in bonds]
    angle_values = []
    for a, b, c in angles:
        left, right = x[a] - x[b], x[c] - x[b]
        angle_values.append(np.dot(left, right) / (np.linalg.norm(left) * np.linalg.norm(right)))
    torsion_values = []
    for a, b, c, d in dihedrals:
        b0, b1, b2 = x[b] - x[a], x[c] - x[b], x[d] - x[c]
        n1, n2 = np.cross(b0, b1), np.cross(b1, b2)
        n1 /= np.linalg.norm(n1)
        n2 /= np.linalg.norm(n2)
        axis = b1 / np.linalg.norm(b1)
        cosine = np.clip(np.dot(n1, n2), -1.0, 1.0)
        sine = np.dot(np.cross(n1, n2), axis)
        torsion_values.extend((sine, cosine))
    # Graph separations >=3 encode through-space molecular shape without
    # duplicating covalent bonds or bond angles. Inverse distance is smooth and
    # reduces domination by remote atom pairs.
    distance_values = [1.0 / np.linalg.norm(x[a] - x[b]) for a, b in nonbonded]
    return np.asarray([*bond_values, *angle_values, *torsion_values, *distance_values])


def finite_difference_jacobian(x, descriptor, dimension) -> np.ndarray:
    jacobian = np.empty((dimension, 168))
    for atom in range(56):
        for axis in range(3):
            plus, minus = x.copy(), x.copy()
            plus[atom, axis] += FD_STEP_ANGSTROM
            minus[atom, axis] -= FD_STEP_ANGSTROM
            jacobian[:, atom * 3 + axis] = (descriptor(plus) - descriptor(minus)) / (2 * FD_STEP_ANGSTROM)
    return jacobian


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


def rms(values):
    values = np.asarray(values)
    return float(np.sqrt(np.mean(values * values)))


def metric_summary(energy_error, force_error):
    return {
        "count": int(len(energy_error)),
        "energy_rms_kcal_mol": rms(energy_error),
        "energy_mae_kcal_mol": float(np.mean(np.abs(energy_error))),
        "global_force_rms_kcal_mol_angstrom": rms(force_error),
        "sulfur_local_force_rms_kcal_mol_angstrom": rms(force_error[:, LOCAL, :]),
        "force_max_component_kcal_mol_angstrom": float(np.max(np.abs(force_error))),
    }


def predict_one(query, training, z, jacobians, energies, covectors, bandwidth):
    delta = z[query] - z[training]
    distances = np.linalg.norm(delta, axis=1)
    chosen_order = np.argsort(distances, kind="mergesort")[: min(NEIGHBOR_COUNT, len(training))]
    chosen = training[chosen_order]
    delta = z[query] - z[chosen]
    distances = np.linalg.norm(delta, axis=1)
    raw_weights = np.exp(-0.5 * (distances / bandwidth) ** 2)
    weights = raw_weights / np.sum(raw_weights)
    local_energies = energies[chosen] + np.einsum("ij,ij->i", covectors[chosen], delta)
    predicted_energy = float(weights @ local_energies)
    gradient_log_weight = -delta / (bandwidth * bandwidth)
    gradient_z = np.einsum("i,ij->j", weights, covectors[chosen])
    gradient_z += np.einsum("i,i,ij->j", weights, local_energies - predicted_energy, gradient_log_weight)
    predicted_force = -(jacobians[query].T @ gradient_z).reshape(56, 3)
    chart_forces = np.array([-(jacobians[query].T @ covectors[index]).reshape(56, 3) for index in chosen])
    force_disagreement = float(np.sqrt(np.einsum("i,iaj,iaj->", weights, chart_forces - predicted_force, chart_forces - predicted_force) / (56 * 3)))
    energy_disagreement = float(np.sqrt(weights @ (local_energies - predicted_energy) ** 2))
    return {
        "predicted_energy_kcal_mol": predicted_energy,
        "predicted_force_kcal_mol_angstrom": predicted_force,
        "nearest_support_distance": float(distances[0]),
        "neighbor_boundary_distance": float(distances[-1]),
        "local_chart_energy_disagreement_kcal_mol": energy_disagreement,
        "local_chart_force_disagreement_kcal_mol_angstrom": force_disagreement,
        "neighbor_indices": chosen.tolist(),
        "neighbor_weights": weights.tolist(),
    }


def evaluate_scheme(name, training_sets, z, jacobians, energies, forces, covectors, bandwidth, identifiers, minima):
    records = []
    for query, training in enumerate(training_sets):
        training = np.asarray(training, dtype=int)
        # The energy origin is a fitted, label-dependent quantity.  It must be
        # derived from this fold's training labels only.  In particular, a
        # global minimum/mean over all 60 labels leaks the held-out energy into
        # every local chart even though a constant energy shift is physically
        # irrelevant.
        training_energy_origin = float(np.mean(energies[training]))
        fold_energies = energies - training_energy_origin
        prediction = predict_one(query, training, z, jacobians,
                                 fold_energies, covectors, bandwidth)
        predicted_force = prediction.pop("predicted_force_kcal_mol_angstrom")
        force_error = predicted_force - forces[query]
        records.append({
            "campaign_id": identifiers[query],
            "source_minimum": minima[query],
            "training_energy_origin_kcal_mol": training_energy_origin,
            "true_energy_kcal_mol_relative_training_origin": float(fold_energies[query]),
            **prediction,
            "energy_error_kcal_mol": float(prediction["predicted_energy_kcal_mol"] - fold_energies[query]),
            "predicted_force_kcal_mol_angstrom": predicted_force.tolist(),
            "force_error_kcal_mol_angstrom": force_error.tolist(),
            "global_force_error_rms_kcal_mol_angstrom": rms(force_error),
            "sulfur_local_force_error_rms_kcal_mol_angstrom": rms(force_error[LOCAL]),
        })
    energy_error = np.array([record["energy_error_kcal_mol"] for record in records])
    force_error = np.array([record["force_error_kcal_mol_angstrom"] for record in records])
    support = np.array([record["nearest_support_distance"] for record in records])
    disagreement = np.array([record["local_chart_force_disagreement_kcal_mol_angstrom"] for record in records])
    local_error = np.array([record["sulfur_local_force_error_rms_kcal_mol_angstrom"] for record in records])
    return records, {
        "scheme": name,
        "aggregate": metric_summary(energy_error, force_error),
        "support_correlations": {
            "nearest_distance_vs_absolute_energy_error_spearman": spearman(support, np.abs(energy_error)),
            "nearest_distance_vs_sulfur_local_force_error_spearman": spearman(support, local_error),
            "chart_force_disagreement_vs_sulfur_local_force_error_spearman": spearman(disagreement, local_error),
        },
        "support_distance_quantiles": {str(q): float(np.quantile(support, q)) for q in (0, .25, .5, .75, .9, 1)},
        "per_minimum": {
            minimum: metric_summary(energy_error[minima == minimum], force_error[minima == minimum])
            for minimum in ("MIN01", "MIN02", "MIN04")
        },
    }


def main():
    mol2, bonds, angles, dihedrals, nonbonded = graph_definition()
    rows = list(csv.DictReader(CHARACTERIZATION.open()))
    identifiers = [row["campaign_id"] for row in rows]
    minima = np.array([row["source_minimum"] for row in rows])
    coordinates, energies, forces, atom_orders = [], [], [], []
    for identifier in identifiers:
        directory = RESULTS / identifier
        symbols, x = xyz(directory / "geometry.xyz")
        result = json.loads((directory / "result.json").read_text())
        if result["geometry_sha256"] != sha256(directory / "geometry.xyz"):
            raise RuntimeError(f"Geometry checksum mismatch: {identifier}")
        coordinates.append(x)
        atom_orders.append(symbols)
        energies.append(float(result["total_energy_hartree"]) * EH_TO_KCAL)
        forces.append(np.asarray(result["force_hartree_per_bohr"]) * FORCE_CONVERSION)
    if any(order != atom_orders[0] for order in atom_orders):
        raise RuntimeError("Atom ordering is not homogeneous")
    coordinates, energies, forces = np.asarray(coordinates), np.asarray(energies), np.asarray(forces)
    # Keep immutable absolute labels here.  Validation folds establish their
    # own training-only energy origin inside evaluate_scheme().

    descriptor = lambda x: internal_coordinates(x, bonds, angles, dihedrals, nonbonded)
    raw = np.array([descriptor(x) for x in coordinates])
    block_dimensions = [len(bonds), len(angles), 2 * len(dihedrals), len(nonbonded)]
    block_names = ["covalent_bonds_angstrom", "bond_angle_cosines", "periodic_dihedral_sin_cos", "inverse_nonbonded_distances_inverse_angstrom"]
    floors = [0.02, 0.02, 0.05, 0.005]
    scale = np.empty(raw.shape[1])
    start = 0
    block_slices = {}
    for name, dimension, floor in zip(block_names, block_dimensions, floors, strict=True):
        stop = start + dimension
        scale[start:stop] = np.maximum(np.std(raw[:, start:stop], axis=0), floor) * math.sqrt(dimension)
        block_slices[name] = [start, stop]
        start = stop
    mean = np.mean(raw, axis=0)
    z = (raw - mean) / scale
    raw_jacobians = np.array([finite_difference_jacobian(x, descriptor, raw.shape[1]) for x in coordinates])
    jacobians = raw_jacobians / scale[:, None]

    # Minimum-norm internal covectors. Cartesian translation/rotation null modes
    # are removed by the Moore-Penrose inverse; no Hessian is constructed.
    covectors = []
    pullback_residuals = []
    for jacobian, force in zip(jacobians, forces, strict=True):
        gradient_x = -force.reshape(-1)
        gram = jacobian.T @ jacobian
        covector = jacobian @ (np.linalg.pinv(gram, rcond=1e-10) @ gradient_x)
        covectors.append(covector)
        pullback_residuals.append(jacobian.T @ covector - gradient_x)
    covectors = np.asarray(covectors)
    pullback_residuals = np.asarray(pullback_residuals)

    pair_i, pair_j = np.triu_indices(60, 1)
    pair_distance = np.linalg.norm(z[pair_i] - z[pair_j], axis=1)
    pair_energy = np.abs(energies[pair_i] - energies[pair_j])
    pair_force = np.sqrt(np.mean((forces[pair_i] - forces[pair_j]) ** 2, axis=(1, 2)))
    nearest_six = np.sort(np.linalg.norm(z[:, None, :] - z[None, :, :], axis=2) + np.eye(60) * 1e9, axis=1)[:, 5]
    bandwidth = float(np.median(nearest_six))
    bins = np.quantile(pair_distance, np.linspace(0, 1, 6))
    pair_bins = []
    for index in range(5):
        mask = (pair_distance >= bins[index]) & (pair_distance <= bins[index + 1] if index == 4 else pair_distance < bins[index + 1])
        pair_bins.append({
            "distance_range": [float(bins[index]), float(bins[index + 1])],
            "pair_count": int(np.sum(mask)),
            "median_energy_difference_kcal_mol": float(np.median(pair_energy[mask])),
            "median_force_difference_kcal_mol_angstrom": float(np.median(pair_force[mask])),
        })

    all_indices = np.arange(60)
    loo_training = [all_indices[all_indices != query] for query in all_indices]
    lomo_training = [all_indices[minima != minima[query]] for query in all_indices]
    loo_records, loo = evaluate_scheme("LEAVE_ONE_OUT", loo_training, z, jacobians, energies, forces, covectors, bandwidth, identifiers, minima)
    lomo_records, lomo = evaluate_scheme("LEAVE_ONE_MINIMUM_OUT", lomo_training, z, jacobians, energies, forces, covectors, bandwidth, identifiers, minima)

    loo_support = np.array([record["nearest_support_distance"] for record in loo_records])
    loo_local_error = np.array([record["sulfur_local_force_error_rms_kcal_mol_angstrom"] for record in loo_records])
    support_cutoff = float(np.quantile(loo_support, .90))
    error_cutoff = float(np.quantile(loo_local_error, .75))
    high_error = loo_local_error >= error_cutoff
    extrapolation_failures = high_error & (loo_support > support_cutoff)
    interpolation_failures = high_error & (loo_support <= support_cutoff)
    failure_separation = {
        "support_cutoff_loo_p90": support_cutoff,
        "high_error_cutoff_loo_p75": error_cutoff,
        "high_error_count": int(np.sum(high_error)),
        "extrapolation_failure_count": int(np.sum(extrapolation_failures)),
        "interpolation_failure_count": int(np.sum(interpolation_failures)),
        "extrapolation_failure_ids": [identifiers[i] for i in np.where(extrapolation_failures)[0]],
        "interpolation_failure_ids": [identifiers[i] for i in np.where(interpolation_failures)[0]],
    }

    prediction_payload = {
        "schema": "tsl-rsh-conservative-local-qm-atlas-predictions-corrected-v2",
        "validation_geometry_protocol": "TRANSDUCTIVE_LABEL_FREE_GEOMETRY_METRIC",
        "loo": loo_records,
        "leave_one_minimum_out": lomo_records,
    }
    PREDICTIONS.write_text(json.dumps(prediction_payload, indent=2, sort_keys=True) + "\n")
    pair_energy_correlation = spearman(pair_distance, pair_energy)
    pair_force_correlation = spearman(pair_distance, pair_force)
    support_force_correlation = loo["support_correlations"]["nearest_distance_vs_sulfur_local_force_error_spearman"]
    result = {
        "schema": "tsl-rsh-conservative-local-qm-atlas-feasibility-corrected-v2",
        "validation_geometry_protocol": "TRANSDUCTIVE_LABEL_FREE_GEOMETRY_METRIC",
        "restrictions": {"new_qm": False, "neural_network": False, "force_field_fit": False, "hessian": False},
        "dataset_count": 60,
        "geometry_characterization_sha256": sha256(CHARACTERIZATION),
        "topology_mol2_sha256": sha256(mol2),
        "atom_order": atom_orders[0],
        "equations": {
            "local_chart": "L_i(z)=E_i+g_i dot (z-z_i)",
            "weights": "w_i(z)=exp(-||z-z_i||^2/(2h^2))/sum_j exp(-||z-z_j||^2/(2h^2))",
            "atlas_energy": "E_hat(z)=sum_i w_i(z)L_i(z)",
            "atlas_gradient": "grad E_hat=sum_i w_i g_i + sum_i w_i(L_i-E_hat)grad(log w_i)",
            "force": "F_hat(x)=-J_z(x)^T grad_z E_hat",
            "covector": "g_i=J_i (J_i^T J_i)^+ grad_x E_i",
        },
        "coordinate_definition": {
            "covalent_bonds": {"count": len(bonds), "indices_zero_based": bonds, "value": "distance in angstrom"},
            "bond_angles": {"count": len(angles), "indices_zero_based": angles, "value": "cos(theta)"},
            "proper_dihedrals": {"count": len(dihedrals), "indices_zero_based": dihedrals, "value": "ordered (sin(phi),cos(phi))"},
            "intramolecular_distances": {"count": len(nonbonded), "indices_zero_based": nonbonded, "selection": "all graph separations >=3", "value": "1/r in inverse angstrom"},
            "block_slices": block_slices,
            "metric": "empirical standard deviation with fixed physical floor, then divide each block by sqrt(block dimension); Euclidean distance in concatenated z",
            "scale_floor_by_block": dict(zip(block_names, floors, strict=True)),
            "mean": mean.tolist(),
            "scale": scale.tolist(),
            "finite_difference_step_angstrom": FD_STEP_ANGSTROM,
        },
        "atlas_rule": {
            "neighbor_count": NEIGHBOR_COUNT,
            "bandwidth_rule": "median sixth-nearest-neighbor distance across the geometry-only GPU-60 support",
            "bandwidth": bandwidth,
            "force_or_energy_hyperparameter_optimization": False,
        },
        "gradient_pullback_check": {
            "rms_kcal_mol_angstrom": rms(pullback_residuals),
            "max_kcal_mol_angstrom": float(np.max(np.abs(pullback_residuals))),
        },
        "pairwise_smoothness": {
            "pair_count": len(pair_distance),
            "geometry_distance_vs_energy_difference_spearman": pair_energy_correlation,
            "geometry_distance_vs_force_difference_spearman": pair_force_correlation,
            "distance_quintiles": pair_bins,
        },
        "leave_one_out": loo,
        "leave_one_minimum_out": lomo,
        "failure_separation": failure_separation,
        "zero_force_comparator": {
            "global_force_rms_kcal_mol_angstrom": rms(forces),
            "sulfur_local_force_rms_kcal_mol_angstrom": rms(forces[:, LOCAL, :]),
        },
        "predictions_path": PREDICTIONS.name,
        "predictions_sha256": sha256(PREDICTIONS),
        "decision_primitives": {
            "pair_smoothness_exists": bool(pair_energy_correlation > .4 and pair_force_correlation > .4),
            "conservative_loo_beats_zero_force_global": bool(loo["aggregate"]["global_force_rms_kcal_mol_angstrom"] < rms(forces)),
            "support_force_error_correlation_exceeds_preregistered_threshold": bool(support_force_correlation >= SUPPORT_CORRELATION_THRESHOLD),
            "support_correlation_threshold": SUPPORT_CORRELATION_THRESHOLD,
        },
    }
    OUT.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps({
        "descriptor_dimension": raw.shape[1],
        "bandwidth": bandwidth,
        "pullback_rms": result["gradient_pullback_check"]["rms_kcal_mol_angstrom"],
        "pairwise": result["pairwise_smoothness"],
        "loo": loo,
        "lomo": lomo,
        "failure_separation": failure_separation,
        "decision_primitives": result["decision_primitives"],
        "output_sha256": sha256(OUT),
    }, indent=2))


if __name__ == "__main__":
    main()
