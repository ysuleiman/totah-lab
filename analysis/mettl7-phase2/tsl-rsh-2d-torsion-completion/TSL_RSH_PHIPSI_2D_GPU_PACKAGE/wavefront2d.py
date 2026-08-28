#!/usr/bin/env python3
"""Deterministic, resumable tuple-valued periodic TorsionDrive controller."""
from __future__ import annotations
import hashlib, json, math, os
from pathlib import Path, PurePosixPath

GRID=tuple(range(-180,180,30)); TOL=0.1; DECREASE=1e-5; WINDOW=15/627.5094740631
def wrap(x): return ((int(x)+180)%360)-180
def cell(phi,psi):
 c=(wrap(phi),wrap(psi))
 if c[0] not in GRID or c[1] not in GRID: raise ValueError(f"off-grid cell {c}")
 return c
def key(c): return f"{c[0]:+04d},{c[1]:+04d}"
def unkey(s): a,b=s.split(','); return cell(int(a),int(b))
def neighbors(c):
 p,q=cell(*c); return tuple(sorted({cell(p-30,q),cell(p+30,q),cell(p,q-30),cell(p,q+30)}))
def angular_error(a,b): return abs((float(a)-float(b)+180)%360-180)
def geometry_gate(record):
 for name in ('phi','psi'):
  if angular_error(record[f'actual_{name}_degrees'],record[f'target_{name}_degrees'])>TOL:
   raise ValueError(f"{name.upper()} constraint violation")
 if record.get('connectivity_pass') is not True or record.get('chirality_pass') is not True: raise ValueError('molecular identity gate')
 return True
def task_id(parent_id,target): return hashlib.sha256(f"{parent_id}|{key(target)}".encode()).hexdigest()[:24]
def atomic_json(path,value):
 path.parent.mkdir(parents=True,exist_ok=True); tmp=path.with_name(path.name+'.tmp')
 tmp.write_text(json.dumps(value,indent=2,sort_keys=True,allow_nan=False)+'\n'); os.replace(tmp,path)
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def checksum_dir(root):
 files=sorted(p for p in root.rglob('*') if p.is_file() and p.name!='SHA256SUMS')
 (root/'SHA256SUMS').write_text(''.join(f'{sha(p)}  {p.relative_to(root)}\n' for p in files))
def verify_dir(root):
 manifest=root/'SHA256SUMS'
 if not manifest.is_file(): raise RuntimeError(f'missing manifest {manifest}')
 listed=set()
 for row in manifest.read_text().splitlines():
  digest,rel=row.split(maxsplit=1); listed.add(rel); p=root/rel
  if not p.is_file() or sha(p)!=digest: raise RuntimeError(f'checksum failure {p}')
 actual={str(p.relative_to(root)) for p in root.rglob('*') if p.is_file() and p.name!='SHA256SUMS'}
 if listed!=actual: raise RuntimeError('manifest coverage mismatch')
def candidate_geometry_ref(candidate_id):
 return f'candidates/{candidate_id}/final.xyz'
def _safe_candidate_ref(ref):
 p=PurePosixPath(ref)
 if p.is_absolute() or '..' in p.parts or len(p.parts)!=3 or p.parts[0]!='candidates' or p.parts[2]!='final.xyz':
  raise RuntimeError(f'unsafe candidate geometry reference {ref}')
 return p.parts[1]
def reduce_round(cells,records):
 """Best update is unconditional; reactivation requires canonical decrease."""
 grouped={}
 for r in records:
  geometry_gate(r); k=key(cell(r['target_phi_degrees'],r['target_psi_degrees']))
  if k not in grouped or (r['energy_hartree'],r['task_id'])<(grouped[k]['energy_hartree'],grouped[k]['task_id']): grouped[k]=r
 active=[]
 for k,r in sorted(grouped.items()):
  old=cells.get(k)
  if old is None: cells[k]=r; active.append(unkey(k))
  elif (r['energy_hartree'],r['task_id'])<(old['energy_hartree'],old['task_id']):
   cells[k]=r
   if old['energy_hartree']-r['energy_hartree']>DECREASE: active.append(unkey(k))
 return cells,tuple(active)
def propagate(cells,active):
 if not cells:return []
 floor=min(r['energy_hartree'] for r in cells.values()); tasks={}
 for source in sorted(active):
  r=cells[key(source)]
  if r['energy_hartree']-floor>WINDOW: continue
  for target in neighbors(source):
   tid=task_id(r['task_id'],target); tasks[tid]={'task_id':tid,'parent_id':r['task_id'],'parent_cell':list(source),'target_cell':list(target),'source_geometry':r['geometry'],'source_geometry_sha256':r['geometry_sha256']}
 return [tasks[k] for k in sorted(tasks)]
