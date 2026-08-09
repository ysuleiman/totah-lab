#!/usr/bin/env python3
"""Independent evidence channels for the focused multi-seed validation."""
from __future__ import annotations
import csv,json,math
from collections import defaultdict,deque
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[3]; HERE=Path(__file__).resolve().parent
import sys
sys.path.insert(0,str(ROOT/'analysis/dcmb/dcmb_tsl_interference'))
import analyze_interference as tsi

TSL={
 '7A':[ROOT/f'analysis/dcmb/tsl_conformational_response/WT_METTL7A_SAM_TSL_relaxed_{i}.pdb' for i in range(1,6)],
 '7B':[ROOT/f'analysis/dcmb/mettl7b_selectivity/WT_METTL7B_SAM_TSL_fixed_{i}.pdb' for i in range(1,7)],
}
SPHERES_7B=ROOT/'analysis/dcmb/sar_experiment/receptors/METTL7B_accepted_pocket_spheres.pqr'
RECEPTOR_PDB={p:ROOT/f'analysis/dcmb/sar_experiment/receptors/WT_METTL{p}_SAM.pdb' for p in ('7A','7B')}

def pdbqt_models(path):
 models=[]; current=[]
 for line in path.read_text().splitlines():
  if line.startswith('MODEL'):current=[]
  elif line.startswith(('ATOM  ','HETATM')):
   token=line.split()[-1].upper()
   if token!='H':current.append({'xyz':np.array([float(line[30:38]),float(line[38:46]),float(line[46:54])])})
  elif line.startswith('ENDMDL') and current:models.append(current);current=[]
 return models

def read_spheres(path):
 return np.array([[float(x[30:38]),float(x[38:46]),float(x[46:54])] for x in path.read_text().splitlines() if x.startswith(('ATOM  ','HETATM'))])

def principal_axis(coords):
 _,_,vh=np.linalg.svd(coords-coords.mean(0),full_matrices=False);return vh[0]

def write_csv(path,rows):
 if not rows:return
 with path.open('w',newline='') as f:
  w=csv.DictWriter(f,fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)

def affinity(path):
 return [float(x.split()[3]) for x in path.read_text().splitlines() if x.startswith('REMARK VINA RESULT:')]

def pdb_atoms(path,res=None):
 a=tsi.atom_records(path)
 if res is not None:a=[x for x in a if x['res']==res]
 return a

def xyz(a):return np.array([x['xyz'] for x in a])

def ca_map(path):
 return {(a['chain'],a['num']):a['xyz'] for a in pdb_atoms(path) if a['name']=='CA' and a['res'] not in {'SAM','TSL'}}

def transform_superpocket():
 b=ca_map(RECEPTOR_PDB['7B']); a=ca_map(RECEPTOR_PDB['7A']); keys=sorted(set(a)&set(b))
 mobile=np.array([b[k] for k in keys]); ref=np.array([a[k] for k in keys])
 fit,r,t=tsi.kabsch_fit(mobile,ref); cloud=read_spheres(SPHERES_7B)
 return {'7B':cloud,'7A':cloud@r+t}, {'matched_CA':len(keys),'fit_RMSD_A':fit,'rotation':r.tolist(),'translation':t.tolist()}

def sphere_overlap_counts(a,b,radius,spacing=.5):
 return tsi.shared_volume(a,b,radius,spacing)

def cluster(entries,threshold=2.0):
 n=len(entries); graph=[[] for _ in range(n)]
 for i in range(n):
  for j in range(i+1,n):
   if entries[i]['coords'].shape==entries[j]['coords'].shape and tsi.rmsd(entries[i]['coords'],entries[j]['coords'])<threshold:
    graph[i].append(j); graph[j].append(i)
 seen=set(); comps=[]
 for i in range(n):
  if i in seen:continue
  q=deque([i]); seen.add(i); comp=[]
  while q:
   x=q.popleft(); comp.append(x)
   for y in graph[x]:
    if y not in seen:seen.add(y);q.append(y)
  comps.append(comp)
 comps.sort(key=lambda c:(-len(c),min(entries[i]['score'] for i in c)))
 assignment={}
 for f,comp in enumerate(comps,1):
  for i in comp:assignment[i]=f
 return assignment,comps

