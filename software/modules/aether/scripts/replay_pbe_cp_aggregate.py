"""Fresh-JVM replay includes the typed three-component CP assembly receipt."""
from pathlib import Path
import xml.etree.ElementTree as ET,subprocess,json
r=Path(__file__).resolve().parents[1];out=r/'validation/milestone-16/replay-cp-aggregate';out.mkdir(exist_ok=True)
p={x.attrib['name']:x.attrib['value'] for x in ET.parse(r/'target/surefire-reports/TEST-totah.lab.aether.AetherD3Test.xml').getroot().find('properties')}
for i in [1,2]:
 dest=out/str(i);dest.mkdir(exist_ok=False)
 cmd=[p['java.home']+'/bin/java','-Xmx512m','-cp',p['java.class.path'],'totah.lab.aether.D3CpProbe','water_dimer',str(dest),str(r/'validation/milestone-16/cache'),'aggregate']
 with (out/f'{i}.log').open('w') as f:subprocess.run(cmd,stdout=f,stderr=subprocess.STDOUT,check=True)
for first in (out/'1').iterdir():assert first.read_bytes()==(out/'2'/first.name).read_bytes()
(out/'result.json').write_text(json.dumps({'TYPED_CP_AGGREGATE_FRESH_JVM_REPLAY':'PASS','system':'water_dimer','heap':'512m','status':'SCREENING_ONLY'},indent=2)+'\n')
print('Typed PBE+D3 CP aggregate replay PASS')
