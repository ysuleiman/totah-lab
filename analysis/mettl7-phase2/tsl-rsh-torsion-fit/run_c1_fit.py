#!/usr/bin/env python3
"""Execute the frozen six-amplitude C1 fit against Sander-relaxed profiles."""

from __future__ import annotations

import csv
import hashlib
import json
import math
import os
import shutil
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import parmed as pmd
import scipy
from scipy.optimize import minimize

import close_publication_gates as gates
import run_first_pass as first


HERE = Path(__file__).resolve().parent
C1 = HERE / "04_FIT/C1"
VALIDATION = HERE / "05_VALIDATION/C1"
FIGURES = HERE / "06_FIGURES"
TABLES = HERE / "07_TABLES"
PUBLICATION = HERE / "08_PUBLICATION"
PROTOCOL_PATH = C1 / "C1_FIT_PROTOCOL.json"
PARAMETER_ORDER = [1, 2, 7, 12, 17, 30]
PARAMETER_IDS = [f"LOCAL_TYPE_{i}" for i in PARAMETER_ORDER]
AXIS_TYPES = {"CHI": [17, 30], "PHI": [1, 12], "PSI": [2, 7]}
BOUNDS = (0.0, 2.0)
REGULARIZATION = 0.01
PRIOR_SCALE = 0.5
SENSITIVITY_STEP = 0.01
PRMTOP_AMPLITUDE_READBACK_TOL = 5e-8


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def atomic_json(path: Path, value) -> None:
    first.atomic_json(path, value)


def parameter_dict(values: np.ndarray | list[float]) -> dict[int, float]:
    return {index: float(value) for index, value in zip(PARAMETER_ORDER, values)}


def initial_parameters() -> dict[int, float]:
    raw = json.loads((C1 / "C1_INITIAL_PARAMETERS.json").read_text())["parameters"]
    return {index: float(raw[f"LOCAL_TYPE_{index}"]) for index in PARAMETER_ORDER}


def mapping_by_type() -> dict[int, list[str]]:
    assignments = json.loads((HERE / "02_TOPOLOGY_MAPPING/LOCAL_CLONE_ASSIGNMENTS.json").read_text())["assignments"]
    result = {index: [] for index in PARAMETER_ORDER}
    for row in assignments:
        source = int(row["source_type_index"])
        if source in result:
            result[source].append(row["term_identity"])
    if any(not result[index] for index in PARAMETER_ORDER):
        raise RuntimeError("incomplete C1 mapping")
    return result


def build_candidate(parameters: dict[int, float], output: Path) -> dict:
    topology = pmd.load_file(str(first.BASELINE))
    identities = mapping_by_type()
    baseline_snapshot = gates.torsion_snapshot(topology)
    expected_changed = set()
    for source in PARAMETER_ORDER:
        selected = set(identities[source])
        matches = [(identity, term) for identity, term in gates.term_identity_records(topology) if identity in selected]
        if len(matches) != len(selected):
            raise RuntimeError(f"C1 mapping mismatch for type {source}")
        clone = None
        for identity, term in matches:
            if clone is None:
                import copy
                clone = copy.copy(term.type)
                clone.phi_k = parameters[source]
                topology.dihedral_types.append(clone)
            term.type = clone
            if abs(baseline_snapshot[identity]["phi_k"] - parameters[source]) > 1e-12:
                expected_changed.add(identity)
    topology.dihedral_types.claim()
    output.parent.mkdir(parents=True, exist_ok=True)
    topology.save(str(output), overwrite=True)
    readback = pmd.load_file(str(output))
    after = gates.torsion_snapshot(readback)
    actual_changed = {identity for identity in baseline_snapshot if baseline_snapshot[identity] != after[identity]}
    if actual_changed != expected_changed:
        raise RuntimeError(f"candidate changed wrong proper terms: {actual_changed ^ expected_changed}")
    baseline_frozen = first.frozen_non_torsional(pmd.load_file(str(first.BASELINE)))["components"]
    candidate_frozen = first.frozen_non_torsional(readback)["components"]
    if baseline_frozen != candidate_frozen:
        raise RuntimeError("candidate changed frozen non-torsional components")
    for source, selected in identities.items():
        for identity in selected:
            if abs(after[identity]["phi_k"] - parameters[source]) > PRMTOP_AMPLITUDE_READBACK_TOL:
                raise RuntimeError("candidate topology readback mismatch")
    return {"sha256": first.sha256_path(output), "changed_term_count": len(actual_changed),
            "frozen_components_unchanged": True, "unrelated_dihedrals_unchanged": True}


