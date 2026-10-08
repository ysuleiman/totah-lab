"""Bounded Foundation v2 seal: fresh committed source, all old/new tests and replay."""
from pathlib import Path
import concurrent.futures, io, json, shutil, subprocess, sys, tarfile, time, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'software/qualification/foundation-v2-workflow-20261008/harness'))
import foundation as v2
f=v2.engine
repo=f.REPO;q=repo/'software/qualification/foundation-v2-seal-20261008'
from replay_v2 import run_v2_replays


def main():
    out=Path(sys.argv[1]).resolve();out.mkdir(exist_ok=False);start=time.monotonic();stages={}
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip()
    for path in (q/'harness').glob('*'):
        if path.is_file():assert subprocess.check_output(['git','show',commit+':'+str(path.relative_to(repo))],cwd=repo)==path.read_bytes(),'Harness must be committed'
    stage=time.monotonic();source=out/'source';source.mkdir();tracked=subprocess.check_output(['git','ls-tree','-r','--name-only',commit],cwd=repo,text=True).splitlines()
    paths=set(f.read(f.BASE/'BUILD.json')['sourcePins'])
    paths.update(p for p in tracked if p.startswith(str(f.BASE.relative_to(repo))+'/'))
    paths.update(p for p in tracked if p.startswith('software/modules/') and '/src/' in p)
    paths.update(p for p in tracked if p.startswith('software/qualification/') and not p.endswith('.class'))
    # Exact literature/standard bytes not covered by textual audit suffixes.
    for p in f.read(f.BASE/'BUILD.json')['sourcePins']:paths.add(p)
    # Bound argv size while preserving the exact committed file selection.
    ordered_paths=sorted(p for p in paths if not p.endswith(".class"))
    for offset in range(0,len(ordered_paths),128):
        archive=subprocess.check_output(['git','archive',commit,'--',*ordered_paths[offset:offset+128]],cwd=repo)
        with tarfile.open(fileobj=io.BytesIO(archive)) as contents:
            contents.extractall(source,filter='data')
    fixture='software/qualification/foundation-v2-review-20261008/reference/CHEBI-142094.mol'
    assert (source/fixture).read_bytes()==subprocess.check_output(['git','show',commit+':'+fixture],cwd=repo), 'Pinned G05/G06 fixture omitted or changed'
    f.write(out/'EXPORT.json',{'sourceCommit':commit,'trackedPaths':ordered_paths,'fixturePreflight':{fixture:f.digest(source/fixture)},'compiledArtifactsExcluded':True})
    stages['cleanExportSeconds']=time.monotonic()-stage
    f.BASE=source/f.BASE.relative_to(repo);f.META=source/f.META.relative_to(repo)
    build=f.build(source,out,Path('/unused-no-release-cache'),clean=True);build['commit']=commit
    f.write(out/'BUILD.json',build);stages['sourceHashAndJarVerificationSeconds']=build['inputHashSeconds'];stages['compileSeconds']=build['compile']['seconds']
    # The development closure includes unrelated untracked files. Admit parallel
    # execution only after proving every committed input and every compiled class
    # matches the independently qualified Tier2 closure. Never add those files.
    meta=f.read(f.META);absent={p:h for p,h in meta['isolationPins'].items() if not (source/p).exists()}
    assert all(p not in tracked for p in absent), 'Missing tracked isolation dependency'
    assert all(meta['isolationPins'].get(p)==h for p,h in build['sourcePins'].items()), 'Unreviewed committed closure'
    prior=f.read(repo/'software/qualification/foundation-v2-closure-20261008/v07-v08-tier2/BUILD.json')
    classpins={str(p.relative_to(Path(build['classes']))):f.digest(p) for p in Path(build['classes']).rglob('*') if p.is_file()}
    assert classpins and all((Path(prior['classes'])/p).is_file() and f.digest(Path(prior['classes'])/p)==h for p,h in classpins.items()), 'Clean/Tier2 class byte mismatch'
    for name,pin in meta['parallelSafeClasses'].items():assert f.digest(source/pin['source'])==pin['sha256'],name
    meta['isolationPins']=build['sourcePins'];f.META=out/'release-isolation-metadata.json';f.write(f.META,meta)
    f.write(out/'COMMITTED_CLOSURE_PARITY.json',{'status':'PASS','compiledClasses':len(classpins),'classPins':classpins,'excludedUntrackedInputs':absent,'tier2BuildKey':prior['cacheKey'],'basis':'Every clean committed input matches reviewed closure; every resulting class byte equals qualified Tier2; no untracked input exported.'})
    print('Clean build and compiled closure parity passed; running full inventory',flush=True)
    tests=f.run(source,f.Output(out,build),f.inventory(source),4);stages['regressionSeconds']=tests['wallSeconds']
    oldcases=[c for p in (f.BASE/'junit').glob('TEST-*.xml') for c in ET.parse(p).getroot().findall('testcase')]
    oldids={(c.get('classname'),c.get('name')) for c in oldcases};newids={tuple(v) for v in tests['executedIdentities']};assert oldids<=newids and len(oldids)==2872
    preserved={}
    for label,path in [('v1','software/qualification/foundation-v1-rc-20261008/release/RESULT.json'),('v2Shared','software/qualification/foundation-v2-closure-20261008/v07-v08-tier2/RESULT.json')]:
        ids={tuple(v) for v in f.read(source/path)['executedIdentities']};assert ids<=newids,(label,sorted(ids-newids));preserved[label]=len(ids)
    assert preserved=={'v1':2876,'v2Shared':779}
    print('Full regression passed: '+str(tests['tests']),flush=True)
    stage=time.monotonic();iso=out/'isolation';iso.mkdir();(out/'mnemosyne-test').mkdir();module=source/'software/modules/mnemosyne';(module/'target/classes').mkdir(parents=True);(module/'pom.xml').write_bytes(subprocess.check_output(['git','show',commit+':software/modules/mnemosyne/pom.xml'],cwd=repo))
    old=f.read(f.BASE/'BUILD.json');oldwork=str(Path(old['source']).parent)
    commands=[[x.replace(oldwork,str(out)) for x in c] for c in f.read(f.BASE/'isolation-commands.json')]
    for i,c in enumerate(commands):
        result=f.timed(c,iso/(str(i)+'.log'),source);assert result['exitCode']==0
    assert '3 tests successful' in (iso/(str(len(commands)-1)+'.log')).read_text();stages['isolationSeconds']=time.monotonic()-stage
    f.write(iso/'commands.json',commands)
    stage=time.monotonic();replay=out/'replay';replay.mkdir();previous=f.read(source/'software/qualification/foundation-batched-closure-20261008/implementation/REPLAY.json')['independentJvmComparisons']
    cp=f.test_base(source,build['classes']);java=cp[0];classpath=cp[cp.index('--class-path')+1]
    two={'WaterBridgeCurrentPipelineTest','ImplicitHProxyCurrentPipelineTest','S1SourceScopeV2Test','S1ImplicitHCompositionTest','HalogenCarbonylAcceptanceTest','ZincCarbonylAcceptanceTest','SelectedPharmacophoreAcceptanceTest','SourceFragmentParentAcceptanceTest','ZnSourceFeatureAcceptanceTest','MetPheSurveyAcceptanceTest','GlycineHCarbonylAcceptanceTest','AdvisoryAlertAcceptanceTest','ClPheAcceptanceTest','SourceSiteMetadataAcceptanceTest','SourceFragmentLineageAcceptanceTest','FoundationV1ConsumerAcceptanceTest'}
    def replay_job(name,n):
        d=replay/(name+str(n));d.mkdir();temp=d/'tmp';temp.mkdir();target=d/'result.json';args=[str(target)]
        if name in two:args=[str(d/'catalog'),str(target)]
        cls=('totah.lab.athena.system.rules.' if name=='AdvisoryAlertNativeAcceptanceTest' else 'totah.lab.daedalus.system.')+name
        cmd=[java,'-Xmx512m','-Djava.io.tmpdir='+str(temp),'-cp',classpath,cls,*args];r=f.timed(cmd,d/'run.log',source);assert r['exitCode']==0,(name,n)
        return {'name':name,'copy':n,'sha256':f.digest(target),'seconds':r['seconds']}
    replay_results=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures=[pool.submit(replay_job,name,n) for name in list(previous)+['FoundationV1ConsumerAcceptanceTest'] for n in (1,2)]
        for future in concurrent.futures.as_completed(futures):replay_results.append(future.result())
    hashes={}
    for name in list(previous)+['FoundationV1ConsumerAcceptanceTest']:
        values={x['sha256'] for x in replay_results if x['name']==name};assert len(values)==1;hashes[name]=values.pop()
        if name in previous:assert hashes[name]==previous[name],name
    assert hashes==f.read(source/'software/qualification/foundation-v1-rc-20261008/release/QUALIFICATION.json')['independentJvmHashes'], 'Frozen v1 replay hash changed'
    extra_replays=run_v2_replays(f,source,out,build)
    stages['independentJvmReplaySeconds']=time.monotonic()-stage
    stage=time.monotonic()
    for n in (1,2):
        r=f.timed([java,'-Xmx512m','-cp',classpath,'totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest',str(replay/('historical'+str(n)))],replay/('historical'+str(n)+'.log'),source);assert r['exitCode']==0
    baseline=source/'software/qualification/ocl-query-b00-corrected-20261005/final-run/scientific-replay-one';files=[p.relative_to(baseline) for p in baseline.rglob('*') if p.is_file()];assert len(files)==65
    for p in files:assert (baseline/p).read_bytes()==(replay/'historical1'/p).read_bytes()==(replay/'historical2'/p).read_bytes(),p
    stages['historicalReplaySeconds']=time.monotonic()-stage
    stage=time.monotonic();pins=f.read(repo/'software/qualification/water-bridge-contract-20261006/PRESERVATION_BEFORE.json')['sha256'];assert len(pins)==25
    for p,h in pins.items():assert f.digest(repo/p)==h,p
    for p,h in f.read(repo/'software/qualification/v09-v10-implementation-20261008/PRESERVATION_BEFORE.json').items():assert f.digest(repo/p)==h,p
    preservation=f.timed(['python3',str(q/'harness/preservation.py')],out/'preservation.log',repo);assert preservation['exitCode']==0
    stages['preservationSeconds']=time.monotonic()-stage
    # Selection harness unit tests deliberately verify the original development
    # closure, including preserved untracked inputs. Execute committed tool bytes
    # against that closure; all scientific tests above use the clean export.
    harness=repo/'software/qualification/foundation-v2-workflow-20261008/harness'
    for path in harness.glob('*'):
        if path.is_file() and path.suffix in ['.py','.json']:assert path.read_bytes()==(source/path.relative_to(repo)).read_bytes()
    unit=f.timed(['python3','-m','unittest','discover','-s',str(harness),'-p','test_harness.py'],out/'harness-tests.log',repo);assert unit['exitCode']==0
    _,_,after,_,_=f.compiler_inputs(source);assert after==build['sourcePins'],'Committed export changed during qualification'
    stages['harnessTestsSeconds']=unit['seconds']
    ledger=f.read(source/'software/qualification/foundation-v2-closure-20261008/FOUNDATION_V2_LEDGER.json');counts={}
    for row in ledger['entries']:counts[row['currentDisposition']]=counts.get(row['currentDisposition'],0)+1
    result={'sourceCommit':commit,'scope':'Foundation v2 bounded release; 75 bounded qualified rows, 12 architectural dispositions, 9 scientific exclusions; no production activation','cleanExport':True,'compileCacheUsed':False,'regressionTests':tests['tests'],'previousTestIdentitiesPreserved':2872,'preservedQualifiedInventories':preserved,'isolationTests':3,'failures':0,'skips':0,'independentJvmPairs':len(hashes),'previousReplayHashesUnchanged':len(hashes),'independentJvmHashes':hashes,'historicalComparisons':65,'preservationPins':25,'unrelatedTrackedEditsPreserved':14,'counts':counts,'full96ScientificCompletion':False,'v2Replay':extra_replays,'v2DefinitionCount':8,'newCapabilityFamilies':0,'productionScientificReceiptsIssued':0,'stages':stages,'totalSeconds':time.monotonic()-start,'replayJobs':replay_results}
    f.write(out/'QUALIFICATION.json',result);print(json.dumps({k:v for k,v in result.items() if k not in ['replayJobs','independentJvmHashes']},indent=2))
if __name__=='__main__':main()
