"""Serial native ghost components; reuse immutable full-basis ERI caches."""
from pathlib import Path
import subprocess,sys,time,json,os,xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1];out=r/'validation/milestone-16/cp-runs';out.mkdir(exist_ok=True)
args=sys.argv[1:]
if args and args[0]=='--after':
 marker=Path(args[1]);args=args[2:];deadline=time.monotonic()+21600
 print('Waiting for preceding native benchmark sequence',marker,flush=True)
 while not marker.exists():
  if time.monotonic()>deadline:raise RuntimeError('Preceding native sequence did not finish within six hours')
  time.sleep(5)
p={x.attrib['name']:x.attrib['value'] for x in ET.parse(r/'target/surefire-reports/TEST-totah.lab.aether.AetherD3Test.xml').getroot().find('properties')}
for name in args:
 dest=out/f'{name}.json'
 if dest.exists():raise RuntimeError('Refusing overwrite '+str(dest))
 cmd=[p['java.home']+'/bin/java','-Xmx512m','-cp',p['java.class.path'],'totah.lab.aether.D3CpProbe',name,str(out),str(r/'validation/milestone-16/cache')]
 print('START',name,flush=True);t=time.perf_counter()
 with (out/f'{name}.log').open('w') as f:
  child=subprocess.Popen(cmd,stdout=f,stderr=subprocess.STDOUT);_,status,usage=os.wait4(child.pid,0);code=os.waitstatus_to_exitcode(status);child.returncode=code
 dest.write_text(json.dumps({'system':name,'exit_code':code,'elapsed_seconds':time.perf_counter()-t,'peak_rss_bytes':usage.ru_maxrss,'heap':'512m','command':cmd,'status':'SCREENING_ONLY'},indent=2)+'\n')
 print('FINISH',name,code,flush=True)
