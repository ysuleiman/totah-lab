"""Persist passing I04B qualification; never issue scientific authority."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys,xml.etree.ElementTree as ET
repo=Path.cwd();q=repo/'software/qualification/foundation-supportable-pass-20261007/i04/implementation';b=Path(sys.argv[1]);r=Path(sys.argv[2])
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,v:p.write_text(json.dumps(v,indent=2)+'\n')
assert '0 tests failed' in (b/'tests.log').read_text()
assert '0 tests failed' in (b/'isolation.log').read_text() and '3 tests successful' in (b/'isolation.log').read_text()
build=json.loads((b/'BUILD.json').read_text())
for p,h in build['sourcePins'].items():
 if p.startswith('software/modules/'):assert sha(repo/p)==h,p
cases=[c for p in (b/'junit').glob('TEST-*.xml') for c in ET.parse(p).getroot().findall('testcase')]
assert len(cases)==2594,len(cases)
assert all(all(c.find(t) is None for t in ['failure','error','skipped']) for c in cases)
selected=[c for c in cases if 'GlycineHCarbonyl' in c.get('classname','')];assert len(selected)==62,len(selected)
replay=json.loads((r/'REPLAY.json').read_text());prior=json.loads((repo/'software/qualification/foundation-supportable-pass-20261007/g03/REPLAY.json').read_text())
assert len(replay['independentJvmComparisons'])==23
for n,h in prior['independentJvmComparisons'].items():assert replay['independentJvmComparisons'][n]==h,n
for n,h in replay['independentJvmComparisons'].items():assert sha(r/(n+'1.json'))==sha(r/(n+'2.json'))==h,n
assert replay['historicalFiles']==65 and replay['historicalByteIdentical'] and replay['preservationPins']==25 and replay['preservationPass']
for group,count in [('WaterBridge',98),('ImplicitHProxy',59),('S1',137)]:assert sum(group in c.get('classname','').split('.')[-1] for c in cases)==count,group
matrix=json.loads((q/'ACCEPTANCE_MATRIX.json').read_text())
for row in matrix['cases']:
 tests=[c for c in selected if any(k in c.get('classname','') or c.get('name','').startswith(k+'(') for k in row['checks'])]
 assert tests,row['id']
 row['executedChecks']=[{'class':c.get('classname'),'test':c.get('name'),'display':(c.findtext('system-out') or '').strip()} for c in tests]
 row['execution']='PASS_COMMITTED_SOURCE'
matrix['status']='PASS_BOUNDED_I04B';matrix['sourceCommit']=build['commit']
matrix['coverageNote']='Each category links exact executed tests. Only approved explicit-source-H glycine Option B is qualified. No general weak-H catalog, energetic, biological, membrane-survey parity or whole-system-negative proposition is claimed. Duplicate canonical names are rejected before state construction.'
write(q/'MATRIX_EXECUTION.json',matrix)
clean=q/'clean';clean.mkdir(exist_ok=True)
for name in ['BUILD.json','compile-command.json','compile.log','test-command.json','tests.log','isolation-commands.json','isolation.log']:shutil.copy2(b/name,clean/name)
shutil.copytree(b/'junit',clean/'junit',dirs_exist_ok=True)
replays=q/'replay-results';replays.mkdir(exist_ok=True)
for p in r.glob('*.log'):shutil.copy2(p,replays/p.name)
for p in r.glob('GlycineHCarbonylAcceptanceTest*.json'):shutil.copy2(p,replays/p.name)
replay.update({'sourceCommit':build['commit'],'previousTwentyTwoReplayHashesUnchanged':True,'productionReceiptsIssued':0});write(q/'REPLAY.json',replay)
subprocess.run(['python3',str(q.parents[1]/'validate_preservation.py')],check=True)
preservation=json.loads((q.parents[1]/'PRESERVATION_VALIDATION.json').read_text())
result={'sourceCommit':build['commit'],'contract':'ATHENA.I04.EXPLICIT_SOURCE_H_GLYCINE_BACKBONE_CARBONYL_CANDIDATE/1','cleanCommittedSourceTests':len(cases),'focusedI04BTests':len(selected),'isolationTests':3,'failures':0,'skips':0,'independentJvmPairs':23,'previousReplayHashesUnchanged':22,'historicalFilesByteIdentical':65,'preservationPinsIntact':25,'protectedTrackedFilesUnchanged':preservation['protectedTrackedFilesUnchanged'],'boundedImplementationQualified':True,'currentPolicyScientificRulesQualified':0,'productionReceiptsIssued':0,'priorS1I03WaterI02RoleEventPathSourceUnchanged':True,'externalRowsClosed':0,'laterCommitsPushed':False}
write(q/'QUALIFICATION.json',result);print(json.dumps(result,indent=2))
