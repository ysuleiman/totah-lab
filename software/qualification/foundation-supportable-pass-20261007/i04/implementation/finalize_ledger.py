"""Seal only approved I04 Option B after committed-source qualification."""
from pathlib import Path
import json,hashlib,copy,collections,subprocess
repo=Path.cwd();q=repo/'software/qualification/foundation-supportable-pass-20261007';d=q/'i04/implementation';f=repo/'software/qualification/chemistry-geometry-foundation-20261005'
write=lambda p,x:p.write_text(json.dumps(x,indent=2)+'\n');rel=lambda p:str(p.relative_to(repo));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
v=json.loads((d/'QUALIFICATION.json').read_text());assert v['boundedImplementationQualified'] and v['failures']==0 and v['productionReceiptsIssued']==0
ledger=json.loads((f/'CAPABILITY_LEDGER.json').read_text());before=copy.deepcopy(ledger);r=next(x for x in ledger['entries'] if x['capabilityId']=='I04');r['historicalPreOptionB']=copy.deepcopy(r)
r['currentDisposition']='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED';r['note']='Only user-approved explicit-source-H glycine Option B is qualified; no universal weak-C-H catalog or membrane-survey parity.'
r['closure'].update({'scope':'Exact selected [GLY CA,one explicit bonded H,GLY carbonyl O,C] in distinct finite N-acylated/C-amidated source components. dHO<3.5 AND(angle>120 OR(dHO<3.0 AND angle>90)); two explicit donor CA H independently required, tuples distinct.', 'qualificationBasis':f"{v['cleanCommittedSourceTests']} committed-source tests +3 isolation;62 focused;23 JVM pairs,22 prior hashes unchanged;65 historical files and25 pins exact.",'existingImplementation':'Opt-in athena.glycine-h-carbonyl/1 leaf; unchanged carbonyl role, source scope, geometry and Research Gate.','qualificationEvidence':[rel(d/x) for x in ['QUALIFICATION.json','MATRIX_EXECUTION.json','REPLAY.json']],'scientificSupport':'Explicitly approved Option B adaptation of Senes2001 Results window; exact finite source glycine context. No energetic or survey-parity claim.','currentPolicyQualified':False,'newScientificDefinitionApproved':True,'reviewRequirements':[],'outsideBoundedDomain':'Option A protocol, universal weak donors, I03/inferred H, C-H-pi and N+-C-H-O proposals, charged/modified/non-GLY context, isotope-labelled donor H and same-component tuples. No whole-system absence or biological inference.'})
r['currentWorkAssessment']={'classification':'BOUNDED_SUPPORTED_DOMAIN_QUALIFIED','artifact':rel(d/'CHECKPOINT.txt'),'remainingDependency':None};r['remainingClosureDependency']=None;r['remainingClosureAssessment']=rel(d/'CHECKPOINT.txt');r['currentContractReview']={'date':'2026-10-08','status':'BOUNDED_IMPLEMENTATION_QUALIFIED','contract':rel(q/'i04/EXPLICIT_GLYCINE_OPTION_REVIEW.txt'),'authorization':rel(d/'APPROVAL.txt'),'sourceCommit':v['sourceCommit'],'productionAuthority':False}
for a,b in zip(before['entries'],ledger['entries']):
 if a['capabilityId']!='I04':assert a==b,a['capabilityId']