def relative_rows(axis: str, point_results: list[dict]) -> list[dict]:
    qm_min = min(row["qm_energy_hartree"] for row in point_results)
    mm_min = min(row["mm_tot_kcal_mol_absolute"] for row in point_results)
    output = []
    for row in sorted(point_results, key=lambda item: item["angle_degrees"]):
        qm = (row["qm_energy_hartree"] - qm_min) * first.HARTREE_TO_KCAL_MOL
        mm = row["mm_tot_kcal_mol_absolute"] - mm_min
        output.append({"axis": axis, "angle_degrees": int(row["angle_degrees"]),
                       "qm_relative_kcal_mol": qm, "mm_relative_kcal_mol": mm,
                       "residual_kcal_mol": qm - mm,
                       "target_angle_degrees": row["target_angle_after_minimization_degrees"],
                       "converged": row["minimization_converged"], "target_pass": row["target_angle_pass"]})
    return output


class Objective:
    def __init__(self, surfaces: dict[str, list[dict]], initial: dict[int, float]):
        self.surfaces = surfaces
        self.initial = initial
        self.evaluation_count = 0
        self.cache = {}
        self.trajectory = []
        self.root = C1 / "evaluations"
        self.root.mkdir(parents=True, exist_ok=True)

    def evaluate_axis(self, axis: str, values: np.ndarray, purpose: str = "optimization") -> dict:
        indices = AXIS_TYPES[axis]
        key = (axis,) + tuple(round(float(x), 12) for x in values)
        if key in self.cache:
            return self.cache[key]
        parameters = dict(self.initial)
        parameters.update({index: float(value) for index, value in zip(indices, values)})
        self.evaluation_count += 1
        evaluation_id = f"EVAL_{self.evaluation_count:05d}"
        evidence_dir = self.root / evaluation_id
        evidence_dir.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="tsl-c1-") as temp_text:
            temp = Path(temp_text)
            topology_path = temp / "candidate.parm7"
            topology_receipt = build_candidate(parameters, topology_path)
            topology = pmd.load_file(str(topology_path))
            points = []
            for record in self.surfaces[axis]:
                run_dir = temp / axis / f"{int(record['angle_degrees']):+04d}"
                points.append(gates.minimize_point(topology, record, run_dir, topology_path=topology_path))
        rows = relative_rows(axis, points)
        if not all(row["converged"] and row["target_pass"] for row in rows):
            raise RuntimeError(f"failed C1 relaxation in {evaluation_id}")
        residual = np.asarray([row["residual_kcal_mol"] for row in rows])
        prior = sum(((parameters[index] - self.initial[index]) / PRIOR_SCALE) ** 2 for index in indices)
        result = {"evaluation_id": evaluation_id, "axis": axis, "purpose": purpose,
                  "parameters": {f"LOCAL_TYPE_{i}": parameters[i] for i in PARAMETER_ORDER},
                  "profile_mse_kcal2_mol2": float(np.mean(residual ** 2)),
                  "regularization": float(REGULARIZATION * prior),
                  "objective": float(np.mean(residual ** 2) + REGULARIZATION * prior),
                  "topology": topology_receipt, "points": rows, "created_utc": now()}
        atomic_json(evidence_dir / "EVALUATION.json", result)
        self.trajectory.append({"evaluation_id": evaluation_id, "axis": axis, "purpose": purpose,
                                **result["parameters"], "profile_mse_kcal2_mol2": result["profile_mse_kcal2_mol2"],
                                "regularization": result["regularization"], "objective": result["objective"]})
        self.cache[key] = result
        return result


def low_weight(energy: float) -> float:
    if energy <= 1.0:
        return 1.0
    if energy >= 10.0:
        return 0.0
    return 0.5 * (1.0 + math.cos(math.pi * (energy - 1.0) / 9.0))


