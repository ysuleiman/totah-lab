#!/usr/bin/env python3
"""Assign predefined pocket states, detect persistent events, and map anchors."""
from __future__ import annotations
import csv,hashlib,json,sys
from collections import Counter,defaultdict
from pathlib import Path
import mdtraj as md
import numpy as np

ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent
FOCUS=ROOT/'analysis/dcmb/focused_validation';SAR=ROOT/'analysis/dcmb/sar_experiment'
sys.path.insert(0,str(FOCUS));import analyze as focused
sys.path.insert(0,str(ROOT/'analysis/dcmb/dcmb_tsl_interference'));import analyze_interference as geom
SYSTEMS=('7A_DCMB_S','7A_24DCMB_R','7B_DCMB_R','7B_24DCMB_R');REPLICAS=(1,2,3)
ANCHORS={'7A':{39,40,43,145,175,195,199,202,231,234},'7B':{39,40,43,145,175,195,198,199,202,232,234}}

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write_csv(p,rows,fields=None):
 if not rows and not fields:return
 with p.open('w',newline='') as f:
  w=csv.DictWriter(f,fieldnames=fields or list(rows[0]));w.writeheader();w.writerows(rows)

def geometry():
 clouds,transfer=focused.transform_superpocket();tsl={}
 for para,paths in focused.TSL.items():tsl[para]=np.vstack([focused.xyz(focused.pdb_atoms(p,'TSL')) for p in paths])
 return clouds,tsl,transfer

def raw_state(lig,cloud,tsl):
 sd=geom.pair_distances(lig,cloud).min(1);td=geom.pair_distances(lig,tsl).min(1);sf=float(np.mean(sd<=4));tf=float(np.mean(td<=1.7));near=float(sd.min())
 if sf>=.7 and tf>0:state='INNER'
 elif sf>=.7:state='ESCAPE'
 elif sf>=.25 or near<=6:state='MOUTH'
 else:state='OUTSIDE'
 return state,sf,tf,near

def persistent(raw,n=5):
 # Confirm a new state only after n identical raw frames; preceding flickers retain
 # the last confirmed state. Initial state is confirmed from frame zero.
 out=[raw[0]]*len(raw);confirmed=raw[0];candidate=None;start=0
 for i in range(1,len(raw)):
  if raw[i]==confirmed:candidate=None;out[i]=confirmed;continue
  if raw[i]!=candidate:candidate=raw[i];start=i
  if i-start+1>=n:
   for j in range(start,i+1):out[j]=candidate
   confirmed=candidate;candidate=None
  else:out[i]=confirmed
 return out

def event_name(a,b):
 return {('INNER','MOUTH'):'INNER_TO_MOUTH',('MOUTH','INNER'):'MOUTH_TO_INNER',('MOUTH','ESCAPE'):'MOUTH_TO_ESCAPE',('ESCAPE','INNER'):'ESCAPE_TO_INNER',('ESCAPE','OUTSIDE'):'ESCAPE_TO_OUTSIDE',('OUTSIDE','INNER'):'OUTSIDE_TO_REENTRY'}.get((a,b),f'{a}_TO_{b}')

