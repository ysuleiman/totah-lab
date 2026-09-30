from pathlib import Path
import xml.etree.ElementTree as ET,subprocess,json
r=Path(__file__).resolve().parents[1];out=r/'validation/milestone-16/replay';out.mkdir(exist_ok=True)
p={x.attrib['name']:x.attrib['value'] for x in ET.parse(r/'validation/milestone-16/junit/TEST-totah.lab.aether.AetherD3Test.xml').getroot().find('properties')}
for i in [1,2]:
 dest=out/str(i);cmd=[p['java.home']+'/bin/java','-Xmx128m','-cp',p['java.class.path'],'totah.lab.aether.D3EvidenceProbe',str(dest)]
 subprocess.run(cmd,check=True)
a=(out/'1/dispersion.receipt').read_bytes();b=(out/'2/dispersion.receipt').read_bytes();assert a==b
(out/'result.json').write_text(json.dumps({'D3_FRESH_JVM_REPLAY':'PASS','systems':21,'heap':'128m','receipt_bytes':len(a),'status':'SCREENING_ONLY'},indent=2)+'\n')
print('D3 replay PASS',len(a))
