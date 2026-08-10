#!/usr/bin/env python3
"""Deterministic, pose-free chemistry/context triage of the frozen 216."""
from __future__ import annotations
import csv,gzip,hashlib,json,math
from pathlib import Path
import numpy as np
from rdkit import Chem,DataStructs,RDLogger
from rdkit.Chem import AllChem,BRICS,Crippen,Descriptors,Lipinski,MolSurf,rdMolDescriptors
from rdkit.Chem.Scaffolds import MurckoScaffold
from rdkit.ML.Cluster import Butina

HERE=Path(__file__).resolve().parent; ROOT=HERE.parents[2]; OUT=HERE/'stage7a'; OUT.mkdir(exist_ok=True)
RDLogger.DisableLog('rdApp.*')
def fp(m): return rdMolDescriptors.GetMorganFingerprintAsBitVect(m,2,nBits=2048)
def sim(a,b): return DataStructs.TanimotoSimilarity(a,b)
def mol_from_occ(o):
 m=Chem.RWMol(); idx={};
 for a in o['atoms']:
  element=str(a['element']).strip().title(); atom=Chem.Atom(element); atom.SetFormalCharge(int(a.get('formal_charge') or 0)); idx[a['name']]=m.AddAtom(atom)
 orders={'SING':Chem.BondType.SINGLE,'DOUB':Chem.BondType.DOUBLE,'TRIP':Chem.BondType.TRIPLE,'AROM':Chem.BondType.AROMATIC}
 for b in o['bonds']:
  if b['first'] in idx and b['second'] in idx:
   try:m.AddBond(idx[b['first']],idx[b['second']],orders.get(b['order'],Chem.BondType.SINGLE))
   except RuntimeError:pass
 x=m.GetMol()
 try: Chem.SanitizeMol(x)
 except Exception:return None
 return x
def shape(m,seed):
 x=Chem.AddHs(Chem.Mol(m)); p=AllChem.ETKDGv3();p.randomSeed=seed;p.numThreads=1
 ids=AllChem.EmbedMultipleConfs(x,numConfs=5,params=p)
 ext=[]
 for i in ids:
  q=np.array(x.GetConformer(i).GetPositions()); _,_,v=np.linalg.svd(q-q.mean(0),full_matrices=False); z=(q-q.mean(0))@v.T; ext.append(np.ptp(z,axis=0))
 return np.median(ext,axis=0).tolist() if ext else [None]*3
def pocket_extent(path):
 q=[]
 for l in path.read_text().splitlines():
  if l.startswith(('ATOM  ','HETATM')):q.append([float(l[30:38]),float(l[38:46]),float(l[46:54])])
 return sorted(np.ptp(np.array(q),axis=0),reverse=True)
