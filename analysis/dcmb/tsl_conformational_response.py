# DEPRECATED FOR V2 USE (2026-09-05): near-attack geometry in this script is superseded by
# athena.tmt.NearAttackGeometry / NearAttackAssessor / EnsembleNacAnalyzer (Java).
# See ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md. Retained for historical regression reproduction only.
"""Minimal restrained geometric response of WT METTL7A around SAM + TSL."""
from pathlib import Path
import csv,json,math,sys
import numpy as np
from rdkit import Chem
from rdkit.Chem import AllChem
ROOT=Path(__file__).resolve().parents[2]; OUT=ROOT/'analysis/dcmb/tsl_conformational_response'; OUT.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(ROOT/'analysis/dcmb')); import same_site_pose_analysis as b
sys.path.insert(0,str(ROOT/'analysis/dcmb/tsl_catalytic_geometry')); import reconstruct_tsl as rt
BACK={'N','CA','C','O','OXT'}

def ensemble():
 mol=Chem.AddHs(Chem.MolFromSmiles(rt.SMILES)); p=AllChem.ETKDGv3(); p.randomSeed=rt.SEED; p.pruneRmsThresh=.35; ids=AllChem.EmbedMultipleConfs(mol,numConfs=12,params=p)
 for cid in ids: AllChem.MMFFOptimizeMolecule(mol,confId=cid,maxIters=500)
 heavy=Chem.RemoveHs(mol); si=next(a.GetIdx() for a in heavy.GetAtoms() if a.GetSymbol()=='S')
 bound=ROOT/'analysis/dcmb/sam_state/validated/WT_METTL7A_SAM_BOUND.pdb'; sa=rt.sam(bound); ss=next(a[5] for a in sa if a[0]=='SD'); mc=next(a[5] for a in sa if a[0]=='CE'); axis=(mc-ss)/np.linalg.norm(mc-ss)
 rec=ROOT/'resources/shared-resources/src/main/resources/Q9H8H3/Q9H8H3_TMT1A_HUMAN.pdb'; pa=rt.receptor(rec); prot=np.array([a[5] for a in pa]); local=np.linalg.norm(prot-mc,axis=1)<16; lprot=prot[local]; latoms=[a for a,k in zip(pa,local) if k]; samxyz=np.array([a[5] for a in sa]); cloud=rt.b.spheres(Path('/Users/yazan/artifacts/UP000005640_9606_HUMAN_v6_pockets/fpocket-human/AF-Q6UX53-F1-model_v6-1472429501895029362/AF-Q6UX53-F1-model_v6_out/pockets/pocket1_vert.pqr')); r,t=rt.fit_ba(); cloud=cloud@r+t
 rng=np.random.default_rng(rt.SEED); candidates=[]
 for ci in range(heavy.GetNumConformers()):
  conf=heavy.GetConformer(ci); raw=np.array([[conf.GetAtomPosition(i).x,conf.GetAtomPosition(i).y,conf.GetAtomPosition(i).z] for i in range(heavy.GetNumAtoms())]); centered=raw-raw[si]
  for distance in rt.DISTANCES:
   for ri in range(rt.ROTATIONS):
    trial=rng.normal(size=3); perp=trial-axis*float(trial@axis); perp/=np.linalg.norm(perp); theta=math.acos(rng.uniform(math.cos(math.radians(30)),1)); avec=axis*math.cos(theta)+perp*math.sin(theta); xyz=centered@rt.random_rotation(rng)+mc+avec*distance
    m=rt.evaluate(xyz,lprot,samxyz,cloud,si,ss,mc); pd=np.linalg.norm(xyz[:,None,:]-lprot[None,:,:],axis=2); clash=np.argwhere(pd<2)
    residues={ (latoms[j][2],latoms[j][3],latoms[j][1]) for _,j in clash }; backbone={ (latoms[j][2],latoms[j][3],latoms[j][1]) for _,j in clash if latoms[j][0] in BACK }
    candidates.append({'ci':ci,'ri':ri,'distance':distance,'xyz':xyz,'metrics':m,'residues':residues,'backbone':backbone,'pairs':[(int(i),latoms[int(j)]) for i,j in clash]})
 return heavy,pa,sa,si,ss,mc,candidates,rec,bound

