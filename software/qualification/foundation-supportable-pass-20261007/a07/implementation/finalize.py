"""Seal passing A07 committed-source evidence without issuing scientific authority."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys,xml.etree.ElementTree as ET
repo=Path.cwd();q=repo/'software/qualification/foundation-supportable-pass-20261007/a07/implementation';b=Path(sys.argv[1]);r=Path(sys.argv[2])
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,v:p.write_text(json.dumps(v,indent=2)+'\n')
assert '0 tests failed' in (b/'tests.log').read_text()
assert '0 tests failed' in (b/'isolation.log').read_text() and '3 tests successful' in (b/'isolation.log').read_text()
build=json.loads((b/'BUILD.json').read_text())
for p,h in build['sourcePins'].items():
 if p.startswith('software/modules/'):assert sha(repo/p)==h,p
cases=[c for p in (b/'junit').glob('TEST-*.xml') for c in ET.parse(p).getroot().findall('testcase')]
assert len(cases)==2686,len(cases)
assert all(all(c.find(t) is None for t in ['failure','error','skipped']) for c in cases)
selected=[c for c in cases if 'AdvisoryAlert' in c.get('classname','')];assert len(selected)==92,len(selected)
replay=json.loads((r/'REPLAY.json').read_text());prior=json.loads((repo/'software/qualification/foundation-supportable-pass-20261007/i04/implementation/REPLAY.json').read_text())
assert len(replay['independentJvmComparisons'])==25
for n,h in prior['independentJvmComparisons'].items():assert replay['independentJvmComparisons'][n]==h,n
for n,h in replay['independentJvmComparisons'].items():assert sha(r/(n+'1.json'))==sha(r/(n+'2.json'))==h,n
assert replay['historicalFiles']==65 and replay['historicalByteIdentical'] and replay['preservationPins']==25 and replay['preservationPass']
for group,count in [('WaterBridge',98),('ImplicitHProxy',59),('S1',137),('GlycineHCarbonyl',62)]:assert sum(group in c.get('classname','').split('.')[-1] for c in cases)==count,group
entry=json.loads((r/'AdvisoryAlertNativeAcceptanceTest1.json').read_text())['entryDispositions']
resource=repo/'software/modules/athena/src/main/resources/totah/lab/athena/system/rules/advisory-alert-v1/entry-dispositions.json'
assert entry==json.loads(resource.read_text())['entries']
from collections import Counter
counts=dict(Counter(e['disposition'] for e in entry));assert counts=={'REPRESENTATION_EXECUTION_QUALIFIED':7,'EXECUTABLE_INCOMPLETELY_QUALIFIED':482,'UNSUPPORTED_NATIVE_PROFILE':401}
write(q/'MATRIX_EXECUTION.json',{'sourceCommit':build['commit'],'status':'PASS_BOUNDED_A07_NATIVE_AND_ADVISORY','matrix':'ACCEPTANCE_MATRIX.txt','executedChecks':[{'class':c.get('classname'),'test':c.get('name'),'display':(c.findtext('system-out') or '').strip()} for c in selected],'entryDispositionCounts':counts,'all890IndividuallyClassified':True,'catalogWideAbsenceAvailable':False,'historicalComparisons':65,'preservationPins':25,'independentJvmPairs':25})
clean=q/'clean';clean.mkdir(exist_ok=True)
for name in ['BUILD.json','compile-command.json','compile.log','test-command.json','tests.log','isolation-commands.json','isolation.log']:shutil.copy2(b/name,clean/name)
shutil.copytree(b/'junit',clean/'junit',dirs_exist_ok=True)
replays=q/'replay-results';replays.mkdir(exist_ok=True)
for p in r.glob('*.log'):shutil.copy2(p,replays/p.name)
for p in r.glob('AdvisoryAlert*.json'):shutil.copy2(p,replays/p.name)
replay.update({'sourceCommit':build['commit'],'previousTwentyThreeReplayHashesUnchanged':True,'productionReceiptsIssued':0});write(q/'REPLAY.json',replay)
subprocess.run(['python3',str(q.parents[1]/'validate_preservation.py')],check=True)
subprocess.run(['python3',str(q/'verify_historical_bodies.py')],check=True)
preservation=json.loads((q.parents[1]/'PRESERVATION_VALIDATION.json').read_text())
result={'sourceCommit':build['commit'],'contract':'ATHENA.A07.OCL_PAINS_ADVISORY_OCCURRENCES/1','cleanCommittedSourceTests':len(cases),'focusedA07Tests':len(selected),'isolationTests':3,'failures':0,'skips':0,'independentJvmPairs':25,'previousReplayHashesUnchanged':23,'historicalFilesByteIdentical':65,'preservationPinsIntact':25,'protectedTrackedFilesUnchanged':preservation['protectedTrackedFilesUnchanged'],'entryDispositionCounts':counts,'catalogWideAbsenceAvailable':False,'boundedImplementationQualified':True,'boundedV04NativeOperationValidationQualified':True,'currentPolicyScientificRulesQualified':0,'productionReceiptsIssued':0,'priorS1I03WaterI02RoleEventPathSourceUnchanged':True,'I04OptionBUnchanged':True,'externalRowsClosed':0,'laterCommitsPushed':False}
write(q/'QUALIFICATION.json',result);print(json.dumps(result,indent=2))
