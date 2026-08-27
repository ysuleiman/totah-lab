#!/usr/bin/env python3
"""Execute the sealed C3 low-energy torsion attribution panel."""

from __future__ import annotations

import csv
import hashlib
import json
import shutil
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import parmed as pmd
from scipy.optimize import minimize


HERE = Path(__file__).resolve().parent
FROZEN = HERE.parent / "tsl-rsh-torsion-fit"
sys.path.insert(0, str(FROZEN))
import close_publication_gates as gates  # noqa: E402
import run_c1_fit as c1  # noqa: E402
import run_c2_fit as c2  # noqa: E402
import run_first_pass as first  # noqa: E402


PROTOCOL = json.loads((HERE / "C3_PROTOCOL.json").read_text())
PHASES = json.loads((HERE / "PHASE_DERIVATION.json").read_text())
RESULTS = HERE / "results"
C1_TOPOLOGY = FROZEN / "04_FIT/C1/C1_FINAL_DERIVED_TOPOLOGY.parm7"
C1_PARAMETERS = FROZEN / "04_FIT/C1/C1_FINAL_PARAMETERS.json"
SOURCE_C1_ROWS = FROZEN / "05_VALIDATION/C1/C1_POINTWISE_VALIDATION.csv"
TOL = float(PROTOCOL["diagnostic_support_rule"]["numerical_materiality_kcal_mol"])


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def atomic_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")
    temporary.replace(path)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def c1_parameters() -> dict[str, float]:
    return {key: float(value) for key, value in json.loads(C1_PARAMETERS.read_text())["fitted"].items()}


def term_spec(parameter_id: str) -> dict:
    value = PROTOCOL["parameters"][parameter_id]
    return {"parameter_id": parameter_id, "axis": parameter_id.split("_")[0],
            "periodicity": int(value["periodicity"]), "phase_degrees": float(value["phase_degrees"])}


def candidate_spec(candidate_id: str) -> dict:
    return next(value for value in PROTOCOL["candidates"] if value["candidate_id"] == candidate_id)


def build_topology(amplitudes: dict[str, float], output: Path) -> dict:
    parameters = c1_parameters()
    parameters.update(amplitudes)
    specs = [term_spec(parameter_id) for parameter_id in amplitudes]
    receipt = c2.build_candidate(parameters, specs, output)
    if not receipt["one_four_defining_entries_unchanged"]:
        raise RuntimeError("C3 topology failed 1-4 integrity")
    return receipt


def load_c1_rows() -> dict[tuple[str, int], dict]:
    with SOURCE_C1_ROWS.open(newline="") as handle:
        return {(row["axis"], int(row["angle_degrees"])): row for row in csv.DictReader(handle)}


def band_metrics(rows: list[dict]) -> list[dict]:
    bands = (("QM_LE_1", lambda q: q <= 1), ("QM_LE_5", lambda q: q <= 5),
             ("QM_LE_10", lambda q: q <= 10), ("QM_GT_10", lambda q: q > 10),
             ("WHOLE", lambda q: True))
    output = []
    for axis in first.AXES:
        axis_rows = [row for row in rows if row["axis"] == axis]
        for band, predicate in bands:
            selected = [row for row in axis_rows if predicate(row["qm_relative_kcal_mol"])]
            residual = np.asarray([row["residual_kcal_mol"] for row in selected])
            output.append({"axis": axis, "band": band, "n": len(selected),
                           "rmse_kcal_mol": float(np.sqrt(np.mean(residual**2))) if len(residual) else None,
                           "mae_kcal_mol": float(np.mean(abs(residual))) if len(residual) else None,
                           "max_abs_kcal_mol": float(np.max(abs(residual))) if len(residual) else None,
                           "signed_mean_kcal_mol": float(np.mean(residual)) if len(residual) else None})
    return output


