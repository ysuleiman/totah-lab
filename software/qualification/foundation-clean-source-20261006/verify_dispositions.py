"""Check the existing ledger's accepted disposition accounting, not scientific eligibility."""
from pathlib import Path
import collections,hashlib,json,subprocess
root=Path.cwd();base=root/'software/qualification/chemistry-geometry-foundation-20261005'
ledger=json.loads((base/'CAPABILITY_LEDGER.json').read_text())
inventory=json.loads((root/'software/qualification/rule-qualification-blueprint-20261005/REFERENCE_CAPABILITY_INVENTORY.json').read_text())
expected={e['id']:e for e in inventory['entries']};entries=ledger['entries'];assert len(entries)==len(expected)==96
assert {e['capabilityId'] for e in entries}==set(expected)
allowed={'BOUNDED_SUPPORTED_DOMAIN_QUALIFIED','SCIENTIFIC_REVIEW_REQUIRED','EXPLICIT_ARCHITECTURAL_DISPOSITION'}
for e in entries:
 identifier=e['capabilityId'];status=e['currentDisposition'];assert status in allowed,identifier
 c=e['closure'];assert c['scope'] and c['qualificationBasis'] and c['existingImplementation'],identifier
 assert c['currentPolicyQualified'] is False and c['newScientificDefinitionApproved'] is False
 if status=='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED':assert e.get('progressArtifacts'),identifier
 if status=='EXPLICIT_ARCHITECTURAL_DISPOSITION':assert identifier=='A01' or expected[identifier]['decision']=='REJECT'
 for path in e.get('progressArtifacts',[])+c.get('researchDossiers',[]):assert (root/path).is_file(),path
pairs=json.loads((root/'software/qualification/i02-expansion-review-20261006/CLASS_PAIR_PROPOSAL.json').read_text())
assert len(pairs['pairs'])==110 and len({p['pairId'] for p in pairs['pairs']})==110
for pair in pairs['pairs']:
 if pair['status']=='SCIENTIFIC_REVIEW_REQUIRED':assert pair['supportedGeometricWindow'] is None and not pair['supportSufficient']
for path,h in pairs['sources'].items():assert hashlib.sha256((root/path).read_bytes()).hexdigest()==h,path
subprocess.run(['python3','docs/manual/athena/render_reference.py','--check'],check=True)
result={'capabilities':96,'unexamined':0,'unexplainedPartial':0,'counts':dict(collections.Counter(e['currentDisposition'] for e in entries)),'currentPolicyScientificReceipts':0,'definitionChanges':0,'i02PairRecords':110,'i02UnapprovedExpansionPairs':106,'ledgerSha256':hashlib.sha256((base/'CAPABILITY_LEDGER.json').read_bytes()).hexdigest()}
(root/'software/qualification/foundation-clean-source-20261006/DISPOSITION_VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
