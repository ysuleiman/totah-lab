#!/usr/bin/env python3
# DEPRECATED FOR V2 USE (2026-09-05): near-attack geometry in this script is superseded by
# athena.tmt.NearAttackGeometry / NearAttackAssessor / EnsembleNacAnalyzer (Java).
# See ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md. Retained for historical regression reproduction only.
"""Analyze paired reciprocal RNA accommodation without constructing a master score."""
from __future__ import annotations
import argparse, csv, json, math
from collections import defaultdict
from pathlib import Path
import numpy as np

def parse(path):
    out=[]
    for x in path.read_text().splitlines():
        if not x.startswith(("ATOM  ","HETATM")): continue
        out.append({"name":x[12:16].strip(),"resname":x[17:20].strip(),"chain":x[21:22],"resid":int(x[22:26]),
                    "element":(x[76:78].strip() or x[12:16].strip()[0]).upper(),"xyz":np.array([float(x[30:38]),float(x[38:46]),float(x[46:54])])})
    return out
def angle(a,b,c):
    x=a-b;y=c-b; return math.degrees(math.acos(np.clip(np.dot(x,y)/(np.linalg.norm(x)*np.linalg.norm(y)),-1,1)))
def internal_rmsd(a,b):
    da=np.linalg.norm(a[:,None]-a[None,:],axis=2); db=np.linalg.norm(b[:,None]-b[None,:],axis=2)
    return float(np.sqrt(np.mean((da-db)**2)))
def target_align_rmsd(start,final,target_res=3):
    names=("N9","C8","N7") if any(a["name"]=="N7" and a["resid"]==3 for a in start) else ("N9","C6","N6")
    sm={a["name"]:a["xyz"] for a in start if a["resid"]==target_res}; fm={a["name"]:a["xyz"] for a in final if a["resid"]==target_res}
    keys=[k for k in names if k in sm and k in fm]; A=np.array([fm[k] for k in keys]); B=np.array([sm[k] for k in keys]); ac=A.mean(0);bc=B.mean(0)
    u,s,v=np.linalg.svd((A-ac).T@(B-bc)); r=u@v
    fa=np.array([a["xyz"] for a in final if a["name"] in ("P","O5'","C5'")]); sa=np.array([a["xyz"] for a in start if a["name"] in ("P","O5'","C5'")])
    n=min(len(fa),len(sa)); return float(np.sqrt(np.mean(np.sum(((fa[:n]-ac)@r+bc-sa[:n])**2,axis=1))))
