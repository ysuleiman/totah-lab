#!/usr/bin/env python3
"""Analyze matched Vina poses with frozen, separately reported evidence gates."""
from __future__ import annotations

import csv
import json
import math
from pathlib import Path

import numpy as np
from rdkit import Chem
from rdkit.Chem import AllChem

ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT / "research/mettl7-netarsudil-sam-mechanism/vina-matched"
RAW, PREP, OUT = BASE / "raw", BASE / "prepared", BASE / "analysis"
OUT.mkdir(parents=True, exist_ok=True)
SEEDS = (172904, 483271, 806519)
VDW = {"C": 1.70, "N": 1.55, "O": 1.52, "S": 1.80, "P": 1.80, "F": 1.47,
       "CL": 1.75, "BR": 1.85, "I": 1.98}
FOCUS = {39, 40, 43, 47, 144, 145, 173, 174, 175, 195, 198, 199, 200,
         228, 229, 230, 231, 232, 233, 234}


def parse_models(path: Path):
    models, rows, score, mode = [], [], None, None
    for line in path.read_text().splitlines():
        if line.startswith("MODEL"):
            rows, score, mode = [], None, int(line.split()[1])
        elif line.startswith("REMARK VINA RESULT:"):
            score = float(line.split()[3])
        elif line.startswith(("ATOM", "HETATM")):
            p = line.split()
            rows.append({"name": line[12:16].strip(), "xyz": np.array([float(line[30:38]), float(line[38:46]), float(line[46:54])]),
                         "type": p[-1], "charge": float(p[-2])})
        elif line.startswith("ENDMDL") and rows:
            models.append({"mode": mode, "score": score, "atoms": rows})
    return models


def receptor(path: Path):
    protein, sam = [], []
    for line in path.read_text().splitlines():
        if not line.startswith(("ATOM", "HETATM")):
            continue
        p, res = line.split(), line[17:20].strip()
        atom = {"name": line[12:16].strip(), "res": res, "chain": line[21:22].strip(),
                "number": int(line[22:26]), "xyz": np.array([float(line[30:38]), float(line[38:46]), float(line[46:54])]),
                "type": p[-1]}
        (sam if res == "SAM" else protein).append(atom)
    return protein, sam


def heavy(atoms):
    return [a for a in atoms if a["type"] not in {"H", "HD"}]


def element(ad_type: str):
    t = ad_type.upper()
    if t in {"A", "C"}: return "C"
    if t in {"N", "NA"}: return "N"
    if t in {"O", "OA"}: return "O"
    if t in {"S", "SA"}: return "S"
    return t


def distances(a, b):
    return np.linalg.norm(np.array([x["xyz"] for x in a])[:, None, :] -
                          np.array([x["xyz"] for x in b])[None, :, :], axis=2)


def fibonacci_sphere(n=256):
    i = np.arange(n); phi = math.pi * (3.0 - math.sqrt(5.0))
    y = 1 - 2 * (i + .5) / n; r = np.sqrt(1-y*y); theta = phi*i
    return np.column_stack((np.cos(theta)*r, y, np.sin(theta)*r))


SPHERE = fibonacci_sphere()


def ligand_sasa_reduction(lig, protein):
    lig = heavy(lig); protein = heavy(protein)
    lc = np.array([a["xyz"] for a in lig]); lr = np.array([VDW.get(element(a["type"]), 1.7) for a in lig]) + 1.4
    pc = np.array([a["xyz"] for a in protein]); pr = np.array([VDW.get(element(a["type"]), 1.7) for a in protein]) + 1.4
    iso = bound = 0.0
    for i, (center, rad) in enumerate(zip(lc, lr)):
        pts = center + rad * SPHERE
        other = np.arange(len(lc)) != i
        open_iso = ~np.any(np.linalg.norm(pts[:, None, :] - lc[None, other, :], axis=2) < lr[other][None, :], axis=1)
        # Only nearby receptor atoms can occlude the ligand sphere.
        near = np.linalg.norm(pc-center, axis=1) < (rad + pr + 1.0)
        open_bound = open_iso & ~np.any(np.linalg.norm(pts[:, None, :] - pc[None, near, :], axis=2) < pr[near][None, :], axis=1)
        area = 4*math.pi*rad*rad
        iso += area * open_iso.mean(); bound += area * open_bound.mean()
    return 100.0 * (iso-bound)/iso if iso else 0.0