def clash_map(candidates):
 fav=[c for c in candidates if c['metrics']['sam_pairs_lt_2A']==0 and c['metrics']['superpocket_atom_fraction']>=.70]; agg={}
 for c in fav:
  seen=set()
  for _,a in c['pairs']:
   k=(a[2],a[3],a[1]); z=agg.setdefault(k,{'candidates':0,'pairs':0,'minimum_A':99.,'backbone_pairs':0,'sidechain_pairs':0}); z['pairs']+=1; z['minimum_A']=min(z['minimum_A'],min(float(np.linalg.norm(c['xyz'][i]-a[5])) for i,x in c['pairs'] if x is a)); z['backbone_pairs' if a[0] in BACK else 'sidechain_pairs']+=1; seen.add(k)
  for k in seen: agg[k]['candidates']+=1
 rows=[]
 for (ch,n,res),v in agg.items(): rows.append({'chain':ch,'residue_number':n,'residue_name':res,'candidate_frequency':v['candidates'],'candidate_fraction':v['candidates']/len(fav),'clashing_pairs':v['pairs'],'minimum_distance_A':v['minimum_A'],'backbone_pairs':v['backbone_pairs'],'sidechain_pairs':v['sidechain_pairs'],'obstruction_type':'backbone_involved' if v['backbone_pairs'] else 'sidechain_only'})
 rows.sort(key=lambda x:(-x['candidate_frequency'],x['minimum_distance_A'])); return fav,rows

def select(fav,n=5):
 order=sorted(fav,key=lambda c:(c['metrics']['protein_pairs_lt_2A'],-c['metrics']['protein_min_A'],-c['metrics']['superpocket_atom_fraction'],abs(c['metrics']['attack_angle_TSL_S_Cmethyl_SAM_S_deg']-180)))
 chosen=[]
 for c in order:
  if all(b.rmsd(c['xyz'],x['xyz'])>=2 for x in chosen): chosen.append(c)
  if len(chosen)==n: break
 return chosen

def relax(pa,lig,samxyz,mobile_res,backbone=False,steps=2500):
 xyz=np.array([a[5] for a in pa]); start=xyz.copy(); mobile=np.array([i for i,a in enumerate(pa) if (a[2],a[3],a[1]) in mobile_res and (backbone or a[0] not in BACK)]); fixed=np.array([i for i in range(len(pa)) if i not in set(mobile)])
 # Preserve initial intra-residue covalent geometry with harmonic springs.
 bonds=[]
 for x,i in enumerate(mobile):
  for j in mobile[x+1:]:
   if pa[i][2:4]==pa[j][2:4] and np.linalg.norm(start[i]-start[j])<1.9: bonds.append((i,j,float(np.linalg.norm(start[i]-start[j]))))
 m=np.zeros_like(xyz); v=np.zeros_like(xyz); lr=.006
 for step in range(1,steps+1):
  grad=np.zeros_like(xyz); grad[mobile]+=4*(xyz[mobile]-start[mobile])
  for env,k in ((lig,80.),(samxyz,80.),(xyz[fixed],50.)):
   delta=xyz[mobile,None,:]-env[None,:,:]; d=np.linalg.norm(delta,axis=2); mask=d<2.1; safe=np.maximum(d,1e-4); grad[mobile]+=np.sum(np.where(mask[:,:,None],-2*k*(2.1-d)[:,:,None]*delta/safe[:,:,None],0),axis=1)
  for i,j,d0 in bonds:
   dv=xyz[i]-xyz[j]; d=max(np.linalg.norm(dv),1e-6); g=120*(d-d0)*dv/d; grad[i]+=g; grad[j]-=g
  m=.9*m+.1*grad; v=.999*v+.001*grad*grad; xyz[mobile]-=lr*(m[mobile]/.1)/(np.sqrt(v[mobile]/.001)+1e-8)
 protein_d=np.linalg.norm(lig[:,None,:]-xyz[None,:,:],axis=2); sam_d=np.linalg.norm(lig[:,None,:]-samxyz[None,:,:],axis=2); disp=np.linalg.norm(xyz-start,axis=1)
 maxbond=max((abs(np.linalg.norm(xyz[i]-xyz[j])-d0) for i,j,d0 in bonds),default=0)
 return xyz,{'protein_min_A':float(protein_d.min()),'protein_pairs_lt_2A':int((protein_d<2).sum()),'sam_min_A':float(sam_d.min()),'sam_pairs_lt_2A':int((sam_d<2).sum()),'mobile_atoms':len(mobile),'sidechain_rmsd_A':float(np.sqrt(np.mean(disp[mobile]**2))) if len(mobile) else 0,'max_atom_displacement_A':float(disp[mobile].max()) if len(mobile) else 0,'backbone_rmsd_A':float(np.sqrt(np.mean(disp[[i for i,a in enumerate(pa) if a[0] in BACK]]**2))),'max_bond_deviation_A':maxbond},disp

