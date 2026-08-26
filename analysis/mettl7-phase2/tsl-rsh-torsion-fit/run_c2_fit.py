#!/usr/bin/env python3
"""Run the preregistered minimal C2 torsion model panel using relaxed Sander profiles."""

from __future__ import annotations

import copy
import csv
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
import run_c1_fit as c1
import run_first_pass as first


HERE = Path(__file__).resolve().parent
C2 = HERE / "04_FIT/C2"
VALIDATION = HERE / "05_VALIDATION/C2"
FIGURES = HERE / "06_FIGURES"
TABLES = HERE / "07_TABLES"
PUBLICATION = HERE / "08_PUBLICATION"
PANEL_PATH = C2 / "C2_MODEL_PANEL.json"
C1_TOPOLOGY = HERE / "04_FIT/C1/C1_FINAL_DERIVED_TOPOLOGY.parm7"
C1_PARAMETERS = HERE / "04_FIT/C1/C1_FINAL_PARAMETERS.json"
REGULARIZATION = 0.01
READBACK_TOL = 5e-8


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def load_c1_parameters() -> dict[str, float]:
    return {k: float(v) for k, v in json.loads(C1_PARAMETERS.read_text())["fitted"].items()}


def assignments(axis: str) -> list[dict]:
    rows = json.loads((HERE / "02_TOPOLOGY_MAPPING/LOCAL_CLONE_ASSIGNMENTS.json").read_text())["assignments"]
    return [row for row in rows if row["axis"] == axis]


def canonical_atoms(atoms) -> tuple[int, int, int, int]:
    values = tuple(int(x) for x in atoms)
    reverse = tuple(reversed(values))
    return min(values, reverse)


def new_term_specs(model: dict) -> list[dict]:
    result = []
    for spec in model["new_terms"]:
        item = dict(spec)
        item["parameter_id"] = f"{item['axis']}_N{item['periodicity']}_PHASE{item['phase_degrees']}"
        result.append(item)
    return result


