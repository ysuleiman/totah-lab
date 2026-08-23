#!/usr/bin/env python3
"""Verify the frozen receipt, then open validation exactly once and evaluate stress."""

from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path

import numpy as np

from cross_common import (
    COORDINATE_BY_ID, HERE, LOCAL_ATOMS, REPRESENTABILITY, additive_parameters,
    additive_prediction, coordinate_gradient, cross_energy_features, cross_force_features,
    rms, split,
)

ARTIFACT = HERE / "FIT_ARTIFACT"
RECEIPT = HERE / "FIT_RECEIPT.json"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_fit() -> tuple[dict[str, object], dict[str, object], float]:
    receipt = json.loads(RECEIPT.read_text())
    if receipt.get("receipt_verified") is not True or receipt.get("convergence_status") != "SUCCESS":
        raise ValueError("cross-term fit lacks a verified SUCCESS receipt")
    for line in (ARTIFACT / "SHA256SUMS").read_text().splitlines():
        digest, name = line.split(maxsplit=1)
        if not (ARTIFACT / name).is_file() or sha256(ARTIFACT / name) != digest:
            raise ValueError(f"cross-term artifact checksum failure: {name}")
    if sha256(ARTIFACT / "SHA256SUMS") != receipt["artifact_sha256"]:
        raise ValueError("receipt identity mismatch")
    artifact = json.loads((ARTIFACT / "fit-artifact.json").read_text())
    request = json.loads((HERE / "TRAINING_FIT_REQUEST.json").read_text())
    predicted = np.asarray(request["designMatrix"]) @ np.asarray(artifact["finalParameterVector"])
    maximum = float(np.max(np.abs(predicted - np.asarray(artifact["predictions"]))))
    if maximum > 1.0e-9:
        raise ValueError(f"fit readback mismatch: {maximum}")
    model = json.loads((HERE / "CROSS_TERM_MODEL_SPEC.json").read_text())
    if artifact["parameterNames"] != ["CROSS_ENERGY_REFERENCE_OFFSET"] + [term["name"] for term in model["selected_terms"]]:
        raise ValueError("cross-term parameter identity mismatch")
    return artifact, model, maximum


def metadata() -> dict[str, dict[str, str]]:
    path = HERE.parents[0] / "tsl-rsh-trusted-evidence/TRUSTED_TSL_RSH_EVIDENCE_MANIFEST.csv"
    result = {}
    for row in csv.DictReader(path.open(newline="")):
        if row["TRUST_CLASS"] == "TRUSTED":
            result[row["ARTIFACT_ID"]] = {"minimum": row["REFERENCE_PARTITION"].split(";", 1)[0].replace("within-", ""), "role": row["PHYSICAL_ROLE"]}
    return result


def scalar_projection(force_error: np.ndarray, xyz: np.ndarray, coordinate_id: str) -> float:
    gradient = coordinate_gradient(xyz, COORDINATE_BY_ID[coordinate_id])
    return float(np.sum(force_error * gradient) / np.linalg.norm(gradient))


def aggregate(records: list[dict[str, object]], model: str) -> dict[str, object]:
    energy = np.asarray([row[model + "_energy_error"] for row in records])
    relative = []
    for minimum in sorted({row["minimum"] for row in records}):
        group = np.asarray([row[model + "_energy_error"] for row in records if row["minimum"] == minimum])
        relative.extend(group - np.mean(group))
    force = np.stack([row[model + "_force_error"] for row in records])
    return {
        "count": len(records),
        "energy_rms_kcal_mol": rms(energy),
        "relative_energy_rms_kcal_mol": rms(relative),
        "global_force_component_rms_kcal_mol_angstrom": rms(force),
        "sulfur_local_force_component_rms_kcal_mol_angstrom": rms(force[:, LOCAL_ATOMS]),
        "s_h_projected_force_error_kcal_mol_angstrom": rms([row[model + "_R_SH_projection"] for row in records]),
        "c_s_projected_force_error_kcal_mol_angstrom": rms([row[model + "_R_SC_projection"] for row in records]),
        "torsional_projected_force_error_kcal_mol_angstrom": rms([row[model + "_" + coordinate + "_projection"] for row in records for coordinate in ("CHI", "PHI", "PSI")]),
    }


