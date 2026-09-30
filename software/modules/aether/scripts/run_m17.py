"""Serial, resumable measurement queue. No production modifications or tuning."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'validation/milestone-17'
specs=json.loads((OUT/'benchmark.json').read_text())
props={x.attrib['name']:x.attrib['value'] for x in ET.parse(ROOT/'target/surefire-reports/TEST-totah.lab.aether.AetherD3Test.xml').getroot().find('properties')}
# Small cases first; no ordering by outcomes.
names=sys.argv[1:] or sorted(specs,key=lambda n:(len(specs[n]['atoms']),n))
for name in names:
    target=OUT/'runs'/name;target.mkdir(parents=True,exist_ok=True)
    if (target/'execution.json').exists():
        print('RETAIN',name,flush=True);continue
    fixture=ROOT/'src/test/resources/totah/lab/aether/reference/m17'/f'{name}.atoms'
    assert hashlib.sha256(fixture.read_bytes()).hexdigest()==specs[name]['fixture_sha256']
    cache=OUT/'transient-cache'/name;cache.mkdir(parents=True,exist_ok=True)
    cmd=[props['java.home']+'/bin/java','-Xmx512m','-cp',props['java.class.path'],
         'totah.lab.aether.matrix.M17InteractionProbe',str(fixture),str(target),str(cache)]
    print('START',name,flush=True);start=time.perf_counter()
    with (target/'run.log').open('w') as log:
        child=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT)
        _,status,usage=os.wait4(child.pid,0);code=os.waitstatus_to_exitcode(status);child.returncode=code
    evidence={'name':name,'exit_code':code,'elapsed_seconds':time.perf_counter()-start,
              'peak_rss_bytes':usage.ru_maxrss,'command':cmd,'fixture_sha256':specs[name]['fixture_sha256'],
              'status':'SCREENING_ONLY'}
    (target/'execution.json').write_text(json.dumps(evidence,indent=2)+'\n')
    # Only disposable M17 exact binary caches, never scientific evidence or historical caches.
    shutil.rmtree(cache)
    print('FINISH',name,code,flush=True)
