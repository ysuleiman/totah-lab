# File verification only; no scientific calculations.
from pathlib import Path
import hashlib,json,subprocess
q=Path('software/qualification/athena-scientific-rules-20261004');prior=Path('software/qualification/rule-registry-20261004')
def sha(path):
 h=hashlib.sha256()
 with Path(path).open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
report={}
for name in ['SHA256SUMS','SOURCE_SHA256SUMS','COMPILED_CLASS_SHA256SUMS','RUNTIME_JAR_SHA256SUMS']:
 count=0;bad=[]
 for line in (prior/name).read_text().splitlines():
  digest,path=line.split(None,1);path=path.strip();count+=1
  if sha(path)!=digest:bad.append(path)
 report[name]={'checked':count,'mismatches':bad}
expected=set(json.loads((q/'BASELINE.json').read_text())['files'])-{'software/modules/athena/src/main/java/totah/lab/athena/system/rules/RuleRequest.java'}
assert set(report['SOURCE_SHA256SUMS']['mismatches'])==expected
assert all(not report[name]['mismatches'] for name in ['SHA256SUMS','COMPILED_CLASS_SHA256SUMS','RUNTIME_JAR_SHA256SUMS'])
def strip(diff):return ''.join('diff --git '+part for part in diff.split('diff --git ')[1:] if not any(part.startswith('a/'+p+' b/'+p+'\n') for p in expected))
report['unrelatedTrackedDiffUnchanged']=strip(subprocess.check_output(['git','diff','--binary'],text=True))==strip((q/'baseline.diff').read_text())
assert report['unrelatedTrackedDiffUnchanged']
rows=json.loads((prior/'baseline-files.json').read_text());changed=[];missing=[]
for row in rows:
 p=Path(row['path'])
 if not p.exists():missing.append(str(p));continue
 st=p.stat()
 if (st.st_size,st.st_mtime_ns)!=(row['size'],row['mtime_ns']):changed.append(str(p))
historical={'software/modules/athena/src/main/java/totah/lab/athena/interaction/'+name+'Detector.java' for name in ['HydrogenBond','HydrophobicContact','PiStacking','PiCation','SaltBridge']}
report['historicalStatBaseline']={'files':len(rows),'missing':missing,'changes':changed,'unexpectedChanges':sorted(set(changed)-historical-{'reports/boltz-persistence-20261004/web-api.log'}),'note':'Historical baseline precedes the accepted registry observer edits. Those five detectors are unchanged in THIS milestone, verified by source hashes. The existing live web-api log may append independently. Stat verification is not new content hashing of every scientific artifact.'}
(q/'PRESERVATION.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
assert not missing and not report['historicalStatBaseline']['unexpectedChanges']
