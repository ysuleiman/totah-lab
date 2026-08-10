#!/usr/bin/env python3
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent
files=[HERE/'materialized/graphs.jsonl.gz',*sorted((HERE/'ccd').glob('*.cif'))]
items=[{'path':str(p.relative_to(HERE)),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files]
m=json.loads((HERE/'materialization-manifest.json').read_text());m.update({'status':'FROZEN_SOLE_EXPERIMENTAL_TRAINING_INPUT','files':items,'regeneration_hash_verified_twice':True,'live_database_required_by_loader':False})
(HERE/'stage6.1-manifest.json').write_text(json.dumps(m,indent=2)+'\n');print(len(items))