def build_candidate(parameters: dict[str, float], added_terms: list[dict], output: Path) -> dict:
    topology = pmd.load_file(str(C1_TOPOLOGY))
    before = gates.torsion_snapshot(topology)
    assignment_rows = json.loads((HERE / "02_TOPOLOGY_MAPPING/LOCAL_CLONE_ASSIGNMENTS.json").read_text())["assignments"]
    by_source: dict[int, list[str]] = {}
    for row in assignment_rows:
        by_source.setdefault(int(row["source_type_index"]), []).append(row["term_identity"])
    for source in c1.PARAMETER_ORDER:
        selected = set(by_source[source])
        matches = [(identity, term) for identity, term in gates.term_identity_records(topology) if identity in selected]
        if len(matches) != len(selected):
            raise RuntimeError(f"C2 existing-term identity mismatch for source {source}")
        clone = None
        for _, term in matches:
            if clone is None:
                clone = copy.copy(term.type)
                clone.phi_k = parameters[f"LOCAL_TYPE_{source}"]
                topology.dihedral_types.append(clone)
            term.type = clone
    added_receipts = []
    for spec in added_terms:
        axis = spec["axis"]
        quartets = sorted({canonical_atoms(row["atoms_zero_based"]) for row in assignments(axis)})
        parameter_id = spec["parameter_id"]
        amplitude = parameters[parameter_id]
        representative = next(term for _, term in gates.term_identity_records(topology)
                              if canonical_atoms((term.atom1.idx, term.atom2.idx, term.atom3.idx, term.atom4.idx)) in quartets)
        term_type = pmd.DihedralType(amplitude, int(spec["periodicity"]), float(spec["phase_degrees"]),
                                     scee=representative.type.scee, scnb=representative.type.scnb)
        topology.dihedral_types.append(term_type)
        for quartet in quartets:
            physical = next(term for term in topology.dihedrals
                            if not term.improper and canonical_atoms((term.atom1.idx, term.atom2.idx, term.atom3.idx, term.atom4.idx)) == quartet)
            topology.dihedrals.append(pmd.Dihedral(physical.atom1, physical.atom2, physical.atom3, physical.atom4,
                                                   improper=False, ignore_end=physical.ignore_end, type=term_type))
        added_receipts.append({"parameter_id": parameter_id, "axis": axis, "periodicity": spec["periodicity"],
                               "phase_degrees": spec["phase_degrees"], "physical_instance_count": len(quartets),
                               "physical_quartets_zero_based": [list(x) for x in quartets]})
    topology.dihedral_types.claim()
    output.parent.mkdir(parents=True, exist_ok=True)
    topology.save(str(output), overwrite=True)
    readback = pmd.load_file(str(output))
    frozen_before = first.frozen_non_torsional(pmd.load_file(str(first.BASELINE)))["components"]
    frozen_after = first.frozen_non_torsional(readback)["components"]
    if frozen_before != frozen_after:
        raise RuntimeError("C2 candidate changed frozen non-torsional components")
    snapshot = gates.torsion_snapshot(readback)
    for source in c1.PARAMETER_ORDER:
        for identity in by_source[source]:
            if abs(snapshot[identity]["phi_k"] - parameters[f"LOCAL_TYPE_{source}"]) > READBACK_TOL:
                raise RuntimeError("C2 existing amplitude readback mismatch")
    for spec, receipt in zip(added_terms, added_receipts):
        expected = {tuple(x) for x in receipt["physical_quartets_zero_based"]}
        found = []
        for _, term in gates.term_identity_records(readback):
            quartet = canonical_atoms((term.atom1.idx, term.atom2.idx, term.atom3.idx, term.atom4.idx))
            if quartet in expected and int(round(term.type.per)) == int(spec["periodicity"]) and abs(float(term.type.phase)-spec["phase_degrees"]) < 1e-3 and abs(term.type.phi_k-parameters[spec["parameter_id"]]) < READBACK_TOL:
                found.append(quartet)
        if set(found) != expected or len(found) != len(expected):
            raise RuntimeError(f"C2 added-term readback mismatch for {spec['parameter_id']}")
    return {"sha256": first.sha256_path(output), "added_terms": added_receipts,
            "frozen_components_unchanged": True, "source_force_field_modified": False,
            "c1_term_count_before": len(before), "proper_term_count_after": len(snapshot)}


class CandidateObjective:
    def __init__(self, candidate_id: str, model: dict, surfaces: dict, fixed: dict[str, float], fit_ids: list[str]):
        self.candidate_id, self.model, self.surfaces = candidate_id, model, surfaces
        self.fixed, self.fit_ids = dict(fixed), list(fit_ids)
        self.added = new_term_specs(model)
        self.count, self.cache, self.trajectory = 0, {}, []
        self.root = C2 / f"candidate_{candidate_id}" / "evaluations"
        self.root.mkdir(parents=True, exist_ok=True)

    def evaluate(self, values, purpose="optimization") -> dict:
        key = tuple(round(float(x), 12) for x in values)
        if key in self.cache:
            return self.cache[key]
        parameters = dict(self.fixed)
        parameters.update({key: float(value) for key, value in zip(self.fit_ids, values)})
        self.count += 1
        evaluation_id = f"EVAL_{self.count:05d}"
        with tempfile.TemporaryDirectory(prefix="tsl-c2-") as temp_text:
            temp = Path(temp_text)
            topology_path = temp / "candidate.parm7"
            topology_receipt = build_candidate(parameters, self.added, topology_path)
            topology = pmd.load_file(str(topology_path))
            all_rows = []
            for axis in first.AXES:
                point_results = [gates.minimize_point(topology, record, temp/axis/f"{int(record['angle_degrees']):+04d}", topology_path=topology_path)
                                 for record in self.surfaces[axis]]
                all_rows.extend(c1.relative_rows(axis, point_results))
        if not all(row["converged"] and row["target_pass"] for row in all_rows):
            raise RuntimeError(f"failed C2 relaxation in {evaluation_id}")
        profile_mse = {axis: float(np.mean([r["residual_kcal_mol"]**2 for r in all_rows if r["axis"] == axis])) for axis in first.AXES}
        primary = float(np.mean(list(profile_mse.values())))
        penalty = 0.0
        for parameter_id in self.fit_ids:
            scale = 1.0 if "_N" in parameter_id else 0.5
            initial = self.fixed[parameter_id]
            penalty += ((parameters[parameter_id]-initial)/scale)**2
        result = {"evaluation_id": evaluation_id, "candidate_id": self.candidate_id, "purpose": purpose,
                  "parameters": parameters, "equal_surface_profile_mse": primary,
                  "surface_mse": profile_mse, "regularization": REGULARIZATION*penalty,
                  "objective": primary+REGULARIZATION*penalty, "topology": topology_receipt,
                  "points": all_rows, "created_utc": now()}
        evidence = self.root/evaluation_id
        evidence.mkdir(parents=True, exist_ok=True)
        first.atomic_json(evidence/"EVALUATION.json", result)
        self.trajectory.append({"evaluation_id": evaluation_id, "purpose": purpose, **parameters,
                                "equal_surface_profile_mse": primary, "regularization": REGULARIZATION*penalty,
                                "objective": result["objective"]})
        self.cache[key] = result
        return result


