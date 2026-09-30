"""Report measured M16 results; missing gates remain explicitly incomplete."""
from pathlib import Path
import json,csv,hashlib,xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1];out=r/'validation/milestone-16';repo=r.parents[2]
def fields(path):return dict(line.split('=',1) for line in path.read_text().splitlines() if '=' in line)
comparison=[]
for p in (out/'pyscf').glob('*.json'):
 s=json.loads(p.read_text());stem=p.stem;name,role=stem.rsplit('-',1);java=out/'runs'/(name+('' if role=='AB' else '-'+role)+'.txt')
 if not java.exists():continue
 j=fields(java);comparison.append({'name':name,'role':role,'PBE_error':abs(float(j['PBE'])-s['PBE']),'D3_error':abs(float(j['D3_TOTAL'])-s['D3']),'PBE_D3_error':abs(float(j['PBE_D3'])-s['PBE_D3'])})
changed=[]
for name,expected in json.loads((out/'frozen-manifest.json').read_text()).items():
 p=repo/name;h=hashlib.sha256()
 if not p.exists():changed.append(name);continue
 with p.open('rb') as f:
  for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
 if h.hexdigest()!=expected:changed.append(name)
approved=json.loads((out/'approved-api-change.json').read_text())
approved_matches=hashlib.sha256((repo/approved['file']).read_bytes()).hexdigest()==approved['approved_sha256']
unexpected=[p for p in changed if not (p==approved['file'] and approved_matches)]
summary={'scientific_status':'SCREENING_ONLY','components_compared':comparison,'frozen_files_changed':changed,
 'unexpected_frozen_changes':unexpected,'approved_API_change_matches_tested_patch':approved_matches,
 'max_PBE_D3_error':max((x['PBE_D3_error'] for x in comparison),default=None),'native_runs':[json.loads(p.read_text()) for p in (out/'runs').glob('*.json')],
 'CP_NATIVE_IMPLEMENTED':True,'CP_VALIDATION':'IN_PROGRESS','MILESTONE_16_STATUS':'IN_PROGRESS'}
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')

# Keep implementation agreement and finite-basis method error in separate tables.
method=[]
for name,ref in json.loads((out/'nenci-selection.json').read_text()).items():
 p=out/'runs'/f'{name}-interaction.txt'
 if not p.exists():continue
 values=fields(p);target=ref['reference_interaction_hartree']
 method.append({'system':name,'category':ref['category'],'reference_method':ref['reference_method'],
  'reference_hartree':target,'PBE':float(values['PBE']),'PBE_D3':float(values['PBE_D3']),
  'PBE_error_hartree':float(values['PBE'])-target,'PBE_D3_error_hartree':float(values['PBE_D3'])-target,
  'electronic_CP':values['CP'],'comparison_limit':'Method, finite-basis, finite-grid and BSSE errors are combined; this is not implementation error.'})
p=out/'runs/cation_pi_small-interaction.txt'
if p.exists():
 values=fields(p);cc={role:json.loads((out/'cc-reference'/f'{role}.json').read_text())['energy_hartree'] for role in ['AB','A','B']}
 target=cc['AB']-cc['A']-cc['B']
 method.append({'system':'cation_pi_small','category':'CATION_PI','reference_method':'frozen-core CCSD(T)/def2-SVP Cartesian; NOT CBS',
  'reference_hartree':target,'PBE':float(values['PBE']),'PBE_D3':float(values['PBE_D3']),
  'PBE_error_hartree':float(values['PBE'])-target,'PBE_D3_error_hartree':float(values['PBE_D3'])-target,
  'electronic_CP':values['CP'],'comparison_limit':'Finite-basis comparison; excluded from CBS statistics.'})
(out/'method-comparison.json').write_text(json.dumps({'status':'SCREENING_ONLY','systems':method},indent=2)+'\n')
performance=[]
for name in ['h2o','dms','chlorobenzene','benzene_dimer','ammonium_benzene']:
 p=out/'runs'/f'{name}.txt';run=out/'runs'/f'{name}.json'
 if not p.exists() or not run.exists():continue
 values=fields(p);timing=json.loads(run.read_text());heap=fields(out/'runs'/f'{name}-heap.txt')
 performance.append({'system':name,'dispersion_seconds':int(values['dispersionNanos'])/1e9,
  'PBE_seconds':int(values['pbeNanos'])/1e9,'overhead_percent':100*int(values['dispersionNanos'])/int(values['pbeNanos']),
  'peak_heap_bytes':int(heap['peakHeap']),'peak_process_RSS_bytes':timing['peak_rss_bytes'],
  'measurement_note':'PBE/D3 evaluation timings; RSS is whole process including monomers. Independent oracle may run concurrently.'})
