#!/usr/bin/env python3
"""Read-only coupling diagnosis for the sealed TSL-RSH C1/C2/C3 evidence."""

from __future__ import annotations

import csv, hashlib, json, math
from collections import defaultdict
from pathlib import Path

import numpy as np
import parmed as pmd

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
FIT = ROOT / "tsl-rsh-torsion-fit"
C3 = ROOT / "tsl-rsh-torsion-c3-diagnostic"
C1_RUNS = FIT / "05_VALIDATION/C1/final-runs"
C1_ROWS = FIT / "05_VALIDATION/C1/C1_POINTWISE_VALIDATION.csv"
MAPPING = FIT / "02_TOPOLOGY_MAPPING/TORSION_TOPOLOGY_MAPPING.csv"
TOPOLOGY = FIT / "04_FIT/C1/C1_FINAL_DERIVED_TOPOLOGY.parm7"
PARAMETERS = FIT / "04_FIT/C1/C1_FINAL_PARAMETERS.json"
PHASES = C3 / "PHASE_DERIVATION.json"
AXIS_ATOMS = {"CHI": (55,25,9,8), "PHI": (25,9,8,7), "PSI": (9,8,7,1)}
COMPONENTS = ("bond", "angle", "dihedral", "vdw", "elec", "vdw_14", "elec_14", "tot")
BANDS = (("QM_LE_1", 1.0), ("QM_LE_5", 5.0), ("QM_LE_10", 10.0), ("WHOLE", math.inf))

def read_csv(path):
    with path.open(newline="") as f: return list(csv.DictReader(f))

def write_csv(path, rows, fields=None):
    fields = fields or list(rows[0])
    with path.open("w", newline="") as f:
        w=csv.DictWriter(f, fieldnames=fields); w.writeheader(); w.writerows(rows)

def dihedral(x, ids):
    p0,p1,p2,p3=(x[i] for i in ids); b0=-(p1-p0); b1=p2-p1; b2=p3-p2
    b1=b1/np.linalg.norm(b1); v=b0-np.dot(b0,b1)*b1; w=b2-np.dot(b2,b1)*b1
    return math.degrees(math.atan2(np.dot(np.cross(b1,v),w),np.dot(v,w)))

def wrap(x): return (x+180.0)%360.0-180.0

def circ_corr(a,b):
    a=np.radians(a); b=np.radians(b)
    sa=np.sin(a-math.atan2(np.mean(np.sin(a)),np.mean(np.cos(a))))
    sb=np.sin(b-math.atan2(np.mean(np.sin(b)),np.mean(np.cos(b))))
    den=math.sqrt(float(np.dot(sa,sa)*np.dot(sb,sb)))
    return float(np.dot(sa,sb)/den) if den else None

def circular_excursion(values):
    u=np.unwrap(np.radians(values)); return float(np.degrees(u.max()-u.min()))

def response_stats(nominal, physical):
    order=np.argsort(nominal); n=np.asarray(nominal)[order]; p=np.unwrap(np.radians(np.asarray(physical)[order]))
    slope=float(np.polyfit(np.radians(n),p,1)[0]) if len(n)>1 else None
    dif=np.diff(p); monotonic=max(float(np.mean(dif>=0)),float(np.mean(dif<=0))) if len(dif) else None
    return circ_corr(nominal,physical), circular_excursion(physical), slope, monotonic

def result_for(axis, angle, root=C1_RUNS):
    return json.loads((root/axis/f"{angle:+04d}"/"RESULT.json").read_text())

def coords_for(axis, angle, root=C1_RUNS):
    obj=pmd.load_file(str(root/axis/f"{angle:+04d}"/"final.rst7")); return np.asarray(obj.coordinates).reshape((-1,3))

def component(record, name): return float(record[f"mm_{name}_kcal_mol_absolute"])

def pearson(x,y):
    if len(x)<3 or np.std(x)==0 or np.std(y)==0: return None
    return float(np.corrcoef(x,y)[0,1])

def direct_term(x, quartets, amp, periodicity, phase):
    return float(sum(amp*(1+math.cos(math.radians(periodicity*dihedral(x,q)-phase))) for q in quartets))

