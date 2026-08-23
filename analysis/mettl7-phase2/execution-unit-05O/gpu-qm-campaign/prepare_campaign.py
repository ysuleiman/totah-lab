#!/usr/bin/env python3
"""Inventory existing labels and select a deterministic 60-point GPU batch."""
from __future__ import annotations

import csv
import hashlib
import json
import math
import shutil
import zipfile
from collections import Counter
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
UNIT = HERE.parent
PREP = UNIT / "gpu-qm-preparation/manifests"
GEOM_CSV = PREP / "EXISTING_TSL_RSH_GEOMETRIES.csv"
QM_CSV = PREP / "EXISTING_QM_RESULTS.csv"
LOCAL = np.array([1, 7, 8, 9, 10, 25, 36, 55])
MINIMA = {m: UNIT / f"qm-native-minima/{m}/final.xyz" for m in ("MIN01", "MIN02", "MIN04")}


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_xyz(path: Path):
    lines = path.read_text().splitlines()
    n = int(lines[0]); rows = [x.split() for x in lines[2:2+n]]
    return [x[0] for x in rows], np.array([[float(v) for v in x[1:4]] for x in rows])


def dihedral(x, ids):
    p = x[np.array(ids)]
    b0, b1, b2 = -(p[1]-p[0]), p[2]-p[1], p[3]-p[2]
    b1 /= np.linalg.norm(b1)
    v, w = b0-np.dot(b0,b1)*b1, b2-np.dot(b2,b1)*b1
    return math.degrees(math.atan2(np.dot(np.cross(b1,v),w), np.dot(v,w)))


def kabsch_rmsd(a, b):
    a=a-a.mean(0); b=b-b.mean(0); u,_,vt=np.linalg.svd(a.T@b)
    d=np.linalg.det(u@vt); u[:,-1]*=d
    return float(np.sqrt(np.mean(np.sum((a@u@vt-b)**2,axis=1))))


def features(x):
    local=x[LOCAL]; distances=[]
    for i in range(len(local)):
        for j in range(i): distances.append(np.linalg.norm(local[i]-local[j]))
    all_d=[]
    for i in range(56):
        for j in range(i): all_d.append(np.linalg.norm(x[i]-x[j]))
    qs=np.quantile(all_d,np.linspace(0,1,21))
    phi=math.radians(dihedral(x,[55,25,9,8])); psi=math.radians(dihedral(x,[25,9,8,7]))
    return np.r_[distances,qs,np.sin(phi),np.cos(phi),np.sin(psi),np.cos(psi)]


def classify(path: str):
    low=path.lower()
    if any(x in low for x in ("execution-unit-05h", "execution-unit-05l", "execution-unit-05m", "phi", "psi", "torsion")):
        return "TORSIONAL_OR_CONSTRAINED_TRAJECTORY"
    if "qm-native-minima" in low:
        return "MINIMUM_OPTIMIZATION_TRAJECTORY"
    if "force-cloud" in low:
        return "FORCE_CLOUD_PERTURBATION"
    return "OTHER_EXISTING_GEOMETRY"


def source_minimum(x, minima):
    scores={m:kabsch_rmsd(x,v) for m,v in minima.items()}
    return min(scores,key=scores.get),scores


def write_csv(path, rows):
    with path.open("w",newline="") as h:
        w=csv.DictWriter(h,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)


