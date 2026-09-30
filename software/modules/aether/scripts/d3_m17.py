"""Evaluate physical-only dispersion even when electronic SCF fails."""
from pathlib import Path
import hashlib,json,subprocess,sys,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'validation/milestone-17'
replay='--replay' in sys.argv;checks={}
props={x.attrib['name']:x.attrib['value'] for x in ET.parse(ROOT/'target/surefire-reports/TEST-totah.lab.aether.AetherD3Test.xml').getroot().find('properties')}
for name,spec in json.loads((OUT/'benchmark.json').read_text()).items():
    fixture=ROOT/'src/test/resources/totah/lab/aether/reference/m17'/f'{name}.atoms'
    assert hashlib.sha256(fixture.read_bytes()).hexdigest()==spec['fixture_sha256']
    dest=OUT/('replay-d3' if replay else 'dispersion')/name
    if not (dest/'dispersion.txt').exists():
        subprocess.run([props['java.home']+'/bin/java','-Xmx128m','-cp',props['java.class.path'],
            'totah.lab.aether.matrix.M17InteractionProbe',str(fixture),str(dest),
            str(OUT/'transient-cache/unused'),'D3_ONLY'],check=True)
    if replay:checks[name]=dest.joinpath('dispersion.txt').read_bytes()==(OUT/'dispersion'/name/'dispersion.txt').read_bytes()
if replay:
    (OUT/'replay-d3/result.json').write_text(json.dumps({'status':'PASS' if all(checks.values()) and len(checks)==17 else 'FAIL',
        'checks':checks,'scientific_status':'SCREENING_ONLY'},indent=2)+'\n')
print('Physical-only dispersion complete')
