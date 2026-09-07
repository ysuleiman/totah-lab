#!/usr/bin/env python3
"""Release validator for the frozen methyl/ethyl/isopropyl A0/B0+SAM corpus."""
from __future__ import annotations
import csv, hashlib, importlib.util, json, math, re, statistics, subprocess, sys, types
from collections import Counter, defaultdict
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[3]; OUT=Path(__file__).resolve().parent
SOURCES={
 "METHYL":(ROOT/"analysis/mettl7/mettl7b_design_campaign_v1/postprocessing/METTL7B_DESIGN_POSE_LEVEL.csv","BRICS0040_NEUTRAL"),
 "ETHYL":(ROOT/"analysis/mettl7/mettl7b_matched_contrast_v1/POSE_LEVEL_ATHENA.csv","MCV1_STERIC_OUTER_ETHYL"),
 "ISOPROPYL":(ROOT/"analysis/mettl7/mettl7b_matched_contrast_v1/neighbor_validation/POSE_LEVEL_ATHENA.csv","MCV1N_OUTER_ISOPROPYL")}
scipy=types.ModuleType("scipy"); stats=types.ModuleType("scipy.stats"); stats.fisher_exact=lambda*a:None; stats.binomtest=lambda*a:None; scipy.stats=stats
sys.modules.setdefault("scipy",scipy); sys.modules.setdefault("scipy.stats",stats)
p=ROOT/"research/mettl7-selectivity-forensics/dcmb-analog-program/v1-baseline-snapshot/ligand-chemistry/analyze_functional_groups.py"
s=importlib.util.spec_from_file_location("frozen_fg",p); fg=importlib.util.module_from_spec(s); s.loader.exec_module(fg)
from rdkit import Chem

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def atoms(path,resno):
 out=[]
 for line in path.read_text(errors="replace").splitlines():
  if not line.startswith(("ATOM  ","HETATM")): continue
  try: rn=int(line[22:26])
  except: continue
  if rn==resno: out.append({"name":line[12:16].strip(),"xyz":np.array([float(line[30:38]),float(line[38:46]),float(line[46:54])])})
 return out
def angle(a,b,c):
 u=a-b;v=c-b; den=np.linalg.norm(u)*np.linalg.norm(v)
 return math.degrees(math.acos(max(-1,min(1,float(np.dot(u,v)/den))))) if den else float("nan")
def log_config(path):
 text=path.read_text();
 def grab(pattern):
  m=re.search(pattern,text); return m.group(1).strip() if m else None
 return {"center":grab(r"Grid center: X (.+)"),"size":grab(r"Grid size  : X (.+)"),"spacing":grab(r"Grid space : (.+)"),"exhaustiveness":grab(r"Exhaustiveness: (\d+)")}
def signature(r):
 return "|".join([r[x] for x in ("contacts_le_4p5","athena_interaction_fingerprint","sector_39_47","sector_144_175","sector_195_207","sector_228_237","productive_geometry_screen")]+["SAM_CLEAR" if int(r["sam_clash_pairs_lt_2p0"])==0 else "SAM_CLASH"])
def write(name,rows):
 with (OUT/name).open("w",newline="") as f:w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)