def metrics(rows: list[dict]) -> dict:
    residual = np.asarray([r["residual_kcal_mol"] for r in rows])
    return {"point_count": len(rows), "rmse_kcal_mol": float(np.sqrt(np.mean(residual**2))),
            "mae_kcal_mol": float(np.mean(abs(residual))), "max_abs_kcal_mol": float(np.max(abs(residual)))}


def periodic_error(a: float, b: float) -> float:
    return abs((a - b + 180) % 360 - 180)


def local_extrema(rows: list[dict], field: str) -> tuple[list[dict], list[dict]]:
    ordered = sorted(rows, key=lambda r: r["angle_degrees"])
    minima, maxima = [], []
    for i, row in enumerate(ordered):
        value = row[field]
        before = ordered[(i-1) % len(ordered)][field]
        after = ordered[(i+1) % len(ordered)][field]
        if value <= before and value <= after:
            minima.append({"angle_degrees": row["angle_degrees"], "energy_kcal_mol": value})
        if value >= before and value >= after:
            maxima.append({"angle_degrees": row["angle_degrees"], "energy_kcal_mol": value})
    return minima, maxima


def validation_analysis(final_rows: list[dict]) -> tuple[dict, dict, dict]:
    whole, low, critical = {}, {}, {}
    for axis in first.AXES:
        rows = [r for r in final_rows if r["axis"] == axis]
        whole[axis] = metrics(rows)
        low_rows = [r for r in rows if r["qm_relative_kcal_mol"] <= 10.0]
        weights = np.asarray([low_weight(r["qm_relative_kcal_mol"]) for r in rows])
        residual = np.asarray([r["residual_kcal_mol"] for r in rows])
        low[axis] = {"definition_count": len(low_rows), "core_count": sum(r["qm_relative_kcal_mol"] <= 1 for r in rows),
                     "weighted_rmse_kcal_mol": float(np.sqrt(np.sum(weights*residual**2)/np.sum(weights))),
                     "mae_kcal_mol": float(np.mean([abs(r["residual_kcal_mol"]) for r in low_rows]))}
        qm_minima, qm_barriers = local_extrema(rows, "qm_relative_kcal_mol")
        mm_minima, mm_barriers = local_extrema(rows, "mm_relative_kcal_mol")
        qm_global_min = min(rows, key=lambda r:r["qm_relative_kcal_mol"])
        mm_global_min = min(rows, key=lambda r:r["mm_relative_kcal_mol"])
        qm_major = max(rows, key=lambda r:r["qm_relative_kcal_mol"])
        mm_major = max(rows, key=lambda r:r["mm_relative_kcal_mol"])
        critical[axis] = {
            "qm_minima": qm_minima, "mm_minima": mm_minima, "qm_barriers": qm_barriers, "mm_barriers": mm_barriers,
            "global_minimum_angle_error_degrees": periodic_error(qm_global_min["angle_degrees"], mm_global_min["angle_degrees"]),
            "major_barrier_angle_error_degrees": periodic_error(qm_major["angle_degrees"], mm_major["angle_degrees"]),
            "major_barrier_height_error_kcal_mol": abs(qm_major["qm_relative_kcal_mol"]-mm_major["mm_relative_kcal_mol"]),
            "major_minimum_ordering": {"qm": [x["angle_degrees"] for x in sorted(qm_minima,key=lambda x:x["energy_kcal_mol"])],
                                       "mm": [x["angle_degrees"] for x in sorted(mm_minima,key=lambda x:x["energy_kcal_mol"])]}}
    return whole, low, critical


def nearest_record(records: list[dict], angle: int) -> dict:
    return min(records, key=lambda r: periodic_error(float(r["angle_degrees"]), angle))


