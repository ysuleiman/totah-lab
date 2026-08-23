#!/usr/bin/env python3
"""One-shot post-receipt evaluation on frozen validation and stress partitions."""

from __future__ import annotations

import csv
import hashlib
import json
import math
from collections import defaultdict
from pathlib import Path

import numpy as np

from classical_common import (
    ANGLES, BONDS, EVIDENCE, LOCAL_ATOMS, PARAMETER_NAMES, RESULTS, ROOT, TORSIONS,
    energy_features, force_features, geometry, load_qm, sha256, split,
)

HERE = Path(__file__).resolve().parent
ARTIFACT = HERE / "FIT_ARTIFACT"
RECEIPT = HERE / "FIT_RECEIPT.json"
BASELINE = HERE / "BASELINE_PREDICTIONS.json"
MANIFEST = EVIDENCE / "TRUSTED_TSL_RSH_EVIDENCE_MANIFEST.csv"


def verify_fit() -> tuple[dict[str, object], float]:
    receipt = json.loads(RECEIPT.read_text())
    if not receipt.get("receipt_verified") or receipt.get("convergence_status") != "SUCCESS":
        raise ValueError("fit receipt is not verified SUCCESS")
    expected = {}
    for line in (ARTIFACT / "SHA256SUMS").read_text().splitlines():
        digest, name = line.split(maxsplit=1)
        path = ARTIFACT / name
        if not path.is_file() or sha256(path) != digest:
            raise ValueError(f"fit artifact checksum failure: {name}")
        expected[name] = digest
    identity = sha256(ARTIFACT / "SHA256SUMS")
    if identity != receipt["artifact_sha256"]:
        raise ValueError("fit receipt identity does not match verified artifact")
    artifact = json.loads((ARTIFACT / "fit-artifact.json").read_text())
    if artifact["parameterNames"] != PARAMETER_NAMES:
        raise ValueError("fit parameter ordering mismatch")
    request = json.loads((HERE / "TRAINING_FIT_REQUEST.json").read_text())
    reconstructed = np.asarray(request["designMatrix"], dtype=float) @ np.asarray(artifact["finalParameterVector"], dtype=float)
    persisted = np.asarray(artifact["predictions"], dtype=float)
    maximum_difference = float(np.max(np.abs(reconstructed - persisted)))
    if maximum_difference > 1.0e-9:
        raise ValueError(f"read-back prediction reproduction exceeds declared 1e-9 tolerance: {maximum_difference}")
    return artifact, maximum_difference


def coordinate_value(coordinates: np.ndarray, kind: str, atoms: tuple[int, ...]) -> float:
    if kind == "bond":
        return float(np.linalg.norm(coordinates[atoms[0]] - coordinates[atoms[1]]))
    if kind == "angle":
        a, b, c = (coordinates[index] for index in atoms)
        u, v = a - b, c - b
        return float(math.acos(np.clip(np.dot(u, v) / np.linalg.norm(u) / np.linalg.norm(v), -1, 1)))
    p0, p1, p2, p3 = (coordinates[index] for index in atoms)
    b0, b1, b2 = -(p1 - p0), p2 - p1, p3 - p2
    b1 /= np.linalg.norm(b1)
    v = b0 - np.dot(b0, b1) * b1
    w = b2 - np.dot(b2, b1) * b1
    return float(math.atan2(np.dot(np.cross(b1, v), w), np.dot(v, w)))


def coordinate_gradient(coordinates: np.ndarray, kind: str, atoms: tuple[int, ...], step: float = 1e-5) -> np.ndarray:
    gradient = np.zeros_like(coordinates)
    for atom in set(atoms):
        for axis in range(3):
            plus, minus = coordinates.copy(), coordinates.copy()
            plus[atom, axis] += step; minus[atom, axis] -= step
            first = coordinate_value(plus, kind, atoms)
            second = coordinate_value(minus, kind, atoms)
            difference = first - second
            if kind == "torsion": difference = math.atan2(math.sin(difference), math.cos(difference))
            gradient[atom, axis] = difference / (2 * step)
    return gradient


def projection(force_residual: np.ndarray, gradient: np.ndarray) -> float:
    norm = float(np.linalg.norm(gradient))
    return float(np.sum(force_residual * gradient) / norm) if norm > 1e-12 else float("nan")


