"""Persist verified source-build, S1/composition matrix and replay evidence; issue no authority."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys,xml.etree.ElementTree as ET
repo=Path.cwd();q=repo/'software/qualification/i03-s1-implementation-20261007'
b=Path(sys.argv[1]).resolve();r=Path(sys.argv[2]).resolve()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,x:p.write_text(json.dumps(x,indent=2)+'\n')
assert '0 tests failed' in (b/'tests.log').read_text()
assert '0 tests failed' in (b/'isolation.log').read_text()
assert '3 tests successful' in (b/'isolation.log').read_text()
build=json.loads((b/'BUILD.json').read_text())
# A subsequent audit commit may change metadata, never the tested production/test source.
for p,h in build['sourcePins'].items():
 if p.startswith('software/modules/'):assert sha(repo/p)==h,p
cases=[]
for p in (b/'junit').glob('TEST-*.xml'):cases.extend(ET.parse(p).getroot().findall('testcase'))
assert cases and all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
s1=[c for c in cases if c.get('classname','').split('.')[-1].startswith('S1')]
composition=[c for c in s1 if c.get('classname','').endswith('S1ImplicitHCompositionTest')]
legacy=[c for c in cases if 'ImplicitHProxy' in c.get('classname','')]
water=[c for c in cases if 'WaterBridge' in c.get('classname','')]
assert len(legacy)==59 and len(water)==98
assert len(cases)==2089+len(s1),(len(cases),len(s1))
assert len(composition)==16,len(composition)
replay=json.loads((r/'REPLAY.json').read_text())
prior=json.loads((repo/'software/qualification/i03-a-implementation-20261006/REPLAY.json').read_text())
assert len(prior['independentJvmComparisons'])==13 and len(replay['independentJvmComparisons'])==16
for name,h in prior['independentJvmComparisons'].items():assert replay['independentJvmComparisons'][name]==h,name
assert replay['historicalFiles']==65 and replay['historicalByteIdentical']
assert replay['preservationPins']==25 and replay['preservationPass']
for name,h in replay['independentJvmComparisons'].items():
 assert sha(r/(name+'1.json'))==sha(r/(name+'2.json'))==h,name
matrix=json.loads((q/'MATRIX_EXECUTION.json').read_text());assert len(matrix['cases'])==94
for row in matrix['cases']:
 evidence=[]
 for check in row['qualificationChecks']:
  if '#' in check:
   cls,method=check.split('#');matched=[c for c in cases if c.get('classname')==cls and c.get('name','').startswith(method+'(')]
   assert matched,check
   evidence.extend({'class':cls,'test':c.get('name'),'display':(c.findtext('system-out') or '').strip()} for c in matched)
  elif check.startswith('independentJvmComparisons:'):assert check.split(':',1)[1] in replay['independentJvmComparisons']
  elif check=='historicalIndependentJvmComparisons:13':assert len(prior['independentJvmComparisons'])==13
  elif check=='historicalFiles:65':assert replay['historicalByteIdentical']
  elif check=='preservationPins:25':assert replay['preservationPass']
  else:raise AssertionError(check)
 row['execution']='PASS_COMMITTED_SOURCE_BOUNDED_CONTRACT'
 row['executedChecks']=evidence
matrix['status']='PASS_BOUNDED_S1_AND_SEPARATE_COMPOSITION'
matrix['sourceCommit']=build['commit']
matrix['authorityScope']='Synthetic qualification fixtures only. No production scientific receipt or source-scope authority.'
write(q/'MATRIX_EXECUTION.json',matrix)
clean=q/'clean';clean.mkdir(exist_ok=True)
for name in ['BUILD.json','compile-command.json','compile.log','test-command.json','tests.log','isolation-commands.json','isolation.log']:shutil.copy2(b/name,clean/name)
shutil.copytree(b/'junit',clean/'junit',dirs_exist_ok=True)
replays=q/'replay-results';replays.mkdir(exist_ok=True)
for p in r.glob('*.log'):shutil.copy2(p,replays/p.name)
for p in r.glob('S1*.json'):shutil.copy2(p,replays/p.name)
replay['previousThirteenReplayHashesUnchanged']=True
replay['sourceCommit']=build['commit'];replay['productionReceiptsIssued']=0
write(q/'REPLAY.json',replay)
subprocess.run(['python3',str(q/'validate_preservation.py')],check=True)
preservation=json.loads((q/'PRESERVATION_VALIDATION.json').read_text())
result={'sourceCommit':build['commit'],'scope':'User-approved bounded S1 source classifier, source-scope /1 and additive /2, and separately qualified opt-in I03-A composition. Formal graph/proxy assertions only.',
 'cleanCommittedSourceTests':len(cases),'S1AndCompositionTests':len(s1),'S1ProducerScopeAndPredicateTests':len(s1)-len(composition),'separateI03CompositionTests':len(composition),'unchangedLegacyI03Tests':len(legacy),'unchangedWaterFamilyTests':len(water),
 'S1AcceptanceMatrixCases':94,'isolationTests':3,'failures':0,'skips':0,'independentJvmPairs':16,'previousReplayHashesUnchanged':13,'historicalFilesByteIdentical':65,'preservationPinsIntact':25,
 'protectedTrackedFilesUnchanged':preservation['protectedTrackedFilesUnchanged'],'SP3ProducerSelected':True,'S1BoundedImplementationQualified':True,'separateI03CompositionQualified':True,
 'historicalI03UnconditionalBlockPreserved':True,'originalI03ReviewMatrixPreserved':True,'compositionQualificationScope':'Six admitted pairs and governed positive/negative/unknown execution; exhaustive negative requires eligible measured pairs. Unsupported-only selections remain unknown, not interaction absence.','currentPolicyScientificRulesQualified':0,'productionReceiptsIssued':0,'productionSourceScopeAuthorityIssued':False,
 'I04':'SCIENTIFIC_REVIEW_REQUIRED; not implemented','externalRowsUnchanged':12,'pushed':False}
write(q/'QUALIFICATION.json',result)
print(json.dumps(result,indent=2))