def full_domain(final_topology: Path, surfaces: dict[str, list[dict]], authoritative_rows: list[dict],
                output_root: Path | None = None) -> tuple[list[dict], dict]:
    topology = pmd.load_file(str(final_topology))
    output = []
    closure = {}
    root = output_root if output_root is not None else VALIDATION / "full-domain-runs"
    authoritative = {(r["axis"], int(r["angle_degrees"])): r for r in authoritative_rows}
    for axis in first.AXES:
        for angle in range(-180, 180, 15):
            if (axis, angle) in authoritative:
                row = dict(authoritative[(axis, angle)])
                row.update({"authoritative_qm": True, "source_geometry_angle": angle})
                output.append(row)
                continue
            source = nearest_record(surfaces[axis], angle)
            record = dict(source)
            record.update({"angle_degrees": angle, "candidate_id": f"MM_ONLY_{axis}_{angle:+04d}",
                           "archive_member": source["archive_member"], "qm_energy_hartree": 0.0})
            result = gates.minimize_point(topology, record, root/axis/f"{angle:+04d}", topology_path=final_topology)
            if not result["minimization_converged"] or not result["target_angle_pass"]:
                raise RuntimeError(f"unconverged full-domain sweep at {axis} {angle:+d}")
            output.append({"axis": axis, "angle_degrees": angle, "mm_absolute_kcal_mol": result["mm_tot_kcal_mol_absolute"],
                           "target_angle_degrees": result["target_angle_after_minimization_degrees"], "converged": result["minimization_converged"],
                           "target_pass": result["target_angle_pass"], "authoritative_qm": False,
                           "source_geometry_angle": int(source["angle_degrees"])})
        axis_rows = [r for r in output if r["axis"] == axis]
        minimum = min(r.get("mm_absolute_kcal_mol", r.get("mm_absolute_energy_kcal_mol")) for r in axis_rows)
        for row in axis_rows:
            absolute = row.get("mm_absolute_kcal_mol", row.get("mm_absolute_energy_kcal_mol"))
            row["mm_relative_full_domain_kcal_mol"] = absolute - minimum
        source = nearest_record(surfaces[axis], 180)
        record = dict(source); record.update({"angle_degrees":180,"candidate_id":f"MM_ONLY_{axis}_+180","qm_energy_hartree":0.0})
        plus = gates.minimize_point(topology, record, root/axis/"+180", topology_path=final_topology)
        if not plus["minimization_converged"] or not plus["target_angle_pass"]:
            raise RuntimeError(f"unconverged periodic-closure sweep at {axis} +180")
        minus = next(r for r in axis_rows if r["angle_degrees"] == -180)
        minus_abs = minus.get("mm_absolute_kcal_mol", minus.get("mm_absolute_energy_kcal_mol"))
        closure[axis] = abs(plus["mm_tot_kcal_mol_absolute"] - minus_abs)
    # Apply locked unsampled pathology rules.
    triggers = []
    for axis in first.AXES:
        rows = sorted([r for r in output if r["axis"] == axis], key=lambda r:r["angle_degrees"])
        sampled = [r for r in rows if r["authoritative_qm"]]
        sampled_min = min(r["mm_relative_full_domain_kcal_mol"] for r in sampled)
        for i,row in enumerate(rows):
            if row["authoritative_qm"]:
                continue
            prev,nxt=rows[(i-1)%24],rows[(i+1)%24]
            strict=row["mm_relative_full_domain_kcal_mol"]<prev["mm_relative_full_domain_kcal_mol"] and row["mm_relative_full_domain_kcal_mol"]<nxt["mm_relative_full_domain_kcal_mol"]
            lower_sampled = sampled_min-row["mm_relative_full_domain_kcal_mol"]>0.5
            # nearest sampled bracketing angles on the periodic ordered grid
            left=next((rows[(i-j)%24] for j in range(1,25) if rows[(i-j)%24]["authoritative_qm"]),None)
            right=next((rows[(i+j)%24] for j in range(1,25) if rows[(i+j)%24]["authoritative_qm"]),None)
            below_interp = left is not None and right is not None and ((left["mm_relative_full_domain_kcal_mol"]+right["mm_relative_full_domain_kcal_mol"])/2-row["mm_relative_full_domain_kcal_mol"]>1.0)
            if strict and (lower_sampled or below_interp):
                triggers.append({"axis":axis,"angle_degrees":row["angle_degrees"],"lower_than_sampled":lower_sampled,"below_interpolation":below_interp})
    return output, {"periodic_closure_kcal_mol":closure,"pathology_triggers":triggers,"pass":not triggers}