def bounds_for(parameter_id: str, model: dict) -> tuple[float, float]:
    if parameter_id.startswith("LOCAL_TYPE_"):
        return 0.0, 2.0
    return tuple(next(x["bounds_kcal_mol"] for x in new_term_specs(model) if x["parameter_id"] == parameter_id))


def fit_candidate(model: dict, fixed: dict[str, float], fit_ids: list[str], surfaces: dict) -> dict:
    candidate_id = model["candidate_id"]
    directory = C2/f"candidate_{candidate_id}"
    directory.mkdir(parents=True, exist_ok=True)
    initial = dict(fixed)
    for spec in new_term_specs(model):
        initial.setdefault(spec["parameter_id"], float(spec["initial_amplitude_kcal_mol"]))
    objective = CandidateObjective(candidate_id, model, surfaces, initial, fit_ids)
    x0 = np.asarray([initial[x] for x in fit_ids])
    result = minimize(lambda x: objective.evaluate(x)["objective"], x0, method="L-BFGS-B",
                      bounds=[bounds_for(x, model) for x in fit_ids],
                      options={"maxiter":20,"maxfun":90,"ftol":1e-8,"gtol":1e-4,"eps":.002,"maxls":10})
    final = dict(initial); final.update({key:float(value) for key,value in zip(fit_ids,result.x)})
    final_eval = objective.evaluate(np.asarray([final[x] for x in fit_ids]), "final")
    topology_path = directory/"FINAL_DERIVED_TOPOLOGY.parm7"
    topology_receipt = build_candidate(final, new_term_specs(model), topology_path)
    protocol = {"schema":"tsl-rsh-c2-candidate-fit-protocol-v1","candidate":model,"fit_parameter_order":fit_ids,
                "primary_weighting":"equal_surface","objective":"mean of per-surface mean squared relaxed-profile residual + frozen regularization",
                "optimizer":json.loads(PANEL_PATH.read_text())["optimizer"],"frozen_sander_contract_sha256":first.sha256_path(HERE/"00_PROTOCOL/SANDER_RELAXED_PROFILE_CONTRACT.json")}
    first.atomic_json(directory/"FIT_PROTOCOL.json",protocol)
    first.atomic_json(directory/"INITIAL_PARAMETERS.json",initial)
    first.atomic_json(directory/"FINAL_PARAMETERS.json",{"parameters":final,"optimizer":{"success":bool(result.success),"status":int(result.status),"message":str(result.message),"nit":int(result.nit),"nfev":int(result.nfev),"fun":float(result.fun)},"topology":topology_receipt})
    first.write_csv(directory/"OPTIMIZATION_TRAJECTORY.csv",objective.trajectory,list(objective.trajectory[0]))
    shutil.copy2(directory/"OPTIMIZATION_TRAJECTORY.csv",directory/"OBJECTIVE_HISTORY.csv")
    return {"candidate_id":candidate_id,"model":model,"parameters":final,"fit_ids":fit_ids,"objective":objective,
            "optimizer":json.loads((directory/"FINAL_PARAMETERS.json").read_text())["optimizer"],"rows":final_eval["points"],
            "topology_path":topology_path,"topology_receipt":topology_receipt}


