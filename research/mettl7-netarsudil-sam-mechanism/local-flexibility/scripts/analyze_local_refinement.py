#!/usr/bin/env python3
"""Calculate separate before/after metrics and apply the frozen local gate."""
from __future__ import annotations
import csv,json,math,re
from pathlib import Path
import numpy as np
from rdkit import Chem
from rdkit.Chem import AllChem

ROOT=Path(__file__).resolve().parents[4]; BASE=ROOT/'research/mettl7-netarsudil-sam-mechanism/local-flexibility'
PREP=BASE/'prepared'; RAW=BASE/'raw'; LOG=BASE/'logs'; OUT=BASE/'analysis'; OUT.mkdir(exist_ok=True)
SEEDS=(314159,271828,161803)
SYSTEMS={'7B_neutral':('7B','neutral','netarsudil_neutral_start.pdbqt'),'7B_plus1':('7B','plus1','netarsudil_plus1_start.pdbqt'),
 '7A_neutral_transfer':('7A','neutral','netarsudil_neutral_start_7A_transfer.pdbqt'),'7A_plus1_transfer':('7A','plus1','netarsudil_plus1_start_7A_transfer.pdbqt')}
VDW={'C':1.70,'N':1.55,'O':1.52,'S':1.80,'P':1.80,'F':1.47,'CL':1.75}
SPHERE=np.column_stack((np.cos(np.arange(128)*math.pi*(3-math.sqrt(5)))*np.sqrt(1-(1-2*(np.arange(128)+.5)/128)**2),1-2*(np.arange(128)+.5)/128,np.sin(np.arange(128)*math.pi*(3-math.sqrt(5)))*np.sqrt(1-(1-2*(np.arange(128)+.5)/128)**2)))

def atoms(path,section='all'):
 rows=[]; in_flex=False
 for l in path.read_text().splitlines():
  if l.startswith('BEGIN_RES'):in_flex=True
  if not l.startswith(('ATOM','HETATM')):continue
  if section=='ligand' and in_flex:continue
  if section=='flex' and not in_flex:continue
  p=l.split();rows.append({'name':l[12:16].strip(),'res':l[17:20].strip(),'chain':l[21:22].strip(),'num':int(l[22:26]),
   'xyz':np.array([float(l[30:38]),float(l[38:46]),float(l[46:54])]),'q':float(p[-2]),'type':p[-1]})
 return rows
def heavy(x):return [a for a in x if a['type'] not in ('H','HD')]
def elem(t):return {'A':'C','NA':'N','OA':'O','SA':'S'}.get(t,t)
def dist(a,b):return np.linalg.norm(np.array([x['xyz'] for x in a])[:,None,:]-np.array([x['xyz'] for x in b])[None,:,:],axis=2)
def contacts(protein,lig,cut=4.0):
 d=dist(heavy(lig),heavy(protein)); out={}
 for j,a in enumerate(heavy(protein)):
  m=float(d[:,j].min())
  if m<=cut:out[(a['chain'],a['num'],a['res'])]=min(m,out.get((a['chain'],a['num'],a['res']),99))
 return out
def sasa_reduction(lig,protein):
 lig=heavy(lig);protein=heavy(protein);lc=np.array([a['xyz'] for a in lig]);lr=np.array([VDW.get(elem(a['type']),1.7) for a in lig])+1.4
 pc=np.array([a['xyz'] for a in protein]);pr=np.array([VDW.get(elem(a['type']),1.7) for a in protein])+1.4;iso=bound=0.
 for i,(c,r) in enumerate(zip(lc,lr)):
  pts=c+r*SPHERE;mask=np.arange(len(lc))!=i; oi=~np.any(np.linalg.norm(pts[:,None,:]-lc[None,mask,:],axis=2)<lr[mask],axis=1)
  near=np.linalg.norm(pc-c,axis=1)<r+pr+1;ob=oi&~np.any(np.linalg.norm(pts[:,None,:]-pc[None,near,:],axis=2)<pr[near],axis=1);area=4*math.pi*r*r
  iso+=area*oi.mean();bound+=area*ob.mean()
 return 100*(iso-bound)/iso