write(f/'CAPABILITY_LEDGER.json',ledger);counts=dict(collections.Counter(x['currentDisposition'] for x in ledger['entries']));assert counts['BOUNDED_SUPPORTED_DOMAIN_QUALIFIED']==64 and counts['SCIENTIFIC_REVIEW_REQUIRED']==8 and sum(counts.values())==96
p=json.loads((f/'PROGRESS.json').read_text());p['dispositionCounts']=counts;p['remaining']=[x for x in p['remaining'] if x!='I04'];assert len(p['remaining'])==20
p.update(programComplete=False,activeWork='Approved I04 Option B qualified. Next: approved A07 native query/catalog boundary, then remaining supportable scientific-review rows.',stopBoundary='No push. Preserve qualified semantics/unrelated edits. Do not reopen explicitly excluded unresolved scientific/data domains.',finalCleanSourceCheckpoint=rel(d/'CHECKPOINT.txt'),finalCleanSourceValidation=v,newSourceWholeFoundationQualification=f"IMPLEMENTED_BOUNDED_DOMAINS_PASS_{v['cleanCommittedSourceTests']}_PLUS_3_ISOLATION; FULL96_INCOMPLETE")
p['currentSupportablePass'].update(status='I04_OPTION_B_QUALIFIED_A07_APPROVED_PENDING_IMPLEMENTATION',remainingAssessment=rel(q/'REMAINING20.json'),pendingPublicBoundaryReview=None,pendingScientificDefinitionReview=None);write(f/'PROGRESS.json',p)
prior=q/'REMAINING21.json';remaining=json.loads(prior.read_text());remaining.update(basis='Approved bounded I04 glycine Option B qualified; all other rows and12 external requirements preserved.',ledgerSha256=sha(f/'CAPABILITY_LEDGER.json'),priorDependencyMap=rel(prior),priorDependencyMapSha256=sha(prior),closedInThisFamily=['I04']);remaining['definitionRepresentationRequirements']=[x for x in remaining['definitionRepresentationRequirements'] if x['capabilityId']!='I04'];assert len(remaining['definitionRepresentationRequirements'])==8;write(q/'REMAINING20.json',remaining)
(d/'CHECKPOINT.txt').write_text(f'''QUALIFIED I04 OPTION B — 2026-10-08
Source commit {v['sourceCommit']}; exact approved contract ../EXPLICIT_GLYCINE_OPTION_REVIEW.txt.
{v['cleanCommittedSourceTests']} fresh committed-source tests +3 isolation;62 focused I04 tests pass.
23 independent JVM pairs;22 prior hashes unchanged;65 historical files and25 pins exact.
30294 protected tracked files and unrelated tracked edits preserved.
No I02/I03/S1/water/role/event/path changes; no new public API/schema.
Supplied source H retain actual preparation attribution; no inferred or observed-H relabeling.
Only complete selected eligible tuple negatives; no whole-system/energetic/biological inference.
No production scientific authority/receipts or default activation. No push.
Counts64 bounded /12 architectural /8 scientific-review /12 external-data;20 unresolved.
Next approved work: A07 native OCL query boundary and890-entry advisory catalog, then
remaining supportable rows. Explicitly excluded unresolved domains remain blocked.
''')
(q/'CHECKPOINT.txt').write_text('''QUALIFIED SUPPORTABLE PASS — 2026-10-08
Latest sealed source: i04/implementation/CHECKPOINT.txt; current map REMAINING20.json.
64 bounded /12 architectural /8 scientific-review /12 external-data; full96 incomplete.
I04 exact glycine Option B user approval implemented and clean-source qualified.
A07 additive native matcher/query/catalog boundary is APPROVED, not pending permission.
Proceed with per-entry qualification; do not equate parsing890 entries with qualification.
Continue I11 bounded source Cl-pi and V04 native-query representation where supportable.
Do not reopen I04-B elevation, I15 sphere, SAM/G05-G06, broader G03, incomplete I17
protocol or12 external-data rows. Preserve unrelated work and existing scientific semantics.
Initial613a56a40 push historical; no later push. No production authority.
''')
s=f/'START_HERE.txt';s.write_text(f'''CURRENT RESUME — I04 OPTION B CLEAN-SOURCE QUALIFIED — 2026-10-08
Read {rel(q/'CHECKPOINT.txt')} and {rel(d/'CHECKPOINT.txt')} first.
Source{v['sourceCommit'][:9]} passes{v['cleanCommittedSourceTests']}+3,23JVM pairs,65historical,25pins.
Counts64/12/8/12;remaining20. A07 public native-query/catalog boundary APPROVED.
Continue approved A07 and supportable remainder; preserve explicit unresolved stop boundaries.
No push; no production authority; unrelated edits preserved. Older blocks are history.

'''+s.read_text())
subprocess.run(['python3',str(repo/'docs/manual/athena/render_reference.py')],check=True)
subprocess.run(['python3',str(q/'validate_preservation.py')],check=True)
print(json.dumps({'counts':counts,'remaining':20}))