def main():
 clouds,transform=transform_superpocket()
 tsl_states={}; frame=[]
 for para,paths in TSL.items():
  rec_ca=ca_map(RECEPTOR_PDB[para])
  states=[]
  for rank,path in enumerate(paths,1):
   ta=pdb_atoms(path); sam=[a for a in ta if a['res']=='SAM']; ligand=[a for a in ta if a['res']=='TSL']
   tca={(a['chain'],a['num']):a['xyz'] for a in ta if a['name']=='CA' and a['res'] not in {'SAM','TSL'}}
   keys=sorted(set(rec_ca)&set(tca)); raw=tsi.rmsd(np.array([rec_ca[k] for k in keys]),np.array([tca[k] for k in keys]))
   smap={a['name']:a['xyz'] for a in sam}; sulfur=next(a['xyz'] for a in ligand if a['element'].upper()=='S')
   states.append({'rank':rank,'tsl':xyz(ligand),'sulfur':sulfur,'sam_ce':smap['CE'],'path':path})
   frame.append({'paralog':para,'tsl_state':rank,'matched_CA':len(keys),'raw_CA_RMSD_A':raw,'transformation_applied':False,'source':str(path)})
  tsl_states[para]=states
 write_csv(HERE/'tsl_frame_validation.csv',frame)

 entries_by=defaultdict(list)
 for path in sorted((HERE/'raw').glob('*.pdbqt')):
  state,cid,seedpart=path.stem.split('__'); para=state[:2]; seed=int(seedpart[1:]); poses=pdbqt_models(path); scores=affinity(path)
  for rank,(pose,score) in enumerate(zip(poses,scores),1):
   entries_by[(para,cid)].append({'paralog':para,'compound_id':cid,'seed':seed,'rank':rank,'score':score,'pose':pose,'coords':np.array([a['xyz'] for a in pose]),'source':str(path)})

 assignments={}; family_rows=[]
 for key,entries in entries_by.items():
  ass,comps=cluster(entries)
  for i,f in ass.items():assignments[(key,i)]=f
  for f,comp in enumerate(comps,1):
   seeds=sorted({entries[i]['seed'] for i in comp}); scores=[entries[i]['score'] for i in comp]
   family_rows.append({'paralog':key[0],'compound_id':key[1],'family_id':f,'population':len(comp),'seed_count':len(seeds),'seeds':';'.join(map(str,seeds)),'recurrent_across_seeds':len(seeds)>=2,'mean_affinity_kcal_mol':float(np.mean(scores)),'min_affinity_kcal_mol':min(scores),'representative_seed':entries[min(comp,key=lambda i:entries[i]['score'])]['seed'],'representative_rank':entries[min(comp,key=lambda i:entries[i]['score'])]['rank']})
 write_csv(HERE/'pose_families.csv',family_rows)

 pose_rows=[]; tsl_rows=[]
 for key,entries in entries_by.items():
  para,cid=key; rec=pdb_atoms(RECEPTOR_PDB[para]); sam=xyz([a for a in rec if a['res']=='SAM']); sphere=clouds[para]
  center=sphere.mean(0); mouth=sphere[np.argmax(np.linalg.norm(sphere-center,axis=1))]-center; mouth/=np.linalg.norm(mouth)
  tsl_union=np.vstack([s['tsl'] for s in tsl_states[para]])
  for i,e in enumerate(entries):
   lig=e['coords']; sd=tsi.pair_distances(lig,sam); sphere_d=tsi.pair_distances(lig,sphere).min(1)
   tdist=tsi.pair_distances(lig,tsl_union).min(1); substrate_fraction=float(np.mean(tdist<=1.7)); centroid=lig.mean(0); axis=principal_axis(lig)
   onsite=float(np.mean(sphere_d<=4))>=.7
   region='SUBSTRATE_FACING' if substrate_fraction>0 else ('WIDER_ESCAPE_SUBPOCKET' if onsite else 'OUTSIDE_TARGET_SITE')
   cls='OUTSIDE_TARGET_SITE' if not onsite else ('SAM_STERIC_CONFLICT' if np.sum(sd<2)>0 else ('SAM_CONTACTING' if np.sum(sd<4)>0 else 'SAM_COMPATIBLE'))
   pose_rows.append({'paralog':para,'compound_id':cid,'seed':e['seed'],'rank':e['rank'],'family_id':assignments[(key,i)],'affinity_kcal_mol':e['score'],'sam_min_distance_A':float(sd.min()),'sam_pairs_lt2A':int(np.sum(sd<2)),'sam_pairs_lt2p5A':int(np.sum(sd<2.5)),'sam_pairs_lt4A':int(np.sum(sd<4)),'sam_classification':cls,'on_site':onsite,'superpocket_atom_fraction_4A':float(np.mean(sphere_d<=4)),'occupied_alpha_spheres_4A':int(np.sum(tsi.pair_distances(sphere,lig).min(1)<=4)),'centroid_x':centroid[0],'centroid_y':centroid[1],'centroid_z':centroid[2],'orientation_axis_x':axis[0],'orientation_axis_y':axis[1],'orientation_axis_z':axis[2],'mouth_projection_A':float((centroid-center)@mouth),'substrate_facing_atom_fraction':substrate_fraction,'directional_subpocket':region,'source_pose':e['source']})
   for ts in tsl_states[para]:
    d=tsi.pair_distances(lig,ts['tsl']); seg,proj=tsi.point_segment(lig,ts['sulfur'],ts['sam_ce']); between=(proj>=0)&(proj<=1)
    tsl_rows.append({'paralog':para,'compound_id':cid,'seed':e['seed'],'rank':e['rank'],'family_id':assignments[(key,i)],'tsl_state':ts['rank'],'minimum_ligand_TSL_distance_A':float(d.min()),'pairs_lt2A':int(np.sum(d<2)),'pairs_lt2p5A':int(np.sum(d<2.5)),'core_overlap_A3':sphere_overlap_counts(lig,ts['tsl'],1.0),'shared_molecular_envelope_A3':sphere_overlap_counts(lig,ts['tsl'],1.7),'ligand_atoms_inside_TSL_envelope':int(np.sum(d.min(1)<=1.7)),'minimum_distance_to_catalytic_corridor_A':float(seg.min()),'atoms_between_and_within_1p5A':int(np.sum(between&(seg<=1.5))),'atoms_between_and_within_2A':int(np.sum(between&(seg<=2.0))),'atoms_between_and_within_2p5A':int(np.sum(between&(seg<=2.5))),'corridor_occupied_fraction_r2':tsi.corridor_fraction(lig,ts['sulfur'],ts['sam_ce'],2.0),'tsl_source':str(ts['path'])})
 write_csv(HERE/'per_seed_pose_metrics.csv',pose_rows); write_csv(HERE/'tsl_interference_metrics.csv',tsl_rows)

 # Aggregate compound/paralog evidence without merging channels into a score.
 families=defaultdict(list)
 for r in family_rows:families[(r['paralog'],r['compound_id'])].append(r)
 posegroup=defaultdict(list); tgroup=defaultdict(list)
 for r in pose_rows:posegroup[(r['paralog'],r['compound_id'])].append(r)
 for r in tsl_rows:tgroup[(r['paralog'],r['compound_id'])].append(r)
 summary=[]
 for key,ps in sorted(posegroup.items()):
  ts=tgroup[key]; seed_best=[]
  for seed in sorted({r['seed'] for r in ps}):seed_best.append(min(r['affinity_kcal_mol'] for r in ps if r['seed']==seed))
  summary.append({'paralog':key[0],'compound_id':key[1],'seed_best_affinity_mean':float(np.mean(seed_best)),'seed_best_affinity_sd':float(np.std(seed_best)),'seed_best_affinity_range':max(seed_best)-min(seed_best),'pose_count':len(ps),'on_site_fraction':float(np.mean([r['on_site']=='True' if isinstance(r['on_site'],str) else r['on_site'] for r in ps])),'sam_compatible_fraction':float(np.mean([r['sam_classification']=='SAM_COMPATIBLE' for r in ps])),'sam_conflict_fraction':float(np.mean([r['sam_classification']=='SAM_STERIC_CONFLICT' for r in ps])),'substrate_facing_pose_fraction':float(np.mean([r['directional_subpocket']=='SUBSTRATE_FACING' for r in ps])),'wider_escape_pose_fraction':float(np.mean([r['directional_subpocket']=='WIDER_ESCAPE_SUBPOCKET' for r in ps])),'tsl_pairing_count':len(ts),'tsl_direct_conflict_fraction':float(np.mean([r['pairs_lt2A']>0 for r in ts])),'mean_core_overlap_A3':float(np.mean([r['core_overlap_A3'] for r in ts])),'mean_shared_envelope_A3':float(np.mean([r['shared_molecular_envelope_A3'] for r in ts])),'corridor_blocked_fraction':float(np.mean([r['atoms_between_and_within_2A']>0 for r in ts])),'pose_family_count':len(families[key]),'recurrent_family_count':sum(r['recurrent_across_seeds'] in (True,'True') for r in families[key]),'largest_family_population':max(r['population'] for r in families[key])})
 write_csv(HERE/'matched_compound_summary.csv',summary)
 cross=[]
 for key,ps in sorted(posegroup.items()):
  rank1=[r for r in ps if r['rank']==1]
  family_counts=defaultdict(set)
  for r in ps:family_counts[r['family_id']].add(r['seed'])
  for seed in sorted({r['seed'] for r in ps}):
   seedposes=[r for r in ps if r['seed']==seed]; best=min(seedposes,key=lambda r:r['affinity_kcal_mol'])
   cross.append({'paralog':key[0],'compound_id':key[1],'seed':seed,'best_affinity_kcal_mol':best['affinity_kcal_mol'],'rank1_family_id':best['family_id'],'rank1_sam_classification':best['sam_classification'],'rank1_directional_subpocket':best['directional_subpocket'],'rank1_family_seed_coverage':len(family_counts[best['family_id']])})
 write_csv(HERE/'cross_seed_reproducibility.csv',cross)

 fb=[]
 for fr in family_rows:
  key=(fr['paralog'],fr['compound_id']); fid=fr['family_id']
  ps=[r for r in posegroup[key] if int(r['family_id'])==int(fid)]; ts=[r for r in tgroup[key] if int(r['family_id'])==int(fid)]
  conflict=float(np.mean([r['pairs_lt2A']>0 for r in ts])); escape=float(np.mean([r['directional_subpocket']=='WIDER_ESCAPE_SUBPOCKET' for r in ps]))
  behavior='SUBSTRATE_CONFLICTING' if conflict>=.5 else ('ESCAPE_LIKE' if escape>=.5 and conflict<.2 else 'MIXED_OR_AMBIGUOUS')
  fb.append({**fr,'on_site_fraction':float(np.mean([r['on_site'] in (True,'True') for r in ps])),'substrate_facing_fraction':float(np.mean([r['directional_subpocket']=='SUBSTRATE_FACING' for r in ps])),'escape_fraction':escape,'tsl_direct_conflict_fraction':conflict,'mean_core_overlap_A3':float(np.mean([r['core_overlap_A3'] for r in ts])),'corridor_blocked_fraction':float(np.mean([r['atoms_between_and_within_2A']>0 for r in ts])),'behavior':behavior})
 write_csv(HERE/'pose_family_behavior.csv',fb)

 parent=lambda c:'BA' if c=='BA' else ('DCMB' if c.startswith('DCMB') else '24DCMB')
 parent_rows=[]
 for para in ('7A','7B'):
  for par in ('BA','DCMB','24DCMB'):
   variants=[k for k in posegroup if k[0]==para and parent(k[1])==par]
   ps=[r for k in variants for r in posegroup[k]]; ts=[r for k in variants for r in tgroup[k]]
   seedbest=[]
   for seed in (1,7,42):seedbest.append(min(r['affinity_kcal_mol'] for r in ps if r['seed']==seed))
   parent_rows.append({'paralog':para,'compound':par,'prepared_variants':';'.join(k[1] for k in variants),'seed_best_affinity_mean':float(np.mean(seedbest)),'seed_best_affinity_sd':float(np.std(seedbest)),'on_site_fraction':float(np.mean([r['on_site'] in (True,'True') for r in ps])),'sam_compatible_fraction':float(np.mean([r['sam_classification']=='SAM_COMPATIBLE' for r in ps])),'substrate_facing_pose_fraction':float(np.mean([r['directional_subpocket']=='SUBSTRATE_FACING' for r in ps])),'escape_pose_fraction':float(np.mean([r['directional_subpocket']=='WIDER_ESCAPE_SUBPOCKET' for r in ps])),'tsl_direct_conflict_fraction':float(np.mean([r['pairs_lt2A']>0 for r in ts])),'mean_core_overlap_A3':float(np.mean([r['core_overlap_A3'] for r in ts])),'mean_shared_envelope_A3':float(np.mean([r['shared_molecular_envelope_A3'] for r in ts])),'corridor_blocked_fraction':float(np.mean([r['atoms_between_and_within_2A']>0 for r in ts]))})
 write_csv(HERE/'matched_parent_summary.csv',parent_rows)
 (HERE/'analysis_manifest.json').write_text(json.dumps({'clustering':'connected components of direct same-atom-order pose RMSD <2.0 A; family count not prespecified','superpocket':'WT7B accepted 197-sphere pocket; rigid CA-homology transfer to WT7A','superpocket_transfer':transform,'on_site':'at least 70% ligand heavy atoms within 4 A of homologous superpocket spheres','substrate_facing':'at least one ligand atom inside the 1.7 A envelope of any retained productive TSL state','escape':'on-site and zero atoms inside union productive-TSL 1.7 A envelope','shared_volume':'0.5 A grid, same radius definitions as prior DCMB/TSL analysis','tsl_states':'reused unchanged: five 7A relaxed productive states and six 7B fixed productive states'},indent=2)+'\n')

if __name__=='__main__':main()
