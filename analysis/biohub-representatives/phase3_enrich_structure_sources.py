#!/usr/bin/env python3
from __future__ import annotations
import argparse,csv,json,urllib.error,urllib.request
from concurrent.futures import ThreadPoolExecutor,as_completed
from pathlib import Path
def main():
 ap=argparse.ArgumentParser();ap.add_argument('phase3',type=Path);a=ap.parse_args();r=a.phase3;inv={x['sequence_id']:x for x in csv.DictReader((r/'all_members_inventory.csv').open())};rows=list(csv.DictReader((r/'all_members_structure_inventory.csv').open()))
 def get(url):
  try:return json.load(urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'totah-lab-phase3/1.0'}),timeout=60))
  except urllib.error.HTTPError as e:
   if e.code==404:return None
   raise
 def one(x):
  acc=inv[x['sequence_id']]['uniprot_accession']
  if not acc:return x
  try:
   u=get(f'https://rest.uniprot.org/uniprotkb/{acc}.json') or {};pdb=sorted(z['id'] for z in u.get('uniProtKBCrossReferences',[]) if z.get('database')=='PDB');af=get(f'https://alphafold.ebi.ac.uk/api/prediction/{acc}');x['experimental_pdb_available']='yes:'+(';'.join(pdb)) if pdb else 'no';x['alphafold_db_available']='yes:'+(';'.join(z.get('entryId','') for z in af)) if isinstance(af,list) and af else 'no'
  except Exception as e:x['status'] += '; external-source-query-failed:'+type(e).__name__
  return x
 out=[]
 with ThreadPoolExecutor(max_workers=12) as ex:
  fs=[ex.submit(one,x) for x in rows]
  for f in as_completed(fs):out.append(f.result())
 order={x['sequence_id']:i for i,x in enumerate(rows)};out.sort(key=lambda x:order[x['sequence_id']])
 with (r/'all_members_structure_inventory.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(out)
if __name__=='__main__':main()
