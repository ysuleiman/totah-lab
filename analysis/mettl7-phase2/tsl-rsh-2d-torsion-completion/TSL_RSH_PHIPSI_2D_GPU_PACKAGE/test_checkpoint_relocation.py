#!/usr/bin/env python3
import copy, json, shutil, tempfile
from pathlib import Path
import wavefront2d as w

def executor(task,out):
    source=Path(task['source_geometry']); assert source.is_file()
    out.mkdir(parents=True); (out/'final.xyz').write_bytes(source.read_bytes()+f"\n{task['task_id']}".encode())
    p,q=task['target_cell']
    return {'energy_hartree':-100+(p*p+q*q)*1e-9,'actual_phi_degrees':p,'actual_psi_degrees':q,
            'connectivity_pass':True,'chirality_pass':True,'geometry':str(out/'final.xyz')}

def candidate_hashes(root):
    return {p.parent.name:w.sha(p) for p in root.glob('candidates/*/final.xyz')}

def rewrite_state(root,mutator):
    state=json.loads((root/'WAVEFRONT_STATE.json').read_text()); mutator(state)
    w.atomic_json(root/'WAVEFRONT_STATE.json',state)
    w.atomic_json(root/'STATE_RECEIPT.json',{'state_sha256':w.sha(root/'WAVEFRONT_STATE.json'),'protocol_sha256':'protocol'})

with tempfile.TemporaryDirectory() as td:
    td=Path(td); seed_a=td/'seed-a.xyz'; seed_a.write_text('stable seed\n')
    seeds_a={'MIN':{'target':(0,0),'geometry':str(seed_a)}}
    root_a=td/'ROOT_A/production'; campaign_a=w.Campaign(root_a,'protocol',executor)
    state=campaign_a.init(seeds_a)
    while state['round']==0: state=campaign_a.advance_one(seeds_a)
    state=campaign_a.advance_one(seeds_a)
    assert state['round']==1 and len(state['completed'])>=2
    referenced_id=next(iter(state['cells'].values()))['task_id']
    hashes_a=candidate_hashes(root_a); queue_a=copy.deepcopy(state['queue'])

    # Interrupted candidates are never finalized and must fail closed until archived by recovery policy.
    pending=next(t for t in state['queue'] if t['task_id'] not in state['completed'])
    partial=root_a/'candidates'/(pending['task_id']+'.in_progress');partial.mkdir();(partial/'partial.log').write_text('interrupted')
    try: campaign_a.advance_one(seeds_a); raise AssertionError('partial candidate accepted')
    except RuntimeError as e: assert 'incomplete candidate' in str(e)
    recovery=root_a/'interrupted-attempts';recovery.mkdir();shutil.move(str(partial),recovery/partial.name)

    root_b=td/'ROOT_B/production';shutil.copytree(root_a,root_b)
    seed_b=td/'seed-b.xyz';seed_b.write_bytes(seed_a.read_bytes());seeds_b={'MIN':{'target':(0,0),'geometry':str(seed_b)}}
    campaign_b=w.Campaign(root_b,'protocol',executor); migrated=campaign_b.load(seeds_b)
    assert migrated['queue']==queue_a
    assert candidate_hashes(root_b)==hashes_a
    assert not any(Path(r['geometry']).is_absolute() for r in migrated['cells'].values())
    assert not any(Path(t['source_geometry']).is_absolute() for t in migrated['queue'])
    resumed=campaign_b.advance_one(seeds_b);assert len(resumed['completed'])==len(migrated['completed'])+1

    # Deterministic migration from a stale absolute ROOT_A path uses candidate identity, manifest, and hash.
    legacy=td/'LEGACY/production';shutil.copytree(root_a,legacy)
    def stale(s):
        for r in s['cells'].values(): r['geometry']=str(root_a/r['geometry'])
        for t in s['queue']:
            if t['source_geometry'].startswith('candidates/'):
                t['source_geometry']=str(root_a/t['source_geometry'])
    rewrite_state(legacy,stale)
    migrated_legacy=w.Campaign(legacy,'protocol',executor).load(seeds_b)
    assert not any(Path(r['geometry']).is_absolute() for r in migrated_legacy['cells'].values())
    assert candidate_hashes(legacy)==hashes_a

    missing=td/'MISSING/production';shutil.copytree(root_a,missing)
    victim=missing/'candidates'/referenced_id/'final.xyz';victim.unlink()
    try:w.Campaign(missing,'protocol',executor).load(seeds_b);raise AssertionError('missing parent accepted')
    except RuntimeError:pass

    wrong=td/'WRONG/production';shutil.copytree(root_a,wrong)
    victim=wrong/'candidates'/referenced_id/'final.xyz';victim.write_text('wrong geometry')
    try:w.Campaign(wrong,'protocol',executor).load(seeds_b);raise AssertionError('wrong parent hash accepted')
    except RuntimeError:pass

    traversal=td/'TRAVERSAL/production';shutil.copytree(root_a,traversal)
    def attack(s):
        task=next(t for t in s['queue'] if not t['source_geometry'].startswith('seed:'))
        task['source_geometry']='../outside/final.xyz'
    rewrite_state(traversal,attack)
    try:w.Campaign(traversal,'protocol',executor).load(seeds_b);raise AssertionError('path traversal accepted')
    except RuntimeError as e:assert 'unsafe candidate' in str(e)

print('CHECKPOINT_RELOCATION_PASS=true')
print('ABSOLUTE_PATHS_IN_NEW_CHECKPOINT=0')
print('PARENT_GEOMETRY_HASH_VERIFICATION_PASS=true')
