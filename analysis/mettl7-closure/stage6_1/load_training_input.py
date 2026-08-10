#!/usr/bin/env python3
"""Hash-enforcing offline loader for the sole permissible training input."""
import gzip,hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
def load():
 m=json.loads((HERE/'stage6.1-manifest.json').read_text())
 for item in m['files']:
  p=HERE/item['path']
  if not p.is_file() or p.stat().st_size!=item['bytes'] or hashlib.sha256(p.read_bytes()).hexdigest()!=item['sha256']:raise RuntimeError(f"Stage 6.1 hash mismatch: {item['path']}")
 with gzip.open(HERE/'materialized/graphs.jsonl.gz','rt') as h:g=[json.loads(x) for x in h]
 if len(g)!=697:raise RuntimeError('Stage 6.1 graph count mismatch')
 return g
if __name__=='__main__':print(len(load()))