def rms(values: np.ndarray) -> float:
    values = np.asarray(values, dtype=float)
    return float(np.sqrt(np.mean(values * values)))


def metadata() -> dict[str, dict[str, str]]:
    result = {}
    for row in csv.DictReader(MANIFEST.open(newline="")):
        if row["TRUST_CLASS"] != "TRUSTED":
            continue
        minimum = row["REFERENCE_PARTITION"].split(";", 1)[0].replace("within-", "")
        result[row["ARTIFACT_ID"]] = {"minimum": minimum, "role": row["PHYSICAL_ROLE"], "domain": row["DOMAIN_CLASS"]}
    return result


def evaluate(ids: list[str], parameters: np.ndarray, baseline: dict[str, object], meta: dict[str, dict[str, str]]) -> tuple[dict[str, object], list[dict[str, object]]]:
    records = []
    for artifact_id in ids:
        qm_energy, qm_force, coordinates = load_qm(artifact_id)
        base = baseline[artifact_id]
        correction_energy = float(energy_features(coordinates) @ parameters)
        correction_force = np.tensordot(force_features(coordinates), parameters, axes=(2, 0))
        predicted_energy = float(base["energy_kcal_mol"]) + correction_energy
        predicted_force = np.asarray(base["force_kcal_mol_angstrom"]) + correction_force
        residual_force = predicted_force - qm_force
        baseline_force_residual = np.asarray(base["force_kcal_mol_angstrom"]) - qm_force
        projections = {}
        for first, second, name in BONDS:
            projections[name] = projection(residual_force, coordinate_gradient(coordinates, "bond", (first, second)))
        for first, center, last, name in ANGLES:
            projections[name] = projection(residual_force, coordinate_gradient(coordinates, "angle", (first, center, last)))
        for first, second, third, fourth, name in TORSIONS:
            projections[name] = projection(residual_force, coordinate_gradient(coordinates, "torsion", (first, second, third, fourth)))
        records.append({
            "artifact_id": artifact_id, **meta[artifact_id],
            "qm_energy": qm_energy, "predicted_energy": predicted_energy,
            "energy_error": predicted_energy - qm_energy,
            "force_error": residual_force, "baseline_force_error": baseline_force_residual,
            "global_force_rms": rms(residual_force),
            "sulfur_local_force_rms": rms(residual_force[LOCAL_ATOMS]),
            "baseline_global_force_rms": rms(baseline_force_residual),
            "baseline_sulfur_local_force_rms": rms(baseline_force_residual[LOCAL_ATOMS]),
            **projections,
        })

    def aggregate(subset: list[dict[str, object]]) -> dict[str, float]:
        errors = np.array([row["energy_error"] for row in subset])
        relative = []
        for minimum in sorted({str(row["minimum"]) for row in subset}):
            group = np.array([row["energy_error"] for row in subset if row["minimum"] == minimum])
            relative.extend(group - np.mean(group))
        force = np.stack([row["force_error"] for row in subset])
        result = {
            "count": len(subset), "energy_rms_kcal_mol": rms(errors),
            "relative_energy_rms_kcal_mol": rms(np.asarray(relative)),
            "global_force_component_rms_kcal_mol_angstrom": rms(force),
            "sulfur_local_force_component_rms_kcal_mol_angstrom": rms(force[:, LOCAL_ATOMS]),
            "s_h_projected_force_rms_kcal_mol_angstrom": rms(np.array([row["S_H"] for row in subset])),
            "c_s_projected_force_rms_kcal_mol_angstrom": rms(np.array([row["S_C"] for row in subset])),
            "sulfur_torsional_projected_force_rms_kcal_mol_angstrom": rms(np.array([row[name] for row in subset for name in ("CHI_SHSC", "PHI_SC", "PSI_BACKBONE")])),
        }
        base_force = np.stack([row["baseline_force_error"] for row in subset])
        result["baseline_global_force_component_rms_kcal_mol_angstrom"] = rms(base_force)
        result["baseline_sulfur_local_force_component_rms_kcal_mol_angstrom"] = rms(base_force[:, LOCAL_ATOMS])
        return result

    metrics = aggregate(records)
    metrics["per_minimum"] = {minimum: aggregate([row for row in records if row["minimum"] == minimum]) for minimum in sorted({row["minimum"] for row in records})}
    metrics["per_physical_role"] = {role: aggregate([row for row in records if row["role"] == role]) for role in sorted({row["role"] for row in records})}
    return metrics, records