def main():
    minima={m:read_xyz(p)[1] for m,p in MINIMA.items()}
    snapshot={r["snapshot_id"]:r for r in csv.DictReader((UNIT/"force-cloud/SNAPSHOT_MANIFEST.csv").open())}
    qrows=list(csv.DictReader(QM_CSV.open()))
    inventory=[]; labeled_sha=set()
    for row in qrows:
        if row["dataset_role"] in ("TRAIN","HOLDOUT"):
            rp=UNIT/f"force-cloud-qm/calculations/{row['geometry_id']}/result.json"
        else:
            note=row.get("notes",""); rp=REPO/note if note.endswith("result.json") else None
        data={}
        if rp and rp.is_file():
            try:data=json.loads(rp.read_text())
            except json.JSONDecodeError:pass
        geom_sha=row.get("geometry_sha256","") or data.get("geometry_checksum","") or data.get("final_xyz_sha256","")
        if geom_sha:labeled_sha.add(geom_sha)
        sealed=row["dataset_role"]=="HOLDOUT"
        grad=np.array(data.get("gradient_hartree_per_bohr",[]),dtype=float)
        force=np.array(data.get("force_hartree_per_bohr",[]),dtype=float)
        if not force.size and grad.shape==(56,3):force=-grad
        path_text=str(rp.relative_to(REPO)) if rp else row.get("notes","")
        minimum=snapshot.get(row["geometry_id"],{}).get("source_minimum") or next((m for m in ("MIN01","MIN02","MIN03","MIN04") if m in path_text),"UNASSIGNED")
        family=(snapshot.get(row["geometry_id"],{}).get("family","")+":"+snapshot.get(row["geometry_id"],{}).get("subfamily","")).strip(":") or classify(path_text)
        inventory.append({
            "geometry_id":row["geometry_id"],"provenance":"CPU_PYSCF" if rp else "UNKNOWN",
            "dataset_role":row["dataset_role"],"minimum":minimum,"perturbation_family":family,
            "geometry_sha256":geom_sha,"result_sha256":row["result_sha256"],
            "energy_hartree":"REDACTED_SEALED" if sealed else row.get("energy_hartree",""),
            "force_rms_ha_per_bohr":"REDACTED_SEALED" if sealed else (float(np.sqrt(np.mean(force**2))) if force.shape==(56,3) else "MISSING"),
            "force_max_ha_per_bohr":"REDACTED_SEALED" if sealed else (float(np.max(np.abs(force))) if force.shape==(56,3) else "MISSING"),
            "sulfur_local_force_rms_ha_per_bohr":"REDACTED_SEALED" if sealed else (float(np.sqrt(np.mean(force[LOCAL]**2))) if force.shape==(56,3) else "MISSING"),
            "torsional_coverage": "PHI_PSI" if any(x in path_text.lower() for x in ("phi","psi","05h","05l","05m")) else "NOT_EXPLICIT",
            "numerical_values_exposed":not sealed,
        })
    write_csv(HERE/"EXISTING_123_QM_LABELS_COVERAGE.csv",inventory)

    grows=list(csv.DictReader(GEOM_CSV.open())); candidates=[]; seen=set()
    for row in grows:
        if row["file_format"]!="XYZ" or row["atom_count"]!="56" or row["composition"]!="C22H30O3S1":continue
        if row["dataset_role"]=="HOLDOUT" or "force-cloud/retained" in row["source_path"]:continue
        p=REPO/row["source_path"]
        if not p.is_file() or row["geometry_sha256"] in labeled_sha or row["geometry_sha256"] in seen:continue
        # Final geometries with adjacent result files are already labels; trajectory frames are not.
        if p.name=="final.xyz" and (p.parent/"result.json").is_file():continue
        try:els,x=read_xyz(p)
        except Exception:continue
        if len(x)!=56:continue
        seen.add(row["geometry_sha256"]); m,scores=source_minimum(x,minima)
        candidates.append({"path":p,"sha":row["geometry_sha256"],"x":x,"feature":features(x),
                           "minimum":m,"scores":scores,"family":classify(row["source_path"]),
                           "phi":dihedral(x,[55,25,9,8]),"psi":dihedral(x,[25,9,8,7])})
    if len(candidates)<60:raise RuntimeError("fewer than 60 eligible existing geometries")
    matrix=np.array([c["feature"] for c in candidates]); scale=np.std(matrix,axis=0);scale[scale<1e-12]=1
    matrix=(matrix-np.mean(matrix,axis=0))/scale
    selected=[]
    for minimum in ("MIN01","MIN02","MIN04"):
        pool=[i for i,c in enumerate(candidates) if c["minimum"]==minimum]
        if len(pool)<20:raise RuntimeError(f"insufficient {minimum} candidates")
        # Deterministic maximin coverage, seeded by the point furthest from its minimum.
        seed=max(pool,key=lambda i:candidates[i]["scores"][minimum]); chosen=[seed]
        while len(chosen)<20:
            nxt=max((i for i in pool if i not in chosen),key=lambda i:min(np.linalg.norm(matrix[i]-matrix[j]) for j in chosen))
            chosen.append(nxt)
        selected.extend(chosen)
    selection=[]
    geometry_dir=HERE/"geometry";geometry_dir.mkdir(exist_ok=True)
    for serial,i in enumerate(selected,1):
        c=candidates[i]
        campaign_id=f"TSLRSH-GPU-{serial:03d}"
        export=geometry_dir/f"{campaign_id}.xyz";shutil.copyfile(c["path"],export)
        if sha(export)!=c["sha"]:raise RuntimeError("geometry export checksum mismatch")
        selection.append({"campaign_id":campaign_id,"source_path":str(c["path"].relative_to(REPO)),"runner_path":f"geometry/{campaign_id}.xyz",
                          "geometry_sha256":c["sha"],"source_minimum":c["minimum"],"perturbation_family":c["family"],
                          "phi_degrees":c["phi"],"psi_degrees":c["psi"],"nearest_minimum_rmsd_angstrom":c["scores"][c["minimum"]],
                          "selection_method":"BALANCED_MINIMUM_STRATIFIED_MAXIMIN_DESCRIPTOR","selected_for_qm":True})
    write_csv(HERE/"NEXT_GPU_QM_BATCH_60.csv",selection)
    summary={
        "existing_qm_labels":len(inventory),"cpu_labels":sum(x["provenance"]=="CPU_PYSCF" for x in inventory),"gpu_labels":0,
        "sealed_rows_redacted":sum(x["dataset_role"]=="HOLDOUT" for x in inventory),
        "eligible_unlabeled_unique_56_atom_geometries":len(candidates),"selected_batch_size":60,
        "selected_minima":Counter(x["source_minimum"] for x in selection),
        "selected_families":Counter(x["perturbation_family"] for x in selection),
        "existing_minima":Counter(x["minimum"] for x in inventory),
        "existing_perturbation_families":Counter(x["perturbation_family"] for x in inventory),
        "public_energy_range_hartree": [min(float(x["energy_hartree"]) for x in inventory if str(x["energy_hartree"]).replace('.','',1).replace('-','',1).replace('e','',1).replace('+','',1).isdigit()), max(float(x["energy_hartree"]) for x in inventory if str(x["energy_hartree"]).replace('.','',1).replace('-','',1).replace('e','',1).replace('+','',1).isdigit())],
        "selection_rationale":"60 is the smallest balanced batch giving 20 new points per frozen minimum while spanning torsional/constrained and optimization-path gaps by deterministic maximin coverage. It matches the scale of the proven 60-point cloud without defaulting to 100 or 200.",
        "dataset_policy":"New GPU labels form a homogeneous provenance partition. CPU and GPU labels are not mixed for training until an explicit dataset-level calibration policy is preregistered."
    }
    (HERE/"CAMPAIGN_COVERAGE_AND_SELECTION.json").write_text(json.dumps(summary,indent=2,sort_keys=True,default=dict)+"\n")
    archive=HERE/"TSL_RSH_GPU_BATCH_60.zip"
    with zipfile.ZipFile(archive,"w",compression=zipfile.ZIP_DEFLATED) as z:
        for name in ("NEXT_GPU_QM_BATCH_60.csv","run_frozen_gpu_campaign.py","FROZEN_GPU_QM_PROTOCOL.json","SCIENTIFIC_EQUIVALENCE_DECISION.json"):
            z.write(HERE/name,name)
        for p in sorted(geometry_dir.iterdir()):z.write(p,f"geometry/{p.name}")
    sums=[]
    for p in sorted(HERE.rglob("*")):
        if p.is_file() and p.name!="SHA256SUMS":sums.append(f"{sha(p)}  {p.relative_to(HERE)}\n")
    (HERE/"SHA256SUMS").write_text("".join(sums))


if __name__=="__main__":main()
