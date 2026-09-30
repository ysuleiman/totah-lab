"""Serial memory gates. Run only after existing validation workers have exited."""
from pathlib import Path
import argparse,json,os,subprocess,sys,time,shutil,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--phase',choices=['compatibility','regression','replay','components','evidence','large'],required=True)
p.add_argument('--system',help='One component/large system; omission runs that phase serially')
a=p.parse_args()
env=os.environ.copy();env['JAVA_HOME']=str(a.java_home)
java=str(a.java_home/'bin/java')
version=subprocess.check_output([java,'-version'],stderr=subprocess.STDOUT,text=True)
if 'version "21' not in version:sys.exit('Java 21 required')
out=ROOT/'validation/milestone-13/memory';out.mkdir(parents=True,exist_ok=True)
base=['mvn','-o','-q','-f',str(ROOT.parent/'pom.xml'),'-pl','aether','-am']
surefire='org.apache.maven.plugins:maven-surefire-plugin:3.5.2:test'

def run(label,cmd,extra_env=None):
    current=env.copy();current.update(extra_env or {})
    record={'command':cmd,'java_version':version.strip(),'environment_overrides':extra_env or {}}
    print('START',label,flush=True)
    with (out/(label+'.log')).open('w') as stdout,(out/(label+'.stderr')).open('w') as stderr:
        with subprocess.Popen(cmd,cwd=ROOT,env=current,stdout=stdout,stderr=stderr) as result:
            _,status,usage=os.wait4(result.pid,0)
            result.returncode=os.waitstatus_to_exitcode(status)
    record['exit_code']=result.returncode
    # macOS time -l exits nonzero when sandboxed sysctl is denied, even if the child passes.
    record['peak_rss_bytes']=int(usage.ru_maxrss)
    record['peak_rss_scope']='OS wait4 resource high-water mark for the command process and reaped descendants; not simultaneous aggregate RSS'
    (out/(label+'.json')).write_text(json.dumps(record,indent=2)+'\n')
    print('FINISH',label,result.returncode,flush=True)
    if result.returncode:sys.exit(result.returncode)

def test(label,selection,heap,properties=(),compile=False,extra_env=None):
    started=time.time()
    diagnostics=out/(label+'-child-diagnostics');diagnostics.mkdir(exist_ok=True)
    run(label,[*base,'test' if compile else surefire,'-Dtest='+selection,
        '-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx'+heap,
        '-DforkCount=1','-Djunit.jupiter.execution.parallel.enabled=false',
        '-Djunit.jupiter.tempdir.cleanup.mode.default=NEVER','-Djava.io.tmpdir='+str(diagnostics),*properties],extra_env)
    wanted=set(selection.split(','));seen=set();counts={'tests':0,'failures':0,'errors':0,'skipped':0}
    snapshot=out/(label+'-junit');snapshot.mkdir(exist_ok=True)
    for report in (ROOT/'target/surefire-reports').glob('TEST-*.xml'):
        root=ET.parse(report).getroot();name=root.get('name','').split('.')[-1]
        if name not in wanted or report.stat().st_mtime<started:continue
        seen.add(name);shutil.copy2(report,snapshot/report.name)
        for key in counts:counts[key]+=int(root.get(key,'0'))
    if seen!=wanted:sys.exit('Missing fresh JUnit evidence: '+str(wanted-seen))
    (snapshot/'counts.json').write_text(json.dumps(counts,indent=2)+'\n')

if a.phase=='compatibility':
    test('canonical-compatibility','AetherMemoryHashCompatibilityTest,AetherIncrementalHashTest','512m',compile=True)
elif a.phase=='regression':
    names=sorted(path.stem for path in (ROOT/'src/test/java').rglob('*Test.java')
        if not path.stem.startswith(('AetherDef2','AetherMemory','AetherIncremental')))
    test('serial-historical-regression',','.join(names),'1g',extra_env={'JAVA_TOOL_OPTIONS':'-Xmx768m'})
elif a.phase=='replay':
    test('def2-replay','AetherDef2ReplayTest','512m')
elif a.phase=='components':
    reports=list((ROOT/'target/surefire-reports').glob('TEST-*.xml'))
    cp=None
    for report in reports:
        for prop in ET.parse(report).findall('./properties/property'):
            if prop.get('name')=='java.class.path':cp=prop.get('value');break
        if cp:break
    if not cp:sys.exit('Run compatibility compilation first to establish the test classpath')
    # Budgets fixed from raw ERI/AO storage plus young generation and bounded headroom.
    budgets={'h2o':'128m','dms':'640m','trimethylsulfonium':'1280m','chlorobenzene':'1536m',
             'water_dimer':'128m','ammonium_benzene':'768m'}
    for name in ([a.system] if a.system else budgets):
        run('components-'+name,[java,'-Xmx'+budgets[name],'-XX:+UseSerialGC','-Xmn32m','-cp',cp,
            'totah.lab.aether.MemoryGateProbe',name])
elif a.phase=='evidence':
    budgets={'h2o':'512m','dms':'1g','trimethylsulfonium':'1536m','water_dimer':'512m'}
    for name in ([a.system] if a.system else budgets):
        if name=='water_dimer':
            test('water-dimer-cp-xmx512m','AetherDef2CounterpoiseTest',budgets[name],['-Daether.def2.dimer='+name])
        else:
            test(name+'-energy-retained-arrays','AetherDef2EnergyTest',budgets[name],['-Daether.def2.system='+name])
elif a.phase=='large':
    for name in ([a.system] if a.system else ['chlorobenzene','ammonium_benzene']):
        if name=='chlorobenzene':
            test('chlorobenzene-lda-xmx2g','AetherDef2EnergyTest','2g',
                 ['-Daether.def2.system=chlorobenzene','-Daether.def2.method=LDA'])
        elif name=='ammonium_benzene':
            test('ammonium-benzene-cp-xmx2g','AetherDef2CounterpoiseTest','2g',
                 ['-Daether.def2.dimer=ammonium_benzene'])
        else:sys.exit('Unknown large gate '+name)
