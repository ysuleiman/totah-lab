"""Foundation development/release runner. Selection is conservative; scientific checks are unchanged."""
from pathlib import Path
import argparse, concurrent.futures, collections, hashlib, json, os, re, shutil, subprocess, sys, time, xml.etree.ElementTree as ET
HERE=Path(__file__).resolve().parent
REPO=HERE.parents[3]
BASE=REPO/'software/qualification/foundation-batched-closure-20261008/implementation/clean'
META=HERE/'dependencies.json'
def read(p): return json.loads(Path(p).read_text())
def write(p,v): Path(p).write_text(json.dumps(v,indent=2)+'\n')
def digest(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def hash_object(v): return hashlib.sha256(json.dumps(v,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def timed(command,output,cwd):
    started=time.monotonic()
    with Path(output).open('w') as stream:
        process=subprocess.Popen(command,cwd=cwd,stdout=stream,stderr=subprocess.STDOUT)
        _,status,usage=os.wait4(process.pid,0)
        process.returncode=os.waitstatus_to_exitcode(status)
    return {'command':command,'seconds':time.monotonic()-started,'cpuUserSeconds':usage.ru_utime,'cpuSystemSeconds':usage.ru_stime,'exitCode':process.returncode}
def inventory(root=REPO):
    meta=read(META); result=set(meta['baselineClasses'])
    for package in ['totah/lab/daedalus/system','totah/lab/athena/system/rules','totah/lab/athena/system/rules/research']:
        for p in (root/'software/modules/daedalus/src/test/java'/package).glob('*.java'):
            if re.search(r'@(Test|TestFactory|ParameterizedTest)\b',p.read_text()): result.add(package.replace('/','.')+'.'+p.stem)
    return sorted(result)
def select(changes,tier=2,root=REPO):
    meta=read(META);all_tests=inventory(root);families=set();reasons=[]
    # The reviewed dispatchers dynamically select leaf adapters. If either changes, no narrow selection is valid.
    for p,h in {**meta['dispatchPins'],**meta['reviewedSharedPins']}.items():
        if not (root/p).exists() or digest(root/p)!=h:return {'tier':3,'classes':all_tests,'reason':['reviewed shared source or dispatcher changed: '+p]}
    if not changes:return {'tier':3,'classes':all_tests,'reason':['empty/unknown change scope']}
    for path in changes:
        matches=[k for k,v in meta['families'].items() if path in v['sources'] or path in v['resources'] or path in v['testSources']]
        if not matches:return {'tier':3,'classes':all_tests,'reason':['unmapped/shared change: '+path]}
        families.update(matches)
    # Reverse test-helper references retain dependent consumers; unfamiliar new consumers force the full tier.
    selected=set()
    for f in families:selected.update(meta['families'][f]['focused' if tier==1 else 'affected'])
    if set(all_tests)-set(meta['baselineClasses'])-set(meta['reviewedNewClasses']):return {'tier':3,'classes':all_tests,'reason':['new tests lack reviewed dependency metadata']}
    changed=tier>1
    while changed:
        previous=set(selected)
        for p in (root/'software/modules/daedalus/src/test/java').rglob('*.java'):
            text=p.read_text(); package=re.search(r'package\s+([\w.]+)',text)
            if not package:continue
            name=package[1]+'.'+p.stem
            if name in all_tests and any(re.search(r'\b'+re.escape(c.split('.')[-1])+r'\b',text) for c in selected):selected.add(name)
        changed=selected!=previous
    return {'tier':tier,'classes':sorted(selected),'families':sorted(families),'reason':['reviewed leaf ownership plus dependent test helpers; shared/unmapped changes escalate to Tier 3']}
def compiler_inputs(root):
    old=read(BASE/'BUILD.json');cmd=read(BASE/'compile-command.json')
    cmd=[x.replace(old['source'],str(root)) for x in cmd]
    for package in ['totah/lab/daedalus/system','totah/lab/athena/system/rules','totah/lab/athena/system/rules/research']:
        for p in sorted((root/'software/modules/daedalus/src/test/java'/package).glob('*.java')):
            if str(p) not in cmd:cmd.append(str(p))
    # All transitive source/resource files in the foundation modules, including implicit javac source reads.
    inputs={}
    modules={Path(p).parts[2] for p in old['sourcePins'] if p.startswith('software/modules/')}
    for module in sorted(modules):
        for p in sorted((root/'software/modules'/module/'src').rglob('*')):
            if p.is_file() and '__pycache__' not in p.parts:inputs[str(p.relative_to(root))]=digest(p)
    jars={p:digest(p) for p in old['jars']}
    assert jars==old['jars'],'Pinned dependency jar changed; qualification closure needs review'
    version=subprocess.check_output([cmd[0],'-version'],stderr=subprocess.STDOUT,text=True).strip()
    return old,cmd,inputs,jars,version

def build(root,out,cache,clean=False):
    started=time.monotonic();old,cmd,pins,jars,version=compiler_inputs(root)
    normalized=[x.replace(str(root),'$SOURCE').replace(old['classes'],'$CLASSES') for x in cmd]
    key=hash_object({'sourceAndResources':pins,'jars':jars,'compiler':version,'command':normalized})
    target=(out/'classes') if clean else (cache/key/'classes');stamp=target.parent/'CACHE.json'
    hit=False
    if not clean and stamp.exists():
        saved=read(stamp);actual={str(p.relative_to(target)):digest(p) for p in target.rglob('*') if p.is_file()}
        hit=saved.get('key')==key and saved.get('classPins')==actual
        if not hit:raise RuntimeError('Compiled cache corruption detected; refusing reuse')
    setup=time.monotonic()-started
    cmd=[x.replace(old['classes'],str(target)) for x in cmd]
    if not hit:
        target.mkdir(parents=True,exist_ok=False)
        compile_result=timed(cmd,out/'compile.log',root)
        write(out/'compile-command.json',cmd)
        if compile_result['exitCode']:raise RuntimeError('Compilation failed; see compile.log')
        if not clean:write(stamp,{'key':key,'classPins':{str(p.relative_to(target)):digest(p) for p in target.rglob('*') if p.is_file()}})
    else:compile_result={'seconds':0.0,'exitCode':0,'cacheHit':True}
    value={'source':str(root),'classes':str(target),'cacheKey':key,'cacheHit':hit,'inputHashSeconds':setup,'compile':compile_result,'sourcePins':pins,'jars':jars,'compiler':version}
    write(out/'BUILD.json',value);return value

def test_base(root,classes):
    old=read(BASE/'BUILD.json');cmd=read(BASE/'test-command.json');cmd=cmd[:cmd.index('--class-path')+2]
    return [x.replace(old['source'],str(root)).replace(old['classes'],str(classes)) for x in cmd]
def run_job(root,classes,out,name,tests):
    job=out/name;job.mkdir();temp=job/'tmp';temp.mkdir();cmd=test_base(root,classes)
    cmd.insert(1,'-Djava.io.tmpdir='+str(temp))
    for cls in tests:cmd+=['--select-class',cls]
    cmd+=['--details','summary','--disable-ansi-colors','--reports-dir',str(job/'junit')]
    result=timed(cmd,job/'tests.log',root);result.update(name=name,classes=tests)
    write(job/'COMMAND.json',result);return result

def results(out,expected):
    cases=[c for p in out.glob('*/junit/TEST-*.xml') for c in ET.parse(p).getroot().findall('testcase')]
    identifiers=[(c.get('classname'),c.get('name')) for c in cases]
    if len(set(identifiers))!=len(identifiers):raise RuntimeError('Duplicate test identities')
    observed={c.get('classname') for c in cases}
    assert observed==set(expected),(sorted(set(expected)-observed),sorted(observed-set(expected)))
    failed=[(c.get('classname'),c.get('name')) for c in cases if any(c.find(t) is not None for t in ['failure','error','skipped'])]
    return {'tests':len(cases),'failuresErrorsOrSkips':failed,'executedIdentities':sorted(identifiers),'classSeconds':dict(sorted(collections.Counter({cl:sum(float(c.get('time','0').replace(',','')) for c in cases if c.get('classname')==cl) for cl in observed}).items()))}

def run(root,out,classes,workers):
    meta=read(META); unchanged=all((root/p).exists() and digest(root/p)==h for p,h in meta['isolationPins'].items());allowed=meta['parallelSafeClasses'] if unchanged else {};separate=[c for c in classes if c in allowed];serial=[c for c in classes if c not in allowed]
    jobs=[];started=time.monotonic()
    # Unknown/unreviewed tests execute alone. Only independently reviewed temp-file-only classes overlap.
    if serial:jobs.append(run_job(root,out.build['classes'],out.path,'serial',serial))
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        futures=[pool.submit(run_job,root,out.build['classes'],out.path,c.split('.')[-1],[c]) for c in separate]
        for future in concurrent.futures.as_completed(futures):
            result=future.result();jobs.append(result);print(result['name'],result['exitCode'],round(result['seconds'],2),flush=True)
    result=results(out.path,classes);result.update(jobs=sorted(jobs,key=lambda j:j['name']),wallSeconds=time.monotonic()-started,workers=workers)
    assert all(j['exitCode']==0 for j in jobs) and not result['failuresErrorsOrSkips'],result['failuresErrorsOrSkips']
    # Rehash input bytes after execution; mutable data and eligibility were never cached.
    _,_,pins,_,_=compiler_inputs(root);assert pins==out.build['sourcePins'],'Source/resource changed during tests'
    write(out.path/'RESULT.json',result);return result
class Output:
    def __init__(self,path,build):self.path=path;self.build=build

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['select','run','build']);parser.add_argument('--tier',type=int,choices=[1,2,3],default=1);parser.add_argument('--changed',action='append',default=[]);parser.add_argument('--class-name',action='append',default=[]);parser.add_argument('--source',type=Path,default=REPO);parser.add_argument('--out',type=Path);parser.add_argument('--cache',type=Path,default=Path('/private/tmp/foundation-v1-build-cache'));parser.add_argument('--workers',type=int,choices=range(1,5),default=4);args=parser.parse_args();root=args.source.resolve()
    selection=select(args.changed,args.tier,root) if args.tier<3 and not args.class_name else {'tier':args.tier,'classes':args.class_name or inventory(root),'reason':['explicit selection' if args.class_name else 'complete release test inventory']}
    if args.action=='select':print(json.dumps(selection,indent=2));return
    if not args.out:parser.error('--out is required')
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=False);write(out/'SELECTION.json',selection);b=build(root,out,args.cache.resolve(),clean=args.tier==3)
    if args.action=='build':print(json.dumps({'cacheHit':b['cacheHit'],'compileSeconds':b['compile']['seconds']}));return
    r=run(root,Output(out,b),selection['classes'],args.workers);print(json.dumps({'tests':r['tests'],'seconds':r['wallSeconds'],'cacheHit':b['cacheHit'],'tier':selection['tier'],'scope':'regression only; Tier 3 additionally requires release.py isolation/replay/pins/export/bookkeeping'}))
if __name__=='__main__':main()
