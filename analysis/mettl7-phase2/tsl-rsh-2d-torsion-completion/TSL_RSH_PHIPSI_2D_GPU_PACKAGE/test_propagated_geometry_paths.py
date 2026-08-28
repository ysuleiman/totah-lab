#!/usr/bin/env python3
import tempfile
from pathlib import Path
import wavefront2d as w

calls=[]
def executor(task,out):
    source=Path(task['source_geometry'])
    if task['parent_cell'] is not None and not source.is_file():
        raise FileNotFoundError(source)
    out.mkdir(parents=True)
    (out/'final.xyz').write_text('propagated geometry\n')
    p,q=task['target_cell'];calls.append((p,q))
    return {'energy_hartree':-100.0+(p*p+q*q)*1e-8,'actual_phi_degrees':p,'actual_psi_degrees':q,
            'connectivity_pass':True,'chirality_pass':True,'geometry':str(out/'final.xyz')}

with tempfile.TemporaryDirectory() as td:
    root=Path(td);seed=root/'seed.xyz';seed.write_text('seed\n')
    campaign=w.Campaign(root/'campaign','protocol',executor)
    seeds={'seed':{'target':(0,0),'geometry':str(seed)}}
    state=campaign.init(seeds)
    while state['round']==0:
        state=campaign.advance_one(seeds)
    failures_before=len(state['failed']);calls_before=len(calls)
    while len(calls)-calls_before<10:
        state=campaign.advance_one(seeds)
        assert state['queue']
    assert len(state['failed'])==failures_before
    assert len(calls)-calls_before==10
    for record in state['cells'].values():
        assert '.in_progress' not in record['geometry']
        assert Path(record['geometry']).is_file()
print('PROPAGATED_GEOMETRY_PATH_REGRESSION_PASS=10/10')
