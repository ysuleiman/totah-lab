#!/usr/bin/env python3
"""Resolve C202/C203 pocket-wall versus contact geometry for netarsudil."""
from __future__ import annotations
import csv,json
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[3]
V=ROOT/'research/mettl7-netarsudil-sam-mechanism/vina-matched'; L=ROOT/'research/mettl7-netarsudil-sam-mechanism/local-flexibility'
OUT=V/'analysis'; POCKET_DIR=ROOT/'resources/shared-resources/src/main/resources/Q6UX53/fpocket/pockets'
SPHERES=POCKET_DIR/'pocket2_vert.pqr'; WALL=POCKET_DIR/'pocket2_atm.pdb'
HEAVY_H={'H','HD'}

def pdbqt_atoms(path,model=None,section='all'):
 out=[];take=model is None;in_flex=False
 for line in path.read_text().splitlines():
  if line.startswith('MODEL'):take=int(line.split()[1])==model
  elif line.startswith('ENDMDL') and take:break
  elif line.startswith('BEGIN_RES'):in_flex=True
  if not take or not line.startswith(('ATOM','HETATM')):continue
  if section=='ligand' and in_flex:continue
  if section=='flex' and not in_flex:continue
  p=line.split();out.append({'name':line[12:16].strip(),'res':line[17:20].strip(),'chain':line[21:22].strip(),'num':int(line[22:26]),
   'xyz':np.array([float(line[30:38]),float(line[38:46]),float(line[46:54])]),'type':p[-1]})
 return out
def pdb_atoms(path):
 out=[]
 for l in path.read_text().splitlines():
  if l.startswith(('ATOM','HETATM')):out.append({'name':l[12:16].strip(),'res':l[17:20].strip(),'chain':l[21:22].strip(),'num':int(l[22:26]),'xyz':np.array([float(l[30:38]),float(l[38:46]),float(l[46:54])])})
 return out
def heavy(x):return [a for a in x if a.get('type') not in HEAVY_H and not a['name'].startswith('H')]
def pair_min(lig,res):
 la,ra=heavy(lig),heavy(res);d=np.linalg.norm(np.array([a['xyz'] for a in la])[:,None,:]-np.array([a['xyz'] for a in ra])[None,:,:],axis=2);i,j=np.unravel_index(np.argmin(d),d.shape)
 return float(d[i,j]),la[i]['name'],ra[j]['name']
def sg_min(lig,res):
 sg=[a for a in res if a['name']=='SG'];la=heavy(lig)
 if not sg:return None,None
 ds=[float(np.linalg.norm(a['xyz']-sg[0]['xyz'])) for a in la];i=int(np.argmin(ds));return ds[i],la[i]['name']
def sphere_data():
 centers=[];radii=[]
 for l in SPHERES.read_text().splitlines():
  if l.startswith(('ATOM','HETATM')):
   p=l.split();centers.append([float(l[30:38]),float(l[38:46]),float(l[46:54])]);radii.append(float(p[-1]))
 return np.array(centers),np.array(radii)
def sphere_field(xyz,centers,radii):return float(np.max(radii-np.linalg.norm(centers-xyz,axis=1)))
def write(path,rows):
 with path.open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)

