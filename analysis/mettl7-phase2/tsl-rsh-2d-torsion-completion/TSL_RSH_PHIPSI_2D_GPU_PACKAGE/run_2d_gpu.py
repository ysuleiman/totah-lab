#!/usr/bin/env python3
"""Production PHI x PSI GPU runner; benchmark is a separate hard gate."""
from __future__ import annotations
import argparse, importlib.util, json, os, platform, shutil, time
from importlib.metadata import version
from pathlib import Path
import numpy as np
import wavefront2d as wf

ROOT=Path(__file__).resolve().parent;INPUT=ROOT/'input';RESULTS=Path(os.environ.get('TSL_RESULTS_ROOT',ROOT/'results'))
PHI=(25,9,8,7);PSI=(9,8,7,1)
BENCHMARK_CELLS=((-60,-60),(-90,-60),(-60,-90),(0,0),(120,120),(-180,-180))
def load_core():
 spec=importlib.util.spec_from_file_location('qualified_core',ROOT/'qualified_level5_derivative_core.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);m.INPUT=INPUT;m.OUTPUT=RESULTS/'_core';return m
def read_xyz(path):
 rows=[x.split() for x in path.read_text().splitlines()[2:58]]
 if len(rows)!=56:raise RuntimeError('atom count')
 return [r[0] for r in rows],np.array([[float(x) for x in r[1:4]] for r in rows])
def dih(x,idx):
 p0,p1,p2,p3=(x[i] for i in idx);b0=p0-p1;b1=p2-p1;b2=p3-p2;b1/=np.linalg.norm(b1);v=b0-np.dot(b0,b1)*b1;w=b2-np.dot(b2,b1)*b1
 return float(np.degrees(np.arctan2(np.dot(np.cross(b1,v),w),np.dot(v,w))))
def graph(elements,x):
 radii={'H':.31,'C':.76,'O':.66,'S':1.05};return {(i,j) for i in range(56) for j in range(i+1,56) if np.linalg.norm(x[i]-x[j])<=1.25*(radii[elements[i]]+radii[elements[j]])}
def chirality(x,g):
 n={i:[] for i in range(56)}
 for a,b in g:n[a].append(b);n[b].append(a)
 out={}
 for c,linked in n.items():
  if len(linked)==4:
   q=sorted(linked);vol=float(np.linalg.det(np.stack([x[q[i]]-x[c] for i in range(3)])))
   if abs(vol)>1e-4:out[str(c)]=1 if vol>0 else -1
 return out
def executor(core):
 def run(task,out):
  import geometric.engine,geometric.errors,geometric.molecule,geometric.optimize
  from pyscf.data.nist import BOHR
  elements,start=read_xyz(Path(task['source_geometry']));p,q=task['target_cell'];out.mkdir(parents=True);initial=out/'initial.xyz';initial.write_text(core.xyz_text(elements,start,'2D parent'))
  constraints=out/'constraints.txt';constraints.write_text('$set\ndihedral '+' '.join(str(i+1) for i in PHI)+f' {p}\ndihedral '+' '.join(str(i+1) for i in PSI)+f' {q}\n$end\n')
  count={'steps':0,'scf_cycles':0};t0=time.time()
  class Engine(geometric.engine.Engine):
   def calc_new(self,coords,dirname):
    step=out/'steps'/f"step_{count['steps']:04d}";count['steps']+=1;result,grad,_=core.calculate(elements,np.asarray(coords).reshape(56,3),step);count['scf_cycles']+=int(result['scf_cycles']);return {'energy':result['total_energy_hartree'],'gradient':grad.reshape(-1)}
  mol=geometric.molecule.Molecule(str(initial));opt=geometric.optimize.run_optimizer(input=str(initial),constraints=str(constraints),customengine=Engine(mol),prefix=str(out/'geometric'),maxiter=300,**core.GEOMETRIC_CONVERGENCE)
  final=np.asarray(opt.xyzs[-1]);result,_,_=core.calculate(elements,final/BOHR,out/'final_single_point');xyz=out/'final.xyz';xyz.write_text(core.xyz_text(elements,final,f'PHI {p} PSI {q}'))
  g0=graph(elements,start);g1=graph(elements,final);rec={'energy_hartree':result['total_energy_hartree'],'actual_phi_degrees':dih(final,PHI),'actual_psi_degrees':dih(final,PSI),'connectivity_pass':g0==g1,'chirality_pass':chirality(start,g0)==chirality(final,g1),'geometry':str(xyz),'optimization_steps':count['steps'],'scf_iterations_total':count['scf_cycles'],'wall_seconds':time.time()-t0,'retries':0,'final_energy_hartree':result['total_energy_hartree']};return rec
 return run
def runtime():
 import cupy as cp
 if cp.cuda.runtime.getDeviceCount()!=1:raise RuntimeError('benchmark requires exactly one GPU')
 name=cp.cuda.runtime.getDeviceProperties(0)['name'];name=name.decode() if isinstance(name,bytes) else str(name)
 if 'A100' not in name:raise RuntimeError(f'A100 required: {name}')
 return {'gpu_model':name,'gpu_count':1,'python':platform.python_version(),'PySCF':version('pyscf'),'GPU4PySCF':version('gpu4pyscf-cuda12x'),'CuPy':version('cupy-cuda12x'),'dftd3':version('dftd3'),'geomeTRIC':version('geometric')}
def benchmark_tasks(seeds):
 tasks=[]
 for c in BENCHMARK_CELLS:
  parent=min(seeds,key=lambda s:wf.angular_error(seeds[s]['target'][0],c[0])**2+wf.angular_error(seeds[s]['target'][1],c[1])**2)
  tasks.append({'task_id':wf.task_id('BENCH_'+parent,c),'parent_id':parent,'parent_cell':None,'target_cell':list(c),'source_geometry':seeds[parent]['geometry']})
 return tasks
def completed_benchmark_record(task):
 d=RESULTS/'benchmark'/task['task_id']
 if not d.exists():return None
 wf.verify_dir(d);r=json.loads((d/'RECORD.json').read_text());wf.geometry_gate(r)
 if (r['target_phi_degrees'],r['target_psi_degrees'])!=tuple(task['target_cell']):raise RuntimeError('completed benchmark identity mismatch')
 return r
def run_benchmark_task(core,task):
 existing=completed_benchmark_record(task)
 if existing is not None:return existing
 d=RESULTS/'benchmark'/task['task_id'];tmp=d.with_name(d.name+'.in_progress')
 if tmp.exists():raise RuntimeError(f'incomplete benchmark attempt requires external recovery: {tmp}')
 r=executor(core)(task,tmp);r.update({'task_id':task['task_id'],'parent_id':task['parent_id'],'target_phi_degrees':task['target_cell'][0],'target_psi_degrees':task['target_cell'][1],'geometry':str(d/'final.xyz')})
 wf.geometry_gate(r);wf.atomic_json(tmp/'RECORD.json',r);wf.checksum_dir(tmp);os.replace(tmp,d);return r
def import_benchmark_records(root,production_root):
 records=[]
 for source in sorted((Path(root)/'benchmark').iterdir()):
  if not (source/'RECORD.json').is_file():continue
  wf.verify_dir(source);r=json.loads((source/'RECORD.json').read_text());wf.geometry_gate(r)
  target=(r['target_phi_degrees'],r['target_psi_degrees'])
  if target not in BENCHMARK_CELLS:raise RuntimeError(f'unauthorized reuse cell {target}')
  dest=production_root/'candidates'/r['task_id']
  if not dest.exists():
   dest.parent.mkdir(parents=True,exist_ok=True);shutil.copytree(source,dest)
  wf.verify_dir(dest);r=json.loads((dest/'RECORD.json').read_text());r['geometry']=str(dest/'final.xyz')
  records.append(r)
 return records
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--benchmark',action='store_true');ap.add_argument('--benchmark-cell',nargs=2,type=int,metavar=('PHI','PSI'));ap.add_argument('--benchmark-finalize',action='store_true');ap.add_argument('--production',action='store_true');ap.add_argument('--production-step',action='store_true');ap.add_argument('--production-status',action='store_true');ap.add_argument('--reuse-benchmark-root');ap.add_argument('--authorization-receipt');a=ap.parse_args()
 rt=runtime();core=load_core();protocol=wf.sha(ROOT/'FINAL_QM_PROTOCOL.json');wf.atomic_json(RESULTS/'RUNTIME.json',rt)
 seeds={}
 for name in ('MIN01','MIN02','MIN04'):
  path=INPUT/f'{name}_verified.xyz';_,x=read_xyz(path);seeds[name]={'target':(min(wf.GRID,key=lambda z:wf.angular_error(dih(x,PHI),z)),min(wf.GRID,key=lambda z:wf.angular_error(dih(x,PSI),z))),'geometry':str(path)}
 if a.production or a.production_step or a.production_status:
  if not a.authorization_receipt:raise RuntimeError('production grid requires explicit post-benchmark authorization receipt')
  receipt=json.loads(Path(a.authorization_receipt).read_text())
  if receipt.get('production_grid_authorized') is not True or receipt.get('scientific_protocol_sha256')!=protocol:raise RuntimeError('production authorization receipt invalid')
  campaign=wf.Campaign(RESULTS/'production',protocol,executor(core)); initial=[]
  if not campaign.state_path.exists():
   if not a.reuse_benchmark_root:raise RuntimeError('initial production state requires verified benchmark reuse root')
   initial=import_benchmark_records(a.reuse_benchmark_root,RESULTS/'production')
  if a.production_status:
   state=campaign.load() if campaign.state_path.exists() else campaign.init(seeds,initial);print(json.dumps({'round':state['round'],'cells':len(state['cells']),'queue':len(state['queue']),'completed':len(state['completed']),'failed':len(state['failed'])},sort_keys=True));return
  state=campaign.advance_one(seeds,initial) if a.production_step else campaign.run(seeds)
  if not state['queue']:
   wf.atomic_json(RESULTS/'PRODUCTION_RESULT.json',{'status':'COMPLETE','cell_count':len(state['cells']),'rounds':state['round']});wf.checksum_dir(RESULTS)
  return
 tasks=benchmark_tasks(seeds)
 if a.benchmark_cell:
  target=tuple(a.benchmark_cell)
  if target not in BENCHMARK_CELLS:raise RuntimeError(f'unauthorized benchmark cell {target}')
  task=next(t for t in tasks if tuple(t['target_cell'])==target);run_benchmark_task(core,task);return
 if a.benchmark:
  out=[run_benchmark_task(core,t) for t in tasks]
  wf.atomic_json(RESULTS/'BENCHMARK_RESULT.json',{'status':'COMPLETE','production_grid_started':False,'runtime':rt,'cells':out});wf.checksum_dir(RESULTS);return
 if a.benchmark_finalize:
  out=[]
  for task in tasks:
   record=completed_benchmark_record(task)
   if record is None:raise RuntimeError(f'missing benchmark cell {tuple(task["target_cell"])}')
   out.append(record)
  wf.atomic_json(RESULTS/'BENCHMARK_RESULT.json',{'status':'COMPLETE','production_grid_started':False,'runtime':rt,'cells':out});wf.checksum_dir(RESULTS);return
if __name__=='__main__':main()
