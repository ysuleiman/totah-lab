#!/usr/bin/env python3
"""Materialize the frozen Stage 6 adapters into offline deterministic records."""
from __future__ import annotations
import csv,gzip,hashlib,json,math,os,subprocess
from collections import defaultdict
from pathlib import Path
import numpy as np
from Bio.PDB import MMCIFParser
from Bio.PDB.MMCIF2Dict import MMCIF2Dict

ROOT=Path(__file__).resolve().parents[3]; HERE=Path(__file__).resolve().parent
S6=ROOT/'analysis/mettl7-closure/stage6'; OUT=HERE/'materialized'
SPATIAL_CUTOFF=4.5; SPHERE_GAP_CUTOFF=1.0
AA_CLASS={'ALA':'HYDROPHOBIC','VAL':'HYDROPHOBIC','LEU':'HYDROPHOBIC','ILE':'HYDROPHOBIC','MET':'HYDROPHOBIC','PRO':'HYDROPHOBIC','PHE':'AROMATIC','TYR':'AROMATIC','TRP':'AROMATIC','SER':'POLAR','THR':'POLAR','ASN':'POLAR','GLN':'POLAR','CYS':'POLAR','GLY':'SPECIAL','HIS':'POSITIVE','LYS':'POSITIVE','ARG':'POSITIVE','ASP':'NEGATIVE','GLU':'NEGATIVE'}
SQL="""COPY (SELECT o.id occurrence_id,o.component_id,o.auth_asym_id,o.auth_sequence_id,coalesce(o.insertion_code,''),o.alternate_location,o.model_number FROM docking.assembly_component_occurrence o JOIN docking.experimental_binding_site s ON s.occurrence_id=o.id ORDER BY o.id) TO STDOUT WITH CSV HEADER"""
GRAMMAR="""COPY (SELECT alignment_id,query_uniprot_position,candidate_uniprot_position,query_residue,candidate_residue,query_chemistry,candidate_chemistry,query_contact_role,candidate_contact_role,query_ca_rmsf,candidate_ca_rmsf,query_side_chain_rmsf,candidate_side_chain_rmsf,query_structural_status,candidate_structural_status FROM docking.experimental_site_grammar_residue ORDER BY alignment_id,query_uniprot_position,candidate_uniprot_position) TO STDOUT WITH CSV HEADER"""

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def psql(q):
 e=dict(os.environ);e.setdefault('PGPASSWORD','admin');r=subprocess.run(['psql','-U','postgres','-d','totah_lab_db','-c',q],env=e,text=True,capture_output=True,check=True);return list(csv.DictReader(r.stdout.splitlines()))
def norm_i(v):
 v='' if v is None else str(v).strip()
 return '' if v in ('','.','?') else v
def xyz(atom): return [round(float(x),6) for x in atom.coord]
def centroid(atoms): return np.mean([a.coord for a in atoms],axis=0) if atoms else None
def canon_pair(a,b): return (a,b) if a<b else (b,a)
def cif_list(d,key):
 v=d.get(key,[]);return v if isinstance(v,list) else [v]

def topology(cif,component,atoms):
 atom_ids=cif_list(cif,'_chem_comp_atom.atom_id');charges=cif_list(cif,'_chem_comp_atom.charge');atom_arom=cif_list(cif,'_chem_comp_atom.pdbx_aromatic_flag')
 props={name:(int(charges[i]) if i<len(charges) and charges[i] not in ('.','?') else None,i<len(atom_arom) and atom_arom[i]=='Y') for i,name in enumerate(atom_ids)}
 for atom in atoms:
  if atom['name'] in props: atom['formal_charge'],atom['aromatic']=props[atom['name']]
 ids=cif_list(cif,'_chem_comp_bond.comp_id'); a1=cif_list(cif,'_chem_comp_bond.atom_id_1');a2=cif_list(cif,'_chem_comp_bond.atom_id_2');orders=cif_list(cif,'_chem_comp_bond.value_order');arom=cif_list(cif,'_chem_comp_bond.pdbx_aromatic_flag')
 present={a['name'] for a in atoms};bonds=[]
 for i,c in enumerate(ids):
  if c==component and a1[i] in present and a2[i] in present:
   bonds.append({'first':a1[i],'second':a2[i],'order':orders[i],'aromatic':i<len(arom) and arom[i]=='Y'})
 return sorted(bonds,key=lambda b:(b['first'],b['second']))

