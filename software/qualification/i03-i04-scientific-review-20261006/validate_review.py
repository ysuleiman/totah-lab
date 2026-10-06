"""Review-package integrity only. This does not execute or qualify I03/I04 science."""
from pathlib import Path
import hashlib,json,subprocess,collections
repo=Path(__file__).resolve().parents[3];q=Path(__file__).resolve().parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
b=json.loads((q/'PRESERVATION_BEFORE.json').read_text())
for group in ['original25','protectedTrackedFiles']:
 for p,h in b[group].items():assert sha(repo/p)==h,p
l=json.loads((repo/'software/qualification/chemistry-geometry-foundation-20261005/CAPABILITY_LEDGER.json').read_text())
external=[r for r in l['entries'] if r['currentDisposition']=='REQUIRES_EXTERNAL_REFERENCE_DATA'];assert external==b['external12'] and len(external)==12
old=json.loads((q/'PRIOR_CAPABILITY_LEDGER.json').read_text());oldrows={e['capabilityId']:e for e in old['entries']}
for r in l['entries']:
 if r['capabilityId'] not in ['I03','I04']:assert r==oldrows[r['capabilityId']],r['capabilityId']
 else:assert r['currentDisposition']==oldrows[r['capabilityId']]['currentDisposition']=='SCIENTIFIC_REVIEW_REQUIRED'
for p,h in json.loads((q/'SOURCE_PINS.json').read_text()).items():assert sha(repo/p)==h,p
matrix=json.loads((q/'ACCEPTANCE_MATRICES.json').read_text());assert len(matrix['cases'])==60 and len({r['id'] for r in matrix['cases']})==60
assert all(r['execution']=='NOT_EXECUTED_REVIEW_SPECIFICATION' for r in matrix['cases'])
roledata=json.loads((q/'EXACT_ROLE_REUSE.json').read_text());m=json.loads((repo/'software/modules/athena/src/main/resources/totah/lab/athena/system/rules/hbond-candidate-v1/ATHENA.HBOND.EXPLICIT_H_DIRECTIONAL_CANDIDATE.rule.json').read_text());sources=json.loads(m['parameters']['sources']['value'])
for k,v in roledata['proposedI03Roles'].items():assert sources[k]==v,k
assert len(roledata['proposedI03Roles'])==5 and roledata['I04ApprovedWeakDonorClasses']==[]
subprocess.run(['python3',str(repo/'docs/manual/athena/render_reference.py'),'--check'],cwd=repo,check=True)
head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip();assert head==b['head']
result={'kind':'REVIEW_DOCUMENT_AND_PRESERVATION_VALIDATION_NOT_SCIENTIFIC_QUALIFICATION','headUnchanged':head,'protectedTrackedFilesUnchanged':len(b['protectedTrackedFiles']),'preservationPinsIntact':25,'externalRowsUnchanged':12,'allOtherLedgerRowsUnchanged':True,'sourcePinsIntact':len(json.loads((q/'SOURCE_PINS.json').read_text())),'unchangedReusedRoleManifests':5,'prospectiveI03Cases':40,'prospectiveI04Cases':20,'scienceTestsExecuted':0,'implementedCapabilities':0,'publicApiOrSchemaChanges':0,'productionReceiptsIssued':0,'counts':dict(collections.Counter(r['currentDisposition'] for r in l['entries']))}
(q/'REVIEW_VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
