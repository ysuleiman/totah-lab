#!/usr/bin/env python3
"""Deterministic experimental-only representation baselines and small autoencoders."""
import gzip,hashlib,json,random
from collections import Counter,defaultdict
from pathlib import Path
import numpy as np
from sklearn.decomposition import PCA
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import accuracy_score,balanced_accuracy_score
from sklearn.linear_model import LogisticRegression
import torch
from torch import nn

ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;S61=ROOT/'analysis/mettl7-closure/stage6_1';OUT=HERE/'results';OUT.mkdir(parents=True,exist_ok=True)
AA=['ALA','ARG','ASN','ASP','CYS','GLN','GLU','GLY','HIS','ILE','LEU','LYS','MET','PHE','PRO','SER','THR','TRP','TYR','VAL']; ELS=['C','N','O','S','P','F','CL','BR','I']
SEED=20260810
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def stats(x):
 x=np.asarray(x,float);return [len(x),*(np.quantile(x,[0,.25,.5,.75,1]).tolist() if len(x) else [0]*5),float(x.mean()) if len(x) else 0,float(x.std()) if len(x) else 0]
def feature(g,variant):
 ca=[n['ca'] for n in g['residue_nodes'] if n['ca']]; d=[]
 for i,a in enumerate(ca):
  for b in ca[i+1:]:d.append(np.linalg.norm(np.array(a)-b))
 f=stats(d)+stats([e['minimum_distance_A'] for e in g['spatial_edges']])+[len(g['sequence_edges'])]
 if variant!='geometry_only':
  c=Counter(n['residue_name'] for n in g['residue_nodes']);f += [c[a]/max(1,len(g['residue_nodes'])) for a in AA]
  f += stats([len(e['establishing_atom_pairs']) for e in g['spatial_edges']])
 if variant in ('residue_ligand_cofactor','residue_ligand_cofactor_cavity'):
  atoms=g['ligand_occurrence']['atoms'];c=Counter(a['element'].upper() for a in atoms);f += [c[x]/max(1,len(atoms)) for x in ELS]
  f += [len(atoms),len(g['ligand_occurrence']['bonds']),sum(a['aromatic'] is True for a in atoms),sum((a['formal_charge'] or 0) for a in atoms)]
 if variant=='residue_ligand_cofactor_cavity':
  f += stats([s['radius'] for s in g['alpha_spheres']])+stats([e['surface_gap_A'] for e in g['alpha_sphere_edges']])
 return np.array(f,float)
def retrieval(z,groups,pdb):
 evaluable=[i for i,g in enumerate(groups) if g and sum(x==g for x in groups)>1];ranks=[]
 for i in evaluable:
  dist=np.linalg.norm(z-z[i],axis=1);order=sorted((dist[j],j) for j in range(len(z)) if j!=i and pdb[j]!=pdb[i]); rel=[k+1 for k,(_,j) in enumerate(order) if groups[j]==groups[i]]
  if rel:ranks.append(min(rel))
 return {'queries':len(ranks),'recall_at_1':float(np.mean(np.array(ranks)<=1)),'recall_at_5':float(np.mean(np.array(ranks)<=5)),'recall_at_10':float(np.mean(np.array(ranks)<=10)),'mrr':float(np.mean(1/np.array(ranks))),'median_first_relevant_rank':float(np.median(ranks))}
class AE(nn.Module):
 def __init__(self,d,k):super().__init__();self.enc=nn.Sequential(nn.Linear(d,32),nn.ReLU(),nn.Linear(32,k));self.dec=nn.Sequential(nn.Linear(k,32),nn.ReLU(),nn.Linear(32,d))
 def forward(self,x):z=self.enc(x);return self.dec(z),z