def template(state):
 source=ROOT/'research/mettl7-netarsudil-sam-mechanism/vina-matched/prepared'/('netarsudil_CID66599893_neutral.sdf' if state=='neutral' else 'netarsudil_CID66599893_monocation_pH7.4.sdf')
 mol=Chem.RemoveHs(Chem.SDMolSupplier(str(source),removeHs=False)[0]); conf=mol.GetConformer()
 prep=heavy(atoms(ROOT/'research/mettl7-netarsudil-sam-mechanism/vina-matched/prepared'/('netarsudil_neutral.pdbqt' if state=='neutral' else 'netarsudil_monocation.pdbqt')))
 unused=set(range(mol.GetNumAtoms()));mapping=[]
 for a in prep:
  j=min(unused,key=lambda k:np.linalg.norm(a['xyz']-np.array(conf.GetAtomPosition(k))));unused.remove(j);mapping.append(j)
 autos=mol.GetSubstructMatches(mol,uniquify=False,maxMatches=1000);props=AllChem.MMFFGetMoleculeProperties(mol,mmffVariant='MMFF94s')
 ref=Chem.Mol(mol);AllChem.MMFFOptimizeMolecule(ref,mmffVariant='MMFF94s',maxIters=1000);e0=AllChem.MMFFGetMoleculeForceField(ref,props).CalcEnergy()
 return mol,mapping,autos,props,e0
def strain_coords(rows,t):
 mol,mapping,autos,props,e0=t;m=Chem.Mol(mol);c=m.GetConformer()
 for a,j in zip(heavy(rows),mapping):x,y,z=a['xyz'];c.SetAtomPosition(j,(float(x),float(y),float(z)))
 return AllChem.MMFFGetMoleculeForceField(m,props).CalcEnergy()-e0,np.array(c.GetPositions())
def sym_rmsd(a,b,autos):return min(float(np.sqrt(np.mean(np.sum((a-np.array([b[j] for j in p]))**2,axis=1)))) for p in autos)
def sidechain_disp(start,end):
 s={(a['chain'],a['num'],a['name']):a['xyz'] for a in heavy(start)};e={(a['chain'],a['num'],a['name']):a['xyz'] for a in heavy(end)};keys=sorted(s.keys()&e.keys())
 ds=[float(np.linalg.norm(s[k]-e[k])) for k in keys if k[2] not in ('CA',)]
 return (float(np.sqrt(np.mean(np.square(ds)))) if ds else 0.,max(ds) if ds else 0.)
def polar_interactions(protein,lig):
 ps=[a for a in heavy(protein) if elem(a['type']) in ('N','O','S')];ls=[a for a in heavy(lig) if elem(a['type']) in ('N','O','S')];out=[]
 for a in ls:
  for b in ps:
   d=float(np.linalg.norm(a['xyz']-b['xyz']))
   if d<=3.5:out.append(f"{b['res']}{b['num']}:{b['name']}-{a['name']}:{d:.2f}")
 return sorted(out)
def hydrogen_bonds(protein,lig):
 def donors(group):
  hs=[a for a in group if a['type'] in ('H','HD')];out=[]
  for d in heavy(group):
   if elem(d['type']) not in ('N','O','S'):continue
   for h in hs:
    if np.linalg.norm(d['xyz']-h['xyz'])<=1.25:out.append((d,h))
  return out
 def acceptors(group):return [a for a in heavy(group) if a['type'] in ('NA','OA','SA')]
 out=[]
 for side,ds,acc in [('ligand_donor',donors(lig),acceptors(protein)),('protein_donor',donors(protein),acceptors(lig))]:
  for d,h in ds:
   for a in acc:
    da=float(np.linalg.norm(d['xyz']-a['xyz']));ha=float(np.linalg.norm(h['xyz']-a['xyz']))
    if not (2.4<=da<=3.5 and ha<=2.5):continue
    v1=d['xyz']-h['xyz'];v2=a['xyz']-h['xyz'];ang=math.degrees(math.acos(np.clip(np.dot(v1,v2)/(np.linalg.norm(v1)*np.linalg.norm(v2)),-1,1)))
    if ang>=120:out.append(f"{side}:{d['res']}{d['num']}:{d['name']}-{a['res']}{a['num']}:{a['name']}:{da:.2f}:{ang:.1f}")
 return sorted(out)
def ionic_interactions(protein,lig,state):
 if state!='plus1':return []
 ln=[a for a in heavy(lig) if elem(a['type'])=='N'];carb=[a for a in heavy(protein) if a['res'] in ('ASP','GLU') and a['name'].startswith(('OD','OE'))]
 return sorted(f"{a['res']}{a['num']}:{a['name']}-ligand:{n['name']}:{np.linalg.norm(a['xyz']-n['xyz']):.2f}" for n in ln for a in carb if np.linalg.norm(a['xyz']-n['xyz'])<=4.0)
def final_energy(log):
 m=re.search(r'Estimated Free Energy of Binding\s+:\s+([-0-9.]+)',log.read_text());return float(m.group(1)) if m else None
def write(path,rows):
 with path.open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
