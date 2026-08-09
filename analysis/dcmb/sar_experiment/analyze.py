#!/usr/bin/env python3
"""SAM-aware geometry, apo/SAM contrasts, SAR summaries, and decisive overlays."""
from __future__ import annotations

import csv
import json
import math
from collections import defaultdict
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
VDW = {"H":1.20,"C":1.70,"N":1.55,"O":1.52,"S":1.80,"P":1.80,"F":1.47,"CL":1.75}
HYDROPHOBIC = {"ALA","VAL","LEU","ILE","MET","PHE","TRP","TYR","PRO"}
AROMATIC = {"PHE","TRP","TYR","HIS"}


def element(line):
    token = line.split()[-1].upper()
    if token in VDW: return token
    field = line[76:78].strip().upper()
    return field if field in VDW else line[12:16].strip()[0].upper()


def atoms(path, models=False):
    result, current = [], []
    for line in path.read_text().splitlines():
        if line.startswith("MODEL"):
            current = []
        elif line.startswith(("ATOM  ","HETATM")):
            e = element(line)
            if e != "H":
                current.append({"line":line,"name":line[12:16].strip(),"res":line[17:20].strip(),
                                "chain":line[21:22].strip(),"resi":int(line[22:26]),"e":e,
                                "xyz":np.array([float(line[30:38]),float(line[38:46]),float(line[46:54])])})
        elif line.startswith("ENDMDL"):
            result.append(current); current=[]
    if models:
        if current: result.append(current)
        return result
    if current: return current
    return result[0] if result else []


def affinity_models(path):
    vals=[]
    for line in path.read_text().splitlines():
        if line.startswith("REMARK VINA RESULT:"): vals.append(float(line.split()[3]))
    return vals


def sphere_intersection(r1,r2,d):
    if d >= r1+r2: return 0.0
    if d <= abs(r1-r2): return 4*math.pi*min(r1,r2)**3/3
    return math.pi*(r1+r2-d)**2*(d*d+2*d*(r1+r2)-3*(r1-r2)**2)/(12*d)


def principal_axis(xyz):
    centered=xyz-xyz.mean(0); _,_,vh=np.linalg.svd(centered,full_matrices=False)
    return vh[0]


def angle_abs(a,b):
    return math.degrees(math.acos(np.clip(abs(float(np.dot(a,b)/(np.linalg.norm(a)*np.linalg.norm(b)))),-1,1)))


def read_spheres(path):
    pts=[]
    for line in path.read_text().splitlines():
        if line.startswith(("ATOM  ","HETATM")):
            pts.append([float(line[30:38]),float(line[38:46]),float(line[46:54])])
    return np.array(pts)


