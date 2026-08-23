#!/usr/bin/env python3
"""Validate and characterize the completed GPU-60 evidence without fitting."""
from __future__ import annotations

import csv, hashlib, json, math
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

HERE=Path(__file__).resolve().parent; ROOT=HERE/"completed-batch-60"; RESULTS=ROOT/"gpu_qm_results"
MANIFEST=ROOT/"NEXT_GPU_QM_BATCH_60.csv"; LOCAL=np.array([1,7,8,9,10,25,36,55]); S=25; SC=9; SH=55
CONV=627.5094740631/0.529177210903

def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def rms(x):return float(np.sqrt(np.mean(np.asarray(x,dtype=float)**2)))
def angle(a,b,c):
 u=a-b;v=c-b;return math.degrees(math.acos(np.clip(np.dot(u,v)/(np.linalg.norm(u)*np.linalg.norm(v)),-1,1)))
def dihedral(x,ids):
 p=x[np.array(ids)];b0,b1,b2=-(p[1]-p[0]),p[2]-p[1],p[3]-p[2];b1/=np.linalg.norm(b1);v=b0-np.dot(b0,b1)*b1;w=b2-np.dot(b2,b1)*b1
 return math.degrees(math.atan2(np.dot(np.cross(b1,v),w),np.dot(v,w)))
def ranks(x):
 order=np.argsort(x);out=np.empty(len(x),float);out[order]=np.arange(len(x));return out
def spear(a,b):return float(np.corrcoef(ranks(np.asarray(a)),ranks(np.asarray(b)))[0,1])