(out/'performance.json').write_text(json.dumps({'status':'SCREENING_ONLY','systems':performance},indent=2)+'\n')
tests=[]
for p in (out/'junit').glob('TEST-*.xml'):
 a=ET.parse(p).getroot().attrib
 tests.append({k:a[k] for k in ['name','tests','failures','errors','skipped']})
(out/'test-summary.json').write_text(json.dumps({'suites':tests,'total_tests':sum(int(x['tests']) for x in tests)},indent=2)+'\n')

cp=[]
for name in ['water_dimer','water_ammonia','hcl_water','methanethiol_water','ammonium_benzene','chlorobenzene_water']:
 paths=[out/'runs'/f'{name}.txt']+[out/'cp-runs'/f'{name}-{role}.txt' for role in ['A_GHOST_B','B_GHOST_A']]
 refs=[out/'pyscf'/f'{name}-AB.json']+[out/'cp-reference'/f'{name}-{role}.json' for role in ['A_GHOST_B','B_GHOST_A']]
 if not all(p.exists() for p in paths+refs):continue
 j=[fields(p) for p in paths];external=[json.loads(p.read_text()) for p in refs]
 pbe=float(j[0]['PBE'])-float(j[1]['PBE'])-float(j[2]['PBE'])
 dispersion=float(j[0]['D3_TOTAL'])-float(j[1]['D3'])-float(j[2]['D3'])
 reference=external[0]['PBE']-external[1]['PBE']-external[2]['PBE']
 reference_d3=external[0]['D3']-external[1]['D3']-external[2]['D3']
 cp.append({'system':name,'PBE_CP':pbe,'physical_D3_difference':dispersion,'PBE_D3_CP':pbe+dispersion,
  'PBE_CP_reference':reference,'PBE_D3_CP_reference':reference+reference_d3,
  'component_PBE_errors':[abs(float(v['PBE'])-e['PBE']) for v,e in zip(j,external)],
  'component_D3_errors':[abs(float(v['D3_TOTAL' if i==0 else 'D3'])-e['D3']) for i,(v,e) in enumerate(zip(j,external))],
  'component_PBE_D3_errors':[abs(float(v['PBE_D3'])-e['PBE_D3']) for v,e in zip(j,external)],
  'CP_error':abs(pbe+dispersion-reference-reference_d3),'component_receipts':[v['receipt'] for v in j],
  'convention':'CP electronic PBE; D3(real AB)-D3(real A)-D3(real B), no ghost dispersion'})
(out/'counterpoise-comparison.json').write_text(json.dumps({'status':'SCREENING_ONLY','systems':cp},indent=2)+'\n')
summary['max_own_basis_PBE_D3_error']=summary['max_PBE_D3_error']
summary['max_PBE_D3_error']=max([summary['max_PBE_D3_error'] or 0.0]+[v for row in cp for v in row['component_PBE_D3_errors']])
summary['max_PBE_error']=max([x['PBE_error'] for x in comparison]+[v for row in cp for v in row['component_PBE_errors']],default=None)
summary['max_D3_error']=max([x['D3_error'] for x in comparison]+[v for row in cp for v in row['component_D3_errors']],default=None)
summary['completed_CP_systems']=len(cp)
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')

interactions=[]
for p in (out/'runs').glob('*-interaction.txt'):
 name=p.name.removesuffix('-interaction.txt');paths=[out/'pyscf'/f'{name}-{role}.json' for role in ['AB','A','B']]
 if not all(q.exists() for q in paths):continue
 j=fields(p);e=[json.loads(q.read_text()) for q in paths]
 pe=e[0]['PBE']-e[1]['PBE']-e[2]['PBE'];de=e[0]['D3']-e[1]['D3']-e[2]['D3']
 interactions.append({'system':name,'PBE_error':abs(float(j['PBE'])-pe),'D3_error':abs(float(j['D3'])-de),
  'PBE_D3_error':abs(float(j['PBE_D3'])-pe-de),'reference_PBE':pe,'reference_D3':de,'native_receipt':j['receipt']})
(out/'own-interaction-comparison.json').write_text(json.dumps({'status':'SCREENING_ONLY','systems':interactions},indent=2)+'\n')

print('own-basis components',len(comparison),'CP systems',len(cp),'max total error',summary['max_PBE_D3_error'],'unexpected frozen changes',len(unexpected))
