# DEPRECATED FOR V2 USE (2026-09-05): near-attack geometry in this script is superseded by
# athena.tmt.NearAttackGeometry / NearAttackAssessor / EnsembleNacAnalyzer (Java).
# See ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md. Retained for historical regression reproduction only.
"""Constrained TSL placement on the validated SAM methyl-transfer axis."""
from pathlib import Path
import csv,hashlib,json,math,sys
import numpy as np
from rdkit import Chem
from rdkit.Chem import AllChem

ROOT=Path(__file__).resolve().parents[3]; HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'analysis/dcmb')); import same_site_pose_analysis as b
SMILES='C[C@]12CCC(=O)C=C1C[C@H]([C@@H]3[C@@H]2CC[C@]4([C@H]3CC[C@@]45CCC(=O)O5)C)S'
DISTANCES=(2.8,3.0,3.2); ROTATIONS=600; SEED=20260808

def receptor(path): return [a for a in b.pdb_atoms(path) if a[1]!='SAM' and a[4]!='H']
def sam(path): return [a for a in b.pdb_atoms(path) if a[1]=='SAM' and a[4]!='H']
def random_rotation(rng):
 q=rng.normal(size=4); q/=np.linalg.norm(q); w,x,y,z=q
 return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
def write_sdf(mol,xyz,path,props):
 q=Chem.Mol(mol); conf=q.GetConformer()
 for i,p in enumerate(xyz): conf.SetAtomPosition(i,p)
 for k,v in props.items(): q.SetProp(k,str(v))
 w=Chem.SDWriter(str(path)); w.write(q); w.close()
def write_complex(rec_path,sam_path,mol,xyz,path):
 lines=[x for x in rec_path.read_text().splitlines() if not x.startswith(('END','CONECT'))]; lines+=['TER']
 lines += [x for x in sam_path.read_text().splitlines() if x.startswith('HETATM') and x[17:20].strip()=='SAM']; lines+=['TER']
 serial=max(int(x[6:11]) for x in lines if x.startswith(('ATOM  ','HETATM')))+1
 for i,(atom,p) in enumerate(zip(mol.GetAtoms(),xyz),serial): lines.append(f"HETATM{i:5d} {atom.GetSymbol()+str(i-serial+1):>4s} TSL T   1    {p[0]:8.3f}{p[1]:8.3f}{p[2]:8.3f}  1.00  0.00          {atom.GetSymbol():>2s}")
 path.write_text('\n'.join(lines+['TER','END','']))
def fit_ba():
 a=ROOT/'resources/shared-resources/src/main/resources/Q9H8H3/Q9H8H3_TMT1A_HUMAN.pdb'; q=ROOT/'experiments/METTL7B-v6_diffdock/target_protein.pdb'; sa,ra=b.ca_sequence(a); sb,rb=b.ca_sequence(q); pairs=b.align_sequences(sb,sa); r,t=b.kabsch(np.array([rb[i][5] for i,j in pairs]),np.array([ra[j][5] for i,j in pairs])); return r,t
def evaluate(xyz,prot,samxyz,cloud,tsl_s,sam_s,methyl):
 pd=np.linalg.norm(xyz[:,None,:]-prot[None,:,:],axis=2); sd=np.linalg.norm(xyz[:,None,:]-samxyz[None,:,:],axis=2)
 attack=xyz[tsl_s]-methyl; leaving=sam_s-methyl; angle=math.degrees(math.acos(np.clip(float(attack@leaving)/(np.linalg.norm(attack)*np.linalg.norm(leaving)),-1,1)))
 within=np.min(np.linalg.norm(xyz[:,None,:]-cloud[None,:,:],axis=2),axis=1)<=4
 return {'tsl_s_to_sam_methyl_A':float(np.linalg.norm(attack)),'attack_angle_TSL_S_Cmethyl_SAM_S_deg':angle,'protein_min_A':float(pd.min()),'protein_pairs_lt_2A':int((pd<2).sum()),'sam_min_nonreactive_A':float(sd.min()),'sam_pairs_lt_2A':int((sd<2).sum()),'superpocket_atom_fraction':float(np.mean(within)),'occupied_alpha_spheres':len(b.occupied(cloud,xyz))}
