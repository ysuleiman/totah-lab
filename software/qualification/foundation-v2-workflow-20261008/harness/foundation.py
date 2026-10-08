"""Reviewed v2 selection/isolation metadata; reuse frozen v1 compilation and result checks."""
from pathlib import Path
import argparse, importlib.util, json, re
HERE=Path(__file__).resolve().parent
REPO=HERE.parents[3]
spec=importlib.util.spec_from_file_location('foundation_v1',REPO/'software/qualification/foundation-v1-rc-20261008/harness/foundation.py')
engine=importlib.util.module_from_spec(spec);spec.loader.exec_module(engine)
engine.META=HERE/'dependencies.json'
read=engine.read;write=engine.write;digest=engine.digest;hash_object=engine.hash_object
META=engine.META

def broad(root,reason):return {'tier':3,'classes':engine.inventory(root),'reason':[reason]}
def select(changes,tier=2,root=REPO):
    root=Path(root);meta=read(META);all_tests=engine.inventory(root);changes=sorted(set(changes))
    if not changes:return broad(root,'empty/unknown change scope')
    for p,h in {**meta['dispatchPins'],**meta['reviewedSharedPins']}.items():
        if not (root/p).exists() or digest(root/p)!=h:return broad(root,'shared/dispatcher bytes differ from reviewed checkpoint: '+p)
    if set(all_tests)-set(meta['baselineClasses'])-set(meta['reviewedNewClasses']):return broad(root,'unreviewed test consumer')
    for name,package in meta['qualifiedChangePackages'].items():
        if set(changes)==set(package['pins']) and all((root/p).is_file() and digest(root/p)==h for p,h in package['pins'].items()):
            return {'tier':tier,'classes':package['classes'] if tier==2 else package.get('focused',meta['families']['G06']['focused']),'package':name,'reason':[package['basis']]}
    families=set()
    for path in changes:
        matches=[k for k,v in meta['families'].items() if path in v['sources']+v['resources']+v['testSources']]
        if not matches:return broad(root,'unmapped/shared change: '+path)
        families.update(matches)
    selected=set()
    for f in families:selected.update(meta['families'][f]['focused' if tier==1 else 'affected'])
    # A selected test is not a modified helper. Propagate only genuinely changed
    # test/helper files, then their transitive consumers. Unknown ownership above
    # and unknown test inventory still require broad qualification.
    seeds={Path(p).stem for p in changes if '/test/' in p and p.endswith('.java')}
    if tier>1:
        prior=None
        while prior!=seeds:
            prior=set(seeds)
            for p in (root/'software/modules/daedalus/src/test/java').rglob('*.java'):
                text=p.read_text();package=re.search(r'package\s+([\w.]+)',text)
                if not package:continue
                if any(re.search(r'\b'+re.escape(seed)+r'\b',text) for seed in prior):
                    seeds.add(p.stem);name=package[1]+'.'+p.stem
                    if name in all_tests:selected.add(name)
    return {'tier':tier,'classes':sorted(selected),'families':sorted(families),'reason':['Reviewed direct affected classes plus transitive consumers of changed test/helper sources; selected unchanged dependencies do not seed expansion.']}

def isolation_eligible(root,classes):
    meta=read(META)
    if not all((root/p).exists() and digest(root/p)==h for p,h in meta['isolationPins'].items()):return []
    return [c for c in classes if c in meta['parallelSafeClasses'] and digest(root/meta['parallelSafeClasses'][c]['source'])==meta['parallelSafeClasses'][c]['sha256']]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['select','run','build']);p.add_argument('--tier',type=int,choices=[1,2,3],default=2);p.add_argument('--changed',action='append',default=[]);p.add_argument('--class-name',action='append',default=[]);p.add_argument('--source',type=Path,default=REPO);p.add_argument('--out',type=Path);p.add_argument('--cache',type=Path,default=Path('/private/tmp/foundation-v1-build-cache'));p.add_argument('--workers',type=int,choices=range(1,5),default=3);args=p.parse_args();root=args.source.resolve()
    selection=select(args.changed,args.tier,root) if args.tier<3 and not args.class_name else {'tier':args.tier,'classes':args.class_name or engine.inventory(root),'reason':['Explicit reviewed selection or release inventory']}
    if args.action=='select':print(json.dumps(selection,indent=2));return
    if selection['tier']==3 and args.tier!=3:p.error('Dependency uncertainty/shared change requires broad qualification; no implicit Tier3 run. Review selection first.')
    if not args.out:p.error('--out required')
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=False);write(out/'SELECTION.json',selection)
    b=engine.build(root,out,args.cache.resolve(),clean=args.tier==3)
    if args.action=='build':print(json.dumps({'cacheHit':b['cacheHit']}));return
    # Per-class pins are checked in addition to the transitive isolation closure.
    meta=read(META);expected=[c for c in selection['classes'] if c in meta['parallelSafeClasses']]
    actual=isolation_eligible(root,selection['classes'])
    if set(actual)!=set(expected):
        # Frozen runner already falls back for closure drift; also handle a corrupt
        # class entry without granting partial or implicit parallel authority.
        args.workers=1
    result=engine.run(root,engine.Output(out,b),selection['classes'],args.workers)
    print(json.dumps({'tests':result['tests'],'seconds':result['wallSeconds'],'workers':args.workers,'parallelClasses':actual,'tier':selection['tier'],'releaseQualification':False}))
if __name__=='__main__':main()
