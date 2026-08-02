#!/usr/bin/env python3
from __future__ import annotations
import argparse,csv,json,urllib.request
from concurrent.futures import ThreadPoolExecutor,as_completed
from pathlib import Path
def main():
 ap=argparse.ArgumentParser();ap.add_argument('phase3',type=Path);a=ap.parse_args();p=a.phase3/'all_members_inventory.csv';rows=list(csv.DictReader(p.open()))
 def one(r):
  upi=r['uniparc_identifier']
  if not upi:return r
  try:
   req=urllib.request.Request(f'https://rest.uniprot.org/uniparc/{upi}.json',headers={'User-Agent':'totah-lab-phase3/1.0'});d=json.load(urllib.request.urlopen(req,timeout=90));active=[x for x in d.get('uniParcCrossReferences',[]) if x.get('active') and x.get('database','').startswith('UniProtKB')];x=active[0] if active else next(iter(d.get('uniParcCrossReferences',[])),{});org=x.get('organism') or {};r['uniprot_accession']=x.get('id','') if active else '';r['organism']=org.get('scientificName','');r['taxonomy_id']=org.get('taxonId','');r['annotation']=x.get('proteinName') or r['annotation']
  except Exception:pass
  return r
 out=[]
 with ThreadPoolExecutor(max_workers=12) as ex:
  fs=[ex.submit(one,r) for r in rows]
  for f in as_completed(fs):out.append(f.result())
 order={x['sequence_id']:i for i,x in enumerate(rows)};out.sort(key=lambda x:order[x['sequence_id']])
 with p.open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(out)
if __name__=='__main__':main()