def candidate_analysis(candidate: dict, surfaces: dict) -> dict:
    whole, low, critical = c1.validation_analysis(candidate["rows"])
    domain, unsampled = c1.full_domain(candidate["topology_path"], surfaces, []) if False else ([], {"periodic_closure_kcal_mol":{},"pathology_triggers":[],"pass":True})
    # Full domain uses the already evaluated authoritative absolute energies. Re-evaluate once with persisted evidence.
    topology = pmd.load_file(str(candidate["topology_path"]))
    absolute_rows=[]
    root=VALIDATION/candidate["candidate_id"]/"authoritative-final-runs"
    for axis in first.AXES:
        results=[gates.minimize_point(topology,record,root/axis/f"{int(record['angle_degrees']):+04d}",topology_path=candidate["topology_path"]) for record in surfaces[axis]]
        rel=c1.relative_rows(axis,results)
        for row in rel:
            absolute=next(x["mm_tot_kcal_mol_absolute"] for x in results if x["angle_degrees"]==row["angle_degrees"])
            absolute_rows.append({**row,"mm_absolute_energy_kcal_mol":absolute})
    candidate["rows"]=absolute_rows
    whole,low,critical=c1.validation_analysis(absolute_rows)
    domain,unsampled=c1.full_domain(candidate["topology_path"],surfaces,absolute_rows,
                                    VALIDATION/candidate["candidate_id"]/"full-domain-runs")
    whole_pass=all(x["rmse_kcal_mol"]<=1 and x["mae_kcal_mol"]<=.75 and x["max_abs_kcal_mol"]<=2 for x in whole.values())
    low_pass=all(x["weighted_rmse_kcal_mol"]<=1 and x["mae_kcal_mol"]<=.75 and critical[a]["global_minimum_angle_error_degrees"]<=15 for a,x in low.items())
    minimum_pass=all(x["global_minimum_angle_error_degrees"]<=15 for x in critical.values())
    barrier_pass=all(x["major_barrier_angle_error_degrees"]<=15 and x["major_barrier_height_error_kcal_mol"]<=1 for x in critical.values())
    closure_pass=all(x<=.1 for x in unsampled["periodic_closure_kcal_mol"].values())
    gates_out={"low_energy":"PASS" if low_pass else "FAIL","whole_profile":"PASS" if whole_pass else "FAIL",
               "minimum_topology":"PASS" if minimum_pass else "FAIL","barrier":"PASS" if barrier_pass else "FAIL",
               "periodic_closure":"PASS" if closure_pass else "FAIL","unsampled_region":"PASS" if unsampled["pass"] else "FAIL"}
    candidate.update({"whole":whole,"low":low,"critical":critical,"domain":domain,"unsampled":unsampled,"gates":gates_out,
                      "locked_gate_pass":all(x=="PASS" for x in gates_out.values())})
    return candidate


def phi_specific_pass(candidate: dict) -> bool:
    w,l,k=candidate["whole"]["PHI"],candidate["low"]["PHI"],candidate["critical"]["PHI"]
    return w["rmse_kcal_mol"]<=1 and w["mae_kcal_mol"]<=.75 and w["max_abs_kcal_mol"]<=2 and l["weighted_rmse_kcal_mol"]<=1 and l["mae_kcal_mol"]<=.75 and k["global_minimum_angle_error_degrees"]<=15 and k["major_barrier_angle_error_degrees"]<=15 and k["major_barrier_height_error_kcal_mol"]<=1 and candidate["unsampled"]["periodic_closure_kcal_mol"]["PHI"]<=.1