def main():
 HERE.mkdir(parents=True,exist_ok=True); canddir=HERE/'candidates'; canddir.mkdir(exist_ok=True)
 mol=Chem.MolFromSmiles(SMILES); mol=Chem.AddHs(mol); params=AllChem.ETKDGv3(); params.randomSeed=SEED; params.pruneRmsThresh=.35; ids=AllChem.EmbedMultipleConfs(mol,numConfs=12,params=params)
 for cid in ids: AllChem.MMFFOptimizeMolecule(mol,confId=cid,maxIters=500)
 heavy=Chem.RemoveHs(mol); tsl_s=next(a.GetIdx() for a in heavy.GetAtoms() if a.GetSymbol()=='S')
 rec_a=ROOT/'resources/shared-resources/src/main/resources/Q9H8H3/Q9H8H3_TMT1A_HUMAN.pdb'; bound_a=ROOT/'analysis/dcmb/sam_state/validated/WT_METTL7A_SAM_BOUND.pdb'; sa=sam(bound_a); sam_s=next(a[5] for a in sa if a[0]=='SD'); methyl=next(a[5] for a in sa if a[0]=='CE'); axis=(methyl-sam_s)/np.linalg.norm(methyl-sam_s)
 prot=np.array([a[5] for a in receptor(rec_a)]); prot=prot[np.linalg.norm(prot-methyl,axis=1)<16]; samxyz=np.array([a[5] for a in sa]); cloudb=b.spheres(Path('/Users/yazan/artifacts/UP000005640_9606_HUMAN_v6_pockets/fpocket-human/AF-Q6UX53-F1-model_v6-1472429501895029362/AF-Q6UX53-F1-model_v6_out/pockets/pocket1_vert.pqr')); r,t=fit_ba(); clouda=cloudb@r+t
 rng=np.random.default_rng(SEED); rows=[]; xyz_by=[]; diagnostics=[]; diagnostic_xyz=[]
 for ci,cid in enumerate(range(heavy.GetNumConformers())):
  conf=heavy.GetConformer(cid); raw=np.array([[conf.GetAtomPosition(i).x,conf.GetAtomPosition(i).y,conf.GetAtomPosition(i).z] for i in range(heavy.GetNumAtoms())]); centered=raw-raw[tsl_s]
  for distance in DISTANCES:
   for ri in range(ROTATIONS):
    # Uniformly sample the chemically acceptable 150-180 degree
    # nucleophile-C-S backside-attack cone rather than forcing 180 degrees.
    trial=rng.normal(size=3); perpendicular=trial-axis*float(trial@axis); perpendicular/=np.linalg.norm(perpendicular)
    theta=math.acos(rng.uniform(math.cos(math.radians(30)),1.0)); attack_axis=axis*math.cos(theta)+perpendicular*math.sin(theta); target=methyl+attack_axis*distance
    xyz=centered@random_rotation(rng)+target; metrics=evaluate(xyz,prot,samxyz,clouda,tsl_s,sam_s,methyl)
    diagnostics.append({'conformer':ci,'rotation':ri,'target_distance_A':distance,**metrics})
    diagnostic_xyz.append(xyz)
    if metrics['protein_pairs_lt_2A']==0 and metrics['sam_pairs_lt_2A']==0 and metrics['superpocket_atom_fraction']>=.70:
     row={'candidate_index':len(rows)+1,'conformer':ci,'rotation':ri,'target_distance_A':distance,**metrics}; rows.append(row); xyz_by.append(xyz)
 if not rows:
  fields=list(diagnostics[0]);
  with (HERE/'failed_candidate_metrics.csv').open('w',newline='') as f: w=csv.DictWriter(f,fieldnames=fields); w.writeheader(); w.writerows(diagnostics)
  best_indices=sorted(range(len(diagnostics)),key=lambda i:(diagnostics[i]['protein_pairs_lt_2A']+diagnostics[i]['sam_pairs_lt_2A'],-diagnostics[i]['superpocket_atom_fraction'],-diagnostics[i]['protein_min_A']))[:20]; best=[diagnostics[i] for i in best_indices]
  for rank,i in enumerate(best_indices[:3],1):
   write_sdf(heavy,diagnostic_xyz[i],canddir/f'FAILED_TSL_7A_near_catalytic_{rank}.sdf',diagnostics[i]); write_complex(rec_a,bound_a,heavy,diagnostic_xyz[i],canddir/f'FAILED_WT_METTL7A_SAM_TSL_{rank}.pdb')
  summary={'status':'FAIL','identity':'7alpha-thiospironolactone','pubchem_cid':119472,'smiles':SMILES,'conformer_method':'RDKit ETKDGv3 + MMFF before constrained placement','seed':SEED,'conformers':len(ids),'rotations_per_distance':ROTATIONS,'attack_distances_A':DISTANCES,'attack_angle_range_deg':[150,180],'tested':len(ids)*len(DISTANCES)*ROTATIONS,'retained':0,'criteria':{'protein_pairs_lt_2A':0,'sam_pairs_lt_2A':0,'superpocket_atom_fraction_min':.70},'sam_source':str(bound_a),'sam_sha256':hashlib.sha256(bound_a.read_bytes()).hexdigest(),'zero_protein_clash':sum(x['protein_pairs_lt_2A']==0 for x in diagnostics),'zero_sam_clash':sum(x['sam_pairs_lt_2A']==0 for x in diagnostics),'zero_both_clash':sum(x['protein_pairs_lt_2A']==0 and x['sam_pairs_lt_2A']==0 for x in diagnostics),'max_containment_zero_both':max((x['superpocket_atom_fraction'] for x in diagnostics if x['protein_pairs_lt_2A']==0 and x['sam_pairs_lt_2A']==0),default=None),'best_failed_candidates':best}
  (HERE/'search_summary.json').write_text(json.dumps(summary,indent=2)+'\n'); print(json.dumps(summary,indent=2)); return
 order=sorted(range(len(rows)),key=lambda i:(-rows[i]['superpocket_atom_fraction'],-rows[i]['protein_min_A'],-rows[i]['sam_min_nonreactive_A'],abs(rows[i]['tsl_s_to_sam_methyl_A']-3.0)))
 # Preserve geometrically distinct retained families at 2 A direct RMSD.
 families=[]
 for i in order:
  if not any(b.rmsd(xyz_by[i],xyz_by[g[0]])<2 for g in families): families.append([i])
  else: next(g for g in families if b.rmsd(xyz_by[i],xyz_by[g[0]])<2).append(i)
 selected=[]
 for fi,g in enumerate(families[:8],1):
  i=g[0]; rows[i]['family']=fi; rows[i]['family_population']=len(g); selected.append((fi,i))
  write_sdf(heavy,xyz_by[i],canddir/f'TSL_7A_catalytic_family_{fi}.sdf',rows[i]); write_complex(rec_a,bound_a,heavy,xyz_by[i],canddir/f'WT_METTL7A_SAM_TSL_family_{fi}.pdb')
 fields=list(rows[0])+['family','family_population']
 with (HERE/'candidate_metrics.csv').open('w',newline='') as f: w=csv.DictWriter(f,fieldnames=fields,extrasaction='ignore'); w.writeheader(); w.writerows(rows)
 with (HERE/'selected_families.csv').open('w',newline='') as f: w=csv.DictWriter(f,fieldnames=fields,extrasaction='ignore'); w.writeheader(); w.writerows(rows[i] for _,i in selected)
 manifest={'status':'PASS','identity':'7alpha-thiospironolactone','pubchem_cid':119472,'smiles':SMILES,'conformer_method':'RDKit ETKDGv3 + MMFF (ligand-only conformer generation before placement)','seed':SEED,'conformers':len(ids),'rotations_per_distance':ROTATIONS,'attack_distances_A':DISTANCES,'tested':len(ids)*len(DISTANCES)*ROTATIONS,'retained':len(rows),'families_retained':len(selected),'criteria':{'protein_pairs_lt_2A':0,'sam_pairs_lt_2A':0,'superpocket_atom_fraction_min':.70,'attack_angle_deg_min':150},'sam_source':str(bound_a),'sam_sha256':hashlib.sha256(bound_a.read_bytes()).hexdigest()}
 (HERE/'search_summary.json').write_text(json.dumps(manifest,indent=2)+'\n'); print(manifest); print([rows[i] for _,i in selected])
if __name__=='__main__': main()