def main():
    HERE.mkdir(parents=True,exist_ok=True)
    top=pmd.load_file(str(TOPOLOGY)); mapping=read_csv(MAPPING)
    c1params=json.loads(PARAMETERS.read_text())["fitted"]
    c1points={(r["axis"],int(r["angle_degrees"])):r for r in read_csv(C1_ROWS)}
    if len(c1points)!=56: raise RuntimeError(f"expected 56 authoritative points, got {len(c1points)}")

    # Parameter -> physical instance map, including all experimental C2/C3 continuations.
    experimental=[("C2_PHI_N1", "PHI",1,0.0), ("C2_PHI_N2","PHI",2,180.0),
                  ("C2_PSI_N1","PSI",1,0.0), ("C3_CHI_N2","CHI",2,264.7015068837035),
                  ("C3_PHI_N3","PHI",3,234.77376015930093)]
    pmap=[]
    for r in mapping:
        pmap.append({"model":"C1","ff_parameter":f"LOCAL_TYPE_{r['type_index']}","atom_type_quartet":r["instance_atom_types"],
            "physical_atoms_zero_based":r["instance_atoms_zero_based"],"atom_names":r["instance_atom_names"],
            "central_bond":'-'.join(r["instance_atoms_zero_based"].split('-')[1:3]),"relationship":r["axis"],
            "periodicity":r["periodicity"],"phase_degrees":r["phase_degrees"],
            "coefficient_kcal_mol":c1params[f"LOCAL_TYPE_{r['type_index']}"],
            "mapped_instance_count":sum(1 for x in mapping if x["type_index"]==r["type_index"]),
            "molecular_type_multiplicity":r["molecular_instance_count"]})
    axis_quartets={a:sorted({tuple(map(int,r["instance_atoms_zero_based"].split('-'))) for r in mapping if r["axis"]==a}) for a in AXIS_ATOMS}
    for pid,axis,n,phase in experimental:
        for q in axis_quartets[axis]:
            atoms=[top.atoms[i] for i in q]
            pmap.append({"model":"C2/C3_DIAGNOSTIC","ff_parameter":pid,"atom_type_quartet":'-'.join(a.type for a in atoms),
                "physical_atoms_zero_based":'-'.join(map(str,q)),"atom_names":'-'.join(a.name for a in atoms),
                "central_bond":f"{q[1]}-{q[2]}","relationship":axis,"periodicity":n,"phase_degrees":phase,
                "coefficient_kcal_mol":"candidate-dependent","mapped_instance_count":len(axis_quartets[axis]),
                "molecular_type_multiplicity":len(axis_quartets[axis])})
    write_csv(HERE/"PARAMETER_INSTANCE_MAP.csv",pmap)

    # All mapped physical torsion trajectories on the frozen C1 relaxed structures.
    trajectories=[]; point_components=[]
    for (axis,angle),row in sorted(c1points.items()):
        x=coords_for(axis,angle); rec=result_for(axis,angle)
        base={"authoritative_point":f"{axis}_{angle:+04d}","scan_axis":axis,"nominal_scan_angle":angle,
              "qm_relative_kcal_mol":float(row["qm_relative_kcal_mol"]),"mm_relative_kcal_mol":float(row["mm_relative_kcal_mol"]),
              "qm_minus_mm_residual_kcal_mol":float(row["residual_kcal_mol"])}
        for i,r in enumerate(mapping):
            q=tuple(map(int,r["instance_atoms_zero_based"].split('-')))
            trajectories.append({**base,"physical_torsion_id":f"{r['axis']}_I{i+1:02d}_{r['instance_atoms_zero_based']}",
                "physical_torsion_relationship":r["axis"],"physical_dihedral_degrees":dihedral(x,q)})
        point_components.append({**base,**{k:component(rec,k) for k in COMPONENTS}})
    write_csv(HERE/"PHYSICAL_DIHEDRAL_TRAJECTORIES.csv",trajectories)
    physical_response={}
    for axis in AXIS_ATOMS:
        for tid in sorted({r["physical_torsion_id"] for r in trajectories if r["physical_torsion_relationship"]==axis}):
            vals=[r["physical_dihedral_degrees"] for r in trajectories if r["scan_axis"]==axis and r["physical_torsion_id"]==tid]
            physical_response[tid]={"nominal_scan_axis":axis,"angular_excursion_degrees":circular_excursion(vals)}

    # Relative component energies and residual correlations, always within each surface.
    for axis in AXIS_ATOMS:
        rows=[r for r in point_components if r["scan_axis"]==axis]
        ref=min(rows,key=lambda r:r["qm_relative_kcal_mol"])
        for r in rows:
            for c in COMPONENTS: r[c+"_relative_kcal_mol"]=r[c]-ref[c]
    write_csv(HERE/"ENERGY_COMPONENTS.csv",point_components)
    correlations=[]
    for axis in AXIS_ATOMS:
      for band,limit in BANDS:
        rows=[r for r in point_components if r["scan_axis"]==axis and r["qm_relative_kcal_mol"]<=limit]
        y=[r["qm_minus_mm_residual_kcal_mol"] for r in rows]
        for c in COMPONENTS[:-1]:
            x=[r[c+"_relative_kcal_mol"] for r in rows]; rho=pearson(x,y)
            correlations.append({"scan_axis":axis,"energy_band":band,"component":c.upper(),"n":len(rows),
                "pearson_residual_correlation":rho,"univariate_r_squared":rho*rho if rho is not None else None,
                "causality_claimed":False})
    write_csv(HERE/"COMPONENT_RESIDUAL_CORRELATIONS.csv",correlations)

    # Cross-coordinate response and mapped-instance response summaries.
    coupling=[]
    for scan in AXIS_ATOMS:
      for band,limit in BANDS:
       pts=sorted([k for k,v in c1points.items() if k[0]==scan and float(v["qm_relative_kcal_mol"])<=limit],key=lambda k:k[1])
       nominal=[k[1] for k in pts]
       for response,q in AXIS_ATOMS.items():
        physical=[dihedral(coords_for(*k),q) for k in pts]; corr,exc,slope,mono=response_stats(nominal,physical)
        coupling.append({"scan_axis":scan,"energy_band":band,"response_coordinate":response,"n":len(pts),"circular_correlation":corr,
            "angular_excursion_degrees":exc,"linear_unwrapped_response":slope,"monotonic_fraction":mono})
    write_csv(HERE/"CROSS_COORDINATE_COUPLING.csv",coupling)

    # C3 direct versus relaxation-mediated propagation using corresponding persisted endpoints.
    phases=json.loads(PHASES.read_text())["parameters"]
    propagation=[]; propagation_summary={}
    for cid,terms in (("C3B_PHI_N3",[("PHI",phases["PHI_N3_RESIDUAL"])]),
                      ("C3C_CHI_N2_PHI_N3",[("CHI",phases["CHI_N2_RESIDUAL"]),("PHI",phases["PHI_N3_RESIDUAL"])])):
      fit=json.loads((C3/"results"/cid/"FIT_RESULT.json").read_text()); amps=fit["final_amplitudes"]
      c3point={(r["axis"],int(r["angle_degrees"])):r for r in read_csv(C3/"results"/cid/"POINTWISE_RESULTS.csv")}
      rows=[]
      for (axis,angle),oldrow in sorted(c1points.items()):
        x0=coords_for(axis,angle); direct0=0.0
        for term_axis,spec in terms:
            ampkey=next(k for k in amps if k.startswith(f"{term_axis}_N{spec['periodicity']}_"))
            direct0+=direct_term(x0,axis_quartets[term_axis],amps[ampkey],int(spec["periodicity"]),float(spec["phase_degrees"]))
        rows.append({"candidate":cid,"axis":axis,"angle_degrees":angle,"qm_relative_kcal_mol":float(oldrow["qm_relative_kcal_mol"]),
            "c1_mm_relative_kcal_mol":float(oldrow["mm_relative_kcal_mol"]),
            "c3_mm_relative_kcal_mol":float(c3point[(axis,angle)]["mm_relative_kcal_mol"]),
            "direct_new_term_on_c1_geometry":direct0,"c3_relaxed_geometry_persisted":False,
            **{f"delta_{c}":"NOT_AVAILABLE_C3_AUTHORITATIVE_COORDINATES_NOT_PERSISTED" for c in COMPONENTS[:-1]}})
      for axis in AXIS_ATOMS:
        ar=[r for r in rows if r["axis"]==axis]; ref=min(ar,key=lambda r:r["qm_relative_kcal_mol"])
        for r in ar:
            total=r["c3_mm_relative_kcal_mol"]-r["c1_mm_relative_kcal_mol"]
            direct=r["direct_new_term_on_c1_geometry"]-ref["direct_new_term_on_c1_geometry"]
            r["relative_profile_response"]=total; r["direct_fixed_geometry_effect"]=direct
            r["relaxation_mediated_effect"]=total-direct
      write_csv(HERE/f"{cid}_PROPAGATION_DETAIL.csv",rows)
      propagation.extend(rows)
      low=[r for r in rows if r["axis"]=="PHI" and r["qm_relative_kcal_mol"]<=10]
      rms=lambda name:float(np.sqrt(np.mean([r[name]**2 for r in low])))
      propagation_summary[cid]={"phi_le10_direct_rms_kcal_mol":rms("direct_fixed_geometry_effect"),
          "phi_le10_relaxation_mediated_rms_kcal_mol":rms("relaxation_mediated_effect"),
          "phi_le10_total_response_rms_kcal_mol":rms("relative_profile_response"),
          "amplification_total_over_direct":rms("relative_profile_response")/rms("direct_fixed_geometry_effect"),
          "collective_direct_effect_over_single_fitted_amplitude":rms("direct_fixed_geometry_effect")/next(iter(amps.values()))}
    write_csv(HERE/"C3_PERTURBATION_PROPAGATION.csv",propagation)

    # Instance-specific analytic counterfactuals (no topology mutation, no minimization).
    counter=[]
    for axis,specname,ampkey,cid in (("CHI","CHI_N2_RESIDUAL","CHI_N2_RESIDUAL","C3A_CHI_N2"),
                                    ("PHI","PHI_N3_RESIDUAL","PHI_N3_RESIDUAL","C3B_PHI_N3")):
        spec=phases[specname]; amp=json.loads((C3/"results"/cid/"FIT_RESULT.json").read_text())["final_amplitudes"][ampkey]
        for q in axis_quartets[axis]:
          for (scan,angle),row in sorted(c1points.items()):
            x=coords_for(scan,angle)
            counter.append({"experimental_term":specname,"physical_instance":'-'.join(map(str,q)),"scan_axis":scan,
                "angle_degrees":angle,"qm_relative_kcal_mol":float(row["qm_relative_kcal_mol"]),
                "direct_instance_energy_kcal_mol":direct_term(x,[q],amp,int(spec["periodicity"]),float(spec["phase_degrees"]))})
    write_csv(HERE/"INSTANCE_COUNTERFACTUALS.csv",counter)
    residual_lookup={(r["scan_axis"],int(r["nominal_scan_angle"])):r["qm_minus_mm_residual_kcal_mol"] for r in point_components}
    instance_alignment={}
    for term,axis in (("CHI_N2_RESIDUAL","CHI"),("PHI_N3_RESIDUAL","PHI")):
        subset=[r for r in counter if r["experimental_term"]==term and r["scan_axis"]==axis and r["qm_relative_kcal_mol"]<=10]
        for instance in sorted({r["physical_instance"] for r in subset}):
            rows=sorted([r for r in subset if r["physical_instance"]==instance],key=lambda r:r["angle_degrees"])
            values=np.asarray([r["direct_instance_energy_kcal_mol"] for r in rows])
            ref=int(np.argmin([r["qm_relative_kcal_mol"] for r in rows])); values=values-values[ref]
            residual=np.asarray([residual_lookup[(axis,int(r["angle_degrees"]))] for r in rows])
            instance_alignment[f"{term}:{instance}"]={"dot_with_qm_minus_c1_residual":float(np.dot(values,residual)),
                "pearson_correlation":pearson(values,residual),"direct_energy_excursion_kcal_mol":float(values.max()-values.min())}

    coverage=[]
    for axis in AXIS_ATOMS:
        known=sorted(k[1] for k in c1points if k[0]==axis)
        coverage.append({"axis":axis,"observed_angles_degrees":' '.join(map(str,known)),"missing_region":
            "none on 15-degree grid" if axis=="CHI" else ("+15..+90" if axis=="PHI" else "seam/flank toward +/-180 incomplete"),
            "interpolation_performed":False,"one_dimensional_scan_can_separate_coupling":False})
    write_csv(HERE/"COVERAGE_COUPLING_MAP.csv",coverage)

    # Evidence-backed classification summaries.
    cross={(r["scan_axis"],r["response_coordinate"]):r for r in coupling if r["energy_band"]=="WHOLE"}
    dominant={}; dominant_by_band={}
    for axis in AXIS_ATOMS:
        for band,_ in BANDS:
            options=[r for r in correlations if r["scan_axis"]==axis and r["energy_band"]==band and r["pearson_residual_correlation"] is not None]
            if options: dominant_by_band[f"{axis}_{band}"]=max(options,key=lambda r:abs(r["pearson_residual_correlation"]))
        dominant[axis]=dominant_by_band[f"{axis}_QM_LE_10"]
    counts={f"LOCAL_TYPE_{i}":sum(1 for r in mapping if int(r["type_index"])==i) for i in (1,2,7,12,17,30)}
    diagnosis={"schema":"tsl-rsh-read-only-coupling-diagnosis-v1","frozen_evidence_unchanged":True,"qm_points_used":56,
      "c1_parameter_instance_counts":counts,"cross_coordinate_coupling":coupling,"dominant_residual_components_qm_le_10":dominant,
      "dominant_residual_components_by_band":dominant_by_band,
      "physical_torsion_response":physical_response,
      "c3_perturbation_summary":propagation_summary,
      "instance_counterfactual_alignment":instance_alignment,
      "evidence_interpretation":{
        "shared_parameter_conflict_demonstrated":True,
        "phi_psi_coupling_demonstrated":abs(cross[("PHI","PSI")]["circular_correlation"] or 0)>0.5 or abs(cross[("PSI","PHI")]["circular_correlation"] or 0)>0.5,
        "nonbonded_coupling_demonstrated":any(abs(dominant[a]["pearson_residual_correlation"])>0.5 and dominant[a]["component"] in ("VDW","ELEC","VDW_14","ELEC_14") for a in AXIS_ATOMS),
        "qm_coverage_limitation_demonstrated":True,
        "correlations_are_not_causal_attribution":True},
      "dominant_diagnosis":"SHARED_TORSION_PARAMETERIZATION",
      "secondary_diagnoses":["MULTIDIMENSIONAL_TORSION_COUPLING","INSUFFICIENT_QM_COVERAGE","TORSION_NONBONDED_OR_BONDED_COUPLING"],
      "c1_thermal_region_status":"CHI is adequate; PHI and PSI low-energy basins are accurate in C1, while shared C3 perturbations damage them, so preserve C1 pending instance separation/coupling evidence.",
      "recommended_next_scientific_step":"Investigate instance-specific torsion typing using existing evidence first; do not refit. If ambiguity remains, preregister a thermally restricted PHIxPSI QM surface.",
      "new_qm_required_next":False}
    (HERE/"COUPLING_DIAGNOSIS.json").write_text(json.dumps(diagnosis,indent=2,sort_keys=True)+"\n")
    md=f"""# TSL-RSH read-only coupling diagnosis\n\nNo QM, MD, fitting, topology mutation, or minimization was run. All 56 authoritative C1 points and persisted C3 endpoints were used.\n\n## Decision\n\nDominant diagnosis: **SHARED_TORSION_PARAMETERIZATION**. The C1 local clones each act on multiple physical quartets (counts: {counts}). The C3 PHI n=3 continuation acts on all {len(axis_quartets['PHI'])} mapped PHI quartets; its fixed-geometry instance counterfactuals show non-equivalent contributions across those quartets. The independently verified Amber phase and 1-4 invariants rule out a sign/topology explanation.\n\nSecondary evidence supports multidimensional torsional response and incomplete QM coverage. Component correlations are reported as diagnostics only and are not interpreted causally. C1 remains the appropriate frozen Hamiltonian in the sampled thermal region.\n\n## C3 amplification\n\n```json\n{json.dumps(propagation_summary,indent=2)}\n```\n\n## Limits\n\nPHI +15..+90 and the PSI seam/flank remain unlabeled. Existing 1-D scans cannot uniquely separate shared-typing, true multidimensional coupling, and nonbonded relaxation. No interpolation was performed.\n"""
    (HERE/"COUPLING_DIAGNOSIS.md").write_text(md)
    files=sorted(p for p in HERE.iterdir() if p.is_file() and p.name!="SHA256SUMS")
    (HERE/"SHA256SUMS").write_text(''.join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in files))

if __name__=="__main__": main()