def update_parameter_manifest(parameters: np.ndarray) -> dict[str, float]:
    path = HERE / "FITTABLE_PARAMETER_MANIFEST.csv"
    rows = list(csv.DictReader(path.open(newline="")))
    values = dict(zip(PARAMETER_NAMES, parameters))
    for row in rows:
        if row["PARAMETER_ID"] in values:
            row["VALUE"] = f"{values[row['PARAMETER_ID']]:.17g}"
            row["SOURCE"] = "FIT_ARTIFACT/fit-artifact.json"
            row["PROVENANCE"] = "verified receipt " + json.loads(RECEIPT.read_text())["artifact_sha256"]
    with path.open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=rows[0].keys(), lineterminator="\n")
        writer.writeheader(); writer.writerows(rows)
    # Total conventional harmonic curvatures diagnose physical validity.
    baseline = {"S_C": (174.66, 1.8629), "S_H": (277.51, 1.3503), "ANGLE_9_10_26": (62.66, math.radians(109.06004685505518)), "ANGLE_11_10_26": (62.66, math.radians(109.06004685505518)), "ANGLE_10_26_56": (66.43, math.radians(96.00004114326525))}
    final = {}
    for name, (force_constant, equilibrium) in baseline.items():
        linear, quadratic = values[name + "_LINEAR"], values[name + "_QUADRATIC"]
        total_k = force_constant + quadratic
        total_eq = (2 * force_constant * equilibrium - linear) / (2 * total_k) if abs(total_k) > 1e-12 else float("nan")
        final[name + "_TOTAL_K"] = total_k
        final[name + "_TOTAL_EQ"] = total_eq
    return final


def serializable_records(records: list[dict[str, object]], partition: str) -> list[dict[str, object]]:
    output = []
    for row in records:
        item = {key: value for key, value in row.items() if key not in {"force_error", "baseline_force_error"}}
        item["partition"] = partition
        output.append(item)
    return output


