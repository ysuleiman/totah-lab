"""Two fresh upstream fixture processes; no Athena implementation qualification."""
import sys
sys.dont_write_bytecode=True
from pathlib import Path
import subprocess,hashlib,json
root=Path(__file__).resolve().parent
runs=[]
for i in range(2):
 r=subprocess.run([sys.executable,str(root/'make_fixtures.py')],capture_output=True,text=True,check=True)
 b=(root/'UPSTREAM_FIXTURES.jsonl.gz').read_bytes()
 runs.append({'process':i+1,'exitCode':r.returncode,'stdout':json.loads(r.stdout),'sha256':hashlib.sha256(b).hexdigest()})
assert runs[0]['sha256']==runs[1]['sha256']
(root/'REPLAY.json').write_text(json.dumps({'status':'BYTE_IDENTICAL_INDEPENDENT_UPSTREAM_PROCESSES','runs':runs},indent=2)+'\n')
print(json.dumps({'status':'PASS','records':runs[0]['stdout']['records'],'sha256':runs[0]['sha256']}))
