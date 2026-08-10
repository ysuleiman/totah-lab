#!/usr/bin/env python3
"""Cache exact CCD component definitions required by frozen Stage 6 IDs."""
import json,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;OUT=HERE/'ccd';OUT.mkdir(parents=True,exist_ok=True)
components=sorted({json.loads(x)['component_id'] for x in (ROOT/'analysis/mettl7-closure/stage6/export/graph-envelopes.jsonl').read_text().splitlines()})
for i,c in enumerate(components,1):
 p=OUT/f'{c}.cif'
 if not p.exists(): urllib.request.urlretrieve(f'https://files.rcsb.org/ligands/download/{c}.cif',p)
 if i%25==0: print(i,flush=True)
print(len(components))
