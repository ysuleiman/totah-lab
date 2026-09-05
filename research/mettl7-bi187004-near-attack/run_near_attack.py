#!/usr/bin/env python3
# DEPRECATED FOR V2 USE (2026-09-05): near-attack geometry in this script is superseded by
# athena.tmt.NearAttackGeometry / NearAttackAssessor / EnsembleNacAnalyzer (Java).
# See ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md. Retained for historical regression reproduction only.
"""Bounded BI-187004 N-methylation near-attack feasibility experiment."""
from __future__ import annotations
import csv, hashlib, json, math, sys
from pathlib import Path
import numpy as np
from rdkit import Chem
from rdkit.Chem import AllChem

ROOT=Path(__file__).resolve().parents[2]; HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'analysis/dcmb'))
import same_site_pose_analysis as pose

RUN_KEY='METTL7_BI187004_N_METHYL_NEAR_ATTACK_V1_2026_09_03'
SEED=187004; DISTANCES=(2.8,3.0,3.2); ROTATIONS=240; CONFORMERS=8
BACK={'N','CA','C','O','OXT'}
TAUTOMERS={
 'TAUTOMER_1':{'smiles':'N#Cc1ccc2c(c1)[C@H]1CCCN(C(=O)c3ccc4[nH]cnc4c3)[C@H]1C2','N1':19,'N3':21,'protonated':'N1'},
 'TAUTOMER_2':{'smiles':'N#Cc1ccc2c(c1)[C@H]1CCCN(C(=O)c3ccc4nc[nH]c4c3)[C@H]1C2','N1':19,'N3':21,'protonated':'N3'},
}
THRESHOLDS={'distance_range_A':[2.8,3.2],'angle_min_deg':150.0,'protein_severe_clash_A':1.8,
 'sam_severe_clash_A':2.0,'pocket_containment_fraction_min':0.70,'ligand_strain_low_kcal_mol_max':15.0,
 'sidechain_rms_displacement_A_max':1.0,'sidechain_max_displacement_A_max':2.5,
 'sidechain_bond_deviation_A_max':0.15,'minimum_independent_starts':3}

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def atoms(path,residue=None):
 out=[]
 for line in path.read_text().splitlines():
  if not line.startswith(('ATOM  ','HETATM')): continue
  if residue and line[17:20].strip()!=residue: continue
  out.append((line[12:16].strip(),line[17:20].strip(),line[21:22].strip(),int(line[22:26]),line[76:78].strip() or line[12:14].strip(),np.array([float(line[30:38]),float(line[38:46]),float(line[46:54])]),line))
 return out
def random_rotation(rng):
 q=rng.normal(size=4); q/=np.linalg.norm(q); w,x,y,z=q
 return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
def angle(a,b,c):
 u=a-b;v=c-b;return math.degrees(math.acos(np.clip(float(u@v)/(np.linalg.norm(u)*np.linalg.norm(v)),-1,1)))
def fit_b_to_a():
 a=ROOT/'resources/shared-resources/src/main/resources/Q9H8H3/Q9H8H3_TMT1A_HUMAN.pdb'; b=ROOT/'experiments/METTL7B-v6_diffdock/target_protein.pdb'
 sa,ra=pose.ca_sequence(a);sb,rb=pose.ca_sequence(b);pairs=pose.align_sequences(sb,sa)
 return pose.kabsch(np.array([rb[i][5] for i,j in pairs]),np.array([ra[j][5] for i,j in pairs]))
def pocket_clouds():
 p=Path('/Users/yazan/artifacts/UP000005640_9606_HUMAN_v6_pockets/fpocket-human/AF-Q6UX53-F1-model_v6-1472429501895029362/AF-Q6UX53-F1-model_v6_out/pockets/pocket1_vert.pqr')
 b=pose.spheres(p);r,t=fit_b_to_a();return {'7A':b@r+t,'7B':b}
def systems():
 return {p:ROOT/'analysis/dcmb/sam_state/validated'/f'WT_METTL{p}_SAM_BOUND.pdb' for p in ('7A','7B')}
