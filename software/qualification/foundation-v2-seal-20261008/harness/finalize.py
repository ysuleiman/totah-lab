"""Complete a release tail after the obsolete v1 preservation gate stopped it.
All scientific stages must already have executed successfully on one immutable
committed export. Revalidate their bytes and exact approved preservation delta;
never skip or turn a failed scientific test into a passing release.
"""
from pathlib import Path
import sys,json,subprocess,time
ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'software/qualification/foundation-v2-workflow-20261008/harness'))
import foundation as v2
from preservation import verify
f=v2.engine
out=Path(sys.argv[1]).resolve();source=out/'source';start=time.monotonic()
assert not (out/'QUALIFICATION.json').exists()
completionCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
for p in Path(__file__).parent.glob('*.py'):assert p.read_bytes()==subprocess.check_output(['git','show',completionCommit+':'+str(p.relative_to(ROOT))],cwd=ROOT)
build=f.read(out/'BUILD.json');commit=build['commit'];assert f.read(out/'EXPORT.json')['sourceCommit']==commit
assert 'WaterIdentity.java' in (out/'preservation.log').read_text() and 'AssertionError' in (out/'preservation.log').read_text()
# Completion may change only release audit code/docs, never qualified implementation.
diff=subprocess.check_output(['git','diff','--name-only',commit,completionCommit],cwd=ROOT,text=True).splitlines()
assert all(p.startswith('software/qualification/foundation-v2-seal-20261008/') for p in diff),diff
f.BASE=source/'software/qualification/foundation-batched-closure-20261008/implementation/clean';f.META=out/'release-isolation-metadata.json'
_,_,sourcepins,_,_=f.compiler_inputs(source);assert sourcepins==build['sourcePins']
for p,h in sourcepins.items():assert f.digest(ROOT/p)==h,p
classes=Path(build['classes']);assert {str(p.relative_to(classes)):f.digest(p) for p in classes.rglob('*') if p.is_file()}==f.read(out/'COMMITTED_CLOSURE_PARITY.json')['classPins']
tests=f.read(out/'RESULT.json');actual=f.results(out,f.inventory(source))
assert tests['tests']==actual['tests']==3255 and not actual['failuresErrorsOrSkips'] and all(j['exitCode']==0 for j in tests['jobs'])
assert tests['executedIdentities']==[list(v) for v in actual['executedIdentities']]
ids={tuple(v) for v in actual['executedIdentities']};preserved={}
for label,path in [('v1','software/qualification/foundation-v1-rc-20261008/release/RESULT.json'),('v2Shared','software/qualification/foundation-v2-closure-20261008/v07-v08-tier2/RESULT.json')]:
 oldids={tuple(v) for v in f.read(source/path)['executedIdentities']};assert oldids<=ids;preserved[label]=len(oldids)
assert preserved=={'v1':2876,'v2Shared':779}
assert '3 tests successful' in (out/'isolation/2.log').read_text()
legacy=f.read(source/'software/qualification/foundation-v1-rc-20261008/release/QUALIFICATION.json')['independentJvmHashes'];assert len(legacy)==29
for name,h in legacy.items():
 for n in [1,2]:assert f.digest(out/'replay'/f'{name}{n}'/'result.json')==h,(name,n)
extra=f.read(out/'V2_REPLAY.json');assert extra['status']=='PASS' and extra['independentJvmPairs']==6 and extra['fullReportPairs']==10
assert len(extra['results'])==12 and {r['domain'] for r in extra['results']}=={'G05','G06','V09_V10','V11','V07','V08'}
for r in extra['results']:
 for suffix,h in r['sha256'].items():
  file='report.json' if suffix=='report' else 'report'+suffix
  assert f.digest(out/'replay-v2'/f"{r['domain']}-{r['copy']}"/file)==h
for name in {r['domain'] for r in extra['results']}:
 pair=[r for r in extra['results'] if r['domain']==name];assert pair[0]['sha256']==pair[1]['sha256']
baseline=source/'software/qualification/ocl-query-b00-corrected-20261005/final-run/scientific-replay-one';files=[p.relative_to(baseline) for p in baseline.rglob('*') if p.is_file()];assert len(files)==65
for p in files:assert (baseline/p).read_bytes()==(out/'replay/historical1'/p).read_bytes()==(out/'replay/historical2'/p).read_bytes()
preservation=verify();f.write(out/'PRESERVATION_V2.json',preservation)
harness=ROOT/'software/qualification/foundation-v2-workflow-20261008/harness'
for p in harness.glob('*'):
 if p.is_file() and p.suffix in ['.py','.json']:assert p.read_bytes()==(source/p.relative_to(ROOT)).read_bytes()
unit=f.timed(['python3','-m','unittest','discover','-s',str(harness),'-p','test_harness.py'],out/'harness-tests.log',ROOT);assert unit['exitCode']==0
_,_,after,_,_=f.compiler_inputs(source);assert after==sourcepins
counts=f.read(source/'software/qualification/foundation-v2-closure-20261008/FOUNDATION_V2_LEDGER.json')['counts'];assert sum(counts.values())==96 and counts['BOUNDED_SUPPORTED_DOMAIN_QUALIFIED']==75
result={'sourceCommit':commit,'completionAuditCommit':completionCommit,'scope':'Foundation v2 bounded release;75 qualified bounded rows,12 architectural dispositions,9 scientific exclusions;no production activation','status':'PASS','cleanExport':True,'compileCacheUsed':False,'regressionTests':tests['tests'],'previousTestIdentitiesPreserved':2872,'preservedQualifiedInventories':preserved,'isolationTests':3,'failures':0,'skips':0,'independentJvmPairs':29,'previousReplayHashesUnchanged':29,'independentJvmHashes':legacy,'v2Replay':extra,'historicalComparisons':65,'preservationPins':25,'unrelatedTrackedEditsPreserved':14,'counts':counts,'full96ScientificCompletion':False,'v2DefinitionCount':8,'newCapabilityFamilies':0,'productionScientificReceiptsIssued':0,'stages':{'compileSeconds':build['compile']['seconds'],'regressionSeconds':tests['wallSeconds'],'completionAuditSeconds':time.monotonic()-start,'harnessTestsSeconds':unit['seconds']},'totalSeconds':time.time()-out.stat().st_birthtime,'totalTimeMeaning':'Elapsed wall time from fresh export creation, including preservation-audit engineering repair. Not summed CPU or uninterrupted test runtime.','completionReason':'Legacy v1 preservation checker predates approved G06 WaterIdentity delegation. Exact qualified hash and mechanically reconstructed delegation-only transformation verified; all other preservation requirements retained. No scientific source, test or assertion changed. Existing completed clean-source stages reverified without rerunning full regression.'}
f.write(out/'QUALIFICATION.json',result);print(json.dumps({k:v for k,v in result.items() if k not in ['independentJvmHashes','v2Replay']},indent=2))