def main():
 clouds,tsls,transfer=geometry();frames=[];events=[];contacts=[];manifest=[];representatives=[]
 starting=[];static=list(csv.DictReader(open(FOCUS/'per_seed_pose_metrics.csv')));tstatic=list(csv.DictReader(open(FOCUS/'tsl_interference_metrics.csv')))
 for sid in SYSTEMS:
  meta=json.load(open(HERE/'systems'/sid/'metadata.json'));para=meta['paralog'];cid=meta['compound_id'];sr=next(r for r in static if r['paralog']==para and r['compound_id']==cid and int(r['seed'])==meta['docking_seed'] and int(r['rank'])==meta['docking_rank']);tr=[r for r in tstatic if r['paralog']==para and r['compound_id']==cid and int(r['seed'])==meta['docking_seed'] and int(r['rank'])==meta['docking_rank']]
  cloud=clouds[para];center=cloud.mean(0);mouth=cloud[np.argmax(np.linalg.norm(cloud-center,axis=1))]-center;mouth/=np.linalg.norm(mouth);maxproj=float(np.max((cloud-center)@mouth));cent=np.array([float(sr[f'centroid_{x}']) for x in 'xyz'])
  starting.append({'system_id':sid,'paralog':para,'compound_id':cid,'stereochemistry':meta['stereochemistry'],'pose_family':meta['pose_family'],'docking_seed':meta['docking_seed'],'docking_rank':meta['docking_rank'],'centroid_x':cent[0],'centroid_y':cent[1],'centroid_z':cent[2],'orientation_axis_x':sr['orientation_axis_x'],'orientation_axis_y':sr['orientation_axis_y'],'orientation_axis_z':sr['orientation_axis_z'],'sam_min_distance_A':sr['sam_min_distance_A'],'sam_classification':sr['sam_classification'],'tsl_conflict_states':sum(int(r['pairs_lt2A'])>0 for r in tr),'tsl_states':len(tr),'directional_subpocket':sr['directional_subpocket'],'protein_contacts_4p5A':sr['protein_contact_fingerprint_4p5A'],'mouth_projection_A':sr['mouth_projection_A'],'pocket_depth_A':maxproj-float(sr['mouth_projection_A']),'ligand_formal_charge':meta['ligand_formal_charge'],'receptor':meta['receptor'],'receptor_sha256':meta['receptor_sha256'],'pose_source':meta['pose_source'],'pose_source_sha256':meta['pose_source_sha256']})
  for rep in REPLICAS:
   rd=HERE/'trajectories'/sid/f'replica_{rep}';rm=json.load(open(rd/'metadata.json'));top=HERE/'systems'/sid/'solvated_initial.pdb';dcd=rd/'production.dcd';traj=md.load_dcd(str(dcd),top=str(top));ref=md.load(str(top));ca=traj.topology.select('protein and name CA');traj.superpose(ref,atom_indices=ca,ref_atom_indices=ca)
   lig_idx=np.array(meta['ligand_heavy_indices']);protein_idx=traj.topology.select('protein and not element H');raw=[];metrics=[]
   p_res=[]
   for idx in protein_idx:
    a=traj.topology.atom(int(idx));p_res.append((a.residue.resSeq,a.residue.name))
   for fi in range(traj.n_frames):
    lig=traj.xyz[fi,lig_idx,:]*10;state,sf,tf,near=raw_state(lig,cloud,tsls[para]);raw.append(state);centroid=lig.mean(0);metrics.append((sf,tf,near,float((centroid-center)@mouth),centroid))
   stable=persistent(raw,5)
   for fi,(rstate,state,m) in enumerate(zip(raw,stable,metrics)):
    frames.append({'system_id':sid,'paralog':para,'compound_id':cid,'replica':rep,'simulation_seed':rm['simulation_seed'],'frame':fi,'time_ps':fi*rm['output_interval_ps'],'raw_state':rstate,'persistent_state':state,'superpocket_fraction':m[0],'productive_TSL_envelope_fraction':m[1],'nearest_superpocket_distance_A':m[2],'mouth_projection_A':m[3],'centroid_x':m[4][0],'centroid_y':m[4][1],'centroid_z':m[4][2]})
    lig=traj.xyz[fi,lig_idx,:]*10;prot=traj.xyz[fi,protein_idx,:]*10;dist=geom.pair_distances(lig,prot).min(0);byres=defaultdict(list)
    for (resi,resn),d in zip(p_res,dist):byres[(resi,resn)].append(float(d))
    for (resi,resn),ds in byres.items():
     if resi in ANCHORS[para] or min(ds)<=4.5:contacts.append({'system_id':sid,'paralog':para,'compound_id':cid,'replica':rep,'frame':fi,'time_ps':fi*rm['output_interval_ps'],'state':state,'residue_number':resi,'residue_name':resn,'minimum_distance_A':min(ds),'contact_lt4p5A':min(ds)<=4.5,'candidate_anchor':resi in ANCHORS[para]})
   for fi in range(1,len(stable)):
    if stable[fi]!=stable[fi-1]:events.append({'system_id':sid,'paralog':para,'compound_id':cid,'replica':rep,'event':event_name(stable[fi-1],stable[fi]),'from_state':stable[fi-1],'to_state':stable[fi],'frame':fi,'time_ps':fi*rm['output_interval_ps'],'persistence_frames_required':5})
   for state in ('INNER','MOUTH','ESCAPE','OUTSIDE'):
    try:fi=stable.index(state)
    except ValueError:continue
    sel=traj.topology.select('protein or resname SAM or resname LIG');out=HERE/'representatives'/f'{sid}_r{rep}_{state}.pdb';out.parent.mkdir(exist_ok=True);traj[fi].atom_slice(sel).save_pdb(str(out));representatives.append({'system_id':sid,'replica':rep,'kind':state,'frame':fi,'time_ps':fi*rm['output_interval_ps'],'path':str(out)})
   manifest.append({'system_id':sid,'paralog':para,'compound_id':cid,'replica':rep,'simulation_seed':rm['simulation_seed'],'production_ns':rm['production_ns'],'frames':traj.n_frames,'output_interval_ps':rm['output_interval_ps'],'dcd':str(dcd),'dcd_sha256':sha(dcd),'metadata':str(rd/'metadata.json'),'topology':str(top),'forcefield':rm['protein_forcefield']+'; '+rm['small_molecule_forcefield'],'water':rm['water'],'ions':rm['ions'],'temperature_K':rm['temperature_K'],'pressure_bar':rm['pressure_bar'],'timestep_fs':rm['timestep_fs'],'production_restraints':rm['production_restraints']})
 write_csv(HERE/'starting_state_audit.csv',starting);write_csv(HERE/'trajectory_manifest.csv',manifest);write_csv(HERE/'per_frame_states.csv',frames);write_csv(HERE/'events.csv',events,fields=['system_id','paralog','compound_id','replica','event','from_state','to_state','frame','time_ps','persistence_frames_required']);write_csv(HERE/'anchor_contact_timeseries.csv',contacts);write_csv(HERE/'representative_structures.csv',representatives)

 # Trajectory summaries and continuous residence runs.
 summary=[]
 for m in manifest:
  q=[r for r in frames if r['system_id']==m['system_id'] and r['replica']==m['replica']];ev=[r for r in events if r['system_id']==m['system_id'] and r['replica']==m['replica']];states=[r['persistent_state'] for r in q];runs=[];s=0
  for i in range(1,len(states)+1):
   if i==len(states) or states[i]!=states[s]:runs.append((states[s],i-s));s=i
  counts=Counter(states);summary.append({'system_id':m['system_id'],'paralog':m['paralog'],'compound_id':m['compound_id'],'replica':m['replica'],'frames':len(states),'inner_fraction':counts['INNER']/len(states),'mouth_fraction':counts['MOUTH']/len(states),'escape_fraction':counts['ESCAPE']/len(states),'outside_fraction':counts['OUTSIDE']/len(states),'inner_to_mouth':sum(e['event']=='INNER_TO_MOUTH' for e in ev),'reentries':sum(e['event'] in {'MOUTH_TO_INNER','ESCAPE_TO_INNER','OUTSIDE_TO_REENTRY'} for e in ev),'recaptures':sum(e['event'] in {'MOUTH_TO_INNER','ESCAPE_TO_INNER'} for e in ev),'irreversible_escape':states[-1] in {'ESCAPE','OUTSIDE'},'longest_inner_ps':max([n*2 for st,n in runs if st=='INNER'] or [0]),'longest_escape_or_outside_ps':max([n*2 for st,n in runs if st in {'ESCAPE','OUTSIDE'}] or [0]),'final_state':states[-1]})
 write_csv(HERE/'trajectory_summary.csv',summary)

 # Anchor state frequencies; recapture enrichment remains unevaluated if no events.
 anchor=[]
 for sid in SYSTEMS:
  para=sid[:2];cid=json.load(open(HERE/'systems'/sid/'metadata.json'))['compound_id']
  for resi in sorted(ANCHORS[para]):
   q=[r for r in contacts if r['system_id']==sid and int(r['residue_number'])==resi]
   if not q:continue
   row={'system_id':sid,'paralog':para,'compound_id':cid,'residue_number':resi,'residue_name':q[0]['residue_name']}
   for state in ('INNER','MOUTH','ESCAPE','OUTSIDE'):
    z=[r for r in q if r['state']==state];row[f'{state.lower()}_frames']=len(z);row[f'{state.lower()}_contact_frequency']=float(np.mean([r['contact_lt4p5A'] in (True,'True') for r in z])) if z else ''
   row['pre_recapture_frames']=0;row['pre_recapture_contact_frequency']='';row['recapture_enrichment_vs_inner']='';anchor.append(row)
 write_csv(HERE/'anchor_state_summary.csv',anchor)
 homolog=[(39,'PHE','LEU'),(40,'LEU','MET'),(43,'PHE','LEU'),(145,'LEU','LEU'),(175,'HIS','HIS'),(195,'TRP','TRP'),(198,'','ILE'),(199,'PHE','GLY'),(202,'CYS','CYS'),(231,'TRP',''),(232,'','LEU'),(234,'VAL','VAL')]
 write_csv(HERE/'homologous_anchor_comparison.csv',[{'position':n,'METTL7A_residue':a,'METTL7B_residue':b,'chemistry_change':'aromatic_to_aliphatic' if (a,b) in {('PHE','LEU')} else ('aromatic_to_glycine' if (a,b)==('PHE','GLY') else ('conserved' if a==b and a else 'alignment_neighborhood_difference'))} for n,a,b in homolog])
 (HERE/'analysis_manifest.json').write_text(json.dumps({'state_definitions':str(HERE/'state_definitions.json'),'state_definitions_sha256':sha(HERE/'state_definitions.json'),'frames_total':len(frames),'events_total':len(events),'superpocket_transfer':transfer,'alignment':'each trajectory superposed to starting solvated topology over protein CA atoms','periodic_note':'ligand remained near the protein in this short pilot; no arbitrary steering or exit bias applied','recapture_enrichment':'computed only if persistent recapture events occur; otherwise blank','representatives':representatives},indent=2)+'\n')
if __name__=='__main__':main()
