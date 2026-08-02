#!/usr/bin/env python3
"""Collect BioHub representative and AlphaFold reference structures."""
from __future__ import annotations
import argparse,csv,json,urllib.request
from pathlib import Path

def get(url, path, binary=False):
    if path.exists(): return path.read_bytes() if binary else path.read_text()
    req=urllib.request.Request(url,headers={'User-Agent':'totah-lab-phase2/1.0'})
    with urllib.request.urlopen(req,timeout=120) as r: data=r.read()
    path.write_bytes(data)
    return data if binary else data.decode()

def main():
    ap=argparse.ArgumentParser();ap.add_argument('phase1',type=Path);ap.add_argument('phase2',type=Path);a=ap.parse_args()
    a.phase2.mkdir(parents=True,exist_ok=True); raw=a.phase2/'raw'; structures=a.phase2/'structures';raw.mkdir(exist_ok=True);structures.mkdir(exist_ok=True)
    meta=list(csv.DictReader((a.phase1/'cluster_representatives_metadata.csv').open()))
    prior={x['sequence_id']:x for x in csv.DictReader((a.phase1/'structural_availability.csv').open())}
    rows=[]
    for r in meta:
        sid=r['sequence_id']; chosen=''; source=''; model_id=''; mean=''; low=''; af=''; bio=''; exp=''
        if sid.startswith('CLUSTER_'):
            h=r['representative_hash']; p=raw/f'{sid}.biohub.json'
            payload=json.loads(get(f'https://biohub.ai/esm/protein/api/v1alpha1/proteins/{h}?topk_features=1&fold_on_miss=false',p))
            chosen=str(structures/f'{sid}.biohub.pdb');Path(chosen).write_text(payload['pdb'])
            source='BioHub Atlas predicted structure';model_id=h
            raw_scores=payload.get('residues_plddt',[]); raw_mean=float(payload.get('mean_plddt',0))
            scale=100.0 if raw_mean<=1.5 else 1.0
            mean=raw_mean*scale;low=sum(x*scale<70 for x in raw_scores);bio=chosen
            acc=r['uniprot_accession']; afjson=a.phase1/'raw'/f'{acc}.alphafold.json' if acc else None
            if afjson and afjson.exists():
                entries=json.loads(afjson.read_text())
                if entries:
                    afp=structures/f'{sid}.alphafold.pdb';get(entries[0]['pdbUrl'],afp,binary=True);af=str(afp)
        else:
            acc=r['uniprot_accession']; j=json.loads((a.phase1/'raw'/f'{acc}.alphafold.json').read_text())
            if not j: raise RuntimeError(f'no AlphaFold model for {sid}')
            entry=j[0]; afp=structures/f'{sid}.alphafold.pdb';get(entry['pdbUrl'],afp,binary=True)
            chosen=str(afp);source='AlphaFold DB';model_id=entry['entryId'];af=str(afp)
            # AlphaFold PDB stores pLDDT in B-factor.
            vals=[]
            for line in afp.read_text().splitlines():
                if line.startswith('ATOM') and line[12:16].strip()=='CA': vals.append(float(line[60:66]))
            mean=sum(vals)/len(vals);low=sum(x<70 for x in vals)
        exp=prior.get(sid,{}).get('pdb_identifiers','')
        rows.append({'sequence_id':sid,'cluster_id':r['cluster_id'],'organism':r['organism'],'sequence_length':r['sequence_length'],'analysis_structure_path':chosen,'analysis_structure_source':source,'model_identifier':model_id,'mean_plddt':f'{float(mean):.2f}','residues_plddt_below_70':low,'biohub_structure_path':bio,'alphafold_structure_path':af,'experimental_pdb_ids':exp,'experimental_structure_available':'yes' if exp else 'no','notes':'BioHub model used for representatives to test BioHub-derived similarity; AlphaFold DB used for human references. Alternate AlphaFold model retained where available.'})
    with (a.phase2/'structure_inventory.csv').open('w',newline='') as f:
        w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
if __name__=='__main__':main()
