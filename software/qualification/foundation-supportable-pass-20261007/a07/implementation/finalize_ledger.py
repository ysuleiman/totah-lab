"""Close only the bounded A07 and V04 domains after passing committed-source evidence."""
from pathlib import Path
import json,hashlib,copy,collections,subprocess
repo=Path.cwd();q=repo/'software/qualification/foundation-supportable-pass-20261007';d=q/'a07/implementation';f=repo/'software/qualification/chemistry-geometry-foundation-20261005'
write=lambda p,x:p.write_text(json.dumps(x,indent=2)+'\n');rel=lambda p:str(p.relative_to(repo));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
v=json.loads((d/'QUALIFICATION.json').read_text());assert v['boundedImplementationQualified'] and v['boundedV04NativeOperationValidationQualified'] and v['failures']==0 and v['productionReceiptsIssued']==0
ledger=json.loads((f/'CAPABILITY_LEDGER.json').read_text());before=copy.deepcopy(ledger)
for capability in ['A07','V04']:
 r=next(x for x in ledger['entries'] if x['capabilityId']==capability);assert r['currentDisposition']=='SCIENTIFIC_REVIEW_REQUIRED';r['historicalPreNativeBoundary']=copy.deepcopy(r)
 r['currentDisposition']='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED';r['note']='Exact approved native query/catalog boundary; operation support is separate from physical chemical validity and scientific authority.'
 if capability=='A07':
  scope='Exact pinned OCL.PAINS/2026.7.2 advisory occurrences for independently qualified entries113,159,162,169,202,207,840 in one complete eligible source component. Every890 entry classified:7 representation/execution qualified,482 executable incomplete,401 unsupported. No catalog-wide absence.'
  outside='Other883 entries lack independent qualification or are unsupported; all-catalog absence unavailable. No toxicity, assay interference truth, drug-likeness, rejection authority, binding failure or medicinal-chemistry score.'
 else:
  scope='Approved OCL_IDCODE_QUERY/2026.7.2 native operation profile with exact original bytes, native atom-index correspondence, supported feature controls, and checked exclusion of unrepresented query/dummy/parity/ESR/target assertions. Seven exact catalog entries independently qualified.'
  outside='No universal query, dummy or enhanced-stereo representation engine; unsupported native features remain unsupported operations, never assertions that a physical molecule is invalid. No canonical re-encoding equality or unproven SMARTS transfer.'
 r['closure'].update({'scope':scope,'qualificationBasis':f"{v['cleanCommittedSourceTests']} committed-source tests +3 isolation;92 focused;25 JVM pairs,23 prior hashes unchanged;65 historical files and25 pins exact.",'existingImplementation':'Approved additive native SubstructureMatcher overload; unchanged historical B00 bodies. Immutable catalog and opt-in athena.advisory-alert/1 leaf; existing source scope, provenance and Research Gate.','qualificationEvidence':[rel(d/x) for x in ['QUALIFICATION.json','MATRIX_EXECUTION.json','REPLAY.json','HISTORICAL_METHOD_PRESERVATION.json']],'scientificSupport':'Explicitly approved native boundary and advisory catalog semantics; exact pinned OCL jar/original catalog queries, entry-specific engineering controls. No catalog-membership-to-biological-truth inference.','currentPolicyQualified':False,'newScientificDefinitionApproved':True,'reviewRequirements':[],'outsideBoundedDomain':outside})
 r['currentWorkAssessment']={'classification':'BOUNDED_SUPPORTED_DOMAIN_QUALIFIED','artifact':rel(d/'CHECKPOINT.txt'),'remainingDependency':None};r['remainingClosureDependency']=None;r['remainingClosureAssessment']=rel(d/'CHECKPOINT.txt');r['currentContractReview']={'date':'2026-10-08','status':'BOUNDED_IMPLEMENTATION_QUALIFIED','contract':rel(q/'a07/NATIVE_QUERY_BOUNDARY_REVIEW.txt'),'authorization':rel(d/'APPROVAL.txt'),'sourceCommit':v['sourceCommit'],'productionAuthority':False}
for a,b in zip(before['entries'],ledger['entries']):
 if a['capabilityId'] not in {'A07','V04'}:assert a==b,a['capabilityId']
