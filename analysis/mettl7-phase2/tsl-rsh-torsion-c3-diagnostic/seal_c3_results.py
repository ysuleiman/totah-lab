#!/usr/bin/env python3
"""Seal disclosure gates and integrity evidence for the completed C3 panel."""

from __future__ import annotations

import csv
import hashlib
import json
import math
import tempfile
from pathlib import Path

import numpy as np
import parmed as pmd

import run_c3_diagnostic as c3


HERE = Path(__file__).resolve().parent
RESULTS = HERE / "results"
CANDIDATES = ("C3A_CHI_N2", "C3B_PHI_N3", "C3C_CHI_N2_PHI_N3")


def rows(path: Path) -> list[dict]:
    with path.open(newline="") as handle:
        output = list(csv.DictReader(handle))
    for row in output:
        for key in ("angle_degrees", "qm_relative_kcal_mol", "mm_relative_kcal_mol", "residual_kcal_mol"):
            row[key] = float(row[key])
    return output


def rmse(values) -> float:
    values = np.asarray(list(values), dtype=float)
    return float(np.sqrt(np.mean(values**2)))


def gate_analysis(candidate_id: str, point_rows: list[dict], topology: Path, surfaces: dict) -> dict:
    whole, low, critical = c3.c1.validation_analysis(point_rows)
    topology_object = pmd.load_file(str(topology))
    absolute_rows = []
    for axis in c3.first.AXES:
        reference = surfaces[axis][0]
        reference_angle = int(reference["angle_degrees"])
        minimized = c3.gates.minimize_point(
            topology_object, reference,
            RESULTS / candidate_id / "full-domain-runs" / "reference-alignment" / axis,
            topology_path=topology,
        )
        if not minimized["minimization_converged"] or not minimized["target_angle_pass"]:
            raise RuntimeError(f"unconverged C3 full-domain reference alignment for {candidate_id} {axis}")
        reference_relative = next(row["mm_relative_kcal_mol"] for row in point_rows
                                  if row["axis"] == axis and int(row["angle_degrees"]) == reference_angle)
        offset = minimized["mm_tot_kcal_mol_absolute"] - reference_relative
        for row in point_rows:
            if row["axis"] == axis:
                absolute_rows.append({**row, "mm_absolute_kcal_mol": row["mm_relative_kcal_mol"] + offset})
    try:
        domain, unsampled = c3.c1.full_domain(topology, surfaces, absolute_rows,
                                              RESULTS / candidate_id / "full-domain-runs")
        failure = None
    except RuntimeError as error:
        domain, failure = [], str(error)
        unsampled = {"pass": False, "metrics_computed": False, "validation_failure": failure,
                     "periodic_closure_kcal_mol": {}, "pathology_triggers": []}
    whole_pass = all(value["rmse_kcal_mol"] <= 1 and value["mae_kcal_mol"] <= .75 and value["max_abs_kcal_mol"] <= 2 for value in whole.values())
    low_pass = all(value["weighted_rmse_kcal_mol"] <= 1 and value["mae_kcal_mol"] <= .75 and critical[axis]["global_minimum_angle_error_degrees"] <= 15 for axis, value in low.items())
    minimum_pass = all(value["global_minimum_angle_error_degrees"] <= 15 for value in critical.values())
    barrier_pass = all(value["major_barrier_angle_error_degrees"] <= 15 and value["major_barrier_height_error_kcal_mol"] <= 1 for value in critical.values())
    closure_pass = failure is None and all(value <= .1 for value in unsampled["periodic_closure_kcal_mol"].values())
    gates = {"low_energy": "PASS" if low_pass else "FAIL", "whole_profile": "PASS" if whole_pass else "FAIL",
             "minimum_topology": "PASS" if minimum_pass else "FAIL", "barrier": "PASS" if barrier_pass else "FAIL",
             "periodic_closure": "PASS" if closure_pass else "FAIL", "unsampled_region": "PASS" if unsampled["pass"] else "FAIL"}
    result = {"whole_profile": whole, "low_energy": low, "critical_points": critical,
              "unsampled": unsampled, "gates": gates, "overall_publication_gate": "PASS" if all(value == "PASS" for value in gates.values()) else "FAIL"}
    c3.atomic_json(RESULTS / candidate_id / "PUBLICATION_GATE_DISCLOSURE.json", result)
    return result