def ligand_mapping(state):
    sdf = PREP / ("netarsudil_CID66599893_neutral.sdf" if state == "neutral" else "netarsudil_CID66599893_monocation_pH7.4.sdf")
    mol = Chem.RemoveHs(Chem.SDMolSupplier(str(sdf), removeHs=False)[0])
    source = heavy(parse_models_like_single(PREP / f"netarsudil_{state}.pdbqt"))
    conf = mol.GetConformer(); unused = set(range(mol.GetNumAtoms())); mapping = []
    for atom in source:
        xyz = atom["xyz"]
        j = min(unused, key=lambda k: np.linalg.norm(xyz-np.array(conf.GetAtomPosition(k))))
        unused.remove(j); mapping.append(j)
    autos = mol.GetSubstructMatches(mol, uniquify=False, maxMatches=1000)
    props = AllChem.MMFFGetMoleculeProperties(mol, mmffVariant="MMFF94s")
    ref = Chem.Mol(mol); AllChem.MMFFOptimizeMolecule(ref, mmffVariant="MMFF94s", maxIters=1000)
    ref_energy = AllChem.MMFFGetMoleculeForceField(ref, props).CalcEnergy()
    return mol, mapping, autos, props, ref_energy


def parse_models_like_single(path):
    rows = []
    for line in path.read_text().splitlines():
        if line.startswith("ATOM"):
            p=line.split(); rows.append({"name":line[12:16].strip(), "xyz":np.array([float(line[30:38]),float(line[38:46]),float(line[46:54])]), "type":p[-1]})
    return rows


def strain_and_coords(model, template):
    mol, mapping, _, props, ref_energy = template
    m = Chem.Mol(mol); conf = m.GetConformer()
    for row, idx in zip(heavy(model["atoms"]), mapping):
        x,y,z = row["xyz"]; conf.SetAtomPosition(idx, (float(x),float(y),float(z)))
    ff = AllChem.MMFFGetMoleculeForceField(m, props)
    energy = ff.CalcEnergy()
    coords = np.array(conf.GetPositions())
    return energy-ref_energy, coords


def symmetry_rmsd(a, b, autos):
    # Fixed receptor frame: permute chemically equivalent labels but do not superpose.
    return min(float(np.sqrt(np.mean(np.sum((a-np.array([b[j] for j in perm]))**2, axis=1)))) for perm in autos)


def clusters(records, autos, cutoff=2.5):
    groups = [[i] for i in range(len(records))]
    while True:
        choices=[]
        for i in range(len(groups)):
            for j in range(i+1,len(groups)):
                d=max(symmetry_rmsd(records[a]["rdcoords"], records[b]["rdcoords"], autos) for a in groups[i] for b in groups[j])
                if d <= cutoff: choices.append((d,i,j))
        if not choices: break
        _,i,j=min(choices); groups[i]+=groups[j]; groups.pop(j)
    return sorted(groups,key=lambda g:(-len(g),min(g)))


def contacts(protein, lig):
    out=[]
    for a in heavy(protein):
        d=min(np.linalg.norm(a["xyz"]-b["xyz"]) for b in heavy(lig))
        if d <= 4.0: out.append((a["chain"],a["number"],a["res"],d))
    best={}
    for ch,n,res,d in out: best[(ch,n,res)]=min(d,best.get((ch,n,res),999))
    return sorted((*k,v) for k,v in best.items())


def write_csv(path, rows):
    if not rows: return
    with path.open("w",newline="") as f:
        w=csv.DictWriter(f,fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)