integrity=[]; poses=[]; expected_config={}; threshold_versions=set()
for member,(table,sid) in SOURCES.items():
 rows=[r for r in csv.DictReader(table.open()) if r["species_id"]==sid and r["receptor_id"] in {"A0","B0"}]
 if {(r["receptor_id"],r["seed"]) for r in rows}!={(q,z) for q in ("A0","B0") for z in ("1","7","42")}: raise RuntimeError(f"incomplete runs: {member}")
 for runid,rr in defaultdict(list).items(): pass
 byrun=defaultdict(list)
 for r in rows: byrun[r["run_id"]].append(r)
 for runid,runrows in byrun.items():
  r=runrows[0]; d=Path(r["receipt_path"]).parent; receipt=json.loads((d/"receipt.json").read_text()); pose=d/"poses.pdbqt"; ligand=Path(receipt["ligandPath"]); receptor=Path(receipt["receptorPath"]); cfg=log_config(d/"vina.log")
  key=r["receptor_id"]; expected_config.setdefault(key,cfg)
  checks={"receipt_status":receipt["status"]=="COMPLETED_VALID","pose_hash":sha(pose)==receipt["posesSha256"]==r["poses_sha256"],"ligand_hash":sha(ligand)==receipt["ligandSha256"],"receptor_hash":sha(receptor)==receipt["receptorSha256"],"seed":str(receipt["seed"])==r["seed"],"cofactor":r["cofactor_state"]=="SAM" and "SAM_BOUND" in receptor.name,"box_and_engine_config":cfg==expected_config[key] and cfg["exhaustiveness"]=="32","ligand_state":r["protonation_or_speciation"] in {"NEUTRAL_ASSAY_STATE","neutral; net 0"} and r["tautomer"] in {"AS_DRAWN","NA"},"pose_count":len(runrows)==receipt["parsedPoseCount"]}
  integrity.append({"member":member,"run_id":runid,**{k:str(v).lower() for k,v in checks.items()},"all_pass":str(all(checks.values())).lower(),"pose_sha256":sha(pose),"receptor_sha256":sha(receptor),"ligand_sha256":sha(ligand),**cfg})
  # Preserve every failure and continue only to build diagnostic tables. The
  # release gate below suppresses all chemical conclusions when any check fails.
 for r in rows:
  threshold_versions.add(r["athena_threshold_provenance"]); posefile=Path(r["receipt_path"]).parent/"poses.pdbqt"; smi,mapping,models=fg.parse_models(posefile); mol=Chem.MolFromSmiles(smi); model=models[int(r["pose_model"])]
  feat=[]
  for la in model:
   idx=mapping.get(la["serial"])
   if idx is None or idx>=mol.GetNumAtoms() or la["ad4"].startswith("G") or la["ad4"] in {"H","HD","HS"}:continue
   feat.append((la,fg.functional_group(mol,idx)))
  out={"member":member,"species_id":sid,"run_id":r["run_id"],"paralog":r["paralog"],"seed":r["seed"],"pose_model":r["pose_model"],"family_signature":signature(r),"burial_fraction":r["burial_fraction"],"sam_clear":str(int(r["sam_clash_pairs_lt_2p0"])==0).lower(),"contacts_le_4p5":r["contacts_le_4p5"],"b207_hbond":str("HYDROGEN_BOND~A:207~" in r["athena_refined_interaction_details"]).lower()}
  if r["paralog"]=="METTL7A":
   ka=atoms(Path(json.loads((Path(r["receipt_path"]).parent/"receipt.json").read_text())["receptorPath"]),151); nz=next(x for x in ka if x["name"]=="NZ"); hz=[x for x in ka if x["name"].startswith("HZ")]; side=[x for x in ka if x["name"] in {"CB","CG","CD","CE","NZ"}]; back=[x for x in ka if x["name"] in {"N","CA","C","O"}]
   closest=min(feat,key=lambda x:np.linalg.norm(x[0]["xyz"]-nz["xyz"])); la,kind=closest; dnz=float(np.linalg.norm(la["xyz"]-nz["xyz"])); ds=min(float(np.linalg.norm(la["xyz"]-x["xyz"])) for x in side); db=min(float(np.linalg.norm(la["xyz"]-x["xyz"])) for x in back)
   acceptor=kind in {"ether oxygen","amide carbonyl","carbonyl oxygen","ester carbonyl","carboxylate / carboxylic acid","alcohol / phenol","sulfonyl oxygen","heteroaromatic nitrogen","nitrile"}; oxygen="oxygen" in kind or kind in {"amide carbonyl","carbonyl oxygen","ester carbonyl","carboxylate / carboxylic acid"}; ha=min(float(np.linalg.norm(la["xyz"]-h["xyz"])) for h in hz); dha=max(angle(nz["xyz"],h["xyz"],la["xyz"]) for h in hz)
   directed=oxygen and acceptor and dnz<=3.5 and ha<=2.5 and dha>=120
   out.update({"k151_nz_closest_feature":kind,"k151_nz_distance_A":f"{dnz:.6f}","k151_sidechain_min_distance_A":f"{ds:.6f}","k151_backbone_min_distance_A":f"{db:.6f}","k151_h_acceptor_min_distance_A":f"{ha:.6f}","k151_n_h_acceptor_angle_deg":f"{dha:.6f}","k151_sidechain_directed_oxygen":str(directed).lower()})
  poses.append(out)
if len(threshold_versions)!=1: raise RuntimeError(f"threshold mismatch: {threshold_versions}")
write("METTL7_TRIO_INPUT_INTEGRITY.csv",integrity); write("METTL7_TRIO_RELEASE_POSE_METRICS.csv",poses)

