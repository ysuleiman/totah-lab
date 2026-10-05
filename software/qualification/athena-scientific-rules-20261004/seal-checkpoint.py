# Pin qualification artifacts and source/runtime files; no scientific computation.
from pathlib import Path
import hashlib,json
q=Path('software/qualification/athena-scientific-rules-20261004')
def sha(path):
 h=hashlib.sha256()
 with Path(path).open('rb') as f:
  for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
 return h.hexdigest()
def write(name,paths):
 paths=sorted(set(map(Path,paths)))
 (q/name).write_text(''.join(sha(p)+'  '+str(p)+'\n' for p in paths))
 return len(paths)
resources=Path('software/modules/athena/src/main/resources/totah/lab/athena/system/rules')
sources=[Path(p) for p in (q/'sources.txt').read_text().splitlines()]+list(q.glob('*.java'))+[p for p in resources.rglob('*') if p.is_file()]
counts={'sources':write('SOURCE_SHA256SUMS',sources)}
counts['classes']=write('COMPILED_CLASS_SHA256SUMS',list((q/'classes').rglob('*.class'))+list((q/'foundation-classes').rglob('*.class')))
counts['jars']=write('RUNTIME_JAR_SHA256SUMS',[Path(p) for p in (q/'runtime-classpath.txt').read_text().strip().split(':') if p.endswith('.jar')])
for p in (resources/'scientific').glob('*.rule.json'):
 m=json.loads(p.read_text())
 for source in m['scientificSources']+m['referenceArtifacts']:assert sha(source['locator'])==source['sha256'],source['locator']
preliminary={'replay-1','replay-2','qualified-replay','qualified-final'}
paths=[p for p in q.rglob('*') if p.is_file() and p.name!='SHA256SUMS'
       and p.relative_to(q).parts[0] not in preliminary|{'classes','foundation-classes'}]
counts['checkpointArtifacts']=write('SHA256SUMS',paths)
print(json.dumps(counts,sort_keys=True))