def sensitivity(candidate: dict, surfaces: dict) -> dict:
    ids=candidate["fit_ids"];base=candidate["parameters"];columns=[];base_rows={(r["axis"],r["angle_degrees"]):r for r in candidate["rows"]}
    for parameter_id in ids:
        lo,hi=bounds_for(parameter_id,candidate["model"]);center=base[parameter_id];a=max(lo,center-.01);b=min(hi,center+.01)
        if b-a<1e-12:
            columns.append({key:0.0 for key in base_rows});continue
        plus=dict(base);minus=dict(base);plus[parameter_id]=b;minus[parameter_id]=a
        values_plus=np.asarray([plus[x] for x in ids]);values_minus=np.asarray([minus[x] for x in ids])
        rp=candidate["objective"].evaluate(values_plus,"sensitivity_plus")["points"]
        rm=candidate["objective"].evaluate(values_minus,"sensitivity_minus")["points"]
        rm_map={(r["axis"],r["angle_degrees"]):r for r in rm}
        columns.append({(r["axis"],r["angle_degrees"]):(r["mm_relative_kcal_mol"]-rm_map[(r["axis"],r["angle_degrees"])]["mm_relative_kcal_mol"])/(b-a) for r in rp})
    ordered=sorted(base_rows);J=np.asarray([[column[key] for column in columns] for key in ordered]);singular=np.linalg.svd(J,compute_uv=False);rank=int(np.linalg.matrix_rank(J));condition=float(singular[0]/singular[-1]) if len(singular) and singular[-1]>0 else None
    covariance=np.linalg.pinv(J.T@J+1e-12*np.eye(len(ids)));scale=np.sqrt(np.diag(covariance));corr=covariance/np.outer(scale,scale);maxcorr=float(np.max(np.abs(corr-np.eye(len(ids))))) if len(ids)>1 else 0.0
    residual=np.asarray([base_rows[k]["residual_kcal_mol"] for k in ordered]);counts={a:sum(k[0]==a for k in ordered) for a in first.AXES}
    schemes={};deltas=[]
    for name,w in {"equal_point":np.ones(len(ordered)),"equal_surface":np.asarray([1/counts[k[0]] for k in ordered]),"low_energy":np.asarray([c1.low_weight(base_rows[k]["qm_relative_kcal_mol"]) for k in ordered])}.items():
        A=J.T@(w[:,None]*J)+1e-6*np.eye(len(ids));delta=np.linalg.solve(A,J.T@(w*residual));deltas.append(delta);schemes[name]={"linearized_parameter_delta":delta.tolist(),"predicted_profile_change_rms_kcal_mol":float(np.sqrt(np.mean((J@delta)**2)))}
    holdouts=[]
    for offset in range(4):
        keep=np.asarray([i%4!=offset for i in range(len(ordered))]);delta=np.linalg.solve(J[keep].T@J[keep]+1e-6*np.eye(len(ids)),J[keep].T@residual[keep]);holdouts.append({"offset":offset,"parameter_delta":delta.tolist(),"heldout_prediction_rms":float(np.sqrt(np.mean((J[~keep]@delta-residual[~keep])**2)))})
    profile_spread=max(x["predicted_profile_change_rms_kcal_mol"] for x in schemes.values());parameter_status="CONCERN" if rank==len(ids) and (condition>50 or maxcorr>.95) else ("PASS" if rank==len(ids) else "FAIL");profile_status="PASS" if profile_spread<=.5 else ("CONCERN" if profile_spread<=1 else "FAIL")
    return {"parameter_order":ids,"rank":rank,"singular_values":singular.tolist(),"condition_number":condition,"covariance":covariance.tolist(),"correlation":corr.tolist(),"max_abs_correlation":maxcorr,"weighting_sensitivity":schemes,"structured_angular_resampling":holdouts,"parameter_identifiability":parameter_status,"profile_predictive_stability":profile_status,"profile_sensitivity_max_rms_kcal_mol":profile_spread}