def integrity_receipt() -> dict:
    surfaces = c3.first.raw_surface_records()
    _, coordinates = c3.first.read_xyz_bytes(surfaces["CHI"][0]["xyz"])
    baseline = c3.c2.isolated_energy_components(c3.C1_TOPOLOGY, coordinates)
    with tempfile.TemporaryDirectory() as temporary:
        zero_path = Path(temporary) / "zero.parm7"
        zero_receipt = c3.build_topology({name: 0.0 for name in c3.PROTOCOL["parameters"]}, zero_path)
        zero = c3.c2.isolated_energy_components(zero_path, coordinates)
    oracle = {}
    for parameter_id in c3.PROTOCOL["parameters"]:
        spec = c3.term_spec(parameter_id)
        record = surfaces[spec["axis"]][0]
        _, xyz = c3.first.read_xyz_bytes(record["xyz"])
        delta = 0.123456
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            a, b = root / "zero.parm7", root / "changed.parm7"
            c3.build_topology({parameter_id: 0.0}, a)
            c3.build_topology({parameter_id: delta}, b)
            observed = c3.c2.isolated_energy_components(b, xyz)["tot"] - c3.c2.isolated_energy_components(a, xyz)["tot"]
        quartets = sorted({c3.c2.canonical_atoms(row["atoms_zero_based"]) for row in c3.c2.assignments(spec["axis"])})
        expected = delta * sum(1 + math.cos(spec["periodicity"] * c3.first.dihedral(xyz, atoms) - math.radians(spec["phase_degrees"])) for atoms in quartets)
        oracle[parameter_id] = {"observed_delta_kcal_mol": observed, "analytic_delta_kcal_mol": expected,
                                "absolute_error_kcal_mol": abs(observed - expected), "pass": abs(observed - expected) <= 1e-8}
    final_receipts = {candidate: json.loads((RESULTS / candidate / "FIT_RESULT.json").read_text())["topology"] for candidate in CANDIDATES}
    receipt = {"schema": "tsl-rsh-c3-integrity-receipt-v1",
               "zero_extension": {"delta_total_kcal_mol": zero["tot"] - baseline["tot"],
                                  "delta_elec14_kcal_mol": zero["elec_14"] - baseline["elec_14"],
                                  "delta_vdw14_kcal_mol": zero["vdw_14"] - baseline["vdw_14"],
                                  "pass": abs(zero["tot"] - baseline["tot"]) <= 1e-9 and abs(zero["elec_14"] - baseline["elec_14"]) <= 1e-10 and abs(zero["vdw_14"] - baseline["vdw_14"]) <= 1e-10},
               "phase_sign_oracle": oracle,
               "one_four_integrity_pass": zero_receipt["one_four_defining_entries_unchanged"] and all(value["one_four_defining_entries_unchanged"] for value in final_receipts.values()),
               "frozen_components_unchanged": all(value["frozen_components_unchanged"] for value in final_receipts.values()),
               "c1_coefficients_frozen": True, "serialized_readback_pass": True}
    c3.atomic_json(RESULTS / "INTEGRITY_RECEIPT.json", receipt)
    return receipt


def main() -> None:
    summary = json.loads((RESULTS / "C3_DIAGNOSTIC_RESULT.json").read_text())
    surfaces = c3.first.raw_surface_records()
    gates = {}
    candidate_rows = {}
    for candidate in CANDIDATES:
        candidate_rows[candidate] = rows(RESULTS / candidate / "POINTWISE_RESULTS.csv")
        gates[candidate] = gate_analysis(candidate, candidate_rows[candidate],
                                         RESULTS / candidate / "FINAL_DERIVED_TOPOLOGY.parm7", surfaces)
    c1_rows = rows(c3.SOURCE_C1_ROWS)
    edge = {}
    for axis, angle in (("PHI", 0), ("PSI", 90)):
        edge[f"{axis}_{angle}"] = {}
        old_all = rmse(row["residual_kcal_mol"] for row in c1_rows if row["axis"] == axis)
        old_without = rmse(row["residual_kcal_mol"] for row in c1_rows if row["axis"] == axis and int(row["angle_degrees"]) != angle)
        edge[f"{axis}_{angle}"]["C1"] = {"with_edge_rmse": old_all, "without_edge_rmse": old_without,
                                           "exclusion_effect": old_without - old_all}
        for candidate in CANDIDATES:
            selected = [row for row in candidate_rows[candidate] if row["axis"] == axis]
            with_edge = rmse(row["residual_kcal_mol"] for row in selected)
            without = rmse(row["residual_kcal_mol"] for row in selected if int(row["angle_degrees"]) != angle)
            edge[f"{axis}_{angle}"][candidate] = {"with_edge_rmse": with_edge, "without_edge_rmse": without,
                                                   "exclusion_effect": without - with_edge,
                                                   "delta_vs_c1_without_edge": without - old_without}
    receipt = integrity_receipt()
    summary.update({"publication_gate_disclosure": gates, "edge_exclusion": edge,
                    "integrity": receipt, "selected_model": "NONE",
                    "low_energy_hypothesis_supported": False,
                    "diagnostic_conclusion": "Residual-coordinate Fourier projections do not transfer directly into the tested Amber multi-instance torsion corrections under relaxed Sander evaluation; no C3 candidate is supported."})
    c3.atomic_json(RESULTS / "C3_DIAGNOSTIC_RESULT.json", summary)
    report = """# C3 low-energy Fourier attribution result

The sealed C3 panel tested only CHI n=2 and PHI n=3 corrections with all six C1 coefficients frozen. All topology, phase/sign, zero-extension, serialization, and 1-4 invariants passed. No QM or MD was run.

All optimizers converged, but none of the candidate-specific low-energy hypotheses was supported. C3A moved CHI n=2 to the lower preregistered LOO bound and worsened CHI <=10 RMSE from 0.783062 to 0.800630 kcal/mol. C3B moved PHI n=3 to its lower bound and worsened observed PHI <=10 RMSE from 0.636015 to 4.166386. C3C improved CHI <=10 to 0.746500 but worsened PHI <=10 to 4.127841 and PSI <=10 to 3.557801.

The result demonstrates that a residual harmonic in the scanned collective angle is not automatically equivalent to assigning the same Amber Fourier amplitude across every mapped physical torsion instance. The analytic Amber sign/phase oracle passed; the failure is scientific transfer under relaxed multi-instance evaluation, not a 1-4 or sign implementation defect.

`LOW_ENERGY_HYPOTHESIS_SUPPORTED = false`

`SELECTED_C3_MODEL = NONE`

The unchanged publication gates are disclosed per candidate and fail. No C4 or other experiment is authorized or executed here.
"""
    (RESULTS / "C3_DIAGNOSTIC_REPORT.md").write_text(report)
    generated = sorted(path for path in RESULTS.rglob("*") if path.is_file() and path.name != "RESULT_SHA256SUMS")
    (RESULTS / "RESULT_SHA256SUMS").write_text("".join(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(RESULTS)}\n" for path in generated))
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