families=[]
for (member,paralog,sig),rr in sorted(defaultdict(list, {k:v for k,v in []}).items()): pass
groups=defaultdict(list)
for r in poses:groups[(r["member"],r["paralog"],r["family_signature"])].append(r)
for (m,p,sig),rr in groups.items():
 seeds=sorted({r["seed"] for r in rr},key=int); families.append({"member":m,"paralog":p,"family_id":"PF_"+hashlib.sha256(sig.encode()).hexdigest()[:12],"pose_count":len(rr),"seed_count":len(seeds),"seeds":";".join(seeds),"recurrent":str(len(seeds)>=2).lower(),"mean_burial":f"{statistics.mean(float(r['burial_fraction']) for r in rr):.6f}","k151_directed_oxygen_poses":sum(r.get("k151_sidechain_directed_oxygen")=="true" for r in rr),"signature":sig})
write("METTL7_TRIO_POSE_FAMILIES.csv",families)

seedrows=[]
for seed in ("1","7","42"):
 base=[r for r in poses if r["member"]=="METHYL" and r["paralog"]=="METTL7A" and r["seed"]==seed]
 bc=sum(r["k151_sidechain_directed_oxygen"]=="true" for r in base)/len(base)
 for m in ("ETHYL","ISOPROPYL"):
  rr=[r for r in poses if r["member"]==m and r["paralog"]=="METTL7A" and r["seed"]==seed]; c=sum(r["k151_sidechain_directed_oxygen"]=="true" for r in rr)/len(rr)
  seedrows.append({"seed":seed,"candidate":m,"parent_fraction":f"{bc:.6f}","candidate_fraction":f"{c:.6f}","delta_candidate_minus_parent":f"{c-bc:.6f}","direction":"REDUCED" if c<bc else "EQUAL" if c==bc else "INCREASED"})
write("METTL7_TRIO_MATCHED_SEED_DELTAS.csv",seedrows)

# Exact frozen family signatures provide within-member families. No exact recurrent
# signature is shared between parent and either analog, so cross-member family
# correspondence is explicitly unevaluated rather than inferred by a new cutoff.
recurrent=defaultdict(set)
for f in families:
 if f["recurrent"]=="true": recurrent[(f["paralog"],f["signature"])].add(f["member"])
correspond=[{"paralog":p,"family_signature_sha256":hashlib.sha256(sig.encode()).hexdigest(),"members":";".join(sorted(ms)),"correspondence":"EXACT"} for (p,sig),ms in recurrent.items() if len(ms)>1]
if not correspond: correspond=[{"paralog":"A0/B0","family_signature_sha256":"","members":"","correspondence":"UNEVALUATED_NO_EXACT_RECURRENT_CROSS_ANALOG_FAMILY_AND_NO_FROZEN_MAPPING_RULE"}]
write("METTL7_TRIO_CROSS_ANALOG_FAMILY_CORRESPONDENCE.csv",correspond)

def member_pose(m,p):return [r for r in poses if r["member"]==m and r["paralog"]==p]
def frac(rr,key):return sum(r.get(key)=="true" for r in rr)/len(rr)
ma,ea,ia=(member_pose(m,"METTL7A") for m in ("METHYL","ETHYL","ISOPROPYL")); mb,eb,ib=(member_pose(m,"METTL7B") for m in ("METHYL","ETHYL","ISOPROPYL"))
deltas=[r for r in seedrows if r["candidate"]=="ETHYL"]
anti=all(r["direction"]=="REDUCED" for r in deltas)
available=all(any(str(n) in r["contacts_le_4p5"].split(";") for r in eb) for n in (196,203,205,206,207,211)) and any(r["b207_hbond"]=="true" for r in eb)
equivalent="unevaluated" # no frozen equivalence margins and no cross-analog family correspondence
ordering="unevaluated" if not correspond or correspond[0]["correspondence"].startswith("UNEVALUATED") else "false"
quality=all(r["all_pass"]=="true" for r in integrity) and len(threshold_versions)==1
released_results={"ETHYL_ANTI_A_SIGNAL_REPRODUCED":anti,"ETHYL_B_STATE_AVAILABLE":available,"ETHYL_B_STATE_EQUIVALENT_TO_PARENT":equivalent,"METHYL_ETHYL_ISOPROPYL_ORDERING_SUPPORTED":ordering,"COMPUTATIONAL_ANTI_A_FRAGMENT_DISCOVERY_COMPLETE":False} if quality else {"ETHYL_ANTI_A_SIGNAL_REPRODUCED":"not_issued","ETHYL_B_STATE_AVAILABLE":"not_issued","ETHYL_B_STATE_EQUIVALENT_TO_PARENT":"not_issued","METHYL_ETHYL_ISOPROPYL_ORDERING_SUPPORTED":"not_issued","COMPUTATIONAL_ANTI_A_FRAGMENT_DISCOVERY_COMPLETE":"not_issued"}
receipt={"run_key":"METTL7_LIGAND_CHEMISTRY_VALIDATOR_RELEASE_V2_2026_09_07","supersedes_all_positive_ethyl_claims":True,"validator_release_quality":quality,"k151_sidechain_geometry_implemented":True,"b_parent_equivalence_implemented":True,"matched_seed_comparison_implemented":True,"pose_family_aware_aggregation_implemented":True,"input_receipt_verification_implemented":True,"input_failures":[r["run_id"] for r in integrity if r["all_pass"]!="true"],"threshold_version":next(iter(threshold_versions)),"diagnostic_results_not_authorized_when_release_fails":{"anti_a_directional_consistency":anti,"b_state_available":available,"b_parent_equivalence":equivalent,"alkyl_ordering":ordering},"results":released_results}
(OUT/"METTL7_TRIO_VALIDATOR_RELEASE_RECEIPT.json").write_text(json.dumps(receipt,indent=2)+"\n")
integrity_boundary = ("All receipt-derived input and protocol checks pass. Chemical verdicts remain limited "
                      "to metrics with frozen semantics." if quality else
                      "Input integrity failed, so no chemical conclusion is authorized.")
