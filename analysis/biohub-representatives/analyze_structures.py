#!/usr/bin/env python3
"""Structure comparison for the fixed 12-sequence BioHub panel."""
from __future__ import annotations
import argparse,csv,math,subprocess
from pathlib import Path
import numpy as np

POCKET=[33,36,44,55,77,78,79,127,144,145,148,149,151,175,195,196,199,200,201,202,203]
AA3={'ALA':'A','ARG':'R','ASN':'N','ASP':'D','CYS':'C','GLN':'Q','GLU':'E','GLY':'G','HIS':'H','ILE':'I','LEU':'L','LYS':'K','MET':'M','PHE':'F','PRO':'P','SER':'S','THR':'T','TRP':'W','TYR':'Y','VAL':'V'}
HYD=set('AVLIMFWYPCG'); POL=set('STNQKRHDE')
CONS=[set('AVLIM'),set('FWY'),set('STNQ'),set('KRH'),set('DE')]

def fasta(path):
 d={};n=None
 for l in path.read_text().splitlines():
  if l.startswith('>'):n=l[1:].split()[0];d[n]=''
  else:d[n]+=l.strip()
 return d
def pdb(path):
 residues={};atoms=[]
 for l in path.read_text().splitlines():
  if not l.startswith('ATOM'):continue
  alt=l[16];
  if alt not in (' ','A'):continue
  name=l[12:16].strip();rn=l[17:20].strip();chain=l[21].strip() or 'A';num=int(l[22:26]);ins=l[26].strip();xyz=np.array([float(l[30:38]),float(l[38:46]),float(l[46:54])]);b=float(l[60:66]);elem=(l[76:78].strip() or name[0])
  key=(chain,num,ins);rec=residues.setdefault(key,{'aa':AA3.get(rn,'X'),'name':rn,'atoms':{},'bf':[]});rec['atoms'][name]=xyz;rec['bf'].append(b);atoms.append((key,name,elem,xyz))
 # choose protein chain with most CA atoms, renumber by order for sequence mapping
 chains={}
 for k,r in residues.items():
  if 'CA' in r['atoms']:chains.setdefault(k[0],[]).append((k,r))
 chain=max(chains,key=lambda c:len(chains[c])); ordered=sorted(chains[chain],key=lambda x:(x[0][1],x[0][2]));return ordered,atoms
def sasa(path):
 out=subprocess.check_output(['freesasa','--format=rsa',str(path)],text=True);d={}
 for l in out.splitlines():
  if l.startswith('RES '):
   f=l.split();d[int(f[3])]=float(f[4])
 return d
def kabsch(x,y):
 cx=x.mean(0);cy=y.mean(0);u,s,vt=np.linalg.svd((x-cx).T@(y-cy));r=vt.T@u.T
 if np.linalg.det(r)<0:vt[-1]*=-1;r=vt.T@u.T
 return r,cx,cy
def tx(points,t):r,cx,cy=t;return (r@(points-cx).T).T+cy
def rms(a,b):return math.sqrt(np.mean(np.sum((a-b)**2,axis=1))) if len(a) else float('nan')
def hull_volume(points):
 p=np.unique(np.round(points,5),axis=0);n=len(p)
 if n<4:return float('nan')
 cen=p.mean(0);vol=0.;seen=set();eps=1e-7
 for i in range(n-2):
  for j in range(i+1,n-1):
   for k in range(j+1,n):
    normal=np.cross(p[j]-p[i],p[k]-p[i]);
    if np.linalg.norm(normal)<eps:continue
    ds=(p-p[i])@normal
    if np.all(ds<=eps) or np.all(ds>=-eps):
     key=(i,j,k)
     if key not in seen:seen.add(key);vol+=abs(np.dot(p[i]-cen,np.cross(p[j]-cen,p[k]-cen)))/6
 return vol
def call(a,b):
 if b=='-':return 'insertion/deletion'
 if a==b:return 'identical'
 return 'conservative substitution' if any(a in g and b in g for g in CONS) else 'non-conservative substitution'