class Campaign:
 def __init__(self,root,protocol_sha,executor): self.root=Path(root);self.protocol_sha=protocol_sha;self.executor=executor
 @property
 def state_path(self):return self.root/'WAVEFRONT_STATE.json'
 def init(self,seeds,initial_records=()):
  if self.state_path.exists():return self.load(seeds)
  if self.root.exists() and any(p.name!='candidates' for p in self.root.iterdir()):raise RuntimeError('partial state without checkpoint')
  self.root.mkdir(parents=True,exist_ok=True); queue=[]
  for s in sorted(seeds):
   target=cell(*seeds[s]['target']);tid=task_id(s,target);queue.append({'task_id':tid,'parent_id':s,'parent_cell':None,'target_cell':list(target),'source_geometry':f'seed:{s}','source_geometry_sha256':sha(Path(seeds[s]['geometry']))})
  cells={}; active=[]; completed=[]
  for record in initial_records:
   record=self._normalize_record(dict(record),seeds)
   geometry_gate(record); c=cell(record['target_phi_degrees'],record['target_psi_degrees'])
   k=key(c); old=cells.get(k)
   if old is None or (record['energy_hartree'],record['task_id'])<(old['energy_hartree'],old['task_id']): cells[k]=record
   active.append(c); completed.append(record['task_id'])
  queue=[t for t in queue if key(cell(*t['target_cell'])) not in cells]
  queue.extend(propagate(cells,active))
  dedup={t['task_id']:t for t in queue}
  state={'schema':'phipsi-wavefront-v1','protocol_sha256':self.protocol_sha,'round':0,'cells':cells,'queue':[dedup[k] for k in sorted(dedup)],'completed':sorted(set(completed)),'failed':[]}
  self.save(state);return state
 def save(self,state):
  atomic_json(self.state_path,state);atomic_json(self.root/'STATE_RECEIPT.json',{'state_sha256':sha(self.state_path),'protocol_sha256':self.protocol_sha})
 def _candidate_path(self,ref,expected_sha):
  candidate_id=_safe_candidate_ref(ref); path=self.root/'candidates'/candidate_id/'final.xyz'
  verify_dir(path.parent)
  if not path.is_file() or sha(path)!=expected_sha: raise RuntimeError(f'parent geometry identity failure {path}')
  return path
 def _legacy_candidate_ref(self,value,expected_id):
  p=Path(value)
  parts=p.parts
  hits=[i for i,x in enumerate(parts) if x=='candidates']
  if len(hits)!=1: raise RuntimeError(f'unrecognized legacy geometry path {value}')
  tail=parts[hits[0]:]
  if len(tail)!=3 or tail[1]!=expected_id or tail[2]!='final.xyz': raise RuntimeError(f'legacy geometry identity mismatch {value}')
  ref=candidate_geometry_ref(expected_id); path=self.root/ref; verify_dir(path.parent)
  if not path.is_file(): raise RuntimeError(f'missing migrated geometry {path}')
  return ref,sha(path)
 def _normalize_record(self,record,seeds):
  value=record.get('geometry'); tid=record.get('task_id')
  if not value or not tid: raise RuntimeError('record geometry provenance missing')
  if Path(value).is_absolute(): value,digest=self._legacy_candidate_ref(value,tid)
  else:
   candidate_id=_safe_candidate_ref(value)
   if candidate_id!=tid: raise RuntimeError('record candidate identity mismatch')
   path=self.root/value; verify_dir(path.parent); digest=sha(path)
  declared=record.get('geometry_sha256')
  if declared is not None and declared!=digest: raise RuntimeError('record geometry hash mismatch')
  record['geometry']=value;record['geometry_sha256']=digest
  return record
 def _normalize_task(self,task,seeds):
  task=dict(task); value=task.get('source_geometry'); parent=task.get('parent_id')
  if not value or not parent: raise RuntimeError('task source provenance missing')
  if value.startswith('seed:'):
   seed=value.split(':',1)[1]
   if seed!=parent or seed not in seeds: raise RuntimeError('seed identity mismatch')
   digest=sha(Path(seeds[seed]['geometry']))
  elif Path(value).is_absolute(): value,digest=self._legacy_candidate_ref(value,parent)
  else:
   candidate_id=_safe_candidate_ref(value)
   if candidate_id!=parent: raise RuntimeError('task parent identity mismatch')
   digest=sha(self._candidate_path(value,task.get('source_geometry_sha256')))
  declared=task.get('source_geometry_sha256')
  if declared is not None and declared!=digest: raise RuntimeError('task source geometry hash mismatch')
  task['source_geometry']=value;task['source_geometry_sha256']=digest
  return task
 def _resolve_task(self,task,seeds):
  task=self._normalize_task(task,seeds); resolved=dict(task); ref=task['source_geometry']
  resolved['source_geometry']=str(Path(seeds[ref.split(':',1)[1]]['geometry']).resolve()) if ref.startswith('seed:') else str(self._candidate_path(ref,task['source_geometry_sha256']).resolve())
  return resolved
 def load(self,seeds=None):
  if seeds is None: raise RuntimeError('seed identities required to load checkpoint')
  state=json.loads(self.state_path.read_text());receipt=json.loads((self.root/'STATE_RECEIPT.json').read_text())
  if state['protocol_sha256']!=self.protocol_sha or receipt['state_sha256']!=sha(self.state_path):raise RuntimeError('state identity failure')
  state['cells']={k:self._normalize_record(dict(v),seeds) for k,v in state['cells'].items()}
  state['queue']=[self._normalize_task(t,seeds) for t in state['queue']]
  self.save(state)
  return state
 def run(self,seeds,stop_after=None):
  state=self.init(seeds);count=0
  while state['queue']:
   finished=[]
   for task in state['queue']:
    out=self.root/'candidates'/task['task_id']
    if task['task_id'] in state['completed']:
     verify_dir(out);finished.append(self._normalize_record(json.loads((out/'RECORD.json').read_text()),seeds));continue
    if out.exists():raise RuntimeError(f'incomplete candidate {out}')
    tmp=out.with_name(out.name+'.in_progress')
    try:r=self.executor(self._resolve_task(task,seeds),tmp)
    except Exception as e:
     fail=self.root/'failures'/task['task_id'];atomic_json(fail/'FAILURE.json',{'task':task,'error':type(e).__name__,'message':str(e)});checksum_dir(fail);state['failed'].append(task['task_id']);self.save(state);continue
    r.update({'task_id':task['task_id'],'parent_id':task['parent_id'],'parent_cell':task['parent_cell'],'target_phi_degrees':task['target_cell'][0],'target_psi_degrees':task['target_cell'][1],'source_geometry_ref':task['source_geometry'],'source_geometry_sha256':task['source_geometry_sha256'],'geometry':candidate_geometry_ref(task['task_id']),'geometry_sha256':sha(tmp/'final.xyz')})
    geometry_gate(r);atomic_json(tmp/'RECORD.json',r);checksum_dir(tmp);os.replace(tmp,out);state['completed'].append(task['task_id']);self.save(state);finished.append(r);count+=1
    if stop_after is not None and count>=stop_after:raise KeyboardInterrupt('deliberate interruption')
   state['cells'],active=reduce_round(state['cells'],finished);queue=propagate(state['cells'],active)
   state['queue']=[t for t in queue if t['task_id'] not in state['completed'] and t['task_id'] not in state['failed']];state['round']+=1;self.save(state)
  return state
 def advance_one(self,seeds,initial_records=()):
  """Complete at most one candidate, preserving a round barrier across processes."""
  state=self.init(seeds,initial_records)
  if not state['queue']: return state
  task=next((t for t in state['queue'] if t['task_id'] not in state['completed'] and t['task_id'] not in state['failed']),None)
  if task is not None:
   out=self.root/'candidates'/task['task_id']; tmp=out.with_name(out.name+'.in_progress')
   if out.exists(): raise RuntimeError(f'unrecorded completed candidate {out}')
   if tmp.exists(): raise RuntimeError(f'incomplete candidate {tmp}')
   try:r=self.executor(self._resolve_task(task,seeds),tmp)
   except Exception as e:
    fail=self.root/'failures'/task['task_id'];atomic_json(fail/'FAILURE.json',{'task':task,'error':type(e).__name__,'message':str(e)});checksum_dir(fail);state['failed'].append(task['task_id']);self.save(state);return state
   r.update({'task_id':task['task_id'],'parent_id':task['parent_id'],'parent_cell':task['parent_cell'],'target_phi_degrees':task['target_cell'][0],'target_psi_degrees':task['target_cell'][1],'source_geometry_ref':task['source_geometry'],'source_geometry_sha256':task['source_geometry_sha256'],'geometry':candidate_geometry_ref(task['task_id']),'geometry_sha256':sha(tmp/'final.xyz')})
   geometry_gate(r);atomic_json(tmp/'RECORD.json',r);checksum_dir(tmp);os.replace(tmp,out);state['completed'].append(task['task_id']);self.save(state);return state
  finished=[]
  for task in state['queue']:
   if task['task_id'] in state['completed']:
    out=self.root/'candidates'/task['task_id'];verify_dir(out);finished.append(self._normalize_record(json.loads((out/'RECORD.json').read_text()),seeds))
  state['cells'],active=reduce_round(state['cells'],finished);queue=propagate(state['cells'],active)
  state['queue']=[t for t in queue if t['task_id'] not in state['completed'] and t['task_id'] not in state['failed']];state['round']+=1;self.save(state)
  return state
