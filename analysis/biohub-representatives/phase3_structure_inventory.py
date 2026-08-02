#!/usr/bin/env python3
from __future__ import annotations
import argparse,csv,json,urllib.request
from concurrent.futures import ThreadPoolExecutor,as_completed
from pathlib import Path
def main():
 ap=argparse.ArgumentParser();ap.add_argument('phase3',type=Path);a=ap.parse_args();r=a.phase3
 inv=list(csv.DictReader((r/'all_members_inventory.csv').open()));regions={x['sequence_id']:x for x in csv.DictReader((r/'all_members_195_203_region.csv').open())};pmap=list(csv.DictReader((r/'all_members_pocket_residue_map.csv').open()));pby={}
 for x in pmap:pby.setdefault(x['sequence_id'],[]).append(x)
 def one(x):
  sid=x['sequence_id'];h=x['biohub_identifier'];url=f'https://biohub.ai/esm/protein/api/v1alpha1/proteins/{h}?topk_features=1&fold_on_miss=false'
  try:
   req=urllib.request.Request(url,headers={'User-Agent':'totah-lab-phase3/1.0'});d=json.load(urllib.request.urlopen(req,timeout=120));raw=[float(z) for z in (d.get('residues_plddt') or []) if z is not None];mv=d.get('mean_plddt');mean=float(mv) if mv is not None else (sum(raw)/len(raw) if raw else 0);scale=100 if mean and mean<=1.5 else 1;scores=[z*scale for z in raw];mean*=scale
   local=[int(regions[sid][f'mettl7b_{p}_target_position']) for p in range(195,204) if regions[sid][f'mettl7b_{p}_target_position']!='gap'];pocket=[int(z['target_residue_number']) for z in pby[sid] if z['target_residue_number']!='gap'];avg=lambda ps:sum(scores[z-1] for z in ps if 0<z<=len(scores))/len([z for z in ps if 0<z<=len(scores)]) if any(0<z<=len(scores) for z in ps) else None
   return {'sequence_id':sid,'biohub_identifier':h,'biohub_cluster':x['biohub_cluster'],'biohub_structure_available':'yes' if d.get('pdb') else 'no','biohub_mean_plddt':f'{mean:.2f}' if mean else '','region_195_203_mean_plddt':f'{avg(local):.2f}' if avg(local)!=None else '','mapped_pocket_mean_plddt':f'{avg(pocket):.2f}' if avg(pocket)!=None else '','alphafold_db_available':'not assessed: no resolved active accession' if not x['uniprot_accession'] else 'not queried','experimental_pdb_available':'not assessed: no resolved active accession' if not x['uniprot_accession'] else 'not queried','high_confidence_structural_candidate':'yes' if mean>=70 and (avg(local) or 0)>=70 and (avg(pocket) or 0)>=70 else 'no','status':'ok' if mean else 'ok_no_confidence_scores'}
  except Exception as e:return {'sequence_id':sid,'biohub_identifier':h,'biohub_cluster':x['biohub_cluster'],'biohub_structure_available':'unknown','biohub_mean_plddt':'','region_195_203_mean_plddt':'','mapped_pocket_mean_plddt':'','alphafold_db_available':'not assessed','experimental_pdb_available':'not assessed','high_confidence_structural_candidate':'no','status':type(e).__name__+': '+str(e)[:120]}
 out=[]
 with ThreadPoolExecutor(max_workers=16) as ex:
  fs={ex.submit(one,x):x for x in inv}
  for f in as_completed(fs):out.append(f.result())
 order={x['sequence_id']:i for i,x in enumerate(inv)};out.sort(key=lambda x:order[x['sequence_id']])
 with (r/'all_members_structure_inventory.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=out[0]);w.writeheader();w.writerows(out)
if __name__=='__main__':main()