def main():
 proto=json.loads((BASE/'frozen_protocol.json').read_text()); rows=[]; summaries={}; contact_rows=[]
 for system,(enzyme,state,start_name) in SYSTEMS.items():
  t=template(state);start_lig=atoms(PREP/start_name);start_strain,start_coords=strain_coords(start_lig,t)
  rigid=atoms(PREP/f'METTL{enzyme}_localflex_rigid_with_SAM.pdbqt');sam=[a for a in rigid if a['res']=='SAM'];rigid_prot=[a for a in rigid if a['res']!='SAM'];start_flex=atoms(PREP/f'METTL{enzyme}_localflex_flex.pdbqt')
  start_prot=rigid_prot+start_flex;start_contacts=contacts(start_prot,start_lig);system_rows=[]
  for (ch,n,res),d in sorted(start_contacts.items()):contact_rows.append({'system':system,'seed':'START','phase':'before','chain':ch,'residue_number':n,'residue_name':res,'minimum_distance_a':d,'retained_from_start':True})
  for seed in SEEDS:
   out=RAW/f'{system}_seed{seed}.pdbqt';lig=atoms(out,'ligand');flex=atoms(out,'flex');protein=rigid_prot+flex
   st,coords=strain_coords(lig,t);rms=sym_rmsd(start_coords,coords,t[2]);cent=float(np.linalg.norm(np.mean([a['xyz'] for a in heavy(lig)],0)-np.mean([a['xyz'] for a in heavy(start_lig)],0)))
   dp=dist(heavy(lig),heavy(protein));ds=dist(heavy(lig),heavy(sam));after=contacts(protein,lig);ret=len(set(start_contacts)&set(after));sc_rms,sc_max=sidechain_disp(start_flex,flex)
   passed=cent<=3 and rms<=4 and ret>=3 and int((dp<1.8).sum())==0 and int((ds<2).sum())==0 and st<=15
   row={'system':system,'enzyme':enzyme,'state':state,'seed':seed,'starting_strain_kcal_mol':start_strain,'final_strain_kcal_mol':st,'strain_change_kcal_mol':st-start_strain,
    'ligand_symmetry_rmsd_a':rms,'centroid_displacement_a':cent,'ligand_sam_min_distance_a':float(ds.min()),'protein_pairs_lt_1p8':int((dp<1.8).sum()),
    'sam_pairs_lt_2':int((ds<2).sum()),'sam_heavy_rmsd_a':0.0,'sidechain_rms_displacement_a':sc_rms,'sidechain_max_displacement_a':sc_max,
    'starting_contact_count':len(start_contacts),'final_contact_count':len(after),'retained_starting_contacts':ret,'burial_reduction_percent':sasa_reduction(lig,protein),
    'vina_local_final_energy':final_energy(LOG/f'{system}_seed{seed}.log'),'hydrogen_bonds':';'.join(hydrogen_bonds(protein,lig)),
    'ionic_interactions':';'.join(ionic_interactions(protein,lig,state)),'polar_distance_contacts':';'.join(polar_interactions(protein,lig)),'gate_pass':passed,
    'rejection_reasons':';'.join(k for k,v in [('centroid_migration',cent>3),('ligand_rmsd',rms>4),('contact_loss',ret<3),('protein_clash',int((dp<1.8).sum())>0),('sam_clash',int((ds<2).sum())>0),('strain',st>15)] if v)}
   rows.append(row);system_rows.append(row)
   for (ch,n,res),d in sorted(after.items()):contact_rows.append({'system':system,'seed':seed,'phase':'after','chain':ch,'residue_number':n,'residue_name':res,'minimum_distance_a':d,'retained_from_start':(ch,n,res) in start_contacts})
  n=sum(r['gate_pass'] for r in system_rows);classification='PASS' if n>=2 else 'FAIL' if n==0 else 'INDETERMINATE'
  summaries[system]={'classification':classification,'passing_replicates':n,'replicates':3,'median_final_strain_kcal_mol':float(np.median([r['final_strain_kcal_mol'] for r in system_rows])),
   'median_centroid_displacement_a':float(np.median([r['centroid_displacement_a'] for r in system_rows])),'median_ligand_rmsd_a':float(np.median([r['ligand_symmetry_rmsd_a'] for r in system_rows])),
   'median_sam_min_distance_a':float(np.median([r['ligand_sam_min_distance_a'] for r in system_rows]))}
 b=summaries['7B_neutral'];hyp='STRENGTHENED' if b['classification']=='PASS' and b['median_final_strain_kcal_mol']<=12.04 else 'WEAKENED' if b['classification']=='FAIL' else 'UNCHANGED'
 result={'classifications':summaries,'NETARSUDIL_7B_SAM_ADJACENT_STRUCTURAL_HYPOTHESIS':hyp,'local_pocket_volume':'NOT_CALCULATED; flexible-side-chain PDBQT partition does not define a validated comparable pocket-volume surface'}
 write(OUT/'replicate_metrics.csv',rows);write(OUT/'contacts.csv',contact_rows);(OUT/'classification.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
if __name__=='__main__':main()
