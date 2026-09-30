"""Audit saved CP iterations against real-system occupation and nuclear repulsion."""
from pathlib import Path
import csv,json,re
r=Path(__file__).resolve().parents[1];out=r/'validation/milestone-16';rows=[]
with (out/'ghost-physical-expectations.csv').open() as f:expected=list(csv.DictReader(f))
for e in expected:
 stem=e['system']+'-'+e['role'];receipt=out/'cp-runs'/f'{stem}.receipt';values=out/'cp-runs'/f'{stem}.txt';reference=out/'cp-reference'/f'{stem}.json'
 if not all(p.exists() for p in [receipt,values,reference]):continue
 text=receipt.read_text();traces=[float(x) for x in re.findall(r'tracePS=([^,\]]+)',text)];hashes=re.findall(r'occupationHash=([0-9a-f]{64})',text)
 iterations=len(re.findall(r'Iteration\[number=',text));v=dict(line.split('=',1) for line in values.read_text().splitlines() if '=' in line);ref=json.loads(reference.read_text())
 ne=int(e['electrons']);trace_error=max((abs(x-ne) for x in traces),default=float('inf'))
 nuc_native=abs(float(v['ENUC'])-float(e['nuclear_repulsion']));nuc_ref=abs(float(v['ENUC'])-ref['ENUC'])
 passed=iterations>0 and len(traces)==iterations and len(hashes)==iterations and all(x==e['occupation_hash'] for x in hashes) and trace_error<=1e-10 and nuc_native==0 and nuc_ref<1e-10 and ref['electrons']==ne and int(e['occupied_orbitals'])*2==ne and len(ref['real_atoms_bohr'])==int(e['real_atoms']) and len(ref['ghost_basis_centers_bohr'])==int(e['ghost_centers'])
 rows.append({'system':e['system'],'role':e['role'],'result':'PASS' if passed else 'FAIL','real_atoms':int(e['real_atoms']),'ghost_basis_centers':int(e['ghost_centers']),'electrons':ne,'occupied_orbitals':int(e['occupied_orbitals']),'iterations_checked':iterations,'max_trace_error':trace_error,'nuclear_repulsion_native_error':nuc_native,'nuclear_repulsion_reference_error':nuc_ref,'occupation_hash_matches_every_iteration':all(x==e['occupation_hash'] for x in hashes)})
status='FAIL' if any(x['result']=='FAIL' for x in rows) else ('PASS' if len(rows)==12 else 'PENDING')
(out/'ghost-physical-validation.json').write_text(json.dumps({'result':status,'components':rows,'status':'SCREENING_ONLY'},indent=2)+'\n')
print('Ghost physical-system audit:',status,len(rows),'/ 12 components')
