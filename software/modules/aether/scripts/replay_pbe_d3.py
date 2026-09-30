from pathlib import Path
import xml.etree.ElementTree as ET,subprocess,json,sys
r=Path(__file__).resolve().parents[1];out=r/'validation/milestone-16'/(sys.argv[1] if len(sys.argv)>1 else 'replay-pbe');out.mkdir(exist_ok=True)
p={x.attrib['name']:x.attrib['value'] for x in ET.parse(r/'validation/milestone-16/junit/TEST-totah.lab.aether.AetherD3Test.xml').getroot().find('properties')}
for i in [1,2]:
 dest=out/str(i);cmd=[p['java.home']+'/bin/java','-Xmx512m','-cp',p['java.class.path'],'totah.lab.aether.D3PbeProbe','h2o',str(dest),str(r/'validation/milestone-16/cache')]
 with (out/(str(i)+'.log')).open('w') as f:subprocess.run(cmd,stdout=f,stderr=subprocess.STDOUT,check=True)
def content(folder):
 text=(folder/'h2o.txt').read_text();return '\n'.join(x for x in text.splitlines() if 'Nanos=' not in x)
assert (out/'1/h2o.receipt').read_bytes()==(out/'2/h2o.receipt').read_bytes()==(r/'validation/milestone-16/runs/h2o.receipt').read_bytes()
assert content(out/'1')==content(out/'2')==content(r/'validation/milestone-16/runs')
(out/'result.json').write_text(json.dumps({'PBE_D3_FRESH_JVM_REPLAY':'PASS','system':'h2o','baseline_receipt_unchanged':True,'status':'SCREENING_ONLY'},indent=2)+'\n');print('PBE+D3 replay PASS')