class Objective:
    def __init__(self, candidate_id: str, surfaces: dict):
        self.candidate_id = candidate_id
        self.spec = candidate_spec(candidate_id)
        self.parameter_ids = list(self.spec["adjustable_parameters"])
        self.surfaces = surfaces
        self.root = RESULTS / candidate_id / "evaluations"
        self.root.mkdir(parents=True, exist_ok=True)
        self.cache, self.trajectory, self.count = {}, [], 0
        self._load()

    def _load(self) -> None:
        for path in sorted(self.root.glob("EVAL_*/EVALUATION.json")):
            record = json.loads(path.read_text())
            if record.get("candidate_id") != self.candidate_id or len(record.get("points", [])) != 56:
                raise RuntimeError(f"invalid C3 resume artifact: {path}")
            key = tuple(round(float(record["amplitudes"][name]), 12) for name in self.parameter_ids)
            self.cache[key] = record
            self.trajectory.append({"evaluation_id": record["evaluation_id"], **record["amplitudes"],
                                    "objective": record["objective"]})
            self.count = max(self.count, int(record["evaluation_id"].split("_")[-1]))

    def evaluate(self, values, purpose="optimization") -> dict:
        key = tuple(round(float(value), 12) for value in values)
        if key in self.cache:
            return self.cache[key]
        amplitudes = {name: float(value) for name, value in zip(self.parameter_ids, values)}
        self.count += 1
        evaluation_id = f"EVAL_{self.count:05d}"
        with tempfile.TemporaryDirectory(prefix="tsl-c3-") as temporary:
            root = Path(temporary)
            topology_path = root / "candidate.parm7"
            topology_receipt = build_topology(amplitudes, topology_path)
            topology = pmd.load_file(str(topology_path))
            points = []
            for axis in first.AXES:
                results = [gates.minimize_point(topology, record, root / axis / f"{int(record['angle_degrees']):+04d}",
                                                topology_path=topology_path) for record in self.surfaces[axis]]
                if not all(result["minimization_converged"] and result["target_angle_pass"] for result in results):
                    raise RuntimeError(f"C3 minimization failed closed: {self.candidate_id} {axis} {evaluation_id}")
                points.extend(c1.relative_rows(axis, results))
        domains = []
        for domain in self.spec["primary_domains"]:
            axis = domain.split("_")[0]
            selected = [row for row in points if row["axis"] == axis and row["qm_relative_kcal_mol"] <= 10]
            domains.append(float(np.mean([row["residual_kcal_mol"]**2 for row in selected])))
        result = {"schema": "tsl-rsh-c3-evaluation-v1", "candidate_id": self.candidate_id,
                  "evaluation_id": evaluation_id, "purpose": purpose, "amplitudes": amplitudes,
                  "objective": float(np.mean(domains)), "domain_mse": domains, "points": points,
                  "topology": topology_receipt, "created_utc": now()}
        evidence = self.root / evaluation_id
        atomic_json(evidence / "EVALUATION.json", result)
        self.trajectory.append({"evaluation_id": evaluation_id, **amplitudes, "objective": result["objective"]})
        self.cache[key] = result
        return result


def fit_candidate(candidate_id: str, surfaces: dict) -> dict:
    spec = candidate_spec(candidate_id)
    ids = spec["adjustable_parameters"]
    x0 = np.asarray([PROTOCOL["parameters"][name]["initial_amplitude_kcal_mol"] for name in ids])
    bounds = [tuple(PROTOCOL["parameters"][name]["bounds_kcal_mol"]) for name in ids]
    objective = Objective(candidate_id, surfaces)
    result = minimize(lambda values: objective.evaluate(values)["objective"], x0, method="L-BFGS-B", bounds=bounds,
                      options={"maxiter": 20, "maxfun": 90, "ftol": 1e-8, "gtol": 1e-4, "eps": .002, "maxls": 10})
    final = {name: float(value) for name, value in zip(ids, result.x)}
    final_eval = objective.evaluate(result.x, "final")
    directory = RESULTS / candidate_id
    topology = directory / "FINAL_DERIVED_TOPOLOGY.parm7"
    receipt = build_topology(final, topology)
    atomic_json(directory / "FIT_RESULT.json", {"optimizer": {"success": bool(result.success), "status": int(result.status),
                "message": str(result.message), "nit": int(result.nit), "nfev": int(result.nfev), "fun": float(result.fun)},
                "initial_amplitudes": {name: float(value) for name, value in zip(ids, x0)},
                "final_amplitudes": final, "topology": receipt})
    first.write_csv(directory / "OBJECTIVE_TRAJECTORY.csv", objective.trajectory, list(objective.trajectory[0]))
    metrics = band_metrics(final_eval["points"])
    first.write_csv(directory / "ENERGY_BAND_METRICS.csv", metrics, list(metrics[0]))
    first.write_csv(directory / "POINTWISE_RESULTS.csv", final_eval["points"], list(final_eval["points"][0]))
    return {"candidate_id": candidate_id, "optimizer": json.loads((directory / "FIT_RESULT.json").read_text())["optimizer"],
            "initial": {name: float(value) for name, value in zip(ids, x0)}, "final": final,
            "rows": final_eval["points"], "metrics": metrics, "topology": topology}