def main():
 OUT.mkdir(parents=True,exist_ok=True)
 envelopes=[json.loads(x) for x in (S6/'export/graph-envelopes.jsonl').read_text().splitlines()]
 ann=defaultdict(list)
 for r in csv.DictReader((S6/'export/site-residue-annotations.csv').open()): ann[int(r['site_id'])].append(r)
 spheres=defaultdict(list)
 for r in csv.DictReader((S6/'export/alpha-spheres.csv').open()): spheres[int(r['site_id'])].append(r)
 occ={int(r['occurrence_id']):r for r in psql(SQL)}; grammar=psql(GRAMMAR)
 grammar_by_pos=defaultdict(list)
 for r in grammar:
  grammar_by_pos[int(r['query_uniprot_position'])].append({'side':'QUERY',**r});grammar_by_pos[int(r['candidate_uniprot_position'])].append({'side':'CANDIDATE',**r})
 cache={}; records=[]
 parser=MMCIFParser(QUIET=True,auth_chains=True,auth_residues=True)
 for n,e in enumerate(envelopes,1):
  path=e['source']['mmcif']
  if path not in cache:
   structure=parser.get_structure(e['pdb_id'],path); cache[path]=(structure,MMCIF2Dict(path))
  structure,cif=cache[path]; model=structure[0]; site_id=e['experimental_site_id']
  selected={}
  for a in ann[site_id]: selected[(a['auth_asym_id'],int(a['residue_number']),norm_i(a['insertion_code']))]=a
  nodes=[];res_objects={}
  for chain,num,ins in sorted(selected):
   if chain not in model: continue
   matches=[r for r in model[chain] if r.id[1]==num and norm_i(r.id[2])==ins and r.id[0]==' ']
   if not matches: continue
   r=matches[0]; atoms=[x for x in r if x.element!='H']; ca=next((x for x in atoms if x.name=='CA'),None);sc=[x for x in atoms if x.name not in {'N','CA','C','O','OXT'}]
   rid=f'{chain}:{num}:{ins}'; ar=selected[(chain,num,ins)]; up=int(ar['uniprot_position']) if ar['uniprot_position'] else None
   node={'id':rid,'chain':chain,'number':num,'insertion_code':ins,'residue_name':r.resname,'chemistry':AA_CLASS.get(r.resname),'chemistry_status':'PRESENT' if r.resname in AA_CLASS else 'NOT_AVAILABLE','ca':xyz(ca) if ca is not None else None,'side_chain_centroid':[round(float(x),6) for x in centroid(sc)] if sc else None,'atoms':[{'name':x.name,'element':x.element,'xyz':xyz(x)} for x in atoms],'contact_roles':sorted({x['distance_band'] for x in ann[site_id] if x['auth_asym_id']==chain and int(x['residue_number'])==num and norm_i(x['insertion_code'])==ins}),'uniprot_accession':ar['uniprot_accession'] or None,'uniprot_position':up,'mapping_outcome':ar['mapping_outcome'] or None,'athena_grammar':grammar_by_pos.get(up,[]) if up else []}
   nodes.append(node);res_objects[rid]=r
  node_ids={x['id'] for x in nodes};seq=[]
  bychain=defaultdict(list)
  for x in nodes: bychain[x['chain']].append(x)
  for chain,vals in bychain.items():
   vals.sort(key=lambda x:(x['number'],x['insertion_code']))
   for a,b in zip(vals,vals[1:]):seq.append({'first':a['id'],'second':b['id'],'provenance':'CHAIN_ORDER_INFERRED'})
  spatial=[]
  for i,a in enumerate(nodes):
   aa=a['atoms'];ca=np.array(a['ca']) if a['ca'] else None;ac=np.mean([x['xyz'] for x in aa],axis=0)
   for b in nodes[i+1:]:
    bb=b['atoms']; pairs=[];minimum=math.inf
    for x in aa:
     for y in bb:
      d=float(np.linalg.norm(np.array(x['xyz'])-np.array(y['xyz'])))
      if d<=SPATIAL_CUTOFF:pairs.append({'first_atom':x['name'],'second_atom':y['name'],'distance_A':round(d,6)})
      minimum=min(minimum,d)
    if minimum<=SPATIAL_CUTOFF:
     bc=np.mean([x['xyz'] for x in bb],axis=0);cb=np.array(b['ca']) if b['ca'] else None
     spatial.append({'first':a['id'],'second':b['id'],'minimum_distance_A':round(minimum,6),'ca_distance_A':round(float(np.linalg.norm(ca-cb)),6) if ca is not None and cb is not None else None,'centroid_distance_A':round(float(np.linalg.norm(ac-bc)),6),'establishing_atom_pairs':pairs})
  oi=e['canonical_adapters']['ligand']['occurrence_id']; meta=occ[oi]; ligand_atoms=[]
  chain=meta['auth_asym_id'];num=int(meta['auth_sequence_id']) if meta['auth_sequence_id'] else None;ins=norm_i(meta['coalesce'])
  if chain in model and num is not None:
   matches=[r for r in model[chain] if r.id[1]==num and norm_i(r.id[2])==ins and r.resname==meta['component_id']]
   if matches:
    ligand_atoms=[{'name':x.name,'element':x.element,'xyz':xyz(x),'formal_charge':getattr(x,'pqr_charge',None),'aromatic':None,'donor':None,'acceptor':None} for x in matches[0] if x.element!='H']
  sphere_nodes=[{'pocket_id':int(x['pocket_id']),'sphere_number':int(x['sphere_number']),'center':[float(x['x']),float(x['y']),float(x['z'])],'radius':float(x['radius'])} for x in spheres[site_id]]
  sphere_edges=[]
  for i,a in enumerate(sphere_nodes):
   for b in sphere_nodes[i+1:]:
    d=float(np.linalg.norm(np.array(a['center'])-np.array(b['center'])));gap=d-a['radius']-b['radius']
    if gap<=SPHERE_GAP_CUTOFF:sphere_edges.append({'first':f"{a['pocket_id']}:{a['sphere_number']}",'second':f"{b['pocket_id']}:{b['sphere_number']}",'center_distance_A':round(d,6),'surface_gap_A':round(gap,6)})
  ccd=MMCIF2Dict(HERE/'ccd'/f"{meta['component_id']}.cif")
  record={'graph_id':e['graph_id'],'physical_site_group_id':e['physical_site_group_id'],'leakage_component_id':e['leakage_component_id'],'source':e['source'],'construction_rules':{'sequence':'Gaia SequencePolicy.EXPLICIT_OR_CHAIN_ORDER','spatial_atom_selection':'HEAVY','spatial_cutoff_A':SPATIAL_CUTOFF,'spatial_edge':'minimum establishing heavy-atom distance <= cutoff','alpha_sphere_edge':'center_distance-radius_i-radius_j <= cutoff','alpha_sphere_surface_gap_cutoff_A':SPHERE_GAP_CUTOFF},'residue_nodes':nodes,'sequence_edges':seq,'spatial_edges':spatial,'ligand_occurrence':{'immutable_identity':{'pdb_id':e['pdb_id'],'assembly_id':e['assembly_id'],'component_id':meta['component_id'],'auth_asym_id':chain,'auth_sequence_id':meta['auth_sequence_id'],'insertion_code':ins,'alternate_location':meta['alternate_location'],'model_number':int(meta['model_number'])},'atoms':ligand_atoms,'bonds':topology(ccd,meta['component_id'],ligand_atoms),'chemistry_missingness':{'donor_acceptor':'NOT_AVAILABLE_CANONICAL_MODEL'}},'alpha_spheres':sphere_nodes,'alpha_sphere_edges':sphere_edges}
  records.append(record)
  if n%50==0: print(n,flush=True)
 out=OUT/'graphs.jsonl.gz'
 with out.open('wb') as raw:
  with gzip.GzipFile(filename='',mode='wb',fileobj=raw,mtime=0) as gz:
   for r in records:gz.write((json.dumps(r,sort_keys=True,separators=(',',':'))+'\n').encode())
 manifest={'status':'PASS','graphs':len(records),'graph_ids_sha256':hashlib.sha256('\n'.join(r['graph_id'] for r in records).encode()).hexdigest(),'physical_group_assignments_sha256':hashlib.sha256('\n'.join(f"{r['graph_id']}\t{r['physical_site_group_id']}\t{r['leakage_component_id']}" for r in records).encode()).hexdigest(),'graphs_sha256':sha(out),'construction_rules':records[0]['construction_rules'],'alpha_spheres':sum(len(r['alpha_spheres']) for r in records),'residue_nodes':sum(len(r['residue_nodes']) for r in records),'sequence_edges':sum(len(r['sequence_edges']) for r in records),'spatial_edges':sum(len(r['spatial_edges']) for r in records),'ligand_atoms':sum(len(r['ligand_occurrence']['atoms']) for r in records),'ligand_bonds':sum(len(r['ligand_occurrence']['bonds']) for r in records),'training_loader_requires_database':False}
 (HERE/'materialization-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(manifest,indent=2))
if __name__=='__main__':main()
