#!/usr/bin/env python3
"""One-time sealed external METTL7 query and mutant sensitivity pass."""
import csv,gzip,json,sys
from pathlib import Path
import numpy as np
from Bio.PDB import PDBParser
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;S61=ROOT/'analysis/mettl7-closure/stage6_1';sys.path.insert(0,str(HERE));from train_experimental import feature,AA
SYSTEMS=['7A_WT','7B_WT','7A_F43L','7A_F199G','7A_F43L_F199G','7B_L43F','7B_G199F','7B_L43F_G199F']
def pqr(path):
 return np.array([[float(x[30:38]),float(x[38:46]),float(x[46:54])] for x in path.read_text().splitlines() if x.startswith(('ATOM  ','HETATM'))])
def graph(system):
 path=ROOT/f'analysis/mettl7-closure/stage2/prepared/{system}_SAM_BOUND.pdb';m=PDBParser(QUIET=True).get_structure(system,path)[0];cloud=pqr(ROOT/('analysis/mettl7-closure/stage0/METTL7A_homologous_197_sphere_SAM_superpocket.pqr' if system.startswith('7A') else 'resources/shared-resources/src/main/resources/Q6UX53/fpocket/pockets/pocket2_vert.pqr'))
 residues=[];sam=[]
 for chain in m:
  for r in chain:
   atoms=[a for a in r if a.element!='H']
   if r.resname=='SAM':sam=[{'name':a.name,'element':a.element,'xyz':a.coord.tolist(),'formal_charge':None,'aromatic':None} for a in atoms];continue
   if r.id[0]!=' ':continue
   if atoms and np.min(np.linalg.norm(np.array([a.coord for a in atoms])[:,None,:]-cloud[None,:,:],axis=2))<=4.0:
    ca=next((a for a in atoms if a.name=='CA'),None);sc=[a for a in atoms if a.name not in {'N','CA','C','O','OXT'}];residues.append({'id':f'{chain.id}:{r.id[1]}:','chain':chain.id,'number':r.id[1],'insertion_code':'','residue_name':r.resname,'ca':ca.coord.tolist() if ca is not None else None,'side_chain_centroid':np.mean([a.coord for a in sc],axis=0).tolist() if sc else None,'atoms':[{'name':a.name,'element':a.element,'xyz':a.coord.tolist()} for a in atoms]})
 seq=[]
 for a,b in zip(residues,residues[1:]):
  if a['chain']==b['chain']:seq.append({'first':a['id'],'second':b['id']})
 spatial=[]
 for i,a in enumerate(residues):
  for b in residues[i+1:]:
   d=np.linalg.norm(np.array([x['xyz'] for x in a['atoms']])[:,None,:]-np.array([x['xyz'] for x in b['atoms']])[None,:,:],axis=2);minimum=float(d.min())
   if minimum<=4.5:spatial.append({'minimum_distance_A':minimum,'establishing_atom_pairs':[0]*int(np.sum(d<=4.5))})
 spheres=[{'center':x.tolist(),'radius':1.5} for x in cloud];return {'residue_nodes':residues,'sequence_edges':seq,'spatial_edges':spatial,'ligand_occurrence':{'atoms':sam,'bonds':[0]*28},'alpha_spheres':spheres,'alpha_sphere_edges':[]}
def cosine(a,b):
 a=np.array(a);b=np.array(b);return float(a@b/(np.linalg.norm(a)*np.linalg.norm(b)+1e-12))
def aa(g):
 c={x:0 for x in AA}
 for n in g['residue_nodes']:
  if n['residue_name'] in c:c[n['residue_name']]+=1
 return np.array([c[x] for x in AA])/max(1,len(g['residue_nodes']))
def main():
 sealed=json.loads((HERE/'results/experimental-results.json').read_text());assert sealed['status']=='SEALED_EXPERIMENTAL_ONLY'
 with gzip.open(S61/'materialized/graphs.jsonl.gz','rt') as h:exp=[json.loads(x) for x in h]
 model=np.load(HERE/'results/residue_graph-sealed.npz');raw=np.vstack([feature(g,'residue_graph') for g in exp]);z=((raw-model['mean'])/model['scale']-model['pca_mean'])@model['pca_components'].T
 queries={s:graph(s) for s in SYSTEMS};qraw={s:feature(g,'residue_graph') for s,g in queries.items()};qz={s:((qraw[s]-model['mean'])/model['scale']-model['pca_mean'])@model['pca_components'].T for s in SYSTEMS}
 wt={}
 for s in ('7A_WT','7B_WT'):
  order=np.argsort(np.linalg.norm(z-qz[s],axis=1))[:10];wt[s]=[{'rank':k+1,'graph_id':exp[i]['graph_id'],'component_id':exp[i]['ligand_occurrence']['immutable_identity']['component_id'],'embedding_distance':float(np.linalg.norm(z[i]-qz[s])),'residue_chemistry_similarity':cosine(aa(queries[s]),aa(exp[i])),'cofactor_contact_similarity':cosine([len(queries[s]['ligand_occurrence']['atoms']),len(queries[s]['spatial_edges'])],[len(exp[i]['ligand_occurrence']['atoms']),len(exp[i]['spatial_edges'])]),'cavity_free_space_similarity':cosine([len(queries[s]['alpha_spheres'])],[len(exp[i]['alpha_spheres'])])} for k,i in enumerate(order)]
 ab=float(np.linalg.norm(qz['7A_WT']-qz['7B_WT']));mov=[]
 for s in SYSTEMS[2:]:
  parent='7A_WT' if s.startswith('7A') else '7B_WT';mov.append({'system':s,'background':parent,'embedding_movement':float(np.linalg.norm(qz[s]-qz[parent])),'fraction_of_wt_A_B_separation':float(np.linalg.norm(qz[s]-qz[parent])/(ab+1e-12))})
 blocks={'geometry':[0,17],'residue_graph':[17,45]};contrib={k:float(np.linalg.norm(((qraw['7A_WT'][a:b]-model['mean'][a:b])/model['scale'][a:b])-((qraw['7B_WT'][a:b]-model['mean'][a:b])/model['scale'][a:b]))) for k,(a,b) in blocks.items()}
 result={'status':'SEALED_ONE_TIME_EXTERNAL_QUERY_COMPLETE','model':'residue_graph non-neural PCA','model_sha256':sealed['sealed']['residue_graph']['sha256'],'wt_A_B_embedding_distance':ab,'layer_contributions_standardized_raw_distance':contrib,'nearest_experimental_sites':wt,'mutant_sensitivity':mov,'interpretation':'Sensitivity movements are compared descriptively with the frozen benchmark; no fitting, threshold tuning, or label transfer was performed.'}
 (HERE/'results/sealed-mettl-benchmark.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
if __name__=='__main__':main()
