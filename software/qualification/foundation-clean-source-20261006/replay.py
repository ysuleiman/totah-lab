"""Replay committed foundation implementations in independent JVMs, without project caches."""
from pathlib import Path
import hashlib,json,subprocess
repo=Path.cwd();q=(repo/'software/qualification/foundation-clean-source-20261006/attempt-3');d=json.loads((q/'BUILD.json').read_text());src=Path(d['source']);cp=(q/'classpath.txt').read_text();out=src.parent/'replay';out.mkdir(exist_ok=True)
java='/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java'
base=[java,'-Xmx384m','-Dathena.reviewPackage='+str(src/'software/qualification/first-real-rule-package-20261006'),'-cp',cp]
rows=json.loads((q/'REPLAY.json').read_text()) if (q/'REPLAY.json').exists() else []
def pins(folder):return {str(p.relative_to(folder)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(folder.rglob('*')) if p.is_file()}
def run(name,style='json'):
 if any(row['class']==name for row in rows):return
 outputs=[]
 suffix='-retry2' if (out/(name+'-one')).exists() else ''
 for index in ['one','two']:
  root=out/(name+suffix+'-'+index);root.mkdir()
  if style=='group' or name=='FrozenContextPersistenceTest':(root/'catalog').mkdir()
  if style=='catalog':args=[str(root/'catalog'),str(root/'summary.json')]
  elif style=='tree':args=[str(root/'catalog')]
  elif style=='group':args=[str(root/'summary.json'),str(root/'catalog')]
  else:args=[str(root/'summary.json')]
  with (out/'execution.log').open('a') as f:subprocess.run(base+['totah.lab.daedalus.system.'+name,*args],cwd=src,check=True,stdout=f,stderr=subprocess.STDOUT)
  outputs.append(root)
 a,b=map(pins,outputs);assert a==b,name
 rows.append({'class':name,'independentJvmIdentical':True,'files':a,'outputOne':str(outputs[0]),'outputTwo':str(outputs[1])})
 (q/'REPLAY.json').write_text(json.dumps(rows,indent=2)+'\n')
 print(name,'PASS',flush=True)
for n in ['FoundationGroupAcceptanceTest','FoundationVocabularyAcceptanceTest','ChemicalRoleAcceptanceTest','RoleCoverageClosureTest','ChargeNonpolarAcceptanceTest','AromaticSystemAcceptanceTest','ChargeGroupAcceptanceTest','AllMembersNonpolarAcceptanceTest','ContinuousGeometryAcceptanceTest','ContinuousGeometryV2AcceptanceTest','ContinuousGeometryV3AcceptanceTest','RoleGeometryAttributionTest','MethylRingGeometryAttributionTest','CysteineBackboneAcceptanceTest','SourceSulfurConnectivityAcceptanceTest','DimensionalValidationAcceptanceTest','DisconnectedValidationAcceptanceTest','PinnedInvocationTimeAuthorityTest']:
 run(n)
for n in ['B01FunctionalGroupAcceptanceTest','GroupContextAcceptanceTest']:run(n,'group')
for n in ['ResearchV2PipelineAcceptanceTest','ResearchGatePipelineAcceptanceTest','DirectAssessmentExecutionAcceptanceTest','FrozenContextPersistenceTest']:run(n,'catalog')
for n in ['SystemQualificationAcceptanceTest','AthenaScientificRulesAcceptanceTest']:run(n,'tree')
# Compare preserved historical golden bytes, not recomputed expectations.
historical={
'ContinuousGeometryAcceptanceTest':'continuous-geometry-20261005/qualified-run/one.json',
'ContinuousGeometryV2AcceptanceTest':'p06-centroid-v2-20261005/final-run/replay-one.json',
'ResearchGatePipelineAcceptanceTest':'research-gate-20261005/final-run/gate-one.json',
'DirectAssessmentExecutionAcceptanceTest':'direct-assessment-current-20261005/final-run/direct-one.json'}
comparisons=[]
for name,path in historical.items():
 raw=subprocess.check_output(['git','show',d['commit']+':software/qualification/'+path],cwd=repo)
 assert raw==(out/(name+'-one')/'summary.json').read_bytes(),path
 comparisons.append({'golden':path,'sha256':hashlib.sha256(raw).hexdigest(),'identical':True})
(q/'HISTORICAL_REPLAY.json').write_text(json.dumps(comparisons,indent=2)+'\n')
print('historical replay PASS',flush=True)
prefix='software/qualification/ocl-query-b00-corrected-20261005/final-run/scientific-replay-one/'
files=subprocess.check_output(['git','ls-tree','-r','--name-only',d['commit'],'--',prefix],cwd=repo,text=True).splitlines()
root=out/'AthenaScientificRulesAcceptanceTest-one/catalog'
assert {str(p.relative_to(root)) for p in root.rglob('*') if p.is_file()}=={p[len(prefix):] for p in files}
for p in files:assert (root/p[len(prefix):]).read_bytes()==subprocess.check_output(['git','show',d['commit']+':'+p],cwd=repo)
comparisons.append({'golden':prefix,'fileCount':len(files),'completeTreeIdentical':True})
(q/'HISTORICAL_REPLAY.json').write_text(json.dumps(comparisons,indent=2)+'\n')
