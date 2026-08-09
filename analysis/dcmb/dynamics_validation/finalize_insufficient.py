#!/usr/bin/env python3
"""Emit explicit unevaluated outputs when matched unbiased sampling is unavailable."""
from __future__ import annotations
import csv,json
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;FOCUS=ROOT/'analysis/dcmb/focused_validation'
SYSTEMS=('7A_DCMB_S','7A_24DCMB_R','7B_DCMB_R','7B_24DCMB_R')

def write(path,fields,rows=()):
 with path.open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(rows)

def main():
 static=list(csv.DictReader(open(FOCUS/'per_seed_pose_metrics.csv')));tsl=list(csv.DictReader(open(FOCUS/'tsl_interference_metrics.csv')));audit=[];planned=[]
 for sid in SYSTEMS:
  m=json.load(open(HERE/'systems'/sid/'metadata.json'));r=next(x for x in static if x['paralog']==m['paralog'] and x['compound_id']==m['compound_id'] and int(x['seed'])==m['docking_seed'] and int(x['rank'])==m['docking_rank']);z=[x for x in tsl if x['paralog']==m['paralog'] and x['compound_id']==m['compound_id'] and int(x['seed'])==m['docking_seed'] and int(x['rank'])==m['docking_rank']]
  audit.append({'system_id':sid,'paralog':m['paralog'],'compound_id':m['compound_id'],'stereochemistry':m['stereochemistry'],'pose_family':m['pose_family'],'docking_seed':m['docking_seed'],'docking_rank':m['docking_rank'],'centroid_x':r['centroid_x'],'centroid_y':r['centroid_y'],'centroid_z':r['centroid_z'],'orientation_axis_x':r['orientation_axis_x'],'orientation_axis_y':r['orientation_axis_y'],'orientation_axis_z':r['orientation_axis_z'],'sam_min_distance_A':r['sam_min_distance_A'],'sam_classification':r['sam_classification'],'tsl_conflict_states':sum(int(x['pairs_lt2A'])>0 for x in z),'tsl_states':len(z),'directional_subpocket':r['directional_subpocket'],'protein_contacts_4p5A':r['protein_contact_fingerprint_4p5A'],'mouth_projection_A':r['mouth_projection_A'],'ligand_formal_charge':m['ligand_formal_charge'],'receptor':m['receptor'],'receptor_sha256':m['receptor_sha256'],'pose_source':m['pose_source'],'pose_source_sha256':m['pose_source_sha256'],'prepared_system':str(HERE/'systems'/sid),'prepared_status':m['status']})
  for rep in (1,2,3):planned.append({'system_id':sid,'paralog':m['paralog'],'compound_id':m['compound_id'],'replica':rep,'planned_simulation_seed':20260820+100*SYSTEMS.index(sid)+rep,'planned_production_ns':0.2,'planned_output_interval_ps':2,'status':'NOT_COMPLETED_CPU_LIMIT','trajectory_path':'','trajectory_sha256':'','frames':0})
 write(HERE/'starting_state_audit.csv',list(audit[0]),audit);write(HERE/'trajectory_manifest.csv',list(planned[0]),planned)
 write(HERE/'per_frame_states.csv',['system_id','replica','frame','time_ps','raw_state','persistent_state','superpocket_fraction','productive_TSL_envelope_fraction','nearest_superpocket_distance_A','mouth_projection_A'])
 write(HERE/'events.csv',['system_id','replica','event','from_state','to_state','frame','time_ps','persistence_frames_required'])
 write(HERE/'anchor_contact_timeseries.csv',['system_id','replica','frame','time_ps','state','residue_number','residue_name','minimum_distance_A','contact_lt4p5A'])
 write(HERE/'recapture_contact_enrichment.csv',['system_id','residue_number','residue_name','inner_contact_frequency','mouth_contact_frequency','pre_recapture_contact_frequency','escape_contact_frequency','recapture_enrichment_vs_inner','evaluated'],[])
 homolog=[(39,'PHE','LEU'),(40,'LEU','MET'),(43,'PHE','LEU'),(145,'LEU','LEU'),(175,'HIS','HIS'),(195,'TRP','TRP'),(198,'','ILE'),(199,'PHE','GLY'),(202,'CYS','CYS'),(231,'TRP',''),(232,'','LEU'),(234,'VAL','VAL')]
 rows=[{'position':n,'METTL7A_residue':a,'METTL7B_residue':b,'chemistry_change':'aromatic_to_aliphatic' if (a,b)==('PHE','LEU') else ('aromatic_to_glycine' if (a,b)==('PHE','GLY') else ('conserved' if a==b and a else 'alignment_neighborhood_difference')),'dynamic_recapture_evaluated':False} for n,a,b in homolog];write(HERE/'homologous_anchor_comparison.csv',list(rows[0]),rows)
 compare=[]
 for para in ('7A','7B'):
  for ligand in ('DCMB','24DCMB'):
   compare.append({'paralog':para,'ligand':ligand,'completed_replicas':0,'sampled_production_ns':0,'exits':'','reentries':'','recaptures':'','irreversible_escapes':'','inner_occupancy':'','escape_occupancy':'','dynamic_comparison':'UNEVALUATED_INSUFFICIENT_SAMPLING'})
 write(HERE/'matched_dynamic_comparison.csv',list(compare[0]),compare)
 (HERE/'sampling_attempt.json').write_text(json.dumps({'prepared_systems':4,'planned_replicas':12,'completed_replicas':0,'completed_production_frames':0,'platform':'CPU','gpu_check':'OpenMM OpenCL context failed: no compatible OpenCL platform available','attempts':[{'production_ns_per_replica':0.2,'outcome':'stopped before first production output; explicit-solvent CPU propagation exceeded bounded task window'},{'production_ns_per_replica':0.02,'outcome':'stopped before first production output; even reduced matched matrix remained CPU-bound'}],'scientific_status':'No trajectory frames exist; all event, occupancy, anchor-enrichment, and dynamic ligand comparisons are UNEVALUATED.','enhanced_sampling_executed':False,'top100_executed':False},indent=2)+'\n')
if __name__=='__main__':main()