def sensitivity(objective: Objective, final_parameters: dict[int,float], final_rows: list[dict]) -> dict:
    base = {(r["axis"],r["angle_degrees"]):r for r in final_rows}
    columns=[]
    for index in PARAMETER_ORDER:
        axis=next(a for a,ids in AXIS_TYPES.items() if index in ids)
        ids=AXIS_TYPES[axis]; center=np.asarray([final_parameters[i] for i in ids]); pos=ids.index(index)
        low=max(BOUNDS[0],center[pos]-SENSITIVITY_STEP); high=min(BOUNDS[1],center[pos]+SENSITIVITY_STEP)
        plus=center.copy();minus=center.copy();plus[pos]=high;minus[pos]=low
        rp=objective.evaluate_axis(axis,plus,"postfit_sensitivity_plus")["points"]
        rm=objective.evaluate_axis(axis,minus,"postfit_sensitivity_minus")["points"]
        derivative={(axis,r["angle_degrees"]):(r["mm_relative_kcal_mol"]-next(x for x in rm if x["angle_degrees"]==r["angle_degrees"])["mm_relative_kcal_mol"])/(high-low) for r in rp}
        columns.append(derivative)
    ordered=sorted(base)
    J=np.asarray([[col.get(key,0.0) for col in columns] for key in ordered])
    singular=np.linalg.svd(J,compute_uv=False);rank=int(np.linalg.matrix_rank(J));condition=float(singular[0]/singular[-1]) if singular[-1]>0 else None
    covariance=np.linalg.pinv(J.T@J + 1e-12*np.eye(6));scale=np.sqrt(np.diag(covariance));corr=covariance/np.outer(scale,scale)
    residual=np.asarray([base[key]["residual_kcal_mol"] for key in ordered])
    schemes={}
    weights_equal_point=np.ones(len(ordered))
    counts={a:sum(k[0]==a for k in ordered) for a in first.AXES}
    weights_surface=np.asarray([1/counts[k[0]] for k in ordered])
    weights_low=np.asarray([low_weight(base[k]["qm_relative_kcal_mol"]) for k in ordered])
    for name,w in {"equal_point":weights_equal_point,"equal_surface":weights_surface,"low_energy":weights_low}.items():
        A=J.T@(w[:,None]*J)+REGULARIZATION/(PRIOR_SCALE**2)*np.eye(6)
        delta=np.linalg.solve(A,J.T@(w*residual))
        schemes[name]={"linearized_parameter_delta_kcal_mol":delta.tolist(),"max_abs_delta":float(np.max(abs(delta))),
                       "predicted_profile_change_rms_kcal_mol":float(np.sqrt(np.mean((J@delta)**2)))}
    holdouts=[]
    for offset in range(4):
        keep=np.asarray([i%4!=offset for i in range(len(ordered))]);A=J[keep].T@J[keep]+REGULARIZATION/(PRIOR_SCALE**2)*np.eye(6)
        delta=np.linalg.solve(A,J[keep].T@residual[keep]);held=~keep
        holdouts.append({"offset":offset,"parameter_delta":delta.tolist(),"heldout_prediction_rms":float(np.sqrt(np.mean((J[held]@delta-residual[held])**2)))})
    max_corr=float(np.max(np.abs(corr-np.eye(6))))
    parameter_status="CONCERN" if rank==6 and (condition>50 or max_corr>0.95) else ("PASS" if rank==6 else "FAIL")
    profile_spread=max(x["predicted_profile_change_rms_kcal_mol"] for x in schemes.values())
    profile_status="PASS" if profile_spread<=0.5 else ("CONCERN" if profile_spread<=1.0 else "FAIL")
    return {"parameter_order":PARAMETER_IDS,"rank":rank,"singular_values":singular.tolist(),"condition_number":condition,
            "covariance":covariance.tolist(),"correlation":corr.tolist(),"max_abs_correlation":max_corr,
            "weighting_sensitivity":schemes,"structured_angular_resampling":holdouts,
            "leave_region_out_note":"four rotations of every-fourth ordered authoritative cell; local production-Jacobian refit",
            "parameter_identifiability":parameter_status,"profile_predictive_stability":profile_status,
            "profile_sensitivity_max_rms_kcal_mol":profile_spread}