def write_complex(rec,pa,pxyz,bound,heavy,lig,path,product=False):
 lookup={(a[2],a[3],a[0]):p for a,p in zip(pa,pxyz)}; lines=[]
 for x in rec.read_text().splitlines():
  if x.startswith('ATOM'):
   k=(x[21:22].strip(),int(x[22:26]),x[12:16].strip()); p=lookup[k]; x=x[:30]+f'{p[0]:8.3f}{p[1]:8.3f}{p[2]:8.3f}'+x[54:]; lines.append(x)
 lines+=['TER']; sl=[x for x in bound.read_text().splitlines() if x.startswith('HETATM') and x[17:20].strip()=='SAM' and (not product or x[12:16].strip()!='CE')]; lines+=sl+['TER']; serial=max(int(x[6:11]) for x in lines if x.startswith(('ATOM','HETATM')))+1
 for i,(a,p) in enumerate(zip(heavy.GetAtoms(),lig),serial): lines.append(f"HETATM{i:5d} {a.GetSymbol()+str(i-serial+1):>4s} {'TMS' if product else 'TSL'} T   1    {p[0]:8.3f}{p[1]:8.3f}{p[2]:8.3f}  1.00  0.00          {a.GetSymbol():>2s}")
 lines+=['TER']
 if product:
  p=lig[next(a.GetIdx() for a in heavy.GetAtoms() if a.GetSymbol()=='S')]; mc=p+(next(x[5] for x in rt.sam(bound) if x[0]=='CE')-p)/np.linalg.norm(next(x[5] for x in rt.sam(bound) if x[0]=='CE')-p)*1.81; lines.append(f"HETATM{serial+heavy.GetNumAtoms():5d}  CM  TMS T   1    {mc[0]:8.3f}{mc[1]:8.3f}{mc[2]:8.3f}  1.00  0.00           C")
 lines+=['END','']; path.write_text('\n'.join(lines))

