"""Serial, bounded M14.1 runs. Frozen M14 outputs are read-only baselines."""
from pathlib import Path
import argparse,json,os,subprocess,time,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--mode',required=True);p.add_argument('--systems',nargs='+',required=True);a=p.parse_args()
out=root/'validation/milestone-14.1';out.mkdir(exist_ok=True)
xml=root/'target/surefire-reports/TEST-totah.lab.aether.AetherSemiDirectWorkflowTest.xml'
props={x.attrib['name']:x.attrib['value'] for x in ET.parse(xml).getroot().find('properties')}
cp=props['java.class.path'];java=str(Path(props['java.home'])/'bin/java')
for name in a.systems:
 stem=f'{a.mode}-{name}'
 record=out/(stem+'.json')
 if record.exists():raise SystemExit('Refusing to overwrite completed benchmark: '+str(record))
 command=[java,'-Xmx512m','-cp',cp,'totah.lab.aether.SemiDirectProbe',a.mode,name,str(out),str(out/'cache')]
 print('START',stem,flush=True);start=time.perf_counter()
 with (out/(stem+'.log')).open('w') as log:
  process=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT)
  _,status,usage=os.wait4(process.pid,0);code=os.waitstatus_to_exitcode(status);process.returncode=code
 elapsed=time.perf_counter()-start
 record.write_text(json.dumps({'command':command,'exit_code':code,'elapsed_seconds':elapsed,'peak_rss_bytes':usage.ru_maxrss,
  'user_cpu_seconds':usage.ru_utime,'system_cpu_seconds':usage.ru_stime,'heap':'512m','os_cache_state':'NOT_FORCIBLY_FLUSHED',
  'scientific_status':'SCREENING_ONLY'},indent=2)+'\n')
 print('FINISH',stem,code,elapsed,flush=True)
 if code:raise SystemExit(code)