def main():
 ap=argparse.ArgumentParser();ap.add_argument('phase1',type=Path);ap.add_argument('phase2',type=Path);a=ap.parse_args()
 seqs=fasta(a.phase1/'cluster_alignment.fasta');inv=list(csv.DictReader((a.phase2/'structure_inventory.csv').open())); paths={r['sequence_id']:Path(r['analysis_structure_path']) for r in inv}
 structs={k:pdb(v)[0] for k,v in paths.items()};sas={k:sasa(v) for k,v in paths.items()}
 refaln=seqs['HUMAN_METTL7B'];refpos={};p=0
 for col,aa in enumerate(refaln):
  if aa!='-':p+=1;refpos[p]=col
 maps={}
 for sid,aln in seqs.items():
  q=0;m={}
  for col,aa in enumerate(aln):
   if aa!='-':q+=1
   if col in refpos.values():m[next((x for x,c in refpos.items() if c==col),-1)]=q if aa!='-' else None
  maps[sid]=m
 ref=structs['HUMAN_METTL7B']; refca={i+1:r['atoms']['CA'] for i,(k,r) in enumerate(ref) if 'CA'in r['atoms']}
 summaries=[];pockets=[];cysrows=[];resmaps=[]
 for sid in seqs:
  st=structs[sid];ca={i+1:r['atoms']['CA'] for i,(k,r) in enumerate(st) if 'CA'in r['atoms']};pairs=[]
  for rp in range(61,241):
   tp=maps[sid].get(rp)
   if tp and rp in refca and tp in ca:pairs.append((rp,tp))
  X=np.array([ca[t] for r,t in pairs]);Y=np.array([refca[r] for r,t in pairs]);trans=kabsch(X,Y);core=rms(tx(X,trans),Y)
  pp=[(r,maps[sid].get(r)) for r in POCKET if maps[sid].get(r) in ca and r in refca]
  PX=np.array([ca[t] for r,t in pp]);PY=np.array([refca[r] for r,t in pp]);PXT=tx(PX,trans);prms=rms(PXT,PY);local=rms(tx(PX,kabsch(PX,PY)),PY)
  deviations=[]
  for rp,tp in pairs:deviations.append((rp,float(np.linalg.norm(tx(ca[tp][None,:],trans)[0]-refca[rp]))) )
  divergent=[];run=[]
  for rp,d in deviations:
   if d>3:run.append(rp)
   else:
    if len(run)>=3:divergent.append(f'{run[0]}-{run[-1]}')
    run=[]
  if len(run)>=3:divergent.append(f'{run[0]}-{run[-1]}')
  summaries.append({'sequence_id':sid,'core_ca_pairs':len(pairs),'core_backbone_ca_rmsd_angstrom':f'{core:.3f}','pocket_ca_pairs':len(pp),'pocket_rmsd_after_core_fit_angstrom':f'{prms:.3f}','pocket_locally_fitted_ca_rmsd_angstrom':f'{local:.3f}','pocket_centroid_displacement_angstrom':f'{np.linalg.norm(PXT.mean(0)-PY.mean(0)):.3f}','divergent_mettl7b_loop_ranges_gt3A_3plus_residues':';'.join(divergent),'method':'Kabsch CA fit, Q6UX53 residues 61-240; pocket evaluated at 21 mapped sites'})
  aas=[];total_sasa=0.;exposed=0
  target_heavy=[];ref_heavy=[]
  for rp,tp in pp:
   key,rr=st[tp-1];aas.append(rr['aa']);total_sasa+=sas[sid].get(key[1],0);exposed+=sas[sid].get(key[1],0)>20
   target_heavy += [v for n,v in rr['atoms'].items() if not n.startswith('H')]
   _,rrr=ref[rp-1];ref_heavy += [v for n,v in rrr['atoms'].items() if not n.startswith('H')]
  TH=tx(np.array(target_heavy),trans);RH=np.array(ref_heavy); overlap=sum(np.min(np.linalg.norm(RH-x,axis=1))<=2 for x in TH)/len(TH) if len(TH) else 0
  pockets.append({'sequence_id':sid,'mapped_pocket_residue_count':len(pp),'pocket_centroid_x':f'{PXT.mean(0)[0]:.3f}','pocket_centroid_y':f'{PXT.mean(0)[1]:.3f}','pocket_centroid_z':f'{PXT.mean(0)[2]:.3f}','ca_convex_hull_volume_angstrom3':f'{hull_volume(PXT):.2f}','pocket_residue_sasa_angstrom2':f'{total_sasa:.2f}','exposed_residues_sasa_gt20':exposed,'residue_composition':''.join(aas),'hydrophobic_fraction':f'{sum(x in HYD for x in aas)/len(aas):.3f}','polar_charged_fraction':f'{sum(x in POL for x in aas)/len(aas):.3f}','pocket_ca_rmsd_angstrom':f'{prms:.3f}','heavy_atom_proximity_overlap_fraction_2A':f'{overlap:.3f}','operational_pocket_definition':'21 Q6UX53 ligand-pocket residues mapped through MAFFT; volume is CA convex-hull envelope, not a cavity-finder volume'})
  aligned=''.join(seqs[sid][refpos[x]] for x in range(195,204));t202=maps[sid].get(202);t203=maps[sid].get(203);vals={'sg_sg_distance_angstrom':'','ca_ca_distance_angstrom':'','cb_cb_distance_angstrom':'','cys202_equivalent_sasa_angstrom2':'','cys203_equivalent_sasa_angstrom2':'','geometry_compatible_with_vicinal_disulfide':'no'}
  if t202 and t203:
   k1,r1=st[t202-1];k2,r2=st[t203-1]
   for col,atom in [('sg_sg_distance_angstrom','SG'),('ca_ca_distance_angstrom','CA'),('cb_cb_distance_angstrom','CB')]:
    if atom in r1['atoms'] and atom in r2['atoms']:vals[col]=f"{np.linalg.norm(r1['atoms'][atom]-r2['atoms'][atom]):.3f}"
   vals['cys202_equivalent_sasa_angstrom2']=f"{sas[sid].get(k1[1],0):.2f}";vals['cys203_equivalent_sasa_angstrom2']=f"{sas[sid].get(k2[1],0):.2f}"
   if r1['aa']==r2['aa']=='C' and vals['sg_sg_distance_angstrom'] and 1.9<=float(vals['sg_sg_distance_angstrom'])<=2.3:vals['geometry_compatible_with_vicinal_disulfide']='yes'
  cysrows.append({'sequence_id':sid,'aligned_mettl7b_195_203_region':aligned,'position202_equivalent':t202 or 'gap','position202_residue':seqs[sid][refpos[202]],'position203_equivalent':t203 or 'gap','position203_residue':seqs[sid][refpos[203]],**vals,'interpretation':'Compatibility requires two cysteines and model SG-SG distance 1.9-2.3 A; prediction is not experimental redox evidence.'})
  for rp in POCKET:
   tp=maps[sid].get(rp);obs=seqs[sid][refpos[rp]];key,rr=st[tp-1] if tp else (None,None);sa=sas[sid].get(key[1],0) if key else ''
   contribution=('sulfur chemistry/redox or pocket shape' if rp in (79,148,202,203) else 'aromatic/hydrophobic packing' if rp in (36,77,145,195,199) else 'electrostatic or hydrogen bonding' if rp in (33,44,55,144,149,151,175,196,200) else 'pocket shape/backbone geometry')
   resmaps.append({'sequence_id':sid,'mettl7b_position':rp,'mettl7b_residue':refaln[refpos[rp]],'aligned_target_position':tp or 'gap','aligned_residue':obs,'substitution_class':call(refaln[refpos[rp]],obs),'structural_location':'195-203 pocket loop' if 195<=rp<=203 else 'methyltransferase-core pocket','residue_sasa_angstrom2':f'{sa:.2f}' if sa!='' else '','pocket_exposure':'exposed' if sa!='' and sa>20 else 'buried/partly buried' if sa!='' else 'not mapped','predicted_contribution_to_ligand_binding':contribution})
 for name,data in [('structure_alignment_summary.csv',summaries),('pocket_structural_comparison.csv',pockets),('cysteine_geometry.csv',cysrows),('pocket_residue_map.csv',resmaps)]:
  with (a.phase2/name).open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=data[0]);w.writeheader();w.writerows(data)
if __name__=='__main__':main()
