#!/usr/bin/env python3
"""Diagnose the sealed C1 residuals without fitting a C2 model."""

from __future__ import annotations

import csv
import json
import math
from pathlib import Path

import numpy as np

import run_c1_fit as c1
import run_first_pass as first


HERE = Path(__file__).resolve().parent
POINTWISE = HERE / "05_VALIDATION/C1/C1_POINTWISE_VALIDATION.csv"
OUTPUT_CSV = HERE / "04_FIT/C2/C1_RESIDUAL_FOURIER_DIAGNOSTIC.csv"
OUTPUT_JSON = HERE / "04_FIT/C2/C2_RESIDUAL_DIAGNOSIS.json"
OUTPUT_MD = HERE / "04_FIT/C2/C1_RESIDUAL_MODEL_DIAGNOSIS.md"


def basis(angle: float, periodicity: int, phase: int) -> float:
    return 1.0 + math.cos(math.radians(periodicity * angle - phase))


def main() -> None:
    decision = json.loads((HERE / "08_PUBLICATION/C1_DECISION.json").read_text())
    if decision["c1_status"] != "FAIL" or not decision["c2_eligible"]:
        raise RuntimeError("sealed C1 result does not authorize C2 diagnosis")
    rows = list(csv.DictReader(POINTWISE.open()))
    output: list[dict] = []
    projections: dict[str, list[dict]] = {}
    for axis in first.AXES:
        group = sorted((r for r in rows if r["axis"] == axis), key=lambda r: int(r["angle_degrees"]))
        numeric = [{**r,
                    "angle_degrees": int(r["angle_degrees"]),
                    "qm_relative_kcal_mol": float(r["qm_relative_kcal_mol"]),
                    "mm_relative_kcal_mol": float(r["mm_relative_kcal_mol"]),
                    "residual_kcal_mol": float(r["residual_kcal_mol"])} for r in group]
        qmin, qbar = c1.local_extrema(numeric, "qm_relative_kcal_mol")
        minima = {x["angle_degrees"] for x in qmin}
        barriers = {x["angle_degrees"] for x in qbar}
        for row in numeric:
            classification = "LOCAL_MINIMUM" if row["angle_degrees"] in minima else ("BARRIER" if row["angle_degrees"] in barriers else "REGULAR")
            output.append({"axis": axis, "angle_degrees": row["angle_degrees"],
                           "qm_relative_energy_kcal_mol": row["qm_relative_kcal_mol"],
                           "c1_mm_relaxed_relative_energy_kcal_mol": row["mm_relative_kcal_mol"],
                           "residual_qm_minus_mm_kcal_mol": row["residual_kcal_mol"],
                           "low_energy_weight": c1.low_weight(row["qm_relative_kcal_mol"]),
                           "sampled_status": "AUTHORITATIVE_CALCULATED",
                           "critical_point_classification": classification})
        y = np.asarray([r["residual_kcal_mol"] for r in numeric])
        axis_projections = []
        for n in (1, 2, 3):
            for phase in (0, 180):
                x = np.asarray([basis(r["angle_degrees"], n, phase) for r in numeric])
                design = np.column_stack([np.ones(len(x)), x])
                coefficients, *_ = np.linalg.lstsq(design, y, rcond=None)
                prediction = design @ coefficients
                ss_total = float(np.sum((y - np.mean(y)) ** 2))
                ss_error = float(np.sum((y - prediction) ** 2))
                axis_projections.append({"periodicity": n, "phase_degrees": phase,
                                         "diagnostic_amplitude_kcal_mol": float(coefficients[1]),
                                         "diagnostic_intercept_kcal_mol": float(coefficients[0]),
                                         "residual_rms_after_projection_kcal_mol": float(np.sqrt(np.mean((y-prediction)**2))),
                                         "explained_variance_fraction": 0.0 if ss_total == 0 else 1.0 - ss_error / ss_total})
        projections[axis] = axis_projections
    OUTPUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    first.write_csv(OUTPUT_CSV, output, list(output[0]))
    diagnosis = {"schema": "tsl-rsh-c1-residual-diagnosis-v1",
                 "source_c1_commit": "47274c77719104de31ce8ab34ad00e71daa38e72",
                 "authoritative_points_only": True, "synthetic_qm_points": 0,
                 "fourier_projection_is_diagnostic_not_fit": True,
                 "projections": projections}
    first.atomic_json(OUTPUT_JSON, diagnosis)
    lines = ["# C1 residual model diagnosis", "", "Only authoritative calculated QM cells are used. These one-term projections are diagnostics, not fits.", ""]
    for axis in first.AXES:
        ranked = sorted(projections[axis], key=lambda x: x["residual_rms_after_projection_kcal_mol"])
        lines += [f"## {axis}", "", f"Best diagnostic direction: n={ranked[0]['periodicity']}, phase={ranked[0]['phase_degrees']} degrees; explained variance={ranked[0]['explained_variance_fraction']:.6f}.", ""]
        if axis == "CHI":
            lines.append("CHI already satisfies aggregate and minimum-topology gates; it remains frozen for initial C2 selection.")
        elif axis == "PHI":
            lines.append("PHI C1 contains n=3 only; candidate additions are selected from the ranked n=1/n=2 diagnostic directions.")
        else:
            lines.append("PSI C1 contains n=2/n=3; candidate addition is selected from the n=1 diagnostic direction.")
        lines.append("")
    first.atomic_text(OUTPUT_MD, "\n".join(lines) + "\n")
    print(json.dumps(diagnosis, indent=2))


if __name__ == "__main__":
    main()
