#!/usr/bin/env python3
from __future__ import annotations
import argparse,csv,json,shutil,urllib.request
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor,as_completed
from pathlib import Path
def fasta(p):
 d={};n=None
 for l in p.read_text().splitlines():
  if l.startswith('>'):n=l[1:].split()[0];d[n]=''
  else:d[n]+=l.strip()
 return d
def main():
 ap=argparse.ArgumentParser();ap.add_argument('phase2',type=Path);ap.add_argument('phase3',type=Path);a=ap.parse_args();r=a.phase3
 inv={x['sequence_id']:x for x in csv.DictReader((r/'all_members_inventory.csv').open())};reg={x['sequence_id']:x for x in csv.DictReader((r/'all_members_195_203_region.csv').open())};pc={x['sequence_id']:x for x in csv.DictReader((r/'all_members_pocket_conservation.csv').open())};cl={x['sequence_id']:x for x in csv.DictReader((r/'all_members_classification.csv').open())};st={x['sequence_id']:x for x in csv.DictReader((r/'all_members_structure_inventory.csv').open())}
 reasons=defaultdict(list)
 for sid,x in reg.items():
  if x['aligned_motif_class']=='CC' and x['region_alignment_quality']=='confident':reasons[sid].append('every confidently aligned CC (mandatory)')
  if x['aligned_motif_class'] in ('CG',) and x['region_alignment_quality']=='confident':reasons[sid].append('unusual exact aligned cysteine motif')
 for sid,x in inv.items():
  if x['representative_flag']=='yes':reasons[sid].append('original BioHub representative')
 # Add structurally available controls, up to 50 analyzable BioHub members total.
 structured=[s for s in inv if st[s]['high_confidence_structural_candidate']=='yes']
 candidates=[]
 for sid in structured:
  score=float(pc[sid]['pocket_identity_fraction']);mot=reg[sid]['aligned_motif_class'];cluster=int(inv[sid]['biohub_cluster']);priority=(0 if mot in ('CN','CG') else 1,cluster,-score)
  candidates.append((priority,sid))
 already_struct={s for s in reasons if st[s]['biohub_structure_available']=='yes'}
 for _,sid in sorted(candidates):
  if len(already_struct)>=50:break
  if sid not in reasons:
   reasons[sid].append('high-confidence structurally available motif/pocket/cluster control');already_struct.add(sid)
 selected=[]
 for sid in inv:
  if sid in reasons:selected.append({'sequence_id':sid,'biohub_identifier':inv[sid]['biohub_identifier'],'biohub_cluster':inv[sid]['biohub_cluster'],'classification':cl[sid]['classification'],'aligned_motif':reg[sid]['aligned_motif_class'],'pocket_identity_fraction':pc[sid]['pocket_identity_fraction'],'biohub_structure_available':st[sid]['biohub_structure_available'],'high_confidence_structure':st[sid]['high_confidence_structural_candidate'],'selected_for_coordinate_analysis':'yes' if sid in already_struct else 'no','selection_reason':'; '.join(reasons[sid]),'constraint_note':'Subset exceeds 80 because 127 confidently aligned CC proteins were mandatory; unavailable structures remain inventory-only.'})
 with (r/'structural_subset.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=selected[0]);w.writeheader();w.writerows(selected)
 work=r/'tmp'/'structural_subset';structures=work/'structures';structures.mkdir(parents=True,exist_ok=True)
 def fetch(sid):
  h=inv[sid]['biohub_identifier'];url=f'https://biohub.ai/esm/protein/api/v1alpha1/proteins/{h}?topk_features=1&fold_on_miss=false';d=json.load(urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'totah-lab-phase3/1.0'}),timeout=120));p=structures/f'{sid}.biohub.pdb';p.write_text(d['pdb']);return sid,p
 paths={}
 with ThreadPoolExecutor(max_workers=12) as ex:
  fs=[ex.submit(fetch,s) for s in sorted(already_struct)]
  for f in as_completed(fs):sid,p=f.result();paths[sid]=p
 for sid in ('HUMAN_METTL7B','HUMAN_METTL7A'):
  src=a.phase2/'structures'/f'{sid}.alphafold.pdb';dst=structures/src.name;shutil.copy2(src,dst);paths[sid]=dst
 aln=fasta(r/'all_members_alignment.fasta')
 with (work/'cluster_alignment.fasta').open('w') as f:
  for sid in ['HUMAN_METTL7B','HUMAN_METTL7A']+sorted(already_struct):f.write(f'>{sid}\n{aln[sid]}\n')
 rows=[]
 for sid,p in paths.items():rows.append({'sequence_id':sid,'analysis_structure_path':str(p)})
 with (work/'structure_inventory.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
if __name__=='__main__':main()
