"""Fresh-JVM replay of full RHF/PBE/D3 counterpoise evidence; bounded heap."""
from pathlib import Path
import hashlib,json,shutil,subprocess,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'validation/milestone-17'
name='nenci_001';target=OUT/'replay';target.mkdir(exist_ok=True)
props={x.attrib['name']:x.attrib['value'] for x in ET.parse(ROOT/'target/surefire-reports/TEST-totah.lab.aether.AetherD3Test.xml').getroot().find('properties')}
fixture=ROOT/'src/test/resources/totah/lab/aether/reference/m17'/f'{name}.atoms'
cache=OUT/'transient-cache/replay';cache.mkdir(parents=True,exist_ok=True)
cmd=[props['java.home']+'/bin/java','-Xmx512m','-cp',props['java.class.path'],
     'totah.lab.aether.matrix.M17InteractionProbe',str(fixture),str(target),str(cache)]
with (target/'run.log').open('w') as log:subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,check=True)
old=OUT/'runs'/name;names=[p.name for p in old.glob('*.receipt')]+['interaction.txt']
checks={n:old.joinpath(n).read_bytes()==target.joinpath(n).read_bytes() for n in names}
result={'status':'PASS' if all(checks.values()) and len(checks)==7 else 'FAIL',
        'fresh_java_processes':2,'checks':checks,
        'sha256':{n:hashlib.sha256(target.joinpath(n).read_bytes()).hexdigest() for n in names},
        'scientific_status':'SCREENING_ONLY'}
(target/'result.json').write_text(json.dumps(result,indent=2)+'\n')
shutil.rmtree(cache);print(result['status'])