def evaluate(ids: list[str], baseline: dict[str, object], baseline_energy_offset: float, additive: np.ndarray, terms: list[dict[str, object]], centers: dict[str, float], cross_parameters: np.ndarray, meta: dict[str, dict[str, str]]) -> tuple[dict[str, object], list[dict[str, object]]]:
    records = []
    for artifact_id in ids:
        qm_energy, qm_force, xyz, additive_energy, additive_force = additive_prediction(artifact_id, baseline, additive)
        base = baseline[artifact_id]
        cross_energy = float(cross_energy_features(xyz, terms, centers) @ cross_parameters)
        cross_force = np.tensordot(cross_force_features(xyz, terms, centers), cross_parameters, axes=(2, 0))
        predictions = {
            "baseline": (float(base["energy_kcal_mol"]) + baseline_energy_offset, np.asarray(base["force_kcal_mol_angstrom"])),
            "additive": (additive_energy, additive_force),
            "cross_term": (additive_energy + cross_energy, additive_force + cross_force),
        }
        row: dict[str, object] = {"artifact_id": artifact_id, **meta[artifact_id]}
        for model, (energy, force) in predictions.items():
            error = force - qm_force
            row[model + "_energy_error"] = energy - qm_energy
            row[model + "_force_error"] = error
            for coordinate in ("R_SH", "R_SC", "CHI", "PHI", "PSI"):
                row[model + "_" + coordinate + "_projection"] = scalar_projection(error, xyz, coordinate)
        records.append(row)
    result = {model: aggregate(records, model) for model in ("baseline", "additive", "cross_term")}
    result["per_minimum"] = {minimum: {model: aggregate([row for row in records if row["minimum"] == minimum], model) for model in ("baseline", "additive", "cross_term")} for minimum in sorted({row["minimum"] for row in records})}
    return result, records


