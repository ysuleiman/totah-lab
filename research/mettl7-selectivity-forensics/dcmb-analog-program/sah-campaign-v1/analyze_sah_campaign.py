#!/usr/bin/env python3
# DEPRECATED FOR V2 USE (2026-09-05): near-attack geometry in this script is superseded by
# athena.tmt.NearAttackGeometry / NearAttackAssessor / EnsembleNacAnalyzer (Java).
# See ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md. Retained for historical regression reproduction only.
"""Analyze the matched DCMB SAM/SAH and protonation sensitivity matrix."""
from __future__ import annotations

import csv
import json
import math
from collections import defaultdict
from pathlib import Path

import numpy as np
from rdkit import Chem

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
SEEDS = (1, 7, 42)
LIGAND_STATES = ("R_NEUTRAL", "R_PROTONATED", "S_NEUTRAL", "S_PROTONATED")
SECTORS = {"7A": {33, 43, 47, 99, 149, 151, 196, 199, 200, 201},
           "7B": {36, 39, 40, 43, 47, 144, 145, 149, 195, 199, 200, 203}}


def write(name: str, rows: list[dict[str, object]]) -> None:
    with (HERE / name).open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(rows)


def pdbqt_models(path: Path) -> list[dict[str, object]]:
    rows, current, score, mode = [], [], math.nan, 0
    for line in path.read_text().splitlines():
        if line.startswith("MODEL"):
            current, score, mode = [], math.nan, int(line.split()[1])
        elif line.startswith("REMARK VINA RESULT:"): score = float(line.split()[3])
        elif line.startswith(("ATOM  ", "HETATM")):
            atom_type = line.split()[-1]
            element = {"A":"C","C":"C","NA":"N","N":"N","OA":"O","O":"O","SA":"S","S":"S","Cl":"Cl","HD":"H","H":"H"}.get(atom_type, line[12:16].strip()[0])
            if element != "H": current.append({"name": line[12:16].strip(), "element": element, "aromatic": atom_type == "A",
                "resname": line[17:20].strip(), "chain": line[21:22].strip(), "resnum": int(line[22:26]),
                "xyz": np.array([float(line[30:38]),float(line[38:46]),float(line[46:54])])})
        elif line.startswith("ENDMDL") and current:
            rows.append({"mode": mode, "score": score, "atoms": current})
    return rows


def receptor_atoms(path: Path) -> list[dict[str, object]]:
    result = []
    for line in path.read_text().splitlines():
        if not line.startswith(("ATOM  ", "HETATM")): continue
        atom_type = line.split()[-1]
        element = {"A":"C","C":"C","NA":"N","N":"N","OA":"O","O":"O","SA":"S","S":"S","Cl":"Cl","HD":"H","H":"H"}.get(atom_type, line[12:16].strip()[0])
        if element != "H": result.append({"name":line[12:16].strip(),"element":element,
            "resname":line[17:20].strip(),"chain":line[21:22].strip(),"resnum":int(line[22:26]),
            "xyz":np.array([float(line[30:38]),float(line[38:46]),float(line[46:54])])})
    return result


def rmsd(a: list[dict[str, object]], b: list[dict[str, object]]) -> float:
    xa=np.array([x["xyz"] for x in a]); xb=np.array([x["xyz"] for x in b])
    return float(np.sqrt(np.mean(np.sum((xa-xb)**2,axis=1))))


def element_matched_spatial_rmsd(a: list[dict[str, object]], b: list[dict[str, object]]) -> float:
    """Permutation-invariant same-frame RMSD for differently prepared protonation forms."""
    if sorted(x["element"] for x in a) != sorted(x["element"] for x in b):
        raise ValueError("heavy-atom elemental composition changed across protonation")
    total=0.0
    for element in sorted({x["element"] for x in a}):
        xa=[x["xyz"] for x in a if x["element"]==element]
        xb=[x["xyz"] for x in b if x["element"]==element]
        cost=[[float(np.sum((x-y)**2)) for y in xb] for x in xa]
        dynamic={0:0.0}
        for row in cost:
            updated={}
            for mask,value in dynamic.items():
                for column,item in enumerate(row):
                    if mask&(1<<column): continue
                    new_mask=mask|(1<<column); candidate=value+item
                    updated[new_mask]=min(updated.get(new_mask,float("inf")),candidate)
            dynamic=updated
        total+=dynamic[(1<<len(xb))-1]
    return float(np.sqrt(total/len(a)))


