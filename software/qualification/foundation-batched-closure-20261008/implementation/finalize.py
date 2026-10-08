"""Seal the combined Batch C only from passing immutable-source evidence; no scientific authority issued."""
from pathlib import Path
import json,hashlib,shutil,subprocess,sys,xml.etree.ElementTree as E
repo=Path.cwd();q=repo/'software/qualification/foundation-batched-closure-20261008/implementation';build=Path(sys.argv[1]);replay_dir=Path(sys.argv[2]);sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda p,v:p.write_text(json.dumps(v,indent=2)+'\n')
b=json.loads((build/'BUILD.json').read_text());r=json.loads((replay_dir/'REPLAY.json').read_text())
cases=[c for p in (build/'junit').glob('TEST-*.xml') for c in E.parse(p).getroot().findall('testcase')]
assert cases and all(all(c.find(t) is None for t in ['failure','error','skipped']) for c in cases)
assert '0 tests failed' in (build/'tests.log').read_text()
assert '3 tests successful' in (build/'isolation.log').read_text() and '0 tests failed' in (build/'isolation.log').read_text()
classes=['ClPheAcceptanceTest','ClPhePredicateTest','SourceSiteMetadataLexicalTest','SourceSiteMetadataAcceptanceTest','SourceFragmentLineageAcceptanceTest','BatchCCurrentPipelineTest','BatchCProvenanceEdgeTest']
selected=[c for c in cases if c.get('classname','').split('.')[-1] in classes]
assert len(cases)==2686+len(selected),(len(cases),len(selected))
for group,count in [('WaterBridge',98),('ImplicitHProxy',59),('S1',137),('GlycineHCarbonyl',62),('AdvisoryAlert',92)]:assert sum(group in c.get('classname','').split('.')[-1] for c in cases)==count,group
for p,h in b['sourcePins'].items():
 if p.startswith('software/modules/'):assert sha(repo/p)==h,p
prior=json.loads((repo/'software/qualification/foundation-supportable-pass-20261007/a07/implementation/REPLAY.json').read_text())
assert len(r['independentJvmComparisons'])==28
for n,h in prior['independentJvmComparisons'].items():assert r['independentJvmComparisons'][n]==h,n
for n,h in r['independentJvmComparisons'].items():assert sha(replay_dir/(n+'1.json'))==sha(replay_dir/(n+'2.json'))==h,n
assert r['historicalFiles']==65 and r['historicalByteIdentical'] and r['preservationPins']==25 and r['preservationPass']
subprocess.run(['python3',str(repo/'software/qualification/foundation-supportable-pass-20261007/validate_preservation.py')],check=True)
preservation=json.loads((repo/'software/qualification/foundation-supportable-pass-20261007/PRESERVATION_VALIDATION.json').read_text())
for p,h in json.loads((q.parent/'UNRELATED_TRACKED_PINS.json').read_text())['sha256'].items():assert sha(repo/p)==h,p
clean=q/'clean';clean.mkdir(exist_ok=True)
for name in ['BUILD.json','compile-command.json','compile.log','test-command.json','tests.log','isolation-commands.json','isolation.log']:shutil.copy2(build/name,clean/name)
shutil.copytree(build/'junit',clean/'junit',dirs_exist_ok=True)
rp=q/'replay-results';rp.mkdir(exist_ok=True)
for p in replay_dir.glob('*.log'):shutil.copy2(p,rp/p.name)
for name in ['ClPheAcceptanceTest','SourceSiteMetadataAcceptanceTest','SourceFragmentLineageAcceptanceTest']:
 for n in [1,2]:shutil.copy2(replay_dir/(name+str(n)+'.json'),rp/(name+str(n)+'.json'))
r.update(sourceCommit=b['commit'],previousTwentyFiveReplayHashesUnchanged=True,productionReceiptsIssued=0);write(q/'REPLAY.json',r)
result={'sourceCommit':b['commit'],'batch':'C: I11/V18/A08 bounded public additions','cleanCommittedSourceTests':len(cases),'focusedBatchTests':len(selected),'isolationTests':3,'failures':0,'skips':0,'independentJvmPairs':28,'previousReplayHashesUnchanged':25,'historicalFilesByteIdentical':65,'preservationPinsIntact':25,'protectedTrackedFilesUnchanged':preservation['protectedTrackedFilesUnchanged'],'unrelatedTrackedEditsUnchanged':True,'boundedImplementationQualified':True,'currentPolicyScientificRulesQualified':0,'productionReceiptsIssued':0,'externalRowsClosed':0,'pushed':False}
write(q/'QUALIFICATION.json',result)
write(q/'MATRIX_EXECUTION.json',{'sourceCommit':b['commit'],'status':'PASS_BOUNDED_BATCH_C','qualification':result,'executedChecks':[{'class':c.get('classname'),'test':c.get('name'),'display':(c.findtext('system-out') or '').strip()} for c in selected]})
print(json.dumps(result,indent=2))