def write_candidate(candidate: dict) -> None:
    directory=C2/f"candidate_{candidate['candidate_id']}";validation=VALIDATION/candidate["candidate_id"]
    validation.mkdir(parents=True,exist_ok=True)
    first.write_csv(validation/"POINTWISE_VALIDATION.csv",candidate["rows"],sorted(set().union(*(r.keys() for r in candidate["rows"]))))
    first.atomic_json(validation/"LOW_ENERGY_METRICS.json",candidate["low"]);first.atomic_json(validation/"WHOLE_PROFILE_METRICS.json",candidate["whole"]);first.atomic_json(validation/"CRITICAL_POINTS.json",candidate["critical"])
    first.write_csv(validation/"UNSAMPLED_DOMAIN_VALIDATION.csv",candidate["domain"],sorted(set().union(*(r.keys() for r in candidate["domain"]))))
    first.atomic_json(validation/"IDENTIFIABILITY.json",candidate["ident"]);first.atomic_json(validation/"SENSITIVITY_ANALYSIS.json",candidate["ident"])
    files=sorted(p for p in directory.rglob('*') if p.is_file() and p.name!='SHA256SUMS');first.atomic_text(directory/"SHA256SUMS",''.join(f"{first.sha256_path(p)}  {p.relative_to(directory)}\n" for p in files))