def main():
    gates=json.loads((ROOT/"research/mettl7-netarsudil-sam-mechanism/frozen_admissibility_gates.json").read_text())
    pose_rows=[]; family_rows=[]; contact_rows=[]
    for enzyme in ("7A","7B"):
        protein,sam=receptor(PREP/f"METTL{enzyme}_SAM_receptor.pdbqt")
        hp,hs=heavy(protein),heavy(sam)
        for state in ("neutral","monocation"):
            templ=ligand_mapping(state); records=[]
            for seed in SEEDS:
                for model in parse_models(RAW/f"{enzyme}_{state}_seed{seed}.pdbqt"):
                    model["seed"]=seed
                    hl=heavy(model["atoms"]); dp=distances(hl,hp); ds=distances(hl,hs)
                    strain,rdcoords=strain_and_coords(model,templ); model["rdcoords"]=rdcoords
                    burial=ligand_sasa_reduction(hl,hp)
                    model.update(protein_pairs_lt_1p8=int((dp<1.8).sum()), protein_pairs_1p8_2p2=int(((dp>=1.8)&(dp<2.2)).sum()),
                                 sam_min_distance=float(ds.min()), sam_pairs_lt_2=int((ds<2.0).sum()), sam_pairs_2_2p5=int(((ds>=2)&(ds<2.5)).sum()),
                                 strain=strain, burial=burial)
                    model["physical_pass"]=(model["protein_pairs_lt_1p8"]==0 and model["sam_pairs_lt_2"]==0 and strain<=15 and burial>=20)
                    records.append(model)
            groups=clusters(records,templ[2])
            for fi,g in enumerate(groups,1):
                members=[records[i] for i in g]; good=[m for m in members if m["physical_pass"]]
                seeds=sorted({m["seed"] for m in good}); admissible=len(good)>=3 and len(seeds)>=2
                rep=min(good or members,key=lambda x:x["score"]); hl=heavy(rep["atoms"])
                minsam=rep["sam_min_distance"]
                relation="OVERLAPPING" if minsam<2 else "PARTIALLY_OVERLAPPING" if minsam<2.5 else "ADJACENT" if minsam<=5 else "DISTINCT"
                family_rows.append({"enzyme":enzyme,"state":state,"family":fi,"population_all":len(members),"physical_pass_population":len(good),
                    "physical_pass_seed_count":len(seeds),"physical_pass_seeds":";".join(map(str,seeds)),"admissible":admissible,
                    "representative_seed":rep["seed"],"representative_mode":rep["mode"],"vina_score_min":min(m["score"] for m in members),
                    "vina_score_mean":sum(m["score"] for m in members)/len(members),"sam_min_distance_a":minsam,"site_relative_to_sam":relation,
                    "burial_reduction_percent":rep["burial"],"strain_kcal_mol":rep["strain"],"protein_pairs_lt_1p8":rep["protein_pairs_lt_1p8"],
                    "sam_pairs_lt_2":rep["sam_pairs_lt_2"]})
                for ch,n,res,d in contacts(hp,hl):
                    contact_rows.append({"enzyme":enzyme,"state":state,"family":fi,"admissible":admissible,"chain":ch,"residue_number":n,"residue_name":res,"minimum_distance_a":d,"focus_region":n in FOCUS})
            membership={i:fi for fi,g in enumerate(groups,1) for i in g}
            for i,m in enumerate(records):
                pose_rows.append({"enzyme":enzyme,"state":state,"seed":m["seed"],"mode":m["mode"],"vina_score":m["score"],"family":membership[i],
                    "protein_pairs_lt_1p8":m["protein_pairs_lt_1p8"],"protein_pairs_1p8_2p2":m["protein_pairs_1p8_2p2"],
                    "sam_min_distance_a":m["sam_min_distance"],"sam_pairs_lt_2":m["sam_pairs_lt_2"],"sam_pairs_2_2p5":m["sam_pairs_2_2p5"],
                    "strain_kcal_mol":m["strain"],"burial_reduction_percent":m["burial"],"physical_pass":m["physical_pass"]})
    write_csv(OUT/"pose_metrics.csv",pose_rows); write_csv(OUT/"family_results.csv",family_rows); write_csv(OUT/"family_contacts.csv",contact_rows)
    result={}
    for enzyme in ("7A","7B"):
        fam=[r for r in family_rows if r["enzyme"]==enzyme and r["admissible"]]
        compatible=[r for r in fam if r["sam_min_distance_a"]>=2.5]
        status="YES" if compatible else ("NO" if fam else "INDETERMINATE")
        relations=sorted({r["site_relative_to_sam"] for r in fam})
        result[enzyme]={"NETARSUDIL_SAM_COMPATIBLE":status,"NETARSUDIL_SITE_RELATIVE_TO_SAM":relations[0] if len(relations)==1 else "INDETERMINATE",
                        "admissible_families":len(fam),"admissible_compatible_families":len(compatible)}
    (OUT/"classification.json").write_text(json.dumps({"frozen_gates":gates,"results":result},indent=2)+"\n")
    print(json.dumps(result,indent=2))


if __name__ == "__main__": main()