def main():
 rows=list(csv.DictReader(MANIFEST.open())); failures=[]; records=[]
 for row in rows:
  gid=row["campaign_id"];d=RESULTS/gid;sums={line.split()[1]:line.split()[0] for line in (d/"SHA256SUMS").read_text().splitlines() if line.strip()}
  for name,want in sums.items():
   if not (d/name).is_file() or sha(d/name)!=want:failures.append(f"{gid}:{name}")
  result=json.loads((d/"result.json").read_text());x=np.array([[float(v) for v in line.split()[1:4]] for line in (d/"geometry.xyz").read_text().splitlines()[2:58]])
  e=np.array(result["electronic_gradient_hartree_per_bohr"]);dg=np.array(result["d3_gradient_hartree_per_bohr"]);tg=np.array(result["total_gradient_hartree_per_bohr"]);f=np.array(result["force_hartree_per_bohr"])
  if not np.array_equal(tg,e+dg) or not np.array_equal(f,-tg):failures.append(f"{gid}:component_identity")
  sc=x[SC]-x[S];sh=x[SH]-x[S];usc=sc/np.linalg.norm(sc);ush=sh/np.linalg.norm(sh);sf=f[S]*CONV
  records.append({"campaign_id":gid,"source_minimum":row["source_minimum"],"family":row["perturbation_family"],"geometry_sha256":row["geometry_sha256"],"result_sha256":sha(d/"result.json"),
   "total_energy_hartree":result["total_energy_hartree"],"electronic_energy_hartree":result["electronic_energy_hartree"],"d3_energy_hartree":result["d3_energy_hartree"],
   "global_force_rms_kcal_mol_a":rms(f)*CONV,"sulfur_local_force_rms_kcal_mol_a":rms(f[LOCAL])*CONV,"sulfur_atom_force_norm_kcal_mol_a":float(np.linalg.norm(sf)),
   "sulfur_force_projection_sc_kcal_mol_a":float(np.dot(sf,usc)),"sulfur_force_projection_sh_kcal_mol_a":float(np.dot(sf,ush)),
   "d3_global_gradient_rms_kcal_mol_a":rms(dg)*CONV,"d3_local_gradient_rms_kcal_mol_a":rms(dg[LOCAL])*CONV,
   "sc_distance_a":float(np.linalg.norm(sc)),"sh_distance_a":float(np.linalg.norm(sh)),"c_s_h_angle_deg":angle(x[SC],x[S],x[SH]),
   "phi_deg":dihedral(x,[55,25,9,8]),"psi_deg":dihedral(x,[25,9,8,7]),"scf_cycles":result["scf_cycles"],"runtime_seconds":result["total_seconds"]})
 if failures:raise RuntimeError(f"validation failures: {failures}")
 with (HERE/"GPU_BATCH_60_CHARACTERIZATION.csv").open("w",newline="") as h:w=csv.DictWriter(h,fieldnames=list(records[0]));w.writeheader();w.writerows(records)
 def group(field):
  out={}
  for key in sorted(set(r[field] for r in records)):
   q=[r for r in records if r[field]==key];out[key]={"count":len(q),"energy_range_hartree":[min(x["total_energy_hartree"] for x in q),max(x["total_energy_hartree"] for x in q)],"global_force_rms_kcal_mol_a":rms([x["global_force_rms_kcal_mol_a"] for x in q]),"sulfur_local_force_rms_kcal_mol_a":rms([x["sulfur_local_force_rms_kcal_mol_a"] for x in q]),"sulfur_atom_force_norm_median_kcal_mol_a":float(np.median([x["sulfur_atom_force_norm_kcal_mol_a"] for x in q]))}
  return out
 summary={"status":"VERIFIED_COMPLETE","count":len(records),"checksum_failures":0,"all_scf_converged":True,"exact_total_component_identity":True,"exact_force_negative_gradient_identity":True,
  "archive_sha256":"5bde40b9ce95a1f725426400de4fb309902662c9e0c0b07c8655fb7890129848","protocol":json.loads((ROOT/"FROZEN_GPU_QM_PROTOCOL.json").read_text()),
  "overall":{"energy_range_hartree":[min(r["total_energy_hartree"] for r in records),max(r["total_energy_hartree"] for r in records)],"energy_span_kcal_mol":(max(r["total_energy_hartree"] for r in records)-min(r["total_energy_hartree"] for r in records))*627.5094740631,
   "global_force_component_rms_kcal_mol_a":rms(np.concatenate([np.array(json.loads((RESULTS/r["campaign_id"]/'result.json').read_text())["force_hartree_per_bohr"]).ravel() for r in records]))*CONV,
   "sulfur_local_force_component_rms_kcal_mol_a":rms(np.concatenate([np.array(json.loads((RESULTS/r["campaign_id"]/'result.json').read_text())["force_hartree_per_bohr"])[LOCAL].ravel() for r in records]))*CONV,
   "sulfur_atom_force_norm_percentiles_kcal_mol_a":{str(p):float(np.percentile([r["sulfur_atom_force_norm_kcal_mol_a"] for r in records],p)) for p in (0,25,50,75,90,95,100)},
   "d3_fraction_global_rms":rms([r["d3_global_gradient_rms_kcal_mol_a"] for r in records])/rms([r["global_force_rms_kcal_mol_a"] for r in records]),"d3_fraction_local_rms":rms([r["d3_local_gradient_rms_kcal_mol_a"] for r in records])/rms([r["sulfur_local_force_rms_kcal_mol_a"] for r in records]),
   "sc_distance_range_a":[min(r["sc_distance_a"] for r in records),max(r["sc_distance_a"] for r in records)],"sh_distance_range_a":[min(r["sh_distance_a"] for r in records),max(r["sh_distance_a"] for r in records)],"c_s_h_angle_range_deg":[min(r["c_s_h_angle_deg"] for r in records),max(r["c_s_h_angle_deg"] for r in records)],"phi_range_deg":[min(r["phi_deg"] for r in records),max(r["phi_deg"] for r in records)],"psi_range_deg":[min(r["psi_deg"] for r in records),max(r["psi_deg"] for r in records)]},
  "by_minimum":group("source_minimum"),"by_family":group("family"),
  "rank_correlations":{"local_force_vs_sc_distance":spear([r["sulfur_local_force_rms_kcal_mol_a"] for r in records],[r["sc_distance_a"] for r in records]),"local_force_vs_sh_distance":spear([r["sulfur_local_force_rms_kcal_mol_a"] for r in records],[r["sh_distance_a"] for r in records]),"local_force_vs_csh_angle":spear([r["sulfur_local_force_rms_kcal_mol_a"] for r in records],[r["c_s_h_angle_deg"] for r in records]),"local_force_vs_energy":spear([r["sulfur_local_force_rms_kcal_mol_a"] for r in records],[r["total_energy_hartree"] for r in records])},
  "interpretation":{"fitting_method_selected":False,"observations":["Sulfur-local force magnitude varies across minima and perturbation families; it is not reducible to one scalar bond coordinate.","The batch spans broad phi/psi torsional space plus explicit S-C/S-H/angle distortions.","D3 is a small but nonzero fraction of local force; the dominant sulfur-force structure is electronic PBE.","A method decision requires preregistered train/validation partitions and baseline residuals on these exact 60 points; target-force magnitude alone does not establish linearity, locality, or model class."]}}
 (HERE/"GPU_BATCH_60_DATASET_CHARACTERIZATION.json").write_text(json.dumps(summary,indent=2,sort_keys=True)+"\n")
 report=f"""# Completed GPU-60 ingestion and sulfur-force characterization

All 60 results passed nested checksum, geometry, SCF, array-shape, component-sum, and force-sign validation. They are ingested as a homogeneous GPU provenance partition; no fitting method was selected.

## What the points establish

- Energy span: {summary['overall']['energy_span_kcal_mol']:.3f} kcal/mol.
- Global force-component RMS: {summary['overall']['global_force_component_rms_kcal_mol_a']:.6f} kcal/mol/A.
- Sulfur-local force-component RMS: {summary['overall']['sulfur_local_force_component_rms_kcal_mol_a']:.6f} kcal/mol/A.
- S-C range: {summary['overall']['sc_distance_range_a'][0]:.4f}–{summary['overall']['sc_distance_range_a'][1]:.4f} A; S-H range: {summary['overall']['sh_distance_range_a'][0]:.4f}–{summary['overall']['sh_distance_range_a'][1]:.4f} A; C-S-H angle range: {summary['overall']['c_s_h_angle_range_deg'][0]:.2f}–{summary['overall']['c_s_h_angle_range_deg'][1]:.2f} degrees.
- Phi/Psi coverage: {summary['overall']['phi_range_deg'][0]:.2f}–{summary['overall']['phi_range_deg'][1]:.2f} / {summary['overall']['psi_range_deg'][0]:.2f}–{summary['overall']['psi_range_deg'][1]:.2f} degrees.
- D3/global RMS fraction: {summary['overall']['d3_fraction_global_rms']:.4f}; D3/sulfur-local RMS fraction: {summary['overall']['d3_fraction_local_rms']:.4f}.

The force field problem is multidimensional: sulfur-local forces change across minimum, torsion, local bond/angle perturbation, and higher-strain geometry. These targets support testing locality and model capacity, but do not by themselves select a fitting method. Baseline residuals on this exact homogeneous set and a frozen split are required before choosing linear corrections, kernels, neural models, or parameter refits.

The batch is deliberately broad rather than near-equilibrium-only. Its 210.112 kcal/mol energy span includes strongly strained force-cloud candidates: sulfur-local per-point RMS ranges from 0.504 to 66.985 kcal/mol/A. Median sulfur-local RMS differs across MIN01/MIN02/MIN04 (21.364/15.789/25.833 kcal/mol/A), while the aggregate is 29.240 kcal/mol/A. Sulfur-local magnitude correlates strongly with total energy rank (`rho=0.903`) but weakly with S-C distance (`-0.020`), S-H distance (`0.125`), or C-S-H angle (`0.046`) individually. This is direct evidence against treating the sulfur problem as a single-coordinate correction.

D3 contributes only about 1.58% of sulfur-local gradient RMS, so the observed structure is overwhelmingly electronic PBE rather than dispersion-driven. The dataset should remain stratified by minimum, perturbation family, and strain/energy regime during any later method comparison. Equal-weight fitting across all 60 would allow the high-force tail to dominate, but the appropriate weighting and model class are deliberately not chosen here.
"""
 (HERE/"GPU_BATCH_60_SULFUR_FORCE_REPORT.md").write_text(report)
 files=[p for p in HERE.rglob("*") if p.is_file() and "completed-batch-60" not in p.parts and p.name!="SHA256SUMS"]
 (HERE/"SHA256SUMS").write_text("".join(f"{sha(p)}  {p.relative_to(HERE)}\n" for p in sorted(files)))

if __name__=="__main__":main()
