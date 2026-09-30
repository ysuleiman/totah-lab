"""Serial fresh reference generation. Existing M15 outputs are verified and reused."""
from pathlib import Path
import subprocess,sys,hashlib
root=Path(__file__).resolve().parents[1];out=root/'validation/milestone-15/reference-logs';out.mkdir(exist_ok=True)
ref=root/'src/test/resources/totah/lab/aether/reference/pbe15'
names='h2 h2o nh3 ch4 co n2 h2s ph3 hcl ch3cl dms trimethylsulfonium chlorobenzene'.split()
cases=[(n,'native',120,590) for n in names]
for n in ['h2o','ch4','h2s']:
 for nr,na in [(40,110),(80,302),(160,974)]:cases.append((n,'native',nr,na))
for n in ['h2o','ch4','nh3','h2s','ph3','hcl']:
 for variant in ['translated','rotated']:cases.append((n,variant,120,590))
for case in cases:
 stem='-'.join(map(str,case));csv=ref/(stem+'.csv')
 if csv.exists():
  assert hashlib.sha256(csv.read_bytes()).hexdigest()==(ref/(stem+'.sha256')).read_text().strip()
  ao=ref/(stem+'-ao.csv');assert hashlib.sha256(ao.read_bytes()).hexdigest()==(ref/(stem+'-ao.sha256')).read_text().strip()
  assert (ref/(stem+'.json')).exists()
  print('EXISTING',stem,flush=True);continue
 print('START',stem,flush=True)
 with (out/(stem+'.log')).open('w') as log:
  subprocess.run([sys.executable,str(root/'scripts/generate_pbe_reference.py'),*map(str,case)],stdout=log,stderr=subprocess.STDOUT,check=True)
 print('FINISH',stem,flush=True)
