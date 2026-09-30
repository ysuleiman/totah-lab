"""M16 serialized native PBE+D3 benchmark; no frozen evidence writes."""
from pathlib import Path
import subprocess,time,sys,os,json,xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1];out=r/'validation/milestone-16/runs';out.mkdir(exist_ok=True)
cache=r/'validation/milestone-16/cache';cache.mkdir(exist_ok=True)
# Verified immutable cache payloads can be reused without modifying old cache directories.
for milestone in ['milestone-14.1','milestone-15']:
 for directory in (r/'validation'/milestone/'cache').iterdir():
  if not directory.is_dir() or '.partial-' in directory.name:continue
  dest=cache/directory.name;dest.mkdir(exist_ok=True)
  for src in directory.iterdir():
   if src.is_file() and not (dest/src.name).exists():os.link(src,dest/src.name)
xml=r/'target/surefire-reports/TEST-totah.lab.aether.AetherD3Test.xml'
p={x.attrib['name']:x.attrib['value'] for x in ET.parse(xml).getroot().find('properties')}
for name in sys.argv[1:]:
 path=out/(name+'.json')
 if path.exists():raise SystemExit('Refusing overwrite '+str(path))
 cmd=[p['java.home']+'/bin/java','-Xmx512m','-cp',p['java.class.path'],'totah.lab.aether.D3PbeProbe',name,str(out),str(cache)]
 print('START',name,flush=True);t=time.perf_counter()
 with (out/(name+'.log')).open('w') as f:
  child=subprocess.Popen(cmd,stdout=f,stderr=subprocess.STDOUT);_,status,usage=os.wait4(child.pid,0);code=os.waitstatus_to_exitcode(status);child.returncode=code
 path.write_text(json.dumps({'system':name,'exit_code':code,'elapsed_seconds':time.perf_counter()-t,'peak_rss_bytes':usage.ru_maxrss,'heap':'512m','command':cmd,'status':'SCREENING_ONLY'},indent=2)+'\n')
 print('FINISH',name,code,flush=True)