def main():
    ap=argparse.ArgumentParser();ap.add_argument("--root",type=Path,required=True);args=ap.parse_args(); rows=[]
    for d in sorted(p.parent for p in args.root.glob("*/*/workflow.cfg")):
        cap=d/"run/5_caprieval/capri_ss.tsv"
        if not cap.exists(): continue
        meta=json.loads((d/"RUN_METADATA.json").read_text()); scores={Path(x["model"]).name:x for x in csv.DictReader(cap.open(),delimiter="\t")}
        start=[a for a in parse(d/"rna_pentamer.pdb") if a["chain"]=="R" and a["element"]!="H"]
        for pdb in sorted((d/"run/4_flexref").glob("flexref_*.pdb")):
            aa=parse(pdb); prot=[a for a in aa if a["chain"] not in ("R","S") and a["element"]!="H"]; rna=[a for a in aa if a["chain"]=="R" and a["element"]!="H"]
            anc={a["resid"]:a["xyz"] for a in aa if a["chain"]=="S"}; accept="N6" if meta["site"]=="FILIP1L_GGACT" else "N7"; target=next(a for a in rna if a["resid"]==3 and a["name"]==accept)
            dist=float(np.linalg.norm(target["xyz"]-anc[1])); ang=angle(target["xyz"],anc[1],anc[2]); D=np.linalg.norm(np.array([a["xyz"] for a in prot])[:,None]-np.array([a["xyz"] for a in rna])[None,:],axis=2)
            contacts=defaultdict(int); target_contacts=defaultdict(int)
            for i,p in enumerate(prot):
                if np.any(D[i]<=4.5): contacts[p["resid"]]+=1
                if np.linalg.norm(p["xyz"]-target["xyz"])<=4.5: target_contacts[p["resid"]]+=1
            s=scores[pdb.name]
            row={**{k:meta[k] for k in ("batch","enzyme","site","mutation","construct","conformer")},"model":pdb.name,
                 "distance_A":dist,"angle_deg":ang,"distance_window":2.8<=dist<=3.4,"angle_window":150<=ang<=180,"both_windows":2.8<=dist<=3.4 and 150<=ang<=180,
                 "rna_internal_distance_rmsd_A":internal_rmsd(np.array([a["xyz"] for a in start]),np.array([a["xyz"] for a in rna])),"target_aligned_backbone_escape_rmsd_A":target_align_rmsd(start,rna),
                 "severe_clashes_lt_1_8A":int(np.count_nonzero(D<1.8)),"close_pairs_lt_2_4A":int(np.count_nonzero(D<2.4)),"contact_pairs_le_4_5A":int(np.count_nonzero(D<=4.5)),
                 "target_contact_residues":";".join(map(str,sorted(target_contacts))),"whole_rna_contact_residues":";".join(map(str,sorted(contacts))),
                 **{f"haddock_{k}":float(s[k]) for k in ("score","air","angles","bonds","desolv","dihe","elec","improper","total","vdw")}}
            rows.append(row)
    if not rows: raise SystemExit("no completed models")
    with (args.root/"model_metrics.csv").open("w",newline="") as h:w=csv.DictWriter(h,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
    numeric=("distance_window","angle_window","both_windows","rna_internal_distance_rmsd_A","target_aligned_backbone_escape_rmsd_A","severe_clashes_lt_1_8A","close_pairs_lt_2_4A","contact_pairs_le_4_5A","haddock_score","haddock_air","haddock_desolv","haddock_elec","haddock_vdw","haddock_total")
    groups=defaultdict(list)
    for r in rows:groups[(r["batch"],r["enzyme"],r["site"],r["mutation"],r["construct"])].append(r)
    summary=[]
    for key,vals in groups.items():
        z=dict(zip(("batch","enzyme","site","mutation","construct"),key));z["n_models"]=len(vals);z["n_conformers"]=len({v["conformer"] for v in vals})
        for k in numeric:z[k+"_mean"]=float(np.mean([v[k] for v in vals]))
        z["conformers_with_both_window"]=sum(any(v["both_windows"] for v in vals if v["conformer"]==c) for c in sorted({v["conformer"] for v in vals}))
        summary.append(z)
    with (args.root/"condition_summary.csv").open("w",newline="") as h:w=csv.DictWriter(h,fieldnames=list(summary[0]));w.writeheader();w.writerows(summary)
    idx={(r["batch"],r["construct"]):r for r in summary}; deltas=[]
    for r in summary:
        if r["construct"]=="WT":continue
        if (r["batch"],"WT") not in idx: continue
        wt=idx[(r["batch"],"WT")];z={k:r[k] for k in ("batch","enzyme","site","mutation")}
        for k in numeric:z["delta_"+k]=r[k+"_mean"]-wt[k+"_mean"]
        z["delta_conformers_with_both_window"]=r["conformers_with_both_window"]-wt["conformers_with_both_window"]
        deltas.append(z)
    with (args.root/"paired_mutation_deltas.csv").open("w",newline="") as h:w=csv.DictWriter(h,fieldnames=list(deltas[0]));w.writeheader();w.writerows(deltas)
    contact_rows=[]
    focus=(43,47,145,148,175,195,199,200,202,232)
    rawgroups=defaultdict(list)
    for r in rows: rawgroups[(r["batch"],r["construct"])].append(r)
    for batch in sorted({r["batch"] for r in rows}):
        if (batch,"WT") not in rawgroups: continue
        mutant_keys=[k for k in rawgroups if k[0]==batch and k[1]!="WT"]
        if not mutant_keys: continue
        mutkey=mutant_keys[0]; wt=rawgroups[(batch,"WT")]; mut=rawgroups[mutkey]
        meta=mut[0]
        all_res=sorted(set(focus)|{int(x) for v in wt+mut for field in ("target_contact_residues","whole_rna_contact_residues") for x in v[field].split(";") if x})
        for resid in all_res:
            z={k:meta[k] for k in ("batch","enzyme","site","mutation")}|{"residue_number":resid,"focus_position":resid in focus}
            for field,label in (("target_contact_residues","target_base"),("whole_rna_contact_residues","whole_rna")):
                wf=np.mean([str(resid) in v[field].split(";") for v in wt]); mf=np.mean([str(resid) in v[field].split(";") for v in mut])
                z[f"wt_{label}_contact_fraction"]=wf; z[f"mutant_{label}_contact_fraction"]=mf; z[f"delta_{label}_contact_fraction"]=mf-wf
            contact_rows.append(z)
    with (args.root/"residue_contact_deltas.csv").open("w",newline="") as h:w=csv.DictWriter(h,fieldnames=list(contact_rows[0]));w.writeheader();w.writerows(contact_rows)
    print(f"analyzed {len(rows)} models, {len(summary)} conditions, {len(deltas)} paired contrasts, {len(contact_rows)} residue contrasts")
if __name__=="__main__":main()
