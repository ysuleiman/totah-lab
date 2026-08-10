#!/usr/bin/env python3
"""Deterministic PocketMatch and geometric retrieval baselines."""
import gzip,json
from collections import defaultdict
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;S61=ROOT/'analysis/mettl7-closure/stage6_1'
GROUP={'ALA':0,'VAL':0,'LEU':0,'ILE':0,'MET':0,'PRO':0,'PHE':1,'TYR':1,'TRP':1,'SER':2,'THR':2,'ASN':2,'GLN':2,'CYS':2,'GLY':2,'HIS':3,'LYS':3,'ARG':3,'ASP':4,'GLU':4}
def signature(g):
 pts=[]
 for n in g['residue_nodes']:
  if n['residue_name'] not in GROUP or not n['ca']:continue
  atoms={a['name']:a['xyz'] for a in n['atoms']};ca=np.array(n['ca']);cb=np.array(atoms.get('CB',n['ca']));sc=np.array(n['side_chain_centroid'] or n['ca'])
  for t,p in enumerate((ca,cb,sc)):pts.append((GROUP[n['residue_name']],t,p))
 buckets=defaultdict(list)
 for i,a in enumerate(pts):
  for b in pts[i+1:]:buckets[tuple(sorted((a[0],b[0])))+tuple(sorted((a[1],b[1])))].append(float(np.linalg.norm(a[2]-b[2])))
 return {k:np.sort(v) for k,v in buckets.items()},sum(map(len,buckets.values()))
def compare(a,b):
 matched=0
 for k in set(a[0])|set(b[0]):
  x,y=a[0].get(k,[]),b[0].get(k,[]);i=j=0
  while i<len(x) and j<len(y):
   if abs(x[i]-y[j])<=.5:matched+=1;i+=1;j+=1
   elif x[i]<y[j]:i+=1
   else:j+=1
 return matched/max(1,max(a[1],b[1])),matched/max(1,a[1])
def metrics(scores,groups,pdb,high=True):
 ranks=[]
 for i,g in enumerate(groups):
  if not g or sum(x==g for x in groups)<2:continue
  order=sorted(((scores[i,j],j) for j in range(len(groups)) if j!=i and pdb[j]!=pdb[i]),reverse=high)
  rel=[k+1 for k,(_,j) in enumerate(order) if groups[j]==g]
  if rel:ranks.append(min(rel))
 a=np.array(ranks);return {'queries':len(a),'recall_at_1':float(np.mean(a<=1)),'recall_at_5':float(np.mean(a<=5)),'recall_at_10':float(np.mean(a<=10)),'mrr':float(np.mean(1/a)),'median_first_relevant_rank':float(np.median(a))}
def main():
 with gzip.open(S61/'materialized/graphs.jsonl.gz','rt') as h:g=[json.loads(x) for x in h]
 n=len(g);groups=[x['physical_site_group_id'] for x in g];pdb=[x['graph_id'].split(':')[1] for x in g];sigs=[signature(x) for x in g]
 sym=np.eye(n);cov=np.eye(n)
 shape=[];raw=[]
 for x in g:
  p=np.array([s['center'] for s in x['alpha_spheres']]);c=p-p.mean(0);rad=np.linalg.norm(c,axis=1);shape.append([len(p),rad.max(),rad.mean(),np.quantile(rad,.95),np.sqrt(np.mean(rad**2)),np.mean([s['radius'] for s in x['alpha_spheres']])]);raw.append(np.quantile(rad,np.linspace(0,1,32)))
 for i in range(n):
  for j in range(i+1,n):
   a,b=compare(sigs[i],sigs[j]);sym[i,j]=sym[j,i]=a;cov[i,j]=a;cov[j,i]=b
 shape=np.array(shape);shape=(shape-shape.mean(0))/(shape.std(0)+1e-8);geom=np.linalg.norm(shape[:,None]-shape[None,:],axis=2);raw=np.array(raw);aligned=np.linalg.norm(raw[:,None]-raw[None,:],axis=2)
 result={'pocketmatch_symmetric':metrics(sym,groups,pdb),'pocketmatch_query_coverage':metrics(cov,groups,pdb),'production_geometric_retrieval':metrics(geom,groups,pdb,False),'aligned_raw_pocket_geometry':metrics(aligned,groups,pdb,False),'rules':{'pocketmatch_tolerance_A':.5,'production_geometry':'PocketShapeStatistics-equivalent alpha-sphere radial channels','aligned_raw_geometry':'centroid-aligned alpha-sphere radial quantile distance'}}
 (HERE/'results/baselines.json').write_text(json.dumps(result,indent=2)+'\n');np.savez_compressed(HERE/'results/baseline-matrices.npz',pocketmatch_symmetric=sym,pocketmatch_query_coverage=cov,production_geometry=geom,aligned_raw_geometry=aligned);print(json.dumps(result,indent=2))
if __name__=='__main__':main()
