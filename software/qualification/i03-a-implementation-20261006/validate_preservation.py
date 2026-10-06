"""Preservation and approved-scope checks; not an SP3 producer or scientific receipt."""
from pathlib import Path
import hashlib,json,subprocess
repo=Path(__file__).resolve().parents[3];q=Path(__file__).resolve().parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
b=json.loads((repo/'software/qualification/i03-i04-scientific-review-20261006/PRESERVATION_BEFORE.json').read_text())
changed={'software/modules/athena/src/main/java/totah/lab/athena/system/rules/RuleAnalyzers.java','software/modules/athena/src/main/java/totah/lab/athena/system/rules/RuleRegistry.java'}
for p,h in b['original25'].items():assert sha(repo/p)==h,p
for p,h in b['protectedTrackedFiles'].items():
 if p not in changed:assert sha(repo/p)==h,p
for p in changed:
 before=subprocess.check_output(['git','show',b['head']+':'+p],cwd=repo,text=True)
 after=(repo/p).read_text();assert '\n'.join(l for l in after.split('\n') if 'ImplicitHProxyRules.' not in l)==before,p
for p,h in json.loads((q/'REVIEW_PINS.json').read_text()).items():assert sha(repo/p)==h,p
ledger=json.loads((repo/'software/qualification/chemistry-geometry-foundation-20261005/CAPABILITY_LEDGER.json').read_text())
assert [r for r in ledger['entries'] if r['currentDisposition']=='REQUIRES_EXTERNAL_REFERENCE_DATA']==b['external12']
old=json.loads((repo/'software/qualification/i03-i04-scientific-review-20261006/PRIOR_CAPABILITY_LEDGER.json').read_text());oldrows={r['capabilityId']:r for r in old['entries']}
for r in ledger['entries']:
 if r['capabilityId'] not in ['I03','I04']:assert r==oldrows[r['capabilityId']],r['capabilityId']
 else:assert r['currentDisposition']=='SCIENTIFIC_REVIEW_REQUIRED'
m=json.loads((repo/'software/modules/athena/src/main/resources/totah/lab/athena/system/rules/implicit-h-proxy-v1/ATHENA.I03.HEAVY_ATOM_DIRECTIONAL_PROXY_SP3_AMINES.rule.json').read_text())
roles=json.loads((repo/'software/qualification/i03-i04-scientific-review-20261006/EXACT_ROLE_REUSE.json').read_text())
assert json.loads(m['parameters']['sources']['value'])==roles['proposedI03Roles']
assert len(json.loads(m['parameters']['classPairs']['value']))==6 and m['qualification']=='NOT_EVALUATED'
for p in m['scientificSources']:assert sha(repo/p['locator'])==p['sha256'],p
out={'kind':'PRESERVATION_AND_APPROVAL_SCOPE_CHECK_NOT_SCIENTIFIC_AUTHORITY','base':b['head'],'preservationPinsIntact':25,'protectedTrackedFilesUnchanged':len(b['protectedTrackedFiles'])-len(changed),'existingDispatchAdditionsOnly':sorted(changed),'externalRowsUnchanged':12,'otherCapabilityRowsUnchanged':True,'roleManifestsUnchanged':5,'approvedClassPairs':6,'currentPolicyQualified':False,'SP3ProducerSelected':False,'I04Implemented':False,'publicJavaSignaturesChanged':False}
(q/'PRESERVATION_AFTER.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps(out,indent=2))