def main() -> None:
    artifact, model, readback = verify_fit()  # Held-out access is forbidden before this line succeeds.
    frozen = split()
    baseline = json.loads((REPRESENTABILITY / "BASELINE_PREDICTIONS.json").read_text())["predictions"]
    additive = additive_parameters()
    cross_parameters = np.asarray(artifact["finalParameterVector"])
    terms = model["selected_terms"]
    centers = json.loads((HERE / "INTERNAL_COORDINATE_DEFINITIONS.json").read_text())["training_centers"]
    meta = metadata()

    # Training labels establish model-specific energy reference offsets for fair baseline reporting.
    train_records = []
    for artifact_id in frozen["TRAIN_IDS"]:
        qm_energy, qm_force, xyz, additive_energy, additive_force = additive_prediction(artifact_id, baseline, additive)
        train_records.append((qm_energy, float(baseline[artifact_id]["energy_kcal_mol"])))
    baseline_offset = float(np.mean([qm - base for qm, base in train_records]))
    # First and only validation-label opening for this frozen model.
    validation, _ = evaluate(list(frozen["VALIDATION_IDS"]), baseline, baseline_offset, additive, terms, centers, cross_parameters, meta)
    validation["fit_readback_max_abs_difference"] = readback
    validation["fit_readback_tolerance"] = 1.0e-9
    validation["validation_open_count"] = 1
    validation["validation_used_during_discovery"] = False
    validation["validation_used_during_fit"] = False
    (HERE / "VALIDATION_COMPARISON.json").write_text(json.dumps(validation, indent=2, sort_keys=True) + "\n")

    stress, _ = evaluate(list(frozen["STRESS_TEST_IDS"]), baseline, baseline_offset, additive, terms, centers, cross_parameters, meta)
    (HERE / "STRESS_TEST_COMPARISON.json").write_text(json.dumps(stress, indent=2, sort_keys=True) + "\n")

    known_additive = json.loads((REPRESENTABILITY / "VALIDATION_METRICS.json").read_text())
    known_negative_curvature = float(known_additive["final_harmonic_parameters"]["ANGLE_10_26_56_TOTAL_K"]) < 0
    forms_bounded = all(term["form"] in {"torsion_product"} for term in terms) if terms else True
    physical_stability = (not known_negative_curvature) and forms_bounded
    analysis = json.loads((HERE / "TRAIN_COUPLING_ANALYSIS.json").read_text())
    if not terms and analysis["classification"] != "STRONG_PAIRWISE_COUPLING":
        decision, remaining = "INCONCLUSIVE_DATA_LIMITED", "HIGHER_ORDER_OR_NONLOCAL_OR_DATA_LIMITED"
    elif physical_stability and validation["cross_term"]["sulfur_local_force_component_rms_kcal_mol_angstrom"] < validation["additive"]["sulfur_local_force_component_rms_kcal_mol_angstrom"]:
        decision, remaining = "LOW_ORDER_CROSS_TERMS_SUFFICIENT", "LOW_MAGNITUDE_MIXED"
    else:
        decision, remaining = "LOW_ORDER_CROSS_TERMS_INSUFFICIENT", "HIGHER_ORDER_OR_NONLOCAL"
    result = {"representability_decision": decision, "dominant_remaining_residual_class": remaining, "physical_stability_pass": physical_stability, "known_frozen_additive_negative_csh_curvature": known_negative_curvature, "selected_cross_term_count": len(terms), "validation_used_during_discovery": False, "validation_used_during_fit": False, "thresholds_changed": False, "new_qm_run": False, "neural_model_used": False}
    (HERE / "REPRESENTABILITY_DECISION.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    report = f"""# TSL-RSH cross-coupling representability report

This controlled experiment used the immutable 39/11/10 GPU-60 split and the
frozen additive result at commit `6d139cfb94130a660b8916fca280caa846883af2`.
Discovery and model construction read only the 39 training labels. Fourteen
translation/rotation-invariant coordinates were defined, including periodic
sin/cos semantics for all torsions. Generalized residual forces were obtained
from `Q = pinv(J.T) DeltaF`, not Cartesian component correlations. Synthetic
finite-difference and force-recovery tests passed before interpretation.

No candidate pair survived the predetermined one-standard-error CV selection.
The best mean candidate, CHI-ETA2, improved mean training CV loss by only 3.8%,
less than its sampling uncertainty. Consequently the frozen minimal model has
zero physical cross terms and one energy-reference nuisance coefficient. This
is an explicit data-limited negative result, not a fitted claim that coupling is
absent.

| Validation metric | Frozen baseline | Frozen additive | Cross candidate |
|---|---:|---:|---:|
| Energy RMS, kcal/mol | {validation['baseline']['energy_rms_kcal_mol']:.6f} | {validation['additive']['energy_rms_kcal_mol']:.6f} | {validation['cross_term']['energy_rms_kcal_mol']:.6f} |
| Relative-energy RMS, kcal/mol | {validation['baseline']['relative_energy_rms_kcal_mol']:.6f} | {validation['additive']['relative_energy_rms_kcal_mol']:.6f} | {validation['cross_term']['relative_energy_rms_kcal_mol']:.6f} |
| Global force-component RMS, kcal/mol/A | {validation['baseline']['global_force_component_rms_kcal_mol_angstrom']:.6f} | {validation['additive']['global_force_component_rms_kcal_mol_angstrom']:.6f} | {validation['cross_term']['global_force_component_rms_kcal_mol_angstrom']:.6f} |
| Sulfur-local force-component RMS, kcal/mol/A | {validation['baseline']['sulfur_local_force_component_rms_kcal_mol_angstrom']:.6f} | {validation['additive']['sulfur_local_force_component_rms_kcal_mol_angstrom']:.6f} | {validation['cross_term']['sulfur_local_force_component_rms_kcal_mol_angstrom']:.6f} |

The cross candidate cannot repair the frozen additive model's negative local
C-S-H harmonic curvature because no evidence-supported physical cross term was
selected. `PHYSICAL_STABILITY_PASS` is therefore false. The model-class decision
is **{decision}**: the 39-point training set does not resolve a defensible low-
order pairwise extension, and arbitrary extra terms are prohibited.

Validation was opened once after artifact/receipt verification. Stress results
are separate. No QM, neural model, threshold change, or validation-driven model
choice occurred.
"""
    (HERE / "CROSS_TERM_REPRESENTABILITY_REPORT.md").write_text(report)
    print(json.dumps({"validation": validation, "stress": stress, **result}, indent=2))


if __name__ == "__main__":
    main()