def metric(candidate: dict, axis: str, band: str) -> dict:
    return next(row for row in candidate["metrics"] if row["axis"] == axis and row["band"] == band)


def main() -> None:
    surfaces = first.raw_surface_records()
    if {axis: len(rows) for axis, rows in surfaces.items()} != {"CHI": 24, "PHI": 18, "PSI": 14}:
        raise RuntimeError("C3 authoritative QM point identity mismatch")
    candidates = [fit_candidate(candidate_id, surfaces) for candidate_id in
                  ("C3A_CHI_N2", "C3B_PHI_N3", "C3C_CHI_N2_PHI_N3")]
    c1_rows = load_c1_rows()
    c1_metrics = band_metrics([{"axis": key[0], "angle_degrees": key[1],
                                "qm_relative_kcal_mol": float(value["qm_relative_kcal_mol"]),
                                "residual_kcal_mol": float(value["residual_kcal_mol"])} for key, value in c1_rows.items()])
    def c1_metric(axis, band): return next(row for row in c1_metrics if row["axis"] == axis and row["band"] == band)
    support = {}
    for candidate in candidates:
        cid = candidate["candidate_id"]
        improvements = {axis: c1_metric(axis, "QM_LE_10")["rmse_kcal_mol"] - metric(candidate, axis, "QM_LE_10")["rmse_kcal_mol"]
                        for axis in first.AXES}
        if cid == "C3A_CHI_N2":
            passed = improvements["CHI"] > TOL and improvements["PHI"] >= -TOL and improvements["PSI"] >= -TOL
        elif cid == "C3B_PHI_N3":
            low_phi = [row for row in candidate["rows"] if row["axis"] == "PHI" and row["qm_relative_kcal_mol"] <= 10]
            loo = []
            for omitted in low_phi:
                retained = [row for row in low_phi if row is not omitted]
                c3_rmse = float(np.sqrt(np.mean([row["residual_kcal_mol"]**2 for row in retained])))
                old = [float(c1_rows[("PHI", int(row["angle_degrees"]))]["residual_kcal_mol"]) for row in retained]
                loo.append({"omitted_angle_degrees": omitted["angle_degrees"], "c3_rmse": c3_rmse,
                            "c1_rmse": float(np.sqrt(np.mean(np.asarray(old)**2))), "directionally_improved": c3_rmse < float(np.sqrt(np.mean(np.asarray(old)**2)))})
            atomic_json(RESULTS / cid / "PHI_LOW_ENERGY_LOO.json", loo)
            passed = improvements["PHI"] > TOL and all(row["directionally_improved"] for row in loo) and improvements["CHI"] >= -TOL and improvements["PSI"] >= -TOL
        else:
            passed = improvements["CHI"] > TOL and improvements["PHI"] > TOL and improvements["PSI"] >= -TOL
        support[cid] = {"low_energy_rmse_improvement_vs_c1": improvements,
                        "low_energy_hypothesis_supported": bool(passed)}
    summary = {"schema": "tsl-rsh-c3-diagnostic-result-v1", "candidates": {}, "support": support,
               "selected_model": "NONE", "formal_publication_qualification": "DISCLOSURE_ONLY"}
    for candidate in candidates:
        cid = candidate["candidate_id"]
        summary["candidates"][cid] = {"optimizer": candidate["optimizer"], "initial_amplitudes": candidate["initial"],
                                      "final_amplitudes": candidate["final"], "metrics": candidate["metrics"]}
    atomic_json(RESULTS / "C3_DIAGNOSTIC_RESULT.json", summary)
    generated = sorted(path for path in HERE.rglob("*") if path.is_file() and path.name != "SHA256SUMS" and "__pycache__" not in path.parts)
    (HERE / "SHA256SUMS").write_text("".join(f"{sha256(path)}  {path.relative_to(HERE)}\n" for path in generated))
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