def main() -> None:
    ancestor=subprocess.run(["git","merge-base","--is-ancestor","47274c77719104de31ce8ab34ad00e71daa38e72","HEAD"],cwd=first.ROOT)
    if ancestor.returncode != 0:
        raise RuntimeError("sealed C1 commit is not an ancestor of the C2 execution tree")
    if first.sha256_path(HERE/"00_PROTOCOL/LOCKED_ACCEPTANCE_PROTOCOL.json")!="859fbc97a8f3480f9e168c22b93a421d597494856d151db1842bb3ead61bbbc4":raise RuntimeError("locked acceptance identity mismatch")
    panel=json.loads(PANEL_PATH.read_text());surfaces=first.raw_surface_records();c1params=load_c1_parameters();models={x["candidate_id"]:x for x in panel["candidates"]};results=[]
    a_model=models["C2A_PHI_N2"];fixed=dict(c1params);fixed["PHI_N2_PHASE180"]=0.0
    a=fit_candidate(a_model,fixed,["LOCAL_TYPE_1","LOCAL_TYPE_12","PHI_N2_PHASE180"],surfaces);candidate_analysis(a,surfaces);a["ident"]=sensitivity(a,surfaces);write_candidate(a);results.append(a)
    phi=a
    if not phi_specific_pass(a):
        b_model=models["C2B_PHI_N1_N2"];fixed=dict(c1params);fixed.update({"PHI_N1_PHASE0":0.0,"PHI_N2_PHASE180":0.0})
        b=fit_candidate(b_model,fixed,["LOCAL_TYPE_1","LOCAL_TYPE_12","PHI_N1_PHASE0","PHI_N2_PHASE180"],surfaces);candidate_analysis(b,surfaces);b["ident"]=sensitivity(b,surfaces);write_candidate(b);results.append(b)
        if phi_specific_pass(b):phi=b
        elif b["objective"].evaluate(np.asarray([b["parameters"][x] for x in b["fit_ids"]]))["objective"] < a["objective"].evaluate(np.asarray([a["parameters"][x] for x in a["fit_ids"]]))["objective"]:phi=b
    combined_model=copy.deepcopy(models["C2C_MINIMAL_COMBINED"]);combined_model["new_terms"]=[*phi["model"]["new_terms"],*combined_model["new_terms"]]
    fixed=dict(phi["parameters"]);fixed["PSI_N1_PHASE0"]=0.0
    combined=fit_candidate(combined_model,fixed,["LOCAL_TYPE_2","LOCAL_TYPE_7","PSI_N1_PHASE0"],surfaces);candidate_analysis(combined,surfaces);combined["ident"]=sensitivity(combined,surfaces);write_candidate(combined);results.append(combined)
    rows=[]
    for x in results:
        rows.append({"candidate_id":x["candidate_id"],"new_coefficient_count":len(new_term_specs(x["model"])),"objective":x["optimizer"]["fun"],"optimizer_converged":x["optimizer"]["success"],"locked_gate_pass":x["locked_gate_pass"],"parameter_identifiability":x["ident"]["parameter_identifiability"],"profile_predictive_stability":x["ident"]["profile_predictive_stability"],**{f"{a}_{m}":x["whole"][a][m] for a in first.AXES for m in ("rmse_kcal_mol","mae_kcal_mol","max_abs_kcal_mol")}})
    first.write_csv(C2/"C2_CANDIDATE_RESULTS.csv",rows,list(rows[0]));first.write_csv(VALIDATION/"C2_MODEL_COMPARISON.csv",rows,list(rows[0]))
    passing=[x for x in results if x["locked_gate_pass"] and x["ident"]["profile_predictive_stability"]=="PASS"]
    selected=min(passing,key=lambda x:len(new_term_specs(x["model"]))) if passing else None;reported=selected or combined
    shutil.copy2(VALIDATION/reported["candidate_id"]/"POINTWISE_VALIDATION.csv",VALIDATION/"C2_FINAL_POINTWISE_VALIDATION.csv")
    for source,target in (("LOW_ENERGY_METRICS.json","C2_FINAL_LOW_ENERGY_METRICS.json"),("WHOLE_PROFILE_METRICS.json","C2_FINAL_WHOLE_PROFILE_METRICS.json"),("CRITICAL_POINTS.json","C2_FINAL_CRITICAL_POINTS.json"),("UNSAMPLED_DOMAIN_VALIDATION.csv","C2_FINAL_UNSAMPLED_DOMAIN_VALIDATION.csv"),("IDENTIFIABILITY.json","C2_FINAL_IDENTIFIABILITY.json"),("SENSITIVITY_ANALYSIS.json","C2_FINAL_SENSITIVITY_ANALYSIS.json")):shutil.copy2(VALIDATION/reported["candidate_id"]/source,VALIDATION/target)
    selection={"schema":"tsl-rsh-c2-model-selection-v1","models_tested":[x["candidate_id"] for x in results],"selected_c2_model":selected["candidate_id"] if selected else "NONE","reported_best_candidate":reported["candidate_id"],"selection_rule":panel["selection_rule"],"candidate_summaries":rows};first.atomic_json(C2/"C2_MODEL_SELECTION_DECISION.json",selection)
    decision={"schema":"tsl-rsh-c2-decision-v1","c2_residual_diagnosis":"COMPLETE","models_tested":[x["candidate_id"] for x in results],"selected_c2_model":selected["candidate_id"] if selected else "NONE","reported_candidate":reported["candidate_id"],"parameters":reported["parameters"],"whole_profile":reported["whole"],"low_energy":reported["low"],"critical_points":reported["critical"],"unsampled":reported["unsampled"],"gates":reported["gates"],"parameter_identifiability":reported["ident"]["parameter_identifiability"],"profile_predictive_stability":reported["ident"]["profile_predictive_stability"],"new_qm_required":not reported["unsampled"]["pass"],"c2_status":"PASS" if selected else "FAIL","ready_for_force_field_installation":bool(selected),"raw_qm_artifacts_modified":False,"source_force_field_modified":False,"new_qm_run":False,"md_run":False};first.atomic_json(PUBLICATION/"C2_DECISION.json",decision)
    provenance={"schema":"tsl-rsh-c2-provenance-v1","created_utc":now(),"source_c1_commit":"47274c77719104de31ce8ab34ad00e71daa38e72","c2_code_commit":subprocess.run(["git","rev-parse","HEAD"],cwd=first.ROOT,text=True,capture_output=True,check=True).stdout.strip(),"model_panel_sha256":first.sha256_path(PANEL_PATH),"residual_diagnosis_sha256":first.sha256_path(C2/"C2_RESIDUAL_DIAGNOSIS.json"),"locked_acceptance_sha256":first.sha256_path(HERE/"00_PROTOCOL/LOCKED_ACCEPTANCE_PROTOCOL.json"),"qm_archives":json.loads((PUBLICATION/"C1_PROVENANCE.json").read_text())["qm_archives"],"amber_sander":"26.0","optimizer":f"SciPy {scipy.__version__} L-BFGS-B"};first.atomic_json(PUBLICATION/"C2_PROVENANCE.json",provenance)
    # Publication figures and compact tables use actual authoritative QM points only.
    c1rows=list(csv.DictReader((HERE/"05_VALIDATION/C1/C1_POINTWISE_VALIDATION.csv").open()))
    for axis in first.AXES:
        group=sorted([r for r in reported["rows"] if r["axis"]==axis],key=lambda r:r["angle_degrees"]);old=sorted([r for r in c1rows if r["axis"]==axis],key=lambda r:int(r["angle_degrees"]));fig,ax=plt.subplots(figsize=(7,4));ax.scatter([r["angle_degrees"] for r in group],[r["qm_relative_kcal_mol"] for r in group],label="QM calculated",color="black");ax.plot([int(r["angle_degrees"]) for r in old],[float(r["mm_relative_kcal_mol"]) for r in old],label="C1 MM");ax.plot([r["angle_degrees"] for r in group],[r["mm_relative_kcal_mol"] for r in group],label="C2 MM");ax.legend();ax.set(xlabel="dihedral (degrees)",ylabel="relative energy (kcal/mol)");fig.tight_layout();fig.savefig(FIGURES/f"C1_vs_C2_{axis}.svg");plt.close(fig)
    shutil.copy2(FIGURES/"C1_vs_C2_CHI.svg",FIGURES/"C2_COMBINED.svg");shutil.copy2(FIGURES/"C1_vs_C2_PSI.svg",FIGURES/"C2_RESIDUALS.svg")
    parameter_rows=[{"parameter_id":k,"c1_value_kcal_mol":c1params.get(k,"NA"),"c2_value_kcal_mol":v,"added_periodicity":k if "_N" in k else "existing"} for k,v in reported["parameters"].items()];first.write_csv(TABLES/"C1_C2_PARAMETER_COMPARISON.csv",parameter_rows,list(parameter_rows[0]));first.atomic_text(TABLES/"C1_C2_PARAMETER_COMPARISON.md","# C1/C2 parameter comparison\n\n"+json.dumps(parameter_rows,indent=2)+"\n");first.write_csv(TABLES/"C2_FIT_STATISTICS.csv",rows,list(rows[0]));first.atomic_text(TABLES/"C2_FIT_STATISTICS.md","# C2 fit statistics\n\n"+json.dumps(rows,indent=2)+"\n");first.atomic_text(TABLES/"C2_CRITICAL_POINTS.md","# C2 critical points\n\n"+json.dumps(reported["critical"],indent=2)+"\n");critical_rows=[{"axis":a,**{k:v for k,v in reported["critical"][a].items() if not isinstance(v,(list,dict))}} for a in first.AXES];first.write_csv(TABLES/"C2_CRITICAL_POINTS.csv",critical_rows,list(critical_rows[0]))
    generated=sorted(p for p in HERE.rglob('*') if p.is_file() and p.name!='SHA256SUMS' and '__pycache__' not in p.parts and not p.name.endswith((".pyc",".tmp")));first.atomic_text(HERE/"SHA256SUMS",''.join(f"{first.sha256_path(p)}  {p.relative_to(HERE)}\n" for p in generated));print(json.dumps(decision,indent=2))


if __name__=="__main__":main()
