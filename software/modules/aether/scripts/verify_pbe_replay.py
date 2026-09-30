"""M15 fresh-JVM deterministic replay, serial and bounded to 512 MiB."""
from pathlib import Path
import hashlib,json,subprocess,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1];out=root/'validation/milestone-15/replay';out.mkdir(exist_ok=False)
props={x.attrib['name']:x.attrib['value'] for x in ET.parse(root/'target/surefire-reports/TEST-totah.lab.aether.matrix.AetherPbeTest.xml').getroot().find('properties')}
results=[]
for name in ['h2o','n2','h2s']:
 receipts=[]
 for run in range(2):
  target=out/f'{name}-{run}';target.mkdir();cmd=[str(Path(props['java.home'])/'bin/java'),'-Xmx512m','-cp',props['java.class.path'],'totah.lab.aether.PbeProbe',name,'native','120','590',str(target),str(out/'cache')]
  with (target/'run.log').open('w') as log:subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,check=True)
  receipts.append((target/f'{name}-native-120-590.receipt').read_bytes())
 assert receipts[0]==receipts[1],name
 assert receipts[0]==(out.parent/'runs'/f'{name}-native-120-590.receipt').read_bytes(),name+' benchmark replay'
 results.append({'system':name,'byte_identical':True,'sha256':hashlib.sha256(receipts[0]).hexdigest()})
(out/'result.json').write_text(json.dumps({'status':'PASS','scientific_status':'SCREENING_ONLY','results':results},indent=2)+'\n');print('PBE fresh-JVM replay PASS')