write(f/'CAPABILITY_LEDGER.json',ledger);counts=dict(collections.Counter(x['currentDisposition'] for x in ledger['entries']));assert counts['BOUNDED_SUPPORTED_DOMAIN_QUALIFIED']==66 and counts['SCIENTIFIC_REVIEW_REQUIRED']==6 and sum(counts.values())==96
p=json.loads((f/'PROGRESS.json').read_text());p['dispositionCounts']=counts;p['remaining']=[x for x in p['remaining'] if x not in {'A07','V04'}];assert len(p['remaining'])==18
p.update(programComplete=False,activeWork='I04 Option B and A07 native/catalog boundary qualified. Continue remaining supportable I11 bounded Cl/Phe review and qualification; retain exact blockers elsewhere.',stopBoundary='No push. Preserve qualified semantics/unrelated edits. Do not reopen explicitly excluded unresolved scientific/data domains.',finalCleanSourceCheckpoint=rel(d/'CHECKPOINT.txt'),finalCleanSourceValidation=v,newSourceWholeFoundationQualification=f"IMPLEMENTED_BOUNDED_DOMAINS_PASS_{v['cleanCommittedSourceTests']}_PLUS_3_ISOLATION; FULL96_INCOMPLETE")
p['currentSupportablePass'].update(status='I04_A07_NATIVE_V04_QUALIFIED_SUPPORTABLE_REMAINDER_PENDING',remainingAssessment=rel(q/'REMAINING18.json'),pendingPublicBoundaryReview=None,pendingScientificDefinitionReview=None);write(f/'PROGRESS.json',p)
prior=q/'REMAINING20.json';remaining=json.loads(prior.read_text());original_external=copy.deepcopy(remaining['externalReferenceDataRequirements']);remaining.update(basis='Approved bounded A07 advisory/native operation and V04 representation validation qualified; all other rows and12 external requirements preserved.',ledgerSha256=sha(f/'CAPABILITY_LEDGER.json'),priorDependencyMap=rel(prior),priorDependencyMapSha256=sha(prior),closedInThisFamily=['A07','V04']);remaining['definitionRepresentationRequirements']=[x for x in remaining['definitionRepresentationRequirements'] if x['capabilityId'] not in {'A07','V04'}];assert len(remaining['definitionRepresentationRequirements'])==6 and remaining['externalReferenceDataRequirements']==original_external;write(q/'REMAINING18.json',remaining)
(d/'CHECKPOINT.txt').write_text(f'''QUALIFIED A07 / BOUNDED V04 NATIVE OPERATION — 2026-10-08
Source commit {v['sourceCommit']}; approved ../NATIVE_QUERY_BOUNDARY_REVIEW.txt.
{v['cleanCommittedSourceTests']} fresh committed-source tests +3 isolation;92 focused tests pass.
25 independent JVM pairs;23 prior hashes unchanged;65 historical files and25 pins exact.
30291 protected tracked files and unrelated tracked edits preserved. Three additional
baseline files changed only for the explicitly approved native overload; historical
two-argument method bodies remain exact, with hash evidence and unchanged replay.
Exact890-entry catalog:7 representation/execution qualified,482 executable incomplete,
401 unsupported;0 malformed. Each original query/label/index/jar/order hash retained.
No catalog-wide absence, no automatic scientific authority, no production receipt.
I04 Option B, I02/I03/S1/water/roles/event/path semantics preserved. No push.
Counts66 bounded /12 architectural /6 scientific-review /12 external-data;18 unresolved.
Continue supportable I11 unique-nearest Cl/Phe candidate; retain explicit unresolved
scientific/data stop boundaries. Older partial catalogs and diagnoses remain history.
''')
(q/'CHECKPOINT.txt').write_text('''QUALIFIED SUPPORTABLE PASS — 2026-10-08
Latest sealed source: a07/implementation/CHECKPOINT.txt; current map REMAINING18.json.
66 bounded /12 architectural /6 scientific-review /12 external-data; full96 incomplete.
Both approvals implemented and clean-source qualified: I04 glycine Option B and A07
native OCL query/catalog boundary. V04 bounded native operation validation qualified.
Seven independently qualified catalog entries;482 incomplete;401 unsupported.
No catalog-wide absence or default activation. Historical two-argument B00 preserved.
Continue remaining supportable I11 Cl/Phe source-domain candidate. Do not reopen
I04-B elevation, I15 sphere, SAM/G05-G06, broader G03, incomplete I17 protocol or12
external-data rows. Preserve unrelated work and existing qualified semantics.
Initial613a56a40 push historical; no later push. No production authority.
''')
s=f/'START_HERE.txt';s.write_text(f'''CURRENT RESUME — A07 / BOUNDED V04 CLEAN-SOURCE QUALIFIED — 2026-10-08
Read {rel(q/'CHECKPOINT.txt')} and {rel(d/'CHECKPOINT.txt')} first.
Source{v['sourceCommit'][:9]} passes{v['cleanCommittedSourceTests']}+3,25JVM pairs,65historical,25pins.
Counts66/12/6/12;remaining18. I04 Option B and approved A07 boundary are complete.
Continue supportable I11; preserve exact unresolved science/data stop boundaries.
No push; no production authority; unrelated edits preserved. Older blocks are history.

'''+s.read_text())
subprocess.run(['python3',str(repo/'docs/manual/athena/render_reference.py')],check=True)
subprocess.run(['python3',str(q/'validate_preservation.py')],check=True)
print(json.dumps({'counts':counts,'remaining':18}))