def ensemble(spec,offset):
 mol=Chem.AddHs(Chem.MolFromSmiles(spec['smiles']));params=AllChem.ETKDGv3();params.randomSeed=SEED+offset;params.pruneRmsThresh=.35
 ids=list(AllChem.EmbedMultipleConfs(mol,numConfs=CONFORMERS,params=params));props=AllChem.MMFFGetMoleculeProperties(mol,mmffVariant='MMFF94s'); energies=[]
 for cid in ids:
  AllChem.MMFFOptimizeMolecule(mol,mmffVariant='MMFF94s',confId=cid,maxIters=1000)
  energies.append(AllChem.MMFFGetMoleculeForceField(mol,props,confId=cid).CalcEnergy())
 heavy=Chem.RemoveHs(mol);return heavy,energies,min(energies)
def metrics(xyz,protein,sam,cloud,nidx,ce,sd):
 pd=np.linalg.norm(xyz[:,None,:]-protein[None,:,:],axis=2);sdist=np.linalg.norm(xyz[:,None,:]-sam[None,:,:],axis=2)
 within=np.min(np.linalg.norm(xyz[:,None,:]-cloud[None,:,:],axis=2),axis=1)<=4
 return {'n_to_sam_methyl_distance_A':float(np.linalg.norm(xyz[nidx]-ce)),'n_c_s_approach_angle_deg':angle(xyz[nidx],ce,sd),
  'protein_min_distance_A':float(pd.min()),'protein_clashes_lt_1p8':int((pd<1.8).sum()),'sam_min_distance_A':float(sdist.min()),
  'sam_clashes_lt_2p0':int((sdist<2).sum()),'pocket_containment_fraction':float(np.mean(within))}
def select(rows,xyzs,n=5):
 order=sorted(range(len(rows)),key=lambda i:(rows[i]['protein_clashes_lt_1p8']+rows[i]['sam_clashes_lt_2p0'],-rows[i]['pocket_containment_fraction'],rows[i]['ligand_strain_kcal_mol'],-rows[i]['protein_min_distance_A']))
 picked=[]
 for i in order:
  if all(pose.rmsd(xyzs[i],xyzs[j])>=2 for j in picked):picked.append(i)
  if len(picked)==n:break
 return picked
def relax_sidechains(pa,lig,sam,mobile_res,steps=2500):
 xyz=np.array([a[5] for a in pa]);start=xyz.copy();mobile=np.array([i for i,a in enumerate(pa) if (a[2],a[3],a[1]) in mobile_res and a[0] not in BACK]);fixed=np.array([i for i in range(len(pa)) if i not in set(mobile)])
 bonds=[]
 for x,i in enumerate(mobile):
  for j in mobile[x+1:]:
   if pa[i][2:4]==pa[j][2:4] and np.linalg.norm(start[i]-start[j])<1.9:bonds.append((i,j,float(np.linalg.norm(start[i]-start[j]))))
 m=np.zeros_like(xyz);v=np.zeros_like(xyz);lr=.006
 for _ in range(steps):
  g=np.zeros_like(xyz);g[mobile]+=4*(xyz[mobile]-start[mobile])
  for env,k,cut in ((lig,80.,2.1),(sam,80.,2.1),(xyz[fixed],50.,2.1)):
   dlt=xyz[mobile,None,:]-env[None,:,:];d=np.linalg.norm(dlt,axis=2);safe=np.maximum(d,1e-4);mask=d<cut
   g[mobile]+=np.sum(np.where(mask[:,:,None],-2*k*(cut-d)[:,:,None]*dlt/safe[:,:,None],0),axis=1)
  for i,j,d0 in bonds:
   dv=xyz[i]-xyz[j];d=max(np.linalg.norm(dv),1e-6);x=120*(d-d0)*dv/d;g[i]+=x;g[j]-=x
  m=.9*m+.1*g;v=.999*v+.001*g*g;xyz[mobile]-=lr*(m[mobile]/.1)/(np.sqrt(v[mobile]/.001)+1e-8)
 pd=np.linalg.norm(lig[:,None,:]-xyz[None,:,:],axis=2);sdist=np.linalg.norm(lig[:,None,:]-sam[None,:,:],axis=2);disp=np.linalg.norm(xyz-start,axis=1)
 return xyz,{'protein_clashes_after':int((pd<1.8).sum()),'sam_clashes_after':int((sdist<2).sum()),'sidechain_rms_displacement_A':float(np.sqrt(np.mean(disp[mobile]**2))) if len(mobile) else 0.,'sidechain_max_displacement_A':float(disp[mobile].max()) if len(mobile) else 0.,'max_sidechain_bond_deviation_A':max((abs(np.linalg.norm(xyz[i]-xyz[j])-d0) for i,j,d0 in bonds),default=0.),'mobile_atoms':len(mobile)}