def clusters(records: list[dict[str, object]], cutoff: float=2.0) -> list[list[int]]:
    groups=[[i] for i in range(len(records))]
    while True:
        options=[]
        for i in range(len(groups)):
            for j in range(i+1,len(groups)):
                d=max(rmsd(records[a]["atoms"],records[b]["atoms"]) for a in groups[i] for b in groups[j])
                if d<=cutoff: options.append((d,i,j))
        if not options: break
        _,i,j=min(options); groups[i].extend(groups[j]); groups.pop(j)
    return sorted(groups,key=lambda g:(-len(g),min(g)))


def pdb_ligand_xyz(path: Path, resname: str) -> np.ndarray:
    xyz=[]
    for line in path.read_text().splitlines():
        if line.startswith(("ATOM  ","HETATM")) and line[17:20].strip()==resname and line[76:78].strip()!="H":
            xyz.append([float(line[30:38]),float(line[38:46]),float(line[46:54])])
    return np.array(xyz)


def tsl_states(paralog: str) -> list[np.ndarray]:
    if paralog=="7A": paths=[ROOT/f"analysis/dcmb/tsl_conformational_response/WT_METTL7A_SAM_TSL_relaxed_{i}.pdb" for i in range(1,6)]
    else: paths=[ROOT/f"analysis/dcmb/mettl7b_selectivity/WT_METTL7B_SAM_TSL_fixed_{i}.pdb" for i in range(1,7)]
    return [pdb_ligand_xyz(path,"TSL") for path in paths]


def ligand_roles(state: str) -> tuple[set[str], str]:
    mol=Chem.RemoveHs(Chem.SDMolSupplier(str(HERE/"ligands"/f"DCMB_{state}.sdf"),removeHs=False)[0])
    aromatic={f"C{a.GetIdx()+1}" for a in mol.GetAtoms() if a.GetIsAromatic()}
    nitrogen=next(f"N{a.GetIdx()+1}" for a in mol.GetAtoms() if a.GetSymbol()=="N")
    return aromatic,nitrogen


