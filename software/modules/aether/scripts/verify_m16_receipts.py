"""Compare historical/non-ghost receipts without changing any reference file."""
from pathlib import Path
import hashlib,json
r=Path(__file__).resolve().parents[1];out=r/'validation/milestone-16'
pairs=[
 ('water_non_ghost_post_API',out/'runs/h2o.receipt',out/'replay-pbe-post-api/1/h2o.receipt'),
 ('water_fresh_JVMs',out/'replay-pbe-post-api/1/h2o.receipt',out/'replay-pbe-post-api/2/h2o.receipt'),
 ('DMS_historical',r/'validation/milestone-15/runs/dms-native-120-590.receipt',out/'runs/dms.receipt'),
 ('chlorobenzene_historical',r/'validation/milestone-15/runs/chlorobenzene-native-120-590.receipt',out/'runs/chlorobenzene.receipt'),
 ('chlorobenzene_non_ghost_post_API',out/'runs/chlorobenzene.receipt',out/'runs/chlorobenzene_water-A.receipt')]
rows=[]
for name,a,b in pairs:
 result={'name':name,'before':str(a),'after':str(b)}
 if not a.exists() or not b.exists():result['result']='PENDING'
 else:
  x=a.read_bytes();y=b.read_bytes();result.update(result='PASS' if x==y else 'FAIL',before_sha256=hashlib.sha256(x).hexdigest(),after_sha256=hashlib.sha256(y).hexdigest())
 rows.append(result)
status='PASS' if all(x['result']=='PASS' for x in rows) else ('FAIL' if any(x['result']=='FAIL' for x in rows) else 'PENDING')
(out/'non-ghost-receipt-regression.json').write_text(json.dumps({'result':status,'comparisons':rows,'status':'SCREENING_ONLY'},indent=2)+'\n')
print('Non-ghost historical receipt comparisons:',status)