def main():
 manifest=json.loads((S61/'materialization-manifest.json').read_text());path=S61/'materialized/graphs.jsonl.gz'
 if sha(path)!=manifest['graphs_sha256']:raise RuntimeError('Stage 6.1 hash mismatch')
 with gzip.open(path,'rt') as h:graphs=[json.loads(x) for x in h]
 assert len(graphs)==697
 groups=[g['physical_site_group_id'] for g in graphs];pdb=[g['graph_id'].split(':')[1] for g in graphs]
 variants=['geometry_only','residue_graph','residue_ligand_cofactor','residue_ligand_cofactor_cavity'];results={};curves={}
 torch.manual_seed(SEED);np.random.seed(SEED);random.seed(SEED);torch.use_deterministic_algorithms(True)
 sealed={}
 for variant in variants:
  raw=np.vstack([feature(g,variant) for g in graphs]);scaler=StandardScaler().fit(raw);x=scaler.transform(raw)
  pca=PCA(n_components=min(16,x.shape[1]),svd_solver='full').fit(x);zp=pca.transform(x)
  # Group-balanced autoencoder: each mapped group contributes total weight one; unmapped observations are excluded.
  idx=[i for i,g in enumerate(groups) if g];counts=Counter(groups[i] for i in idx);weights=torch.tensor([1/counts[groups[i]] for i in idx],dtype=torch.float32);xt=torch.tensor(x[idx],dtype=torch.float32)
  model=AE(x.shape[1],min(16,x.shape[1]));opt=torch.optim.Adam(model.parameters(),lr=.005,weight_decay=1e-4);curve=[]
  for epoch in range(101):
   opt.zero_grad();recon,z=model(xt);loss=(((recon-xt)**2).mean(1)*weights).sum()/weights.sum();loss.backward();opt.step()
   if epoch%10==0:curve.append({'epoch':epoch,'group_balanced_reconstruction_loss':float(loss.detach())})
  with torch.no_grad():za=model.enc(torch.tensor(x,dtype=torch.float32)).numpy()
  results[variant]={'non_neural_pca':retrieval(zp,groups,pdb),'small_autoencoder':retrieval(za,groups,pdb),'dimensions':x.shape[1],'pca_explained_variance':float(pca.explained_variance_ratio_.sum())}
  curves[variant]=curve
  payload={'mean':scaler.mean_,'scale':scaler.scale_,'pca_components':pca.components_,'pca_mean':pca.mean_}
  payload.update({f'ae_{key}':value.detach().numpy() for key,value in model.state_dict().items()});np.savez(OUT/f'{variant}-sealed.npz',**payload)
  sealed[variant]={'artifact':f'{variant}-sealed.npz','sha256':sha(OUT/f'{variant}-sealed.npz')}
 # Masked node controls: predict contact role and residue identity using only evaluated nodes; observation and inverse-group weighted.
 node_x=[];contact=[];residue=[];node_groups=[]
 for g in graphs:
  for n in g['residue_nodes']:
   if n['ca']:
    node_x.append([len(n['atoms']),*(1 if n['residue_name']==a else 0 for a in AA)]);contact.append(2 if 'DIRECT' in n['contact_roles'] else 1 if 'NEAR_SHELL' in n['contact_roles'] else 0);residue.append(AA.index(n['residue_name']) if n['residue_name'] in AA else -1);node_groups.append(g['physical_site_group_id'])
 nx=np.asarray(node_x);contact=np.asarray(contact);residue=np.asarray(residue);valid=np.array([g is not None for g in node_groups]);train=np.where(valid)[0];split=np.array([int(hashlib.sha256(str(node_groups[i]).encode()).hexdigest()[:8],16)%5 for i in train]);tr=train[split!=0];te=train[split==0]
 clf=LogisticRegression(max_iter=300,random_state=SEED).fit(nx[tr],contact[tr]);pred=clf.predict(nx[te]);masked={'contact_role':{'observation_accuracy':accuracy_score(contact[te],pred),'balanced_accuracy':balanced_accuracy_score(contact[te],pred)}}
 rvalid=train[residue[train]>=0];split=np.array([int(hashlib.sha256(str(node_groups[i]).encode()).hexdigest()[:8],16)%5 for i in rvalid]);tr=rvalid[split!=0];te=rvalid[split==0];rx=np.delete(nx,slice(1,21),axis=1);clf=LogisticRegression(max_iter=300,random_state=SEED).fit(rx[tr],residue[tr]);pred=clf.predict(rx[te]);masked['residue_identity']={'observation_accuracy':accuracy_score(residue[te],pred),'balanced_accuracy':balanced_accuracy_score(residue[te],pred),'trivial_majority_accuracy':float(max(Counter(residue[tr]).values())/len(tr))}
 report={'status':'SEALED_EXPERIMENTAL_ONLY','seed':SEED,'input_sha256':manifest['graphs_sha256'],'variants':results,'masked_tasks':masked,'controls':{'same_pdb_excluded_from_retrieval':True,'group_balanced_training':True,'target_ccd_cofactor_group_ids_not_features':True,'family_held_out_claim':False},'sealed':sealed}
 (OUT/'experimental-results.json').write_text(json.dumps(report,indent=2)+'\n');(OUT/'learning-curves.json').write_text(json.dumps(curves,indent=2)+'\n');print(json.dumps(report,indent=2))
if __name__=='__main__':main()
