"""Proposal, source pin and preservation checks only; never scientific qualification."""
from pathlib import Path
import hashlib,json,subprocess
repo=Path(__file__).resolve().parents[3];q=Path(__file__).resolve().parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
b=json.loads((q/'PRESERVATION_BEFORE.json').read_text())
for group in ['protectedFiles','original25']:
 for p,h in b[group].items():assert sha(repo/p)==h,p
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip()==b['head']
old={r['capabilityId']:r for r in b['ledger']['entries']};new=json.loads((repo/'software/qualification/chemistry-geometry-foundation-20261005/CAPABILITY_LEDGER.json').read_text())
for r in new['entries']:
 if r['capabilityId']=='I03':assert {k:v for k,v in r.items() if k!='proposedAssignmentSourceReview'}==old['I03']
 else:assert r==old[r['capabilityId']],r['capabilityId']
sources=json.loads((q/'SOURCE_PINS.json').read_text())
for s in sources:
 assert 'error' not in s,s
 assert sha(q/'reference'/s['file'])==s['sha256'],s['file']
assert (q/'reference/ocl-ExtendedMolecule.java').read_bytes()==(q/'reference/ocl-ExtendedMolecule-immutable.java').read_bytes()
protocol=json.loads((q/'OCL_REFERENCE_PROTOCOL.json').read_text());assert sha(Path(protocol['jar']))==protocol['jarSha256']
rd=json.loads((q/'RDKIT_REFERENCE_RESULTS.json').read_text())
for p,h in rd['binaryPins'].items():assert sha(Path(p))==h,p
m=json.loads((q/'ACCEPTANCE_MATRIX.json').read_text());assert m['caseCount']==len(m['cases'])==94
assert len({c['id'] for c in m['cases']})==94 and all(c['execution']=='NOT_EXECUTED_PROPOSED_ATHENA_ORACLE' for c in m['cases'])
assert len(json.loads((q/'REFERENCE_COMPARISON.json').read_text())['rows'])==31
subprocess.run(['python3',str(repo/'docs/manual/athena/render_reference.py'),'--check'],cwd=repo,check=True)
out={'kind':'REVIEW_INTEGRITY_ONLY_NOT_CLASSIFIER_QUALIFICATION','headUnchanged':b['head'],'protectedFilesUnchanged':len(b['protectedFiles']),'preservationPinsIntact':25,'allOtherCapabilityRowsUnchanged':True,'I04AndExternal12Unchanged':True,'primarySourcePinsVerified':len(sources),'externalReferenceCases':31,'proposedAthenaCases':94,'AthenaClassifierImplemented':False,'AthenaClassifierTestsExecuted':0,'currentI03Activation':'NOT_EVALUATED_BLOCKED','publicApiChanges':0,'schemaRegistrationsAdded':0,'productionReceiptsIssued':0}
(q/'VALIDATION.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps(out,indent=2))