def write_complex(path,pa,pxyz,sam_lines,mol,lig):
 lines=[]
 for a,p in zip(pa,pxyz):
  x=a[6];lines.append(x[:30]+f'{p[0]:8.3f}{p[1]:8.3f}{p[2]:8.3f}'+x[54:])
 lines+=['TER']+[a[6] for a in sam_lines]+['TER'];serial=max(int(x[6:11]) for x in lines if x.startswith(('ATOM','HETATM')))+1
 for i,(a,p) in enumerate(zip(mol.GetAtoms(),lig),serial):lines.append(f"HETATM{i:5d} {a.GetSymbol()+str(i-serial+1):>4s} BI1 L   1    {p[0]:8.3f}{p[1]:8.3f}{p[2]:8.3f}  1.00  0.00          {a.GetSymbol():>2s}")
 path.write_text('\n'.join(lines+['TER','END','']))
def main():
 HERE.mkdir(parents=True,exist_ok=True);(HERE/'starting_states').mkdir(exist_ok=True);(HERE/'relaxed_states').mkdir(exist_ok=True)
 protocol={'run_key':RUN_KEY,'classification':'bounded near-attack feasibility; not affinity or catalysis proof','seed':SEED,'distances_A':DISTANCES,'angle_sampling_deg':[150,180],'rotations_per_distance':ROTATIONS,'conformers_requested':CONFORMERS,'thresholds':THRESHOLDS,'backbone':'fixed','sam':'canonical Saez-2015 coordinates fixed','ligand':'rigid internally during placement; distinct pre-minimized conformers sample ligand flexibility','scientific_settings_frozen_before_outcomes':True,'new_qm':False,'md':False,'gpu':False}
 (HERE/'protocol.json').write_text(json.dumps(protocol,indent=2)+'\n')
 clouds=pocket_clouds();starts=[];relaxrows=[];results=[];sysmap=systems();rng=np.random.default_rng(SEED)
 for ti,(tname,spec) in enumerate(TAUTOMERS.items()):
  mol,energies,emin=ensemble(spec,ti+1)
  for paralog,bound in sysmap.items():
   all_atoms=atoms(bound);pa=[a for a in all_atoms if a[1]!='SAM' and a[4]!='H'];sa=[a for a in all_atoms if a[1]=='SAM' and a[4]!='H'];protein=np.array([a[5] for a in pa]);sam=np.array([a[5] for a in sa]);ce=next(a[5] for a in sa if a[0]=='CE');sd=next(a[5] for a in sa if a[0]=='SD');axis=(ce-sd)/np.linalg.norm(ce-sd)
   for nlabel in ('N1','N3'):
    nidx=spec[nlabel];rows=[];xyzs=[]
    for ci in range(mol.GetNumConformers()):
     conf=mol.GetConformer(ci);raw=np.array([[conf.GetAtomPosition(i).x,conf.GetAtomPosition(i).y,conf.GetAtomPosition(i).z] for i in range(mol.GetNumAtoms())]);center=raw-raw[nidx]
     for distance in DISTANCES:
      for ri in range(ROTATIONS):
       trial=rng.normal(size=3);perp=trial-axis*float(trial@axis)
       if np.linalg.norm(perp)<1e-8:continue
       perp/=np.linalg.norm(perp);theta=math.acos(rng.uniform(math.cos(math.radians(30)),1));attack=axis*math.cos(theta)+perp*math.sin(theta);xyz=center@random_rotation(rng)+ce+attack*distance
       met=metrics(xyz,protein,sam,clouds[paralog],nidx,ce,sd);rows.append({'conformer':ci,'rotation':ri,'target_distance_A':distance,'ligand_strain_kcal_mol':energies[ci]-emin,**met});xyzs.append(xyz)
    chosen=select(rows,xyzs);passes=[]
    for rank,i in enumerate(chosen,1):
     row=rows[i];xyz=xyzs[i];mobile={(a[2],a[3],a[1]) for a in pa if np.min(np.linalg.norm(xyz-a[5],axis=1))<4.0}
     pxyz,rr=relax_sidechains(pa,xyz,sam,mobile);ret=(2.75<=row['n_to_sam_methyl_distance_A']<=3.25 and row['n_c_s_approach_angle_deg']>=150)
     valid=(rr['protein_clashes_after']==0 and rr['sam_clashes_after']==0 and row['pocket_containment_fraction']>=THRESHOLDS['pocket_containment_fraction_min'] and rr['max_sidechain_bond_deviation_A']<=THRESHOLDS['sidechain_bond_deviation_A_max'] and ret)
     key=f'{paralog}_{tname}_{nlabel}_start{rank}';starts.append({'key':key,'paralog':paralog,'tautomer':tname,'nitrogen':nlabel,'nitrogen_protonated':nlabel==spec['protonated'],'chemically_accepting_in_fixed_tautomer':nlabel!=spec['protonated'],**row})
     relaxrows.append({'key':key,'paralog':paralog,'tautomer':tname,'nitrogen':nlabel,'retained_productive_geometry':ret,'valid_after_relaxation':valid,**rr})
     write_complex(HERE/'starting_states'/f'{key}.pdb',pa,np.array([a[5] for a in pa]),sa,mol,xyz);write_complex(HERE/'relaxed_states'/f'{key}.pdb',pa,pxyz,sa,mol,xyz)
     if valid:passes.append((row,rr,key))
    chemical=[x for x in passes if nlabel!=spec['protonated']]
    if chemical:
     best=min(chemical,key=lambda x:(x[0]['ligand_strain_kcal_mol'],x[1]['sidechain_rms_displacement_A']))
     if best[0]['ligand_strain_kcal_mol']>15:classification='PRODUCTIVE_GEOMETRY_HIGH_STRAIN'
     elif best[1]['sidechain_rms_displacement_A']<=.35 and best[1]['sidechain_max_displacement_A']<=1.0:classification='PRODUCTIVE_GEOMETRY_FEASIBLE_LOW_STRAIN'
     else:classification='PRODUCTIVE_GEOMETRY_FEASIBLE_WITH_LOCAL_REORGANIZATION'
    elif nlabel==spec['protonated'] and passes:classification='INDETERMINATE'
    elif any(x['retained_productive_geometry'] for x in relaxrows if x['paralog']==paralog and x['tautomer']==tname and x['nitrogen']==nlabel):classification='PRODUCTIVE_GEOMETRY_CLASHED'
    else:classification='PRODUCTIVE_GEOMETRY_NOT_RETAINED'
    results.append({'paralog':paralog,'tautomer':tname,'nitrogen':nlabel,'nitrogen_state':'PROTONATED_NONACCEPTOR' if nlabel==spec['protonated'] else 'UNPROTONATED_ACCEPTOR','classification':classification,'starts_tested':len(rows),'independent_starts_relaxed':len(chosen),'valid_geometric_starts':len(passes),'chemically_competent_starts':len(chemical),'best_state':best[2] if chemical else '','minimum_ligand_strain_kcal_mol':min((x[0]['ligand_strain_kcal_mol'] for x in chemical),default=''),'minimum_sidechain_rms_displacement_A':min((x[1]['sidechain_rms_displacement_A'] for x in chemical),default='')})
 def write(name,rows):
  with (HERE/name).open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
 write('bi187004_near_attack_starting_states.csv',starts);write('bi187004_near_attack_relaxation_metrics.csv',relaxrows);write('bi187004_near_attack_results.csv',results)
 productive=[r for r in results if r['classification'].startswith('PRODUCTIVE_GEOMETRY_FEASIBLE')];lowest=min(productive,key=lambda r:float(r['minimum_ligand_strain_kcal_mol'])) if productive else None
 by={(r['nitrogen'],r['paralog']):any(x['nitrogen']==r['nitrogen'] and x['paralog']==r['paralog'] and x['classification'].startswith('PRODUCTIVE_GEOMETRY_FEASIBLE') for x in results) for r in results}
 a=any(r['paralog']=='7A' and r['classification'].startswith('PRODUCTIVE_GEOMETRY_FEASIBLE') for r in results);b=any(r['paralog']=='7B' and r['classification'].startswith('PRODUCTIVE_GEOMETRY_FEASIBLE') for r in results)
 common={n for n in ('N1','N3') if by.get((n,'7A'),False) and by.get((n,'7B'),False)}
 hypothesis='BOTH_PRODUCTIVE_SAME_N' if a and b and common else 'BOTH_PRODUCTIVE_DIFFERENT_N' if a and b else 'A_ONLY_PRODUCTIVE_GEOMETRY' if a else 'B_ONLY_PRODUCTIVE_GEOMETRY' if b else 'NEITHER_PRODUCTIVE_GEOMETRY'
 report=['# BI 187004 N-methylation near-attack feasibility','',f'Run `{RUN_KEY}` tested {len(starts)} independently selected starting placements after bounded sampling. This is a geometric/strain/clash experiment, not affinity or proof of catalysis.','', '## Per-combination results','', '| Paralog | Tautomer | N | fixed-tautomer state | Class | competent starts | minimum strain (kcal/mol) | minimum side-chain RMS displacement (Å) |','|---|---|---|---|---|---:|---:|---:|']
 for r in results:report.append(f"| {r['paralog']} | {r['tautomer']} | {r['nitrogen']} | {r['nitrogen_state']} | {r['classification']} | {r['chemically_competent_starts']}/{r['independent_starts_relaxed']} | {r['minimum_ligand_strain_kcal_mol'] if r['minimum_ligand_strain_kcal_mol'] != '' else 'N/A'} | {r['minimum_sidechain_rms_displacement_A'] if r['minimum_sidechain_rms_displacement_A'] != '' else 'N/A'} |")
 tied=';'.join(r['best_state'] for r in productive if float(r['minimum_ligand_strain_kcal_mol'])==float(lowest['minimum_ligand_strain_kcal_mol'])) if lowest else 'NONE'
 report+=['','## Required return','',f"`N1_PRODUCTIVE_7A = {str(by.get(('N1','7A'),False)).lower()}`",'',f"`N1_PRODUCTIVE_7B = {str(by.get(('N1','7B'),False)).lower()}`",'',f"`N3_PRODUCTIVE_7A = {str(by.get(('N3','7A'),False)).lower()}`",'',f"`N3_PRODUCTIVE_7B = {str(by.get(('N3','7B'),False)).lower()}`",'',f"`LOWEST_STRAIN_PRODUCTIVE_STATE = FOUR_WAY_TIE: {tied}`",'',f"`A_B_PRODUCTIVE_GEOMETRY_DIFFERENCE = {hypothesis}`",'',f"`REGIOSELECTIVITY_PREDICTION = {'NO_UNIQUE_REGIOSELECTIVITY_FROM_GEOMETRY' if a and b else hypothesis}`",'',f"`DOES_CURRENT_STATIC_DOCKING_FAILURE_REFLECT_HARD_GEOMETRIC_EXCLUSION = {'NO' if productive else 'YES_WITHIN_BOUNDED_PROTOCOL'}`",'', '`BIOLOGICAL_CLAIM_AUTHORIZED = NO_UNTIL_RECOMBINANT_EXPERIMENT`','', 'Protonated `[nH]` placements are retained as geometric controls but cannot be called chemically competent without changing the fixed tautomer/protonation state. Force-field or geometric relaxation energies are not binding affinities.']
 (HERE/'BI187004_N_METHYLATION_NEAR_ATTACK_FEASIBILITY.md').write_text('\n'.join(report)+'\n')
 files=[]
 for p in sorted(HERE.rglob('*')):
  if p.is_file() and p.name not in {'SHA256SUMS','persist.sql'}:files.append((sha(p),str(p.relative_to(HERE))))
 (HERE/'SHA256SUMS').write_text('\n'.join(f'{h}  {p}' for h,p in files)+'\n')
 conclusion=f'{hypothesis}; biological claim unauthorized pending recombinant experiment'
 q=lambda x:"'"+str(x).replace("'","''")+"'"
 sql=['BEGIN;',"""CREATE TABLE IF NOT EXISTS docking.mettl7_bi187004_near_attack (run_key varchar(100) NOT NULL REFERENCES docking.mettl7_computational_run(run_key),paralog varchar(8) NOT NULL,tautomer varchar(24) NOT NULL,nitrogen varchar(4) NOT NULL,nitrogen_state varchar(32) NOT NULL,classification varchar(80) NOT NULL,starts_tested integer NOT NULL,independent_starts_relaxed integer NOT NULL,valid_geometric_starts integer NOT NULL,chemically_competent_starts integer NOT NULL,best_state text,minimum_ligand_strain_kcal_mol double precision,minimum_sidechain_rms_displacement_a double precision,PRIMARY KEY(run_key,paralog,tautomer,nitrogen));"""]
 sql.append(f"INSERT INTO docking.mettl7_computational_run(run_key,title,method,method_version,classification,report_path,input_path,completed_on,protocol,conclusion) VALUES ({q(RUN_KEY)},{q('BI 187004 N-methylation near-attack feasibility')},{q('bounded constrained geometry and local relaxation')},{q('protocol v1')},{q(hypothesis)},{q(str((HERE/'BI187004_N_METHYLATION_NEAR_ATTACK_FEASIBILITY.md').relative_to(ROOT)))},{q(str((HERE/'protocol.json').relative_to(ROOT)))},'2026-09-03',{q(json.dumps(protocol,separators=(',',':')))}::jsonb,{q(conclusion)}) ON CONFLICT(run_key) DO UPDATE SET classification=EXCLUDED.classification,protocol=EXCLUDED.protocol,conclusion=EXCLUDED.conclusion;")
 sql.append(f'DELETE FROM docking.mettl7_bi187004_near_attack WHERE run_key={q(RUN_KEY)};')
 for r in results:
  st='NULL' if r['minimum_ligand_strain_kcal_mol']=='' else str(r['minimum_ligand_strain_kcal_mol']);sr='NULL' if r['minimum_sidechain_rms_displacement_A']=='' else str(r['minimum_sidechain_rms_displacement_A'])
  sql.append(f"INSERT INTO docking.mettl7_bi187004_near_attack VALUES ({q(RUN_KEY)},{q(r['paralog'])},{q(r['tautomer'])},{q(r['nitrogen'])},{q(r['nitrogen_state'])},{q(r['classification'])},{r['starts_tested']},{r['independent_starts_relaxed']},{r['valid_geometric_starts']},{r['chemically_competent_starts']},{q(r['best_state'])},{st},{sr});")
 sql+=['COMMIT;'];(HERE/'persist.sql').write_text('\n'.join(sql)+'\n')
 print(json.dumps({'run_key':RUN_KEY,'hypothesis':hypothesis,'results':results},indent=2))
if __name__=='__main__':main()
