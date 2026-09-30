"""Two fresh Java 21 JVMs must produce byte-identical ghost-state receipts."""
from pathlib import Path
import xml.etree.ElementTree as ET,subprocess,json
r=Path(__file__).resolve().parents[1];out=r/'validation/milestone-16/replay-cp';out.mkdir(exist_ok=True)
p={x.attrib['name']:x.attrib['value'] for x in ET.parse(r/'target/surefire-reports/TEST-totah.lab.aether.AetherD3Test.xml').getroot().find('properties')}
for i in [1,2]:
 dest=out/str(i);dest.mkdir(exist_ok=False)
 cmd=[p['java.home']+'/bin/java','-Xmx512m','-cp',p['java.class.path'],'totah.lab.aether.D3CpProbe','water_dimer',str(dest),str(r/'validation/milestone-16/cache')]
 with (out/f'{i}.log').open('w') as f:subprocess.run(cmd,stdout=f,stderr=subprocess.STDOUT,check=True)
for role in ['A_GHOST_B','B_GHOST_A']:
 for suffix in ['receipt','txt']:
  name=f'water_dimer-{role}.{suffix}'
  assert (out/'1'/name).read_bytes()==(out/'2'/name).read_bytes()
(out/'result.json').write_text(json.dumps({'GHOST_PBE_D3_FRESH_JVM_REPLAY':'PASS','system':'water_dimer','ghost_components':2,'heap':'512m','status':'SCREENING_ONLY'},indent=2)+'\n')
print('Ghost PBE+D3 replay PASS')