def pose_metrics(state,compound,rank,score,lig,protein,sam,spheres,mouth_vec,pocket_center):
    xyz=np.array([a["xyz"] for a in lig]); centroid=xyz.mean(0); axis=principal_axis(xyz)
    sphere_d=np.min(np.linalg.norm(xyz[:,None,:]-spheres[None,:,:],axis=2),axis=1)
    in_fraction=float(np.mean(sphere_d<=4.0)); nearest=float(sphere_d.min())
    pxyz=np.array([p["xyz"] for p in protein])
    prot_d=np.linalg.norm(xyz[:,None,:]-pxyz[None,:,:],axis=2)
    residue_ids=np.array([f'{p["chain"]}:{p["res"]}{p["resi"]}' for p in protein])
    contacts=set(residue_ids[np.any(prot_d<=4.5,axis=0)])
    burial=float(np.mean(np.min(prot_d,axis=1)<=4.5))
    hyd=set(); arom=set(); hb=set(); chlorine_env=set(); amine_env=set()
    for i,a in enumerate(lig):
        close45=np.where(prot_d[i]<=4.5)[0]
        close50=np.where(prot_d[i]<=5.0)[0]
        close35=np.where(prot_d[i]<=3.5)[0]
        if a["e"] in {"C","CL"}: hyd.update(residue_ids[j] for j in close45 if protein[j]["res"] in HYDROPHOBIC)
        if a["e"]=="C": arom.update(residue_ids[j] for j in close50 if protein[j]["res"] in AROMATIC)
        if a["e"] in {"N","O"}: hb.update(residue_ids[j] for j in close35 if protein[j]["e"] in {"N","O","S"})
        if a["e"]=="CL": chlorine_env.update(residue_ids[close45])
        if a["e"]=="N": amine_env.update(residue_ids[close45])
    sam_min=sam_contacts_3=sam_contacts_4=hard=overlap=0.0
    if sam:
        distances=[]
        for a in lig:
            for s in sam:
                d=np.linalg.norm(a["xyz"]-s["xyz"]); distances.append(d)
                overlap += sphere_intersection(VDW.get(a["e"],1.7),VDW.get(s["e"],1.7),d)
        sam_min=float(min(distances)); sam_contacts_3=sum(d<3.0 for d in distances)
        sam_contacts_4=sum(d<4.0 for d in distances); hard=sum(d<2.0 for d in distances)
    if in_fraction < 0.25 and nearest > 4.0: classification="OUTSIDE_TARGET_SITE"
    elif sam and hard: classification="SAM_STERIC_CONFLICT"
    elif sam and sam_contacts_4: classification="SAM_CONTACTING"
    else: classification="SAM_COMPATIBLE"
    mouth_projection=float(np.dot(centroid-pocket_center,mouth_vec)); far_projection=float(np.max((xyz-pocket_center)@mouth_vec))
    return {"state":state,"paralog":state[:2],"cofactor_state":"SAM" if state.endswith("SAM") else "APO",
            "compound_id":compound,"pose_rank":rank,"affinity_kcal_mol":score,
            "centroid_x":centroid[0],"centroid_y":centroid[1],"centroid_z":centroid[2],
            "orientation_axis_x":axis[0],"orientation_axis_y":axis[1],"orientation_axis_z":axis[2],
            "pocket_sphere_fraction_4A":in_fraction,"nearest_pocket_sphere_A":nearest,
            "sam_min_heavy_distance_A":sam_min if sam else "","sam_contacts_lt3A":sam_contacts_3 if sam else "",
            "sam_contacts_lt4A":sam_contacts_4 if sam else "","sam_hard_overlaps_lt2A":hard if sam else "",
            "sam_overlap_proxy_A3":overlap if sam else "","protein_contacts":";".join(sorted(contacts)),
            "hydrophobic_contacts":";".join(sorted(hyd)),"aromatic_contacts":";".join(sorted(arom)),
            "hydrogen_bond_candidates":";".join(sorted(hb)),"amine_environment":";".join(sorted(amine_env)),
            "chlorine_environment":";".join(sorted(chlorine_env)),"burial_atom_fraction_4p5A":burial,
            "mouth_projection_A":mouth_projection,"farthest_mouth_projection_A":far_projection,
            "exits_toward_mouth":far_projection>6.0,"classification":classification}


def write_csv(path,rows):
    if not rows: return
    with path.open("w",newline="") as fh:
        w=csv.DictWriter(fh,fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)


def overlay(state,compound,out):
    rec=(HERE/"receptors"/f"WT_METTL{state}.pdb").read_text().splitlines()
    pose=atoms(HERE/"raw"/f"{state}__{compound}.pdbqt",models=True)[0]
    protein=[x for x in rec if x.startswith(("ATOM  ","HETATM"))]
    serial=max(int(x[6:11]) for x in protein); lines=list(protein)
    for i,a in enumerate(pose,serial+1):
        x,y,z=a["xyz"]; lines.append(f"HETATM{i:5d} {a['name'][:4]:<4} LIG Z 900    {x:8.3f}{y:8.3f}{z:8.3f}  1.00  0.00          {a['e']:>2}")
    out.write_text("\n".join(lines)+"\nEND\n")


