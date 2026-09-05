#!/usr/bin/env python3
# DEPRECATED FOR V2 USE (2026-09-05): near-attack geometry in this script is superseded by
# athena.tmt.NearAttackGeometry / NearAttackAssessor / EnsembleNacAnalyzer (Java).
# See ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md. Retained for historical regression reproduction only.
"""Bounded, non-MD METTL7 reaction-competence comparison.

Reuses the frozen BI-187004 near-attack geometry machinery. This is a
geometric feasibility calculation, not an affinity or catalytic-rate model.
"""
from __future__ import annotations
import csv, json, math, sys
from pathlib import Path
import numpy as np
from rdkit import Chem
from rdkit.Chem import AllChem

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "research/mettl7-bi187004-near-attack"))
import run_near_attack as na

RUN_KEY = "METTL7_REACTION_COMPETENCE_V1_2026_09_04"
SEED = 714004
CAPTOPRIL = {
    "THIOL_NEUTRAL_CARBOXYLATE": "C[C@H](CS)C(=O)N1CCC[C@H]1C(=O)[O-]",
    "THIOLATE_CARBOXYLATE": "C[C@H](C[S-])C(=O)N1CCC[C@H]1C(=O)[O-]",
}

def write_csv(path, rows):
    with path.open("w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)

def residue_environment(pa, acceptor, cutoff=4.5):
    by = {}
    for atom in pa:
        d = float(np.linalg.norm(atom[5] - acceptor))
        if d <= cutoff:
            key = f"{atom[1]}{atom[3]}"
            by[key] = min(by.get(key, 99), d)
    acidbase_names = {"ASP","GLU","HIS","LYS","ARG","CYS","TYR"}
    polar_names = acidbase_names | {"SER","THR","ASN","GLN"}
    acid = [f"{k}:{v:.2f}" for k,v in sorted(by.items()) if k[:3] in acidbase_names]
    polar = [f"{k}:{v:.2f}" for k,v in sorted(by.items()) if k[:3] in polar_names]
    return ";".join(acid) or "NONE_WITHIN_4.5A", ";".join(polar) or "NONE_WITHIN_4.5A"

def captopril_analysis():
    rng = np.random.default_rng(SEED); clouds = na.pocket_clouds(); systems = na.systems()
    output=[]
    for state_i,(state,smi) in enumerate(CAPTOPRIL.items()):
        m=Chem.AddHs(Chem.MolFromSmiles(smi)); p=AllChem.ETKDGv3(); p.randomSeed=SEED+state_i
        ids=list(AllChem.EmbedMultipleConfs(m,numConfs=8,params=p)); props=AllChem.MMFFGetMoleculeProperties(m,mmffVariant="MMFF94s")
        energies=[]
        for cid in ids:
            if props is not None:
                AllChem.MMFFOptimizeMolecule(m,mmffVariant="MMFF94s",confId=cid,maxIters=1000)
                energies.append(AllChem.MMFFGetMoleculeForceField(m,props,confId=cid).CalcEnergy())
            else: energies.append(0.0)
        heavy=Chem.RemoveHs(m); emin=min(energies); sidx=next(a.GetIdx() for a in heavy.GetAtoms() if a.GetSymbol()=="S")
        for paralog,bound in systems.items():
            aa=na.atoms(bound); pa=[a for a in aa if a[1]!="SAM" and a[4]!="H"]; sa=[a for a in aa if a[1]=="SAM" and a[4]!="H"]
            protein=np.array([a[5] for a in pa]); sam=np.array([a[5] for a in sa]); ce=next(a[5] for a in sa if a[0]=="CE"); sd=next(a[5] for a in sa if a[0]=="SD"); axis=(ce-sd)/np.linalg.norm(ce-sd)
            rows=[]; xyzs=[]
            for ci in range(heavy.GetNumConformers()):
                c=heavy.GetConformer(ci); raw=np.array([[c.GetAtomPosition(i).x,c.GetAtomPosition(i).y,c.GetAtomPosition(i).z] for i in range(heavy.GetNumAtoms())]); centered=raw-raw[sidx]
                for distance in na.DISTANCES:
                    for ri in range(80):
                        trial=rng.normal(size=3); perp=trial-axis*float(trial@axis)
                        if np.linalg.norm(perp)<1e-8: continue
                        perp/=np.linalg.norm(perp); theta=math.acos(rng.uniform(math.cos(math.radians(30)),1)); attack=axis*math.cos(theta)+perp*math.sin(theta)
                        xyz=centered@na.random_rotation(rng)+ce+attack*distance
                        met=na.metrics(xyz,protein,sam,clouds[paralog],sidx,ce,sd)
                        rows.append({"conformer":ci,"rotation":ri,"target_distance_A":distance,"ligand_strain_kcal_mol":energies[ci]-emin,**met}); xyzs.append(xyz)
            chosen=na.select(rows,xyzs); passes=[]
            for i in chosen:
                row=rows[i]; xyz=xyzs[i]; mobile={(a[2],a[3],a[1]) for a in pa if np.min(np.linalg.norm(xyz-a[5],axis=1))<4.0}
                _,rr=na.relax_sidechains(pa,xyz,sam,mobile)
                valid=(rr["protein_clashes_after"]==0 and rr["sam_clashes_after"]==0 and row["pocket_containment_fraction"]>=.70 and rr["max_sidechain_bond_deviation_A"]<=.15)
                if valid: passes.append((row,rr,xyz))
            if passes:
                best=min(passes,key=lambda z:(z[0]["ligand_strain_kcal_mol"],z[1]["sidechain_rms_displacement_A"])); acid,polar=residue_environment(pa,best[2][sidx]); cls="SPATIAL_NEAR_ATTACK_FEASIBLE"
                vals={"distance_A":best[0]["n_to_sam_methyl_distance_A"],"angle_deg":best[0]["n_c_s_approach_angle_deg"],"strain_kcal_mol":best[0]["ligand_strain_kcal_mol"],"severe_clashes":0,"sidechain_rms_A":best[1]["sidechain_rms_displacement_A"]}
            else:
                acid=polar="NOT_AVAILABLE"; cls="NOT_FEASIBLE_WITHIN_BOUNDED_PROTOCOL"; vals={"distance_A":"","angle_deg":"","strain_kcal_mol":"","severe_clashes":"","sidechain_rms_A":""}
            output.append({"ligand":"CAPTOPRIL","paralog":paralog,"state":state,"candidate_acceptor":"THIOL_S" if "NEUTRAL" in state else "THIOLATE_S","classification":cls,"chemically_activated_state":"NO" if "NEUTRAL" in state else "YES_MODEL_STATE","valid_starts":len(passes),**vals,"nearby_acid_base_residues":acid,"nearby_polar_residues":polar,"explicit_waters":"NONE_IN_CANONICAL_INPUT"})
    return output

def pdbqt_model(path, model):
    lines=[]; active=False
    for line in path.read_text().splitlines():
        if line.startswith("MODEL"): active=int(line.split()[1])==model
        elif line.startswith("ENDMDL") and active: break
        elif active and line.startswith(("ATOM","HETATM")): lines.append(line)
    return lines

def coords(lines):
    return [(x[12:16].strip(),x[77:79].strip() or x[12:14].strip(),np.array([float(x[30:38]),float(x[38:46]),float(x[46:54])])) for x in lines]

def netarsudil_analysis():
    # Accepted 7B neutral family-5 representative; matched 7A control is the
    # corrected lowest-strain neutral pose (mode 15), both already generated.
    specs=[("7B","neutral","ACCEPTED_7B_FAMILY5",ROOT/"research/mettl7-netarsudil-sam-mechanism/vina-matched/raw/7B_neutral_seed483271.pdbqt",5),
           ("7A","neutral","MATCHED_7A_LOWEST_STRAIN_CONTROL",ROOT/"research/mettl7-netarsudil-sam-mechanism/local-architecture/prepared/corrected_7A_lowest_strain_mode15.pdbqt",1)]
    systems=na.systems(); out=[]
    for par,state,family,path,model in specs:
        la=coords(pdbqt_model(path,model) if "raw" in str(path) else [x for x in path.read_text().splitlines() if x.startswith(("ATOM","HETATM"))])
        sam=na.atoms(systems[par],"SAM"); ce=next(a[5] for a in sam if a[0]=="CE"); sd=next(a[5] for a in sam if a[0]=="SD")
        # Atom names established by the checked-in state SDF: N5 terminal primary amine, N6 isoquinoline N.
        for label,name,pstate in [("PRIMARY_AMINE_N","N5","NEUTRAL_FREE_BASE"),("ISOQUINOLINE_N","N6","NEUTRAL_AROMATIC_N")]:
            matches=[a for a in la if a[0]==name]
            if not matches:
                out.append({"ligand":"NETARSUDIL","paralog":par,"pose_family":family,"state":state,"candidate_acceptor":label,"atom_name":name,"distance_A":"","angle_deg":"","in_pose_near_attack":"NO_DATA","productive_feasibility":"INDETERMINATE"}); continue
            p=matches[0][2]; d=float(np.linalg.norm(p-ce)); ang=na.angle(p,ce,sd)
            near=(2.8<=d<=3.2 and ang>=150)
            out.append({"ligand":"NETARSUDIL","paralog":par,"pose_family":family,"state":state,"candidate_acceptor":label,"atom_name":name,"distance_A":round(d,4),"angle_deg":round(ang,3),"in_pose_near_attack":str(near).upper(),"productive_feasibility":"SUPPORTED_IN_ACCEPTED_POSE" if near else "NOT_OBSERVED_IN_ACCEPTED_POSE; BOUNDED_REORIENTATION_NOT_RUN"})
    return out

def main():
    HERE.mkdir(exist_ok=True)
    cap=captopril_analysis(); net=netarsudil_analysis()
    write_csv(HERE/"captopril_productive_state_analysis.csv",cap)
    write_csv(HERE/"netarsudil_acceptor_state_analysis.csv",net)
    (HERE/"protocol.json").write_text(json.dumps({"run_key":RUN_KEY,"classification":"non-MD reaction competence; no affinity inference","near_attack":{"distance_A":[2.8,3.2],"angle_deg_min":150,"protein_clash_A":1.8,"sam_clash_A":2.0,"pocket_containment_min":.70},"captopril":{"states":CAPTOPRIL,"conformers":8,"rotations_per_distance":80,"backbone":"fixed","SAM":"canonical Saez-2015 fixed","local_sidechains":"same bounded relaxation as BI187004"},"netarsudil":{"method":"measure accepted pre-existing poses only"},"new_qm":False,"md":False,"broad_docking":False},indent=2)+"\n")
    print(json.dumps({"captopril":cap,"netarsudil":net},indent=2))
if __name__=="__main__": main()
