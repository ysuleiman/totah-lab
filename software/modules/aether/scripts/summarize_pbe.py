"""Summarize only M15 evidence and verify frozen milestones without rewriting them."""
from pathlib import Path
import json,csv,re,hashlib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1];out=root/'validation/milestone-15'
s={'scientific_status':'SCREENING_ONLY','completed':[],'failed':[],'intermediate':[],'tests':0,'test_failures':0}
for p in sorted((out/'runs').glob('*.json')):
 process=json.loads(p.read_text());stem=process['case'];txt=p.with_suffix('.txt')
 if process['exit_code']!=0:s['failed'].append(stem)
 if not txt.exists():continue
 values=dict(line.split('=',1) for line in txt.read_text().splitlines() if '=' in line)
 row={'case':stem,'exit_code':process['exit_code'],'rss_bytes':process['peak_rss_bytes']}
 for key in ['energy','electronic','Exc','electrons']:row[key]=float(values[key])
 for key in ['aoCount','iterations','entries','aoNanos','derivativeNanos','rhoGradientNanos','functionalNanos','VxcNanos','JNanos','eigensolveNanos','totalNanos','blockStorageEstimate','peakHeap']:row[key]=int(values[key])
 row['errors']={k:float(v) for k,v in re.findall(r'(\w+)=([0-9.Ee+-]+)',values['errors'])}
 row['receipt']=values['receipt'];s['completed'].append(row)
for p in (out/'junit').glob('TEST-*.xml'):
 r=ET.parse(p).getroot();s['tests']+=int(r.attrib['tests']);s['test_failures']+=int(r.attrib['failures'])+int(r.attrib['errors'])
 for match in re.finditer(r'M15 INTERMEDIATE (\S+) ([^\n]+)',p.read_text()):
  s['intermediate'].append({'case':match[1],**{k:float(v) for k,v in re.findall(r'(\w+)=([0-9.Ee+-]+)',match[2])}})
manifest=json.loads((out/'frozen-manifest.json').read_text());s['frozen_files']=len(manifest);s['frozen_changed']=[]
for path,expected in manifest.items():
 with (root/path).open('rb') as f:actual=hashlib.file_digest(f,'sha256').hexdigest()
 if actual!=expected:s['frozen_changed'].append(path)
s['max_errors']={k:max(x['errors'].get(k,0) for x in s['completed']) for k in ['total','electronic','density','orbital','Exc','Vxc','Fock','electrons']} if s['completed'] else {}
s['max_intermediate']={k:max(x.get(k,0) for x in s['intermediate']) for k in ['maxAOderivative','maxRhoGradient','maxSigma','maxExc','maxVxc']} if s['intermediate'] else {}
by={x['case']:x for x in s['completed']};s['grid_ladder']=[];s['rotations']=[]
for name in ['h2o','ch4','h2s']:
 ladder=[by.get(f'{name}-native-{nr}-{na}') for nr,na in [(40,110),(80,302),(120,590),(160,974)]]
 if all(ladder):s['grid_ladder'].append({'system':name,'rows':[{k:x[k] for k in ['case','energy','Exc','electrons']} for x in ladder],
  'last_step':{k:abs(ladder[-1][k]-ladder[-2][k]) for k in ['energy','Exc','electrons']}})
 a=by.get(f'{name}-native-120-590');b=by.get(f'{name}-rotated-120-590')
 if a and b:s['rotations'].append({'system':name,**{k:abs(a[k]-b[k]) for k in ['energy','Exc','electrons']}})
replay=out/'replay/result.json';s['replay']=json.loads(replay.read_text()) if replay.exists() else None
(out/'summary.json').write_text(json.dumps(s,indent=2)+'\n')
with (out/'performance.csv').open('w') as f:
 keys=['case','aoCount','iterations','aoNanos','derivativeNanos','rhoGradientNanos','functionalNanos','VxcNanos','JNanos','eigensolveNanos','totalNanos','blockStorageEstimate','peakHeap','rss_bytes']
 w=csv.DictWriter(f,fieldnames=keys,extrasaction='ignore');w.writeheader();w.writerows(s['completed'])
print('Completed',len(s['completed']),'failed',s['failed'],'tests',s['tests'],'frozen changed',s['frozen_changed'])