def write_figures(rows: list[dict]) -> None:
    prefit=list(csv.DictReader((HERE/"03_PREFIT_BASELINE/PRE_FIT_POINTWISE_PUBLICATION_TABLE.csv").open()))
    for axis in first.AXES:
        group=sorted([r for r in rows if r["axis"]==axis],key=lambda r:r["angle_degrees"])
        old=sorted([r for r in prefit if r["axis"]==axis],key=lambda r:int(r["angle_degrees"]))
        fig,ax=plt.subplots(figsize=(7,4));ax.scatter([r["angle_degrees"] for r in group],[r["qm_relative_kcal_mol"] for r in group],label="QM calculated",color="black")
        ax.plot([int(r["angle_degrees"]) for r in old],[float(r["mm_relaxed_relative_energy_kcal_mol"]) for r in old],label="prefit MM")
        ax.plot([r["angle_degrees"] for r in group],[r["mm_relative_kcal_mol"] for r in group],label="C1 MM")
        ax.set(xlabel="dihedral (degrees)",ylabel="relative energy (kcal/mol)",title=f"{axis}: QM vs MM-relaxed profiles");ax.legend();fig.tight_layout()
        fig.savefig(FIGURES/f"{axis}_C1_QM_vs_PREFIT_vs_POSTFIT.svg");plt.close(fig)
    fig,ax=plt.subplots(figsize=(8,4));
    for axis in first.AXES:
        group=sorted([r for r in rows if r["axis"]==axis],key=lambda r:r["angle_degrees"]);ax.plot([r["angle_degrees"] for r in group],[r["residual_kcal_mol"] for r in group],marker="o",label=axis)
    ax.axhline(0,color="black",linewidth=.7);ax.set(xlabel="dihedral (degrees)",ylabel="QM - C1 MM (kcal/mol)");ax.legend();fig.tight_layout();fig.savefig(FIGURES/"C1_RESIDUALS.svg");plt.close(fig)
    shutil.copy2(FIGURES/"C1_RESIDUALS.svg",FIGURES/"C1_COMBINED.svg")