def main():
 heavy,pa,sa,si,ss,mc,cands,rec,bound=ensemble(); fav,cmap=clash_map(cands); chosen=select(fav); samxyz=np.array([a[5] for a in sa]);
 with (OUT/'clash_frequency.csv').open('w',newline='') as f: w=csv.DictWriter(f,fieldnames=list(cmap[0])); w.writeheader(); w.writerows(cmap)
 sel=[]; relaxrows=[]; prodrows=[]
 for rank,c in enumerate(chosen,1):
  mobile=set(c['residues']); sel.append({'rank':rank,'conformer':c['ci'],'rotation':c['ri'],'distance_A':c['distance'],**c['metrics'],'clashing_residues':';'.join(f'{x[0]}{x[1]}:{x[2]}' for x in sorted(mobile,key=lambda z:z[1])),'selection_reason':'SAM-clear; contained; lowest protein clash burden; >=2 A from earlier selected orientations'})
  xyz,m,disp=relax(pa,c['xyz'],samxyz,mobile,False); passed=m['protein_pairs_lt_2A']==0 and m['sam_pairs_lt_2A']==0 and m['max_bond_deviation_A']<=.15
  cost='SMALL RESPONSE' if passed and m['max_atom_displacement_A']<1 else ('MODERATE RESPONSE' if passed and m['max_atom_displacement_A']<2.5 else ('LARGE RESPONSE' if passed else 'UNRESOLVED'))
  relaxrows.append({'rank':rank,'stage':'sidechain_only','passed':passed,'classification':cost,'sam_displacement_A':0,'tsl_displacement_A':0,'catalytic_distance_A':c['metrics']['tsl_s_to_sam_methyl_A'],'attack_angle_deg':c['metrics']['attack_angle_TSL_S_Cmethyl_SAM_S_deg'],**m})
  if not passed:
   near={ (a[2],a[3],a[1]) for a in pa if np.min(np.linalg.norm(c['xyz']-a[5],axis=1))<4 }; xyz,m,disp=relax(pa,c['xyz'],samxyz,mobile|near,True); passed=m['protein_pairs_lt_2A']==0 and m['sam_pairs_lt_2A']==0 and m['max_bond_deviation_A']<=.15
   cost='MODERATE RESPONSE' if passed and m['backbone_rmsd_A']<1 and m['max_atom_displacement_A']<2.5 else ('LARGE RESPONSE' if passed else 'UNRESOLVED')
   relaxrows.append({'rank':rank,'stage':'local_backbone','passed':passed,'classification':cost,'sam_displacement_A':0,'tsl_displacement_A':0,'catalytic_distance_A':c['metrics']['tsl_s_to_sam_methyl_A'],'attack_angle_deg':c['metrics']['attack_angle_TSL_S_Cmethyl_SAM_S_deg'],**m})
  if passed: write_complex(rec,pa,xyz,bound,heavy,c['xyz'],OUT/f'WT_METTL7A_SAM_TSL_relaxed_{rank}.pdb')
  # Product state: SAH lacks CE; methyl carbon is placed 1.81 A from TSL sulfur toward its pre-transfer position.
  ts=c['xyz'][si]; pm=ts+(mc-ts)/np.linalg.norm(mc-ts)*1.81; sah=np.array([a[5] for a in sa if a[0]!='CE']); product=np.vstack([c['xyz'],pm]); pd=np.linalg.norm(product[:,None,:]-xyz[None,:,:],axis=2); sd=np.linalg.norm(product[:,None,:]-sah[None,:,:],axis=2)
  pp={'rank':rank,'based_on_relaxed_pretransfer':passed,'protein_min_A':float(pd.min()),'protein_pairs_lt_2A':int((pd<2).sum()),'sah_product_min_A':float(sd.min()),'sah_product_pairs_lt_2A':int((sd<2).sum()),'product_viable':int((pd<2).sum())==0 and int((sd<2).sum())==0}; prodrows.append(pp)
  if pp['product_viable']: write_complex(rec,pa,xyz,bound,heavy,c['xyz'],OUT/f'WT_METTL7A_SAH_TMSL_product_{rank}.pdb',True)
 def write(name,rows):
  with (OUT/name).open('w',newline='') as f: w=csv.DictWriter(f,fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)
 write('selected_near_misses.csv',sel); write('relaxation_metrics.csv',relaxrows); write('product_state_metrics.csv',prodrows)
 (OUT/'summary.json').write_text(json.dumps({'candidates_total':len(cands),'otherwise_favorable':len(fav),'selected':len(chosen),'bottleneck_residues':len(cmap),'pretransfer_passed':sum(x['passed'] for x in relaxrows),'product_states_viable':sum(x['product_viable'] for x in prodrows),'relaxation_method':'restrained Cartesian steric/bond/positional geometric minimization; not a molecular-mechanics force field'},indent=2)+'\n')
 print((OUT/'summary.json').read_text()); print('top bottlenecks',cmap[:15]); print('relax',relaxrows); print('product',prodrows)
if __name__=='__main__': main()