def main():
    allrows=[]; state_data={}
    for paralog in ("7A","7B"):
        sphere_path=HERE/"receptors"/f"METTL{paralog}_accepted_pocket_spheres.pqr"
        spheres=read_spheres(sphere_path); center=spheres.mean(0)
        # Operational mouth vector: direction from the sphere centroid to the most
        # peripheral alpha sphere. It is a geometric descriptor, not a membrane axis.
        mouth=spheres[np.argmax(np.linalg.norm(spheres-center,axis=1))]-center; mouth/=np.linalg.norm(mouth)
        for suffix in ("APO","SAM"):
            state=f"{paralog}_{suffix}"; rec=atoms(HERE/"receptors"/f"WT_METTL{state}.pdb")
            protein=[a for a in rec if a["res"]!="SAM"]; sam=[a for a in rec if a["res"]=="SAM"]
            state_data[state]=(protein,sam,spheres,mouth,center)
    for path in sorted((HERE/"raw").glob("*.pdbqt")):
        state,compound=path.stem.split("__",1); models=atoms(path,models=True); scores=affinity_models(path)
        protein,sam,spheres,mouth,center=state_data[state]
        for i,(pose,score) in enumerate(zip(models,scores),1):
            allrows.append(pose_metrics(state,compound,i,score,pose,protein,sam,spheres,mouth,center))
    write_csv(HERE/"pose_metrics.csv",allrows)

    by=defaultdict(list)
    for r in allrows: by[(r["state"],r["compound_id"])].append(r)
    summary=[]
    for (state,cid),rs in sorted(by.items()):
        rs=sorted(rs,key=lambda r:r["pose_rank"]); best=rs[0]
        summary.append({"state":state,"compound_id":cid,"best_affinity_kcal_mol":best["affinity_kcal_mol"],
                        "best_pose_class":best["classification"],"best_pose_sam_min_A":best["sam_min_heavy_distance_A"],
                        "best_pose_burial":best["burial_atom_fraction_4p5A"],
                        "target_site_pose_count":sum(r["classification"]!="OUTSIDE_TARGET_SITE" for r in rs),
                        "sam_compatible_pose_count":sum(r["classification"]=="SAM_COMPATIBLE" for r in rs),
                        "sam_contacting_pose_count":sum(r["classification"]=="SAM_CONTACTING" for r in rs),
                        "sam_conflict_pose_count":sum(r["classification"]=="SAM_STERIC_CONFLICT" for r in rs)})
    write_csv(HERE/"condition_summary.csv",summary)
    contrasts=[]
    for para in ("7A","7B"):
        cids=sorted({k[1] for k in by if k[0]==f"{para}_APO"})
        for cid in cids:
            a=sorted(by[(f"{para}_APO",cid)],key=lambda r:r["pose_rank"])[0]
            s=sorted(by[(f"{para}_SAM",cid)],key=lambda r:r["pose_rank"])[0]
            ca=np.array([a[f"centroid_{q}"] for q in "xyz"]); cs=np.array([s[f"centroid_{q}"] for q in "xyz"])
            aa=np.array([a[f"orientation_axis_{q}"] for q in "xyz"]); sa=np.array([s[f"orientation_axis_{q}"] for q in "xyz"])
            ac=set(filter(None,a["protein_contacts"].split(";"))); sc=set(filter(None,s["protein_contacts"].split(";")))
            contrasts.append({"paralog":para,"compound_id":cid,"sam_minus_apo_score_kcal_mol":float(s["affinity_kcal_mol"])-float(a["affinity_kcal_mol"]),
                              "rank1_centroid_displacement_A":float(np.linalg.norm(cs-ca)),"rank1_orientation_change_deg":angle_abs(aa,sa),
                              "contact_jaccard":len(ac&sc)/len(ac|sc) if ac|sc else 1.0,
                              "contacts_lost":";".join(sorted(ac-sc)),"contacts_gained":";".join(sorted(sc-ac)),
                              "apo_class":a["classification"],"sam_class":s["classification"],
                              "sam_min_distance_A":s["sam_min_heavy_distance_A"],"sam_overlap_proxy_A3":s["sam_overlap_proxy_A3"]})
    write_csv(HERE/"apo_sam_contrasts.csv",contrasts)

    vis=HERE/"visuals"; vis.mkdir(exist_ok=True)
    for para in ("7A","7B"):
        for cid in ("DCMB_R","BA"):
            overlay(f"{para}_SAM",cid,vis/f"{para}_SAM_{cid}_rank1.pdb")
    # Compact coordinate figures; inspectable PDB overlays remain the source artifacts.
    comparisons=[("7A_SAM","DCMB_R","BA","7A_DCMB_vs_BA"),("7B_SAM","DCMB_R","BA","7B_DCMB_vs_BA")]
    for state,c1,c2,label in comparisons:
        rec=atoms(HERE/"receptors"/f"WT_METTL{state}.pdb"); sam=np.array([a["xyz"] for a in rec if a["res"]=="SAM"])
        p1=np.array([a["xyz"] for a in atoms(HERE/"raw"/f"{state}__{c1}.pdbqt",models=True)[0]])
        p2=np.array([a["xyz"] for a in atoms(HERE/"raw"/f"{state}__{c2}.pdbqt",models=True)[0]])
        fig=plt.figure(figsize=(6,5)); ax=fig.add_subplot(projection="3d")
        ax.scatter(*sam.T,s=18,c="#d4a017",label="SAM"); ax.scatter(*p1.T,s=35,c="#c0392b",label="DCMB R")
        ax.scatter(*p2.T,s=30,c="#2471a3",label="benzylamine"); ax.legend(); ax.set_title(label.replace("_"," "))
        fig.tight_layout(); fig.savefig(vis/f"{label}.png",dpi=180); plt.close(fig)
    fig=plt.figure(figsize=(6,5)); ax=fig.add_subplot(projection="3d")
    for state,color,label in (("7A_SAM","#c0392b","7A DCMB R"),("7B_SAM","#2471a3","7B DCMB R")):
        rec=atoms(HERE/"receptors"/f"WT_METTL{state}.pdb")
        sam=np.array([a["xyz"] for a in rec if a["res"]=="SAM"])
        lig=np.array([a["xyz"] for a in atoms(HERE/"raw"/f"{state}__DCMB_R.pdbqt",models=True)[0]])
        ax.scatter(*sam.T,s=12,c=color,alpha=.25)
        ax.scatter(*lig.T,s=35,c=color,label=label)
    ax.legend(); ax.set_title("DCMB R in aligned 7A and 7B receptor frames")
    fig.tight_layout(); fig.savefig(vis/"DCMB_7A_vs_7B.png",dpi=180); plt.close(fig)
    (HERE/"analysis_manifest.json").write_text(json.dumps({
        "classification":"OUTSIDE if <25% ligand heavy atoms lie within 4 A of accepted alpha spheres and nearest sphere >4 A; otherwise SAM conflict <2 A, SAM contacting <4 A, else compatible",
        "sam_overlap_proxy":"sum of pairwise van der Waals sphere intersections; may double-count and is not exact union volume",
        "burial":"fraction of ligand heavy atoms within 4.5 A of a protein heavy atom",
        "mouth":"vector from accepted alpha-sphere centroid to its most peripheral sphere; operational geometry only",
        "hydrogen_bonds":"heavy-atom donor/acceptor distance candidates <=3.5 A; angular criteria unavailable from PDBQT",
        "ligand_strain":"not evaluated: Vina poses do not provide a validated strain energy decomposition",
        "pose_pairing":"apo/SAM comparisons use rank-1 poses; full alternate-pose counts retained separately"
    },indent=2)+"\n")


if __name__=="__main__": main()