def main() -> None:
    for path in (C1,VALIDATION,FIGURES,TABLES,PUBLICATION):path.mkdir(parents=True,exist_ok=True)
    if first.sha256_path(PROTOCOL_PATH) == "": raise RuntimeError("missing protocol")
    if first.sha256_path(HERE/"00_PROTOCOL/LOCKED_ACCEPTANCE_PROTOCOL.json")!="859fbc97a8f3480f9e168c22b93a421d597494856d151db1842bb3ead61bbbc4":raise RuntimeError("acceptance identity mismatch")
    subprocess.run([os.fspath(Path(os.sys.executable)),os.fspath(first.RAW/"audit_torsion_publication_record.py"),"--verify-only"],check=True,stdout=subprocess.DEVNULL)
    surfaces=first.raw_surface_records();initial=initial_parameters();objective=Objective(surfaces,initial);final=dict(initial);optimizer_results={}
    for axis in first.AXES:
        ids=AXIS_TYPES[axis];x0=np.asarray([initial[i] for i in ids])
        result=minimize(lambda x:objective.evaluate_axis(axis,x)["objective"],x0,method="L-BFGS-B",bounds=[BOUNDS]*2,
                        options={"maxiter":20,"maxfun":90,"ftol":1e-8,"gtol":1e-4,"eps":0.002,"maxls":10})
        final.update({i:float(v) for i,v in zip(ids,result.x)})
        optimizer_results[axis]={"success":bool(result.success),"status":int(result.status),"message":str(result.message),"nit":int(result.nit),"nfev":int(result.nfev),"fun":float(result.fun)}
    final_top=C1/"C1_FINAL_DERIVED_TOPOLOGY.parm7";top_receipt=build_candidate(final,final_top)
    first.atomic_text(C1/"C1_FINAL_TOPOLOGY_SHA256",f"{first.sha256_path(final_top)}  C1_FINAL_DERIVED_TOPOLOGY.parm7\n")
    atomic_json(C1/"C1_FINAL_PARAMETERS.json",{"schema":"tsl-rsh-c1-final-parameters-v1","baseline":{f"LOCAL_TYPE_{i}":initial[i] for i in PARAMETER_ORDER},"fitted":{f"LOCAL_TYPE_{i}":final[i] for i in PARAMETER_ORDER},"units":"kcal/mol","topology":top_receipt})
    first.write_csv(C1/"C1_OPTIMIZATION_TRAJECTORY.csv",objective.trajectory,list(objective.trajectory[0]));shutil.copy2(C1/"C1_OPTIMIZATION_TRAJECTORY.csv",C1/"C1_OBJECTIVE_HISTORY.csv")
    final_topology=pmd.load_file(str(final_top));final_results=[]
    for axis in first.AXES:
        for record in surfaces[axis]:final_results.append(gates.minimize_point(final_topology,record,VALIDATION/"final-runs"/axis/f"{int(record['angle_degrees']):+04d}",topology_path=final_top))
    final_rows=[]
    for axis in first.AXES:final_rows.extend(relative_rows(axis,[r for r in final_results if r["axis"]==axis]))
    first.write_csv(VALIDATION/"C1_POINTWISE_VALIDATION.csv",final_rows,list(final_rows[0]))
    whole,low,critical=validation_analysis(final_rows);atomic_json(VALIDATION/"C1_WHOLE_PROFILE_METRICS.json",whole);atomic_json(VALIDATION/"C1_LOW_ENERGY_METRICS.json",low);atomic_json(VALIDATION/"C1_CRITICAL_POINTS.json",critical)
    domain,unsampled=full_domain(final_top,surfaces,[{**r,"mm_absolute_energy_kcal_mol":next(x for x in final_results if x["axis"]==r["axis"] and x["angle_degrees"]==r["angle_degrees"])["mm_tot_kcal_mol_absolute"]} for r in final_rows])
    first.write_csv(VALIDATION/"C1_UNSAMPLED_DOMAIN_VALIDATION.csv",domain,sorted(set().union(*(r.keys() for r in domain))))
    ident=sensitivity(objective,final,final_rows);atomic_json(VALIDATION/"C1_IDENTIFIABILITY.json",ident);atomic_json(VALIDATION/"C1_SENSITIVITY_ANALYSIS.json",ident)
    first.write_csv(C1/"C1_OPTIMIZATION_TRAJECTORY.csv",objective.trajectory,list(objective.trajectory[0]));shutil.copy2(C1/"C1_OPTIMIZATION_TRAJECTORY.csv",C1/"C1_OBJECTIVE_HISTORY.csv")
    whole_pass=all(m["rmse_kcal_mol"]<=1 and m["mae_kcal_mol"]<=.75 and m["max_abs_kcal_mol"]<=2 for m in whole.values())
    minimum_pass=all(v["global_minimum_angle_error_degrees"]<=15 for v in critical.values())
    barrier_pass=all(v["major_barrier_angle_error_degrees"]<=15 and v["major_barrier_height_error_kcal_mol"]<=1 for v in critical.values())
    low_pass=all(v["weighted_rmse_kcal_mol"]<=1 and v["mae_kcal_mol"]<=.75 and critical[a]["global_minimum_angle_error_degrees"]<=15 for a,v in low.items())
    closure_pass=all(v<=.1 for v in unsampled["periodic_closure_kcal_mol"].values())
    c1_pass=all([whole_pass,minimum_pass,barrier_pass,low_pass,closure_pass,unsampled["pass"]])
    decision={"schema":"tsl-rsh-c1-decision-v1","c1_optimization_run":True,"c1_converged":all(x["success"] for x in optimizer_results.values()),"optimizer":optimizer_results,"parameters":{f"LOCAL_TYPE_{i}":{"baseline":initial[i],"fitted":final[i]} for i in PARAMETER_ORDER},"whole_profile":whole,"low_energy":low,"critical_points":critical,"unsampled":unsampled,"gates":{"low_energy":"PASS" if low_pass else "FAIL","whole_profile":"PASS" if whole_pass else "FAIL","minimum_topology":"PASS" if minimum_pass else "FAIL","barrier":"PASS" if barrier_pass else "FAIL","periodic_closure":"PASS" if closure_pass else "FAIL","unsampled_region":"PASS" if unsampled["pass"] else "FAIL"},"parameter_identifiability":ident["parameter_identifiability"],"profile_predictive_stability":ident["profile_predictive_stability"],"new_qm_required":not unsampled["pass"],"c1_status":"PASS" if c1_pass else "FAIL","c2_eligible":not c1_pass and any(not x for x in [low_pass,minimum_pass,barrier_pass,unsampled["pass"]]),"raw_qm_artifacts_modified":False,"source_force_field_modified":False,"c2_run":False,"new_qm_run":False,"md_run":False}
    atomic_json(PUBLICATION/"C1_DECISION.json",decision)
    fitting_commit=subprocess.run(["git","rev-parse","HEAD"],cwd=first.ROOT,text=True,capture_output=True,check=True).stdout.strip()
    provenance={"schema":"tsl-rsh-c1-provenance-v1","created_utc":now(),"qm_archives":json.loads((HERE/"08_PUBLICATION/GATE_CLOSURE_PROVENANCE.json").read_text())["qm_archives"],"qm_surface_identities":json.loads((HERE/"08_PUBLICATION/GATE_CLOSURE_PROVENANCE.json").read_text())["qm_surface_identities"],"baseline_sha256":first.sha256_path(first.BASELINE),"locked_acceptance_sha256":first.sha256_path(HERE/"00_PROTOCOL/LOCKED_ACCEPTANCE_PROTOCOL.json"),"step0_commit":"7e574822dd5c3e24da38ddb762f92644dfd77f2e","c1_fitting_code_commit":fitting_commit,"configuration_sha256":first.sha256_path(PROTOCOL_PATH),"amber_sander":"26.0","optimizer":f"SciPy {scipy.__version__} L-BFGS-B","final_topology_sha256":first.sha256_path(final_top)};atomic_json(PUBLICATION/"C1_PROVENANCE.json",provenance)
    write_figures(final_rows)
    params=[{"parameter_id":f"LOCAL_TYPE_{i}","baseline_kcal_mol":initial[i],"fitted_kcal_mol":final[i],"axis":next(a for a,v in AXIS_TYPES.items() if i in v)} for i in PARAMETER_ORDER];first.write_csv(TABLES/"C1_PARAMETER_TABLE.csv",params,list(params[0]));first.atomic_text(TABLES/"C1_PARAMETER_TABLE.md","# C1 parameter table\n\n"+"\n".join(f"- {r['parameter_id']}: {r['baseline_kcal_mol']:.9f} -> {r['fitted_kcal_mol']:.9f} kcal/mol" for r in params)+"\n")
    stats=[{"axis":a,**whole[a]} for a in first.AXES];first.write_csv(TABLES/"C1_FIT_STATISTICS.csv",stats,list(stats[0]));first.atomic_text(TABLES/"C1_FIT_STATISTICS.md","# C1 fit statistics\n\n"+json.dumps(whole,indent=2)+"\n")
    critical_rows=[{"axis":a,**{k:v for k,v in critical[a].items() if not isinstance(v,(list,dict))}} for a in first.AXES];first.write_csv(TABLES/"C1_CRITICAL_POINT_TABLE.csv",critical_rows,list(critical_rows[0]));first.atomic_text(TABLES/"C1_CRITICAL_POINT_TABLE.md","# C1 critical points\n\n"+json.dumps(critical,indent=2)+"\n")
    generated=sorted(p for p in HERE.rglob('*') if p.is_file() and p.name!='SHA256SUMS' and '__pycache__' not in p.parts and not p.name.endswith(('.pyc','.tmp')));first.atomic_text(HERE/"SHA256SUMS",''.join(f'{first.sha256_path(p)}  {p.relative_to(HERE)}\n' for p in generated));print(json.dumps(decision,indent=2))


if __name__=="__main__":main()