def main():
 manifest=json.loads((HERE/'provenance-manifest.json').read_text()); expected=manifest['files'][0]['sha256']
 if hashlib.sha256((HERE/'candidate-provenance.csv').read_bytes()).hexdigest()!=expected:raise RuntimeError('provenance hash mismatch')
 cand=list(csv.DictReader((HERE/'candidate-provenance.csv').open())); sealed=json.loads((ROOT/'analysis/mettl7-closure/stage7/results/sealed-mettl-benchmark.json').read_text())
 near={t:{x['graph_id'] for x in sealed['nearest_experimental_sites'][t]} for t in ('7A_WT','7B_WT')}
 exp=[]
 with gzip.open(ROOT/'analysis/mettl7-closure/stage6_1/materialized/graphs.jsonl.gz','rt') as h:
  for line in h:
   g=json.loads(line); m=mol_from_occ(g['ligand_occurrence'])
   if m: exp.append((g['graph_id'],g['ligand_occurrence']['immutable_identity']['component_id'],fp(m),set(BRICS.BRICSDecompose(m))))
 probes=[]
 for r in csv.DictReader((ROOT/'analysis/dcmb/sar_experiment/sar_compounds.csv').open()):
  m=Chem.MolFromSmiles(r['prepared_smiles'] or r['canonical_smiles']);
  if m:probes.append((r['compound_id'],fp(m)))
 cavity={'7A':pocket_extent(ROOT/'analysis/mettl7-closure/stage0/METTL7A_homologous_197_sphere_SAM_superpocket.pqr'),'7B':pocket_extent(ROOT/'resources/shared-resources/src/main/resources/Q6UX53/fpocket/pockets/pocket2_vert.pqr')}
 rows=[]; mols=[]; fps=[]
 for i,r in enumerate(cand):
  m=Chem.MolFromSmiles(r['smiles']); valid=m is not None
  if not valid:
   rows.append({**r,'preparation_valid':False});mols.append(None);fps.append(None);continue
  f=fp(m); fr=set(BRICS.BRICSDecompose(m)); sh=shape(m,20260810+i); ss=sorted([x for x in sh if x is not None],reverse=True)
  allsim=[(sim(f,e[2]),e) for e in exp]; best=max(allsim,key=lambda x:x[0]);
  def nb(key):
   q=[x for x in allsim if x[1][0] in near[key]]; return max(q,key=lambda x:x[0]) if q else (None,None)
  a,b=nb('7A_WT'),nb('7B_WT'); motif=max((len(fr&e[3])/max(1,len(fr|e[3])) for e in exp),default=0)
  scaffold=Chem.MolToSmiles(MurckoScaffold.GetScaffoldForMol(m),isomericSmiles=True)
  rec={**r,'preparation_valid':True,'canonical_isomeric_smiles':Chem.MolToSmiles(m,True),'heavy_atoms':m.GetNumHeavyAtoms(),'molecular_weight':Descriptors.MolWt(m),'cLogP':Crippen.MolLogP(m),'tpsa':MolSurf.TPSA(m),'rotatable_bonds':Lipinski.NumRotatableBonds(m),'formal_charge':Chem.GetFormalCharge(m),'scaffold':scaffold or 'ACYCLIC','conformer_extent_1_A':ss[0],'conformer_extent_2_A':ss[1],'conformer_extent_3_A':ss[2],'coarse_fit_7A':all(ss[j]<=cavity['7A'][j] for j in range(3)),'coarse_fit_7B':all(ss[j]<=cavity['7B'][j] for j in range(3)),'max_experimental_ligand_similarity':best[0],'nearest_experimental_component':best[1][1],'max_7A_neighborhood_ligand_similarity':a[0],'max_7B_neighborhood_ligand_similarity':b[0],'experimental_brics_motif_jaccard':motif,'max_dcmb_probe_similarity':max(sim(f,x[1]) for x in probes)}
  rows.append(rec);mols.append(m);fps.append(f)
 valididx=[i for i,f in enumerate(fps) if f]; d=[]
 for x in range(1,len(valididx)):
  d.extend([1-sim(fps[valididx[x]],fps[valididx[y]]) for y in range(x)])
 clusters=Butina.ClusterData(d,len(valididx),0.35,isDistData=True,reordering=True); cid={valididx[j]:k+1 for k,c in enumerate(clusters) for j in c}
 dims=['max_experimental_ligand_similarity','max_7B_neighborhood_ligand_similarity','experimental_brics_motif_jaccard']
 for i,r in enumerate(rows):r['fingerprint_cluster']=cid.get(i,''); r['pareto_nondominated']=r.get('preparation_valid') and not any(all(o[d]>=r[d] for d in dims) and any(o[d]>r[d] for d in dims) for o in rows if o.get('preparation_valid'))
 for r in rows:
  label=(r.get('external_id') or r.get('name') or '').upper()
  r['prospective_eligibility']='EXCLUDED_WARHEAD' if label.startswith('WH-') else ('ELIGIBLE' if r.get('preparation_valid') else 'EXCLUDED_INVALID')
 eligible=[i for i in valididx if rows[i]['prospective_eligibility']=='ELIGIBLE']
 order=sorted(eligible,key=lambda i:(not rows[i]['pareto_nondominated'],-rows[i]['max_7B_neighborhood_ligand_similarity'],-rows[i]['experimental_brics_motif_jaccard'],rows[i]['immutable_ligand_identity_sha256']))
 selected=[]; used=set()
 for i in order:
  if cid[i] not in used:selected.append(i);used.add(cid[i])
  if len(selected)==40:break
 for i in order:
  if len(selected)==40:break
  if i not in selected:selected.append(i)
 for i,r in enumerate(rows):
  r['selected_stage7a']=i in selected;r['retention_reason']='EXCLUDED_WARHEAD' if r['prospective_eligibility']=='EXCLUDED_WARHEAD' else ('' if i not in selected else ('PARETO_NONDOMINATED_DIVERSE_CLUSTER' if r['pareto_nondominated'] else 'DIVERSITY_COVERAGE_WITH_EXPERIMENTAL_CONTEXT'))
 fields=list(rows[0]);
 with (OUT/'zero-docking-features.csv').open('w',newline='') as h:w=csv.DictWriter(h,fields);w.writeheader();w.writerows(rows)
 subset=[rows[i] for i in selected]
 with (OUT/'stage7a-subset.csv').open('w',newline='') as h:w=csv.DictWriter(h,fields);w.writeheader();w.writerows(subset)
 summary={'schema':'mettl7_stage7a_zero_docking_v2','input_count':len(rows),'valid_count':sum(bool(r['preparation_valid']) for r in rows),'warheads_excluded':sum(r['prospective_eligibility']=='EXCLUDED_WARHEAD' for r in rows),'eligible_non_warhead_count':sum(r['prospective_eligibility']=='ELIGIBLE' for r in rows),'selected_count':40,'fingerprint_clusters':len(clusters),'selected_wh_count':sum((r.get('external_id') or r.get('name') or '').upper().startswith('WH-') for r in subset),'historical_scores_used_for_selection':False,'pose_specific_channels_evaluated':False,'selection':'non-WH only; Pareto-first, one-per-fingerprint-cluster, deterministic diversity fill','files':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (OUT/'zero-docking-features.csv',OUT/'stage7a-subset.csv')}}
 (OUT/'stage7a-manifest.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2))
if __name__=='__main__':main()