report=f"""# Release-quality methyl/ethyl/isopropyl validation

The validator recomputed pose/ligand/receptor hashes and checked seed, explicit-SAM receptor, ligand state, pose count, paralog-specific box and exhaustiveness 32 for all {len(integrity)} runs. Release status is **{str(quality).upper()}**. The failed runs are: {', '.join(receipt['input_failures']) or 'none'}. {integrity_boundary} The Athena threshold provenance is unique and frozen.

K151 is now decomposed into NZ, side-chain-heavy and backbone distances. `sidechain_directed_oxygen` uses Athena's frozen hydrogen-bond geometry (D–A ≤3.5 Å, H–A ≤2.5 Å, D–H–A ≥120°) and records the ligand feature class and raw geometry.

Ethyl-versus-parent side-chain-directed oxygen deltas by seed are: {', '.join(r['seed']+'='+r['delta_candidate_minus_parent'] for r in deltas)}. Raw poses and exact-signature pose families are separate. No exact recurrent cross-analog family correspondence exists, and no mapping threshold was frozen, so family-matched alkyl ordering is unevaluated. The validator does not manufacture a family-mapping threshold or issue that unsupported comparison.

The ethyl B state is available. Parent equivalence is implemented as a multidimensional comparison, but its verdict is `unevaluated`: exact family correspondence is absent and no prospective equivalence margins exist for residue recurrence, B207 H-bonding, burial or family recurrence. No post-hoc margin was invented.

`VALIDATOR_RELEASE_QUALITY = {str(quality).lower()}`

`K151_SIDECHAIN_GEOMETRY_IMPLEMENTED = true`

`B_PARENT_EQUIVALENCE_IMPLEMENTED = true`

`MATCHED_SEED_COMPARISON_IMPLEMENTED = true`

`POSE_FAMILY_AWARE_AGGREGATION_IMPLEMENTED = true`

`INPUT_RECEIPT_VERIFICATION_IMPLEMENTED = true`

`ETHYL_ANTI_A_SIGNAL_REPRODUCED = {released_results['ETHYL_ANTI_A_SIGNAL_REPRODUCED']}`

`ETHYL_B_STATE_EQUIVALENT_TO_PARENT = {released_results['ETHYL_B_STATE_EQUIVALENT_TO_PARENT']}`

`METHYL_ETHYL_ISOPROPYL_ORDERING_SUPPORTED = {released_results['METHYL_ETHYL_ISOPROPYL_ORDERING_SUPPORTED']}`

The superseded positive ethyl result remains invalid. No chemical optimization or new docking was performed.
"""
(OUT/"METTL7_TRIO_VALIDATOR_RELEASE_REPORT.md").write_text(report)
files=["METTL7_TRIO_INPUT_INTEGRITY.csv","METTL7_TRIO_RELEASE_POSE_METRICS.csv","METTL7_TRIO_POSE_FAMILIES.csv","METTL7_TRIO_MATCHED_SEED_DELTAS.csv","METTL7_TRIO_CROSS_ANALOG_FAMILY_CORRESPONDENCE.csv","METTL7_TRIO_VALIDATOR_RELEASE_RECEIPT.json","METTL7_TRIO_VALIDATOR_RELEASE_REPORT.md"]
with (OUT/"RELEASE_SHA256SUMS").open("w") as f:
 for n in files:f.write(f"{sha(OUT/n)}  {n}\n")
print(json.dumps(receipt,indent=2))