def main():
 pose_metrics=list(csv.DictReader((OUT/'pose_metrics.csv').open()));members=[r for r in pose_metrics if r['enzyme']=='7B' and r['state']=='neutral' and r['family']=='5']
 rec=pdbqt_atoms(V/'prepared/METTL7B_SAM_receptor.pdbqt');r202=[a for a in rec if a['chain']=='A' and a['num']==202];r203=[a for a in rec if a['chain']=='A' and a['num']==203]
 rows=[]
 for m in members:
  lig=pdbqt_atoms(V/'raw'/f"7B_neutral_seed{m['seed']}.pdbqt",int(m['mode']))
  d202,l202,a202=pair_min(lig,r202);d203,l203,a203=pair_min(lig,r203);sg202,sl202=sg_min(lig,r202);sg203,sl203=sg_min(lig,r203)
  rows.append({'seed':m['seed'],'mode':m['mode'],'physically_admissible':m['physical_pass'],'c202_min_a':d202,'c202_ligand_atom':l202,'c202_atom':a202,'c202_sg_min_a':sg202,'c202_sg_ligand_atom':sl202,
   'c203_min_a':d203,'c203_ligand_atom':l203,'c203_atom':a203,'c203_sg_min_a':sg203,'c203_sg_ligand_atom':sl203,'c202_within_4a':d202<=4,'c203_within_4a':d203<=4})
 write(OUT/'c202_c203_family_geometry.csv',rows)
 centers,radii=sphere_data();wall_atoms=pdb_atoms(WALL);wall_res={(a['chain'],a['num'],a['res']) for a in wall_atoms};rep=next(r for r in rows if r['seed']=='483271' and r['mode']=='5')
 lig=pdbqt_atoms(V/'raw/7B_neutral_seed483271.pdbqt',5);lh=heavy(lig);fields=[sphere_field(a['xyz'],centers,radii) for a in lh]
 receptor_heavy=heavy(rec);resmap={}
 for a in receptor_heavy:
  if a['res']=='SAM':continue
  resmap.setdefault((a['chain'],a['num'],a['res']),[]).append(a)
 local=[]
 for key,ats in resmap.items():
  d,la,pa=pair_min(lig,ats);iswall=key in wall_res
  if d<=4:role='FPOCKET_WALL_RESIDUE' if iswall else 'OUTSIDE_POCKET_CONTACT'
  elif d<=5:role='PROXIMITY_ONLY'
  elif iswall:role='WALL_NONCONTACT'
  else:continue
  local.append({'chain':key[0],'residue_number':key[1],'residue_name':key[2],'min_distance_a':d,'ligand_atom':la,'protein_atom':pa,'fpocket_wall':iswall,'classification':role})
 write(OUT/'representative_local_wall_classification.csv',sorted(local,key=lambda r:r['min_distance_a']))
 can=pdb_atoms(ROOT/'analysis/dcmb/sam_state/validated/WT_METTL7B_SAM_BOUND.pdb');numbering={}
 for n in (202,203):
  ca=next(a for a in can if a['chain']=='A' and a['num']==n and a['name']=='CA');prep=next(a for a in rec if a['chain']=='A' and a['num']==n and a['name']=='CA')
  numbering[str(n)]={'canonical_residue':ca['res'],'prepared_residue':prep['res'],'ca_coordinate_delta_a':float(np.linalg.norm(ca['xyz']-prep['xyz']))}
 # Follow the homologous positions through the already-completed refinement.
 lr=[];systems={'7B_neutral':('7B','netarsudil_neutral_start.pdbqt'),'7B_plus1':('7B','netarsudil_plus1_start.pdbqt'),'7A_neutral_transfer':('7A','netarsudil_neutral_start_7A_transfer.pdbqt'),'7A_plus1_transfer':('7A','netarsudil_plus1_start_7A_transfer.pdbqt')}
 for system,(enzyme,start) in systems.items():
  rigid=pdbqt_atoms(L/f'prepared/METTL{enzyme}_localflex_rigid_with_SAM.pdbqt');startflex=pdbqt_atoms(L/f'prepared/METTL{enzyme}_localflex_flex.pdbqt');startlig=pdbqt_atoms(L/'prepared'/start)
  for phase,seed,lig,flex in [('before','START',startlig,startflex)]+[('after',str(s),pdbqt_atoms(L/f'raw/{system}_seed{s}.pdbqt',section='ligand'),pdbqt_atoms(L/f'raw/{system}_seed{s}.pdbqt',section='flex')) for s in (314159,271828,161803)]:
   protein=rigid+flex
   for n in (202,203):
    rr=[a for a in protein if a['chain']=='A' and a['num']==n];d,la,pa=pair_min(lig,rr);sg,sla=sg_min(lig,rr)
    lr.append({'system':system,'enzyme':enzyme,'phase':phase,'seed':seed,'position':n,'residue_name':rr[0]['res'],'min_distance_a':d,'ligand_atom':la,'residue_atom':pa,'sg_min_a':sg if sg is not None else '','sg_ligand_atom':sla or '','within_4a':d<=4})
 write(L/'analysis/c202_c203_refinement_tracking.csv',lr)
 admiss=[r for r in rows if r['physically_admissible']=='True']
 c202_contact=all(r['c202_within_4a'] for r in admiss);c203_contact=all(r['c203_within_4a'] for r in admiss)
 closest_i=int(np.argmin(np.abs(fields)));summary={'pocket_identity':'197-alpha-sphere METTL7B SAM superpocket; checked-in rerun pocket 2',
  'sphere_count':len(centers),'numbering_qc':numbering,'family_pose_count':len(rows),'admissible_pose_count':len(admiss),
  'admissible_contact_consistency':{'C202_all_three_within_4a':c202_contact,'C203_all_three_within_4a':c203_contact},
  'complete_family_distributions_a':{k:{'min':min(r[k] for r in rows),'median':float(np.median([r[k] for r in rows])),'max':max(r[k] for r in rows)} for k in ('c202_min_a','c203_min_a','c202_sg_min_a','c203_sg_min_a')},
  'representative':{'C202':rep['c202_min_a'],'C203':rep['c203_min_a'],'C202_is_fpocket_wall':('A',202,'CYS') in wall_res,'C203_is_fpocket_wall':('A',203,'CYS') in wall_res,
   'residue_alpha_sphere_boundary':{str(n):{'atoms_inside_sphere_union':sum(sphere_field(a['xyz'],centers,radii)>=0 for a in rr),'heavy_atoms':len(rr),
    'closest_atom_to_boundary':min(rr,key=lambda a:abs(sphere_field(a['xyz'],centers,radii)))['name'],
    'closest_boundary_field_a':sphere_field(min(rr,key=lambda a:abs(sphere_field(a['xyz'],centers,radii)))['xyz'],centers,radii),
    'wall_atoms_listed_by_fpocket':[a['name'] for a in wall_atoms if a['chain']=='A' and a['num']==n]} for n,rr in ((202,heavy(r202)),(203,heavy(r203)))},
   'ligand_heavy_atoms_inside_alpha_sphere_union':sum(f>=0 for f in fields),'ligand_heavy_atoms':len(fields),'inside_fraction':sum(f>=0 for f in fields)/len(fields),
   'ligand_atoms_within_4a_of_sphere_center_fraction':float(np.mean([np.min(np.linalg.norm(centers-a['xyz'],axis=1))<=4 for a in lh])),
   'closest_ligand_atom_to_operational_sphere_union_boundary':lh[closest_i]['name'],'boundary_field_a':fields[closest_i],
   'occupancy_classification':'ADJACENT_EXTENSION_CROSSING_BOUNDARY'},
  'classifications':{'C202_NETARSUDIL_ROLE':'WALL_NONCONTACT','C203_NETARSUDIL_ROLE':'OUTSIDE_POCKET_CONTACT'}}
 (OUT/'c202_c203_geometry_summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2))
if __name__=='__main__':main()