def main() -> None:
    manifest=list(csv.DictReader((HERE/"job_manifest.csv").open()))
    receptor_paths={(r["paralog"],r["cofactor"]):Path(r["receptor"]) for r in manifest}
    methyl_points={}
    for paralog in ("7A","7B"):
        sam=receptor_atoms(receptor_paths[(paralog,"SAM")])
        methyl_points[paralog]=next(a["xyz"] for a in sam if a["resname"]=="SAM" and a["name"]=="C9")
    results=[]; representatives={}
    for paralog in ("7A","7B"):
      states=tsl_states(paralog)
      for cofactor in ("SAM","SAH"):
       receptor=receptor_atoms(receptor_paths[(paralog,cofactor)])
       protein=[a for a in receptor if a["resname"] not in {"SAM","SAH"}]
       cof=[a for a in receptor if a["resname"] in {"SAM","SAH"}]
       pxyz=np.array([a["xyz"] for a in protein]); cxyz=np.array([a["xyz"] for a in cof])
       sulfur=next(a["xyz"] for a in cof if a["element"]=="S")
       grouped=defaultdict(list)
       for a in protein: grouped[(a["chain"],a["resnum"],a["resname"])].append(a["xyz"])
       for ligand_state in LIGAND_STATES:
        records=[]
        for seed in SEEDS:
            path=HERE/"raw"/f"{paralog}_{cofactor}__{ligand_state}__s{seed}.pdbqt"
            for model in pdbqt_models(path): model["seed"]=seed; records.append(model)
        groups=clusters(records); dominant=groups[0]; rep_index=min(dominant,key=lambda i:records[i]["score"]); rep=records[rep_index]
        representatives[(paralog,cofactor,ligand_state)]=rep["atoms"]
        lig=np.array([a["xyz"] for a in rep["atoms"]])
        amine=next(a["xyz"] for a in rep["atoms"] if a["element"]=="N")
        ring=np.array([a["xyz"] for a in rep["atoms"] if a["aromatic"]]); ring_cent=ring.mean(0)
        orient=(amine-ring_cent)/np.linalg.norm(amine-ring_cent)
        distances=np.linalg.norm(lig[:,None,:]-pxyz[None,:,:],axis=2)
        contact_list=[]
        for (chain,num,res),xyz in grouped.items():
            d=float(np.min(np.linalg.norm(np.array(xyz)[:,None,:]-lig[None,:,:],axis=2)))
            if d<=4.5: contact_list.append((d,num,res))
        nums={num for _,num,_ in contact_list}
        overlaps=[int(np.sum(np.linalg.norm(lig[:,None,:]-state[None,:,:],axis=2)<2.0)>0) for state in states]
        cof_dist=np.linalg.norm(lig[:,None,:]-cxyz[None,:,:],axis=2)
        methyl=methyl_points[paralog]; va=amine-methyl; vs=sulfur-methyl
        angle=math.degrees(math.acos(np.clip(float(va@vs/np.linalg.norm(va)/np.linalg.norm(vs)),-1,1))) if cofactor=="SAM" else ""
        results.append({"paralog":paralog,"cofactor":cofactor,"ligand_state":ligand_state,
          "stereochemistry":ligand_state[0],"protonation":"protonated" if "PROTONATED" in ligand_state else "neutral",
          "formal_charge":1 if "PROTONATED" in ligand_state else 0,"best_vina_score":min(x["score"] for x in records),
          "seed_rank1_scores":";".join(str(records[i*9]["score"]) for i in range(3)),
          "seed_rank1_mean":round(float(np.mean([records[i*9]["score"] for i in range(3)])),4),
          "seed_rank1_range":round(float(np.ptp([records[i*9]["score"] for i in range(3)])),4),
          "dominant_family_population":len(dominant),"dominant_family_fraction":round(len(dominant)/len(records),4),
          "dominant_family_seed_count":len({records[i]["seed"] for i in dominant}),"family_count":len(groups),
          "centroid_x":round(float(lig.mean(0)[0]),4),"centroid_y":round(float(lig.mean(0)[1]),4),"centroid_z":round(float(lig.mean(0)[2]),4),
          "orientation_ring_to_amine_x":round(float(orient[0]),5),"orientation_ring_to_amine_y":round(float(orient[1]),5),"orientation_ring_to_amine_z":round(float(orient[2]),5),
          "amine_x":round(float(amine[0]),4),"amine_y":round(float(amine[1]),4),"amine_z":round(float(amine[2]),4),
          "dichlorophenyl_centroid_x":round(float(ring_cent[0]),4),"dichlorophenyl_centroid_y":round(float(ring_cent[1]),4),"dichlorophenyl_centroid_z":round(float(ring_cent[2]),4),
          "contacts_4p5A":";".join(f"{res}{num}:{d:.2f}" for d,num,res in sorted(contact_list)),
          "selectivity_sector_contacts":";".join(f"{res}{num}" for d,num,res in sorted(contact_list) if num in SECTORS[paralog]),
          "rear_195_203_contacts":";".join(map(str,sorted(nums&set(range(195,204))))),
          "exit_228_237_contacts":";".join(map(str,sorted(nums&set(range(228,238))))),
          "ligand_atom_burial_fraction_4p5A":round(float(np.mean(np.min(distances,axis=1)<=4.5)),4),
          "protein_min_distance_A":round(float(distances.min()),4),"severe_clash_pairs_lt_2A":int(np.sum(distances<2.0)),
          "cofactor_min_distance_A":round(float(cof_dist.min()),4),"cofactor_pairs_lt_2A":int(np.sum(cof_dist<2.0)),
          "amine_to_sulfur_A":round(float(np.linalg.norm(amine-sulfur)),4),
          "amine_to_transferable_or_vacated_methyl_region_A":round(float(np.linalg.norm(amine-methyl)),4),
          "amine_methyl_sulfur_angle_deg":round(float(angle),3) if angle!="" else "",
          "methyl_geometry_interpretation":"NOT_NEAR_ATTACK" if cofactor=="SAM" and (np.linalg.norm(amine-methyl)>3.5 or angle<150) else ("SAH_METHYL_ABSENT_DISTANCE_TO_VACATED_REGION_ONLY" if cofactor=="SAH" else "GEOMETRIC_CANDIDATE_ONLY"),
          "productive_TSL_volume_overlap_states":sum(overlaps),"productive_TSL_state_count":len(states),
          "occupies_productive_substrate_volume":sum(overlaps)>0})
    write("sah_protonation_condition_results.csv",results)
    by={(r["paralog"],r["cofactor"],r["ligand_state"]):r for r in results}
    comparisons=[]
    for paralog in ("7A","7B"):
      for stereo in ("R","S"):
       for protonation in ("NEUTRAL","PROTONATED"):
        sam=by[(paralog,"SAM",f"{stereo}_{protonation}")]; sah=by[(paralog,"SAH",f"{stereo}_{protonation}")]
        pose_rmsd=rmsd(representatives[(paralog,"SAM",f"{stereo}_{protonation}")],representatives[(paralog,"SAH",f"{stereo}_{protonation}")])
        comparisons.append({"paralog":paralog,"stereochemistry":stereo,"protonation":protonation.lower(),
          "sam_best_vina":sam["best_vina_score"],"sah_best_vina":sah["best_vina_score"],
          "sah_minus_sam_vina":round(float(sah["best_vina_score"])-float(sam["best_vina_score"]),4),
          "dominant_pose_SAM_to_SAH_RMSD_A":round(pose_rmsd,4),
          "pose_family_changed":pose_rmsd>2.0,"productive_volume_occupancy_changed":sam["occupies_productive_substrate_volume"]!=sah["occupies_productive_substrate_volume"]})
    write("sam_vs_sah_comparisons.csv",comparisons)
    protonation_comparisons=[]
    for paralog in ("7A","7B"):
      for cofactor in ("SAM","SAH"):
       for stereo in ("R","S"):
        neutral=by[(paralog,cofactor,f"{stereo}_NEUTRAL")]; protonated=by[(paralog,cofactor,f"{stereo}_PROTONATED")]
        pose_rmsd=element_matched_spatial_rmsd(representatives[(paralog,cofactor,f"{stereo}_NEUTRAL")],representatives[(paralog,cofactor,f"{stereo}_PROTONATED")])
        protonation_comparisons.append({"paralog":paralog,"cofactor":cofactor,"stereochemistry":stereo,
          "neutral_best_vina":neutral["best_vina_score"],"protonated_best_vina":protonated["best_vina_score"],
          "protonated_minus_neutral_vina":round(float(protonated["best_vina_score"])-float(neutral["best_vina_score"]),4),
          "dominant_pose_neutral_to_protonated_RMSD_A":round(pose_rmsd,4),"pose_family_changed":pose_rmsd>2.0,
          "productive_volume_occupancy_changed":neutral["occupies_productive_substrate_volume"]!=protonated["occupies_productive_substrate_volume"]})
    write("protonation_sensitivity_comparisons.csv",protonation_comparisons)
    effects=[]
    for cofactor in ("SAM","SAH"):
      for stereo in ("R","S"):
       for protonation in ("NEUTRAL","PROTONATED"):
        a=by[("7A",cofactor,f"{stereo}_{protonation}")]; b=by[("7B",cofactor,f"{stereo}_{protonation}")]
        effects.append({"cofactor":cofactor,"stereochemistry":stereo,"protonation":protonation.lower(),
          "vina_best_7A":a["best_vina_score"],"vina_best_7B":b["best_vina_score"],
          "delta_7B_minus_7A":round(float(b["best_vina_score"])-float(a["best_vina_score"]),4),
          "native_frame_pose_coordinates_directly_comparable":False})
    write("ab_separation_by_state.csv",effects)
    summary={"conditions":len(results),"docking_jobs":48,"poses":48*9,
      "neutral_sam_sah_effects":{f"{r['paralog']}_{r['stereochemistry']}":r["sah_minus_sam_vina"] for r in comparisons if r["protonation"]=="neutral"},
      "ab_separations":{f"{r['cofactor']}_{r['stereochemistry']}_{r['protonation']}":r["delta_7B_minus_7A"] for r in effects},
      "protonation_pose_family_changes":sum(bool(r["pose_family_changed"]) for r in protonation_comparisons),
      "protonation_comparisons":len(protonation_comparisons),
      "interpretation_limits":["Vina scores are not binding free energies","SAH has no transferable methyl; only homologous-atom and vacated-region geometry is reported","DCMB amine is not an established METTL7 methyl acceptor"]}
    (HERE/"analysis_summary.json").write_text(json.dumps(summary,indent=2)+"\n")


if __name__=="__main__": main()
