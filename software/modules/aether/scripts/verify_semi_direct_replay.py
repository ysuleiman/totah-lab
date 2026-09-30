"""Separate-JVM cold-generation replay; run serially after large benchmarks."""
from pathlib import Path
import hashlib,json,subprocess,xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
out=root/'validation/milestone-14.1/cold-replay'
out.mkdir(exist_ok=False)
xml=root/'target/surefire-reports/TEST-totah.lab.aether.AetherSemiDirectWorkflowTest.xml'
props={x.attrib['name']:x.attrib['value'] for x in ET.parse(xml).getroot().find('properties')}
java=str(Path(props['java.home'])/'bin/java')
def sha(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
results=[]
for mode,name in [('RHF','h2o'),('CP','water_dimer')]:
    inventories=[];receipts=[]
    for run in range(2):
        directory=out/f'{mode}-{run}';cache=directory/'cache'
        directory.mkdir()
        command=[java,'-Xmx512m','-cp',props['java.class.path'],
                 'totah.lab.aether.SemiDirectProbe',mode,name,str(directory),str(cache)]
        with (directory/'run.log').open('w') as log:
            subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,check=True)
        receipts.append(sha(directory/f'{name}-{mode}.receipt'))
        inventories.append({str(p.relative_to(cache)):sha(p)
                            for p in sorted(cache.glob('*/*')) if p.is_file()})
    assert receipts[0]==receipts[1],(mode,'receipt mismatch')
    assert inventories[0]==inventories[1],(mode,'cache byte mismatch')
    results.append({'mode':mode,'system':name,'receipt_sha256':receipts[0],
                    'cache_files_sha256':inventories[0],
                    'fresh_JVM_receipts_identical':True,'independent_cache_bytes_identical':True})
(out/'result.json').write_text(json.dumps({'status':'PASS','scientific_status':'SCREENING_ONLY',
                                        'heap':'512m','results':results},indent=2)+'\n')
print('Independent cold-generation JVM replay PASS')
