"""Serialized Java21 M15 validation. Never overwrites frozen milestones or completed cases."""
from pathlib import Path
import argparse,json,os,subprocess,time,sys,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('cases',nargs='+');p.add_argument('--output',default='runs');p.add_argument('--keep-going',action='store_true');a=p.parse_args()
out=root/'validation/milestone-15'/a.output;out.mkdir(parents=True,exist_ok=True)
xml=root/'target/surefire-reports/TEST-totah.lab.aether.matrix.AetherPbeTest.xml'
props={x.attrib['name']:x.attrib['value'] for x in ET.parse(xml).getroot().find('properties')}
failures=[]
for case in a.cases:
 name,variant,nr,na=case.split('-');record=out/(case+'.json')
 if record.exists():raise SystemExit('Refusing completed case overwrite '+case)
 command=[str(Path(props['java.home'])/'bin/java'),'-Xmx512m','-cp',props['java.class.path'],'totah.lab.aether.PbeProbe',name,variant,nr,na,str(out),str(root/'validation/milestone-15/cache')]
 print('START',case,flush=True);t=time.perf_counter()
 with (out/(case+'.log')).open('w') as f:
  child=subprocess.Popen(command,stdout=f,stderr=subprocess.STDOUT);_,status,usage=os.wait4(child.pid,0);code=os.waitstatus_to_exitcode(status);child.returncode=code
 record.write_text(json.dumps({'case':case,'exit_code':code,'elapsed_seconds':time.perf_counter()-t,'peak_rss_bytes':usage.ru_maxrss if sys.platform=='darwin' else usage.ru_maxrss*1024,'platform':sys.platform,'heap':'512m','command':command,'scientific_status':'SCREENING_ONLY'},indent=2)+'\n')
 print('FINISH',case,code,flush=True)
 if code:
  failures.append(case)
  if not a.keep_going:raise SystemExit(code)
if failures:raise SystemExit('Failed cases: '+', '.join(failures))