def main() -> None:
    artifact, readback_difference = verify_fit()  # Validation cannot open until this succeeds.
    parameters = np.asarray(artifact["finalParameterVector"], dtype=float)
    frozen_split = split()
    baseline = json.loads(BASELINE.read_text())["predictions"]
    meta = metadata()
    train_metrics, train_records = evaluate(frozen_split["TRAIN_IDS"], parameters, baseline, meta)
    # First and only held-out label access begins here, after predictor and receipt verification.
    validation_metrics, validation_records = evaluate(frozen_split["VALIDATION_IDS"], parameters, baseline, meta)
    stress_metrics, stress_records = evaluate(frozen_split["STRESS_TEST_IDS"], parameters, baseline, meta)
    final_harmonics = update_parameter_manifest(parameters)
    physical_stability = all(value > 0 for key, value in final_harmonics.items() if key.endswith("_TOTAL_K"))
    validation_metrics["generalization_gap"] = {
        "energy_rms": validation_metrics["energy_rms_kcal_mol"] - train_metrics["energy_rms_kcal_mol"],
        "global_force_rms": validation_metrics["global_force_component_rms_kcal_mol_angstrom"] - train_metrics["global_force_component_rms_kcal_mol_angstrom"],
        "sulfur_local_force_rms": validation_metrics["sulfur_local_force_component_rms_kcal_mol_angstrom"] - train_metrics["sulfur_local_force_component_rms_kcal_mol_angstrom"],
    }
    validation_metrics["final_harmonic_parameters"] = final_harmonics
    validation_metrics["positive_harmonic_curvature"] = physical_stability
    validation_metrics["fit_readback_prediction_tolerance"] = 1.0e-9
    validation_metrics["fit_readback_prediction_max_abs_difference"] = readback_difference
    validation_pass = validation_metrics["energy_rms_kcal_mol"] <= 2.0 and validation_metrics["sulfur_local_force_component_rms_kcal_mol_angstrom"] <= 7.5
    if validation_pass and physical_stability:
        decision, dominant = "ADDITIVE_CLASSICAL_PLAUSIBLE", "PARAMETER_ESTIMATION"
    elif not physical_stability or validation_metrics["sulfur_local_force_component_rms_kcal_mol_angstrom"] >= validation_metrics["baseline_sulfur_local_force_component_rms_kcal_mol_angstrom"]:
        decision, dominant = "ADDITIVE_CLASSICAL_INSUFFICIENT", "ADDITIVE_FUNCTIONAL_FORM"
    else:
        decision, dominant = "INCONCLUSIVE_DATA_LIMITED", "MIXED"

    (HERE / "TRAIN_METRICS.json").write_text(json.dumps(train_metrics, indent=2, sort_keys=True) + "\n")
    (HERE / "VALIDATION_METRICS.json").write_text(json.dumps(validation_metrics, indent=2, sort_keys=True) + "\n")
    (HERE / "STRESS_TEST_METRICS.json").write_text(json.dumps(stress_metrics, indent=2, sort_keys=True) + "\n")
    diagnostic_rows = serializable_records(train_records, "TRAIN") + serializable_records(validation_records, "VALIDATION") + serializable_records(stress_records, "STRESS_TEST")
    with (HERE / "RESIDUAL_DIAGNOSTICS.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=diagnostic_rows[0].keys(), lineterminator="\n")
        writer.writeheader(); writer.writerows(diagnostic_rows)
    report = f"""# Additive classical representability report

The tested model is the frozen AmberTools26/GAFF2/RESP baseline plus 28 local
additive intramolecular corrections: two harmonic bond coordinates, three
harmonic angles, and three independent n=1..3 proper Fourier torsions. Charges,
LJ, impropers, 1-4 scaling, and every unlisted parameter remained frozen. One
global energy-reference offset is a fitted nuisance parameter.

The objective was frozen before fitting and used training labels only:
0.5 times mean squared energy residual normalized by the training energy scale,
plus 0.5 times mean squared Cartesian force residual normalized by the training
force scale. No regularization or validation-driven choice was made.

The receipt-backed fit is `{json.loads(RECEIPT.read_text())['artifact_sha256']}`.
All artifact checksums and the receipt were verified before the 11 validation
labels were opened once. Reconstructed training predictions agree with the
persisted predictions to maximum absolute difference {readback_difference:.3e}
against a declared deterministic tolerance of 1e-9. Stress-test metrics remain
separate.

## Results

| Metric | Train | Validation | Stress test |
|---|---:|---:|---:|
| Energy RMS, kcal/mol | {train_metrics['energy_rms_kcal_mol']:.6f} | {validation_metrics['energy_rms_kcal_mol']:.6f} | {stress_metrics['energy_rms_kcal_mol']:.6f} |
| Relative-energy RMS, kcal/mol | {train_metrics['relative_energy_rms_kcal_mol']:.6f} | {validation_metrics['relative_energy_rms_kcal_mol']:.6f} | {stress_metrics['relative_energy_rms_kcal_mol']:.6f} |
| Global force-component RMS, kcal/mol/A | {train_metrics['global_force_component_rms_kcal_mol_angstrom']:.6f} | {validation_metrics['global_force_component_rms_kcal_mol_angstrom']:.6f} | {stress_metrics['global_force_component_rms_kcal_mol_angstrom']:.6f} |
| Sulfur-local force-component RMS, kcal/mol/A | {train_metrics['sulfur_local_force_component_rms_kcal_mol_angstrom']:.6f} | {validation_metrics['sulfur_local_force_component_rms_kcal_mol_angstrom']:.6f} | {stress_metrics['sulfur_local_force_component_rms_kcal_mol_angstrom']:.6f} |

The combined C-S-H harmonic curvature is {final_harmonics['ANGLE_10_26_56_TOTAL_K']:.6f}
kcal/mol/radian^2; positive-curvature stability is `{physical_stability}`. The
validation decision is therefore **{decision}**, with dominant residual class
**{dominant}**. This conclusion concerns only the current conformational
development domain. The historical `EXTENDED_BOUND_DOMAIN` term is retained in
provenance but is not interpreted as a thermally populated bound-state limit.

No QM, neural model, cross term, charge/LJ fit, or threshold change occurred.
"""
    (HERE / "ADDITIVE_CLASSICAL_REPRESENTABILITY_REPORT.md").write_text(report)
    summary = {"decision": decision, "dominant_residual_class": dominant, "validation_used_during_fit": False, "fit_receipt_verified": True, "physical_stability": physical_stability}
    (HERE / "REPRESENTABILITY_DECISION.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"train": train_metrics, "validation": validation_metrics, "stress": stress_metrics, **summary}, indent=2, default=float))


if __name__ == "__main__":
    main()
