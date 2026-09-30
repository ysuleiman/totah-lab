"""Serialize remaining checks after their corresponding numerical queue finishes."""
from pathlib import Path
import json,os,shutil,subprocess,sys,time
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'validation/milestone-17'
mode=sys.argv[1]
marker=OUT/('runs/nenci_047/execution.json' if mode=='native' else 'external/def2-svp/nenci_047/B_GHOST_A-PBE.json')
start=time.monotonic()
while not marker.exists():
    if time.monotonic()-start>172800:raise RuntimeError('Preceding queue incomplete after 48 hours')
    time.sleep(10)
if mode=='external':
    subprocess.run([sys.executable,str(ROOT/'scripts/reference_m17.py'),'def2-tzvp'],check=True)
else:
    subprocess.run([sys.executable,str(ROOT/'scripts/replay_m17.py')],check=True)
    names=[x['name'].split('.')[-1] for x in json.loads((ROOT/'validation/milestone-16/test-summary.json').read_text())['suites']]
    env=os.environ.copy();env['JAVA_HOME']='/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home'
    env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH'];env['MAVEN_OPTS']='-Xmx256m'
    subprocess.run(['mvn','-o','-f',str(ROOT.parent/'pom.xml'),'-pl','aether','-am','test',
        '-Dtest='+','.join(names),'-Dsurefire.failIfNoSpecifiedTests=false'],env=env,check=True)
    target=OUT/'junit';target.mkdir(exist_ok=True)
    for p in (ROOT/'target/surefire-reports').glob('*'):
        if any(n in p.name for n in names):shutil.copy2(p,target/p.name)
    (OUT/'regression-complete.json').write_text(json.dumps({'status':'PASS','suites':names},indent=2)+'\n')
print('COMPLETE',mode,flush=True)
