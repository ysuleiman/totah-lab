#!/usr/bin/env python3
import json, random, tempfile
from pathlib import Path
import wavefront2d as w

checks=0
assert w.cell(180,-180)==(-180,-180) and w.cell(210,-210)==(-150,150);checks+=1
assert set(w.neighbors((-180,-180)))=={(150,-180),(-150,-180),(-180,150),(-180,-150)};checks+=1
r={'actual_phi_degrees':-60.04,'actual_psi_degrees':29.95,'target_phi_degrees':-60,'target_psi_degrees':30,'connectivity_pass':True,'chirality_pass':True}
assert w.geometry_gate(r);checks+=1
for bad in ('phi','psi'):
 x=dict(r);x[f'actual_{bad}_degrees']+=.2
 try:w.geometry_gate(x);raise AssertionError('gate accepted bad angle')
 except ValueError:pass
checks+=1
base={'target_phi_degrees':0,'target_psi_degrees':0,'actual_phi_degrees':0,'actual_psi_degrees':0,'connectivity_pass':True,'chirality_pass':True,'geometry':'g'}
records=[dict(base,task_id='b',energy_hartree=-10.1),dict(base,task_id='a',energy_hartree=-10.1),dict(base,task_id='c',energy_hartree=-10.0)]
for seed in range(12):
 random.Random(seed).shuffle(records);cells,active=w.reduce_round({},records)
 assert cells['+000,+000']['task_id']=='a' and active==((0,0),)
checks+=1
old={'-030,+000':dict(base,task_id='old',energy_hartree=-10,target_phi_degrees=-30)}
cells,active=w.reduce_round(old,[dict(base,task_id='new',energy_hartree=-10-1e-6,target_phi_degrees=-30,actual_phi_degrees=-30)])
assert cells['-030,+000']['task_id']=='new' and active==();checks+=1
high={'-030,+000':dict(base,task_id='h',energy_hartree=-9,target_phi_degrees=-30,actual_phi_degrees=-30),'+000,+000':dict(base,task_id='l',energy_hartree=-10)}
assert w.propagate(high,[(-30,0)])==[];checks+=1

def executor(task,out):
 p,q=task['target_cell'];out.mkdir(parents=True)
 energy=-100+((p+60)%360-180)**2/1e7+((q+60)%360-180)**2/1e7
 (out/'geometry.xyz').write_text(f'{p},{q}\n')
 return {'energy_hartree':energy,'actual_phi_degrees':p,'actual_psi_degrees':q,'connectivity_pass':True,'chirality_pass':True,'geometry':f'geom/{p},{q}','attempt_provenance':{'parent_id':task['parent_id']}}
seeds={'A':{'target':(-60,-60),'geometry':'A'}}
with tempfile.TemporaryDirectory() as td:
 full=w.Campaign(Path(td)/'full','p',executor).run(seeds)
 resume_calls={}
 def counted(task,out):
  resume_calls[task['task_id']]=resume_calls.get(task['task_id'],0)+1
  return executor(task,out)
 try:w.Campaign(Path(td)/'resume','p',counted).run(seeds,stop_after=7)
 except KeyboardInterrupt:pass
 resumed=w.Campaign(Path(td)/'resume','p',counted).run(seeds)
 for state in (full,resumed):state.pop('completed',None)
 assert full==resumed and len(resumed['cells'])>1
 assert max(resume_calls.values())==1
 candidate=next((Path(td)/'resume/candidates').iterdir())
 assert json.loads((candidate/'RECORD.json').read_text())['attempt_provenance']
checks+=3

with tempfile.TemporaryDirectory() as td:
 calls={}
 def flaky(task,out):
  calls[task['task_id']]=calls.get(task['task_id'],0)+1
  if task['target_cell']==[-60,-60]:raise RuntimeError('sealed candidate failure')
  return executor(task,out)
 state=w.Campaign(Path(td)/'fail','p',flaky).run(seeds)
 assert len(state['failed'])==1 and calls[state['failed'][0]]==1
checks+=1
print(f'WAVEFRONT_TESTS_PASS={checks}')
