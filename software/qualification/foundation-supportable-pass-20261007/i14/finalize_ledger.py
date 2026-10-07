"""Close only bounded I14 after verified qualification; preserve all other capability rows."""
from pathlib import Path
import collections,copy,hashlib,json,subprocess
repo=Path.cwd();q=repo/'software/qualification/foundation-supportable-pass-20261007';f=repo/'software/qualification/chemistry-geometry-foundation-20261005'
rel=lambda p:str(p.relative_to(repo));write=lambda p,v:p.write_text(json.dumps(v,indent=2)+'\n');sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
v=json.loads((q/'i14/QUALIFICATION.json').read_text());assert v['boundedImplementationQualified'] and v['failures']==0 and v['productionReceiptsIssued']==0
ledger=json.loads((f/'CAPABILITY_LEDGER.json').read_text());before=copy.deepcopy(ledger);row=next(r for r in ledger['entries'] if r['capabilityId']=='I14')
row.setdefault('historicalPreI14Gate',{k:copy.deepcopy(row[k]) for k in ['currentDisposition','closure','currentWorkAssessment','remainingClosureDependency','remainingClosureAssessment']})
row['currentDisposition']='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED'
row['closure'].update({'scope':'Exact selected source monatomic Zn2+ and unchanged neutral carbonyl-O in separate components, 0 < distance <= 2.8 angstrom; source-structural proximity only, not coordination or energy.',
'qualificationBasis':f"{v['cleanCommittedSourceTests']} fresh committed-source tests +3 isolation;39 focused I14;18 JVM pairs,17 prior hashes unchanged;65 historical files and25 pins exact.",'existingImplementation':'Opt-in athena.zinc-carbonyl/1; source metal and neutral carbonyl coverage remain independent.',
'qualificationEvidence':[rel(q/'i14/QUALIFICATION.json'),rel(q/'i14/MATRIX_EXECUTION.json'),rel(q/'i14/REPLAY.json')],'scientificSupport':'Exact pinned ProLIF MetalDonor distance window 2.8 angstrom, restricted to source monatomic Zn2+ and unchanged carbonyl role; PLIP coordination definitions remain separate. Adopted under REQUEST.txt authorization.',
'currentPolicyQualified':False,'newScientificDefinitionApproved':True,'reviewRequirements':[],
'outsideBoundedDomain':'No other metals/charges/bonded or H-bearing Zn/other acceptors/same-component/nonordinary or unresolved source state; no whole-system negative, coordination sphere, energy or full-engine parity. Independent current source-scope and I14 authority required.'})
row['currentWorkAssessment']={'classification':'BOUNDED_SUPPORTED_DOMAIN_QUALIFIED','artifact':rel(q/'i14/CHECKPOINT.txt'),'remainingDependency':None};row['remainingClosureDependency']=None;row['remainingClosureAssessment']=rel(q/'i14/CHECKPOINT.txt')
row['currentContractReview']={'date':'2026-10-07','status':'BOUNDED_IMPLEMENTATION_QUALIFIED','contract':rel(q/'i14/CONTRACT.txt'),'authorization':rel(q/'REQUEST.txt'),'sourceCommit':v['sourceCommit'],'productionAuthority':False}
for old,new in zip(before['entries'],ledger['entries']):
 if old['capabilityId']!='I14':assert old==new,old['capabilityId']
write(f/'CAPABILITY_LEDGER.json',ledger)
counts=dict(collections.Counter(r['currentDisposition'] for r in ledger['entries']));assert sum(counts.values())==96 and counts['BOUNDED_SUPPORTED_DOMAIN_QUALIFIED']==59 and counts['SCIENTIFIC_REVIEW_REQUIRED']==13
progress=json.loads((f/'PROGRESS.json').read_text());progress['dispositionCounts']=counts;progress['remaining']=[x for x in progress['remaining'] if x!='I14'];assert len(progress['remaining'])==25
progress['programComplete']=False;progress['activeWork']='Supportable-foundation pass ongoing: I14 bounded implementation qualified; proceed approved N08 from explicit contract; A07 public boundary approval pending; all other review/data rows remain open.'
progress['stopBoundary']='Continue authorized supportable rows family by family. No unapproved public API/schema change, invented chemistry/data, or production authority. Only initial613a56a40 push was performed.'
progress['finalCleanSourceCheckpoint']=rel(q/'i14/CHECKPOINT.txt');progress['finalCleanSourceValidation']=v;progress['newSourceWholeFoundationQualification']='IMPLEMENTED_BOUNDED_DOMAINS_PASS_2310_PLUS_3_ISOLATION; FULL96_INCOMPLETE'
progress['currentSupportablePass'].update({'status':'I14_QUALIFIED_CONTINUE_APPROVED_N08','remainingAssessment':rel(q/'REMAINING25.json'),'localExternalDataCharacterization':rel(q/'EXTERNAL_DATA_REASSESSMENT.json'),'pendingPublicBoundaryReview':rel(q/'a07/NATIVE_QUERY_BOUNDARY_REVIEW.txt')})
write(f/'PROGRESS.json',progress)
prior=q/'REMAINING26.json';remaining=json.loads(prior.read_text());remaining.update({'basis':'I14 bounded closure in ongoing supportable pass; all other rows and12 external requirements preserved. Availability reassessment is separate.','ledgerSha256':sha(f/'CAPABILITY_LEDGER.json'),'priorDependencyMap':rel(prior),'priorDependencyMapSha256':sha(prior),'closedInThisFamily':['I14']});remaining['definitionRepresentationRequirements']=[r for r in remaining['definitionRepresentationRequirements'] if r['capabilityId']!='I14'];assert len(remaining['definitionRepresentationRequirements'])==13;write(q/'REMAINING25.json',remaining)
summary=f'''QUALIFIED BOUNDED I14 — 2026-10-07
Source commit: {v['sourceCommit']}
Contract: CONTRACT.txt. Evidence: QUALIFICATION.json, MATRIX_EXECUTION.json, REPLAY.json.
2310 fresh committed-source tests +3 isolation;39 I14 tests pass, no failures/skips.
18 independent JVM pairs;17 previous hashes unchanged;65 historical files and25pins exact.
30294 protected tracked files unchanged, including unrelated dirty edits.
No public API/schema, role, event/path, I02, water or S1/I03 modification.
Only exact selected eligible tuples receive negatives; no coordination or energy claim.
No default activation/production authority/receipt. Later commits not pushed.
Full96 incomplete:59 bounded,12 architectural,13 scientific-review,12 external-data;25 unresolved.
Continue the authorized pass from ../CHECKPOINT.txt; this is not the end of the task.
'''
(q/'i14/CHECKPOINT.txt').write_text(summary)
(q/'CHECKPOINT.txt').write_text('''IN PROGRESS — authorized supportable-foundation pass, 2026-10-07
Initial qualified613a56a40 was pushed and remote-verified; see PUSH_VERIFICATION.json.
I14 clean-source qualified; read i14/CHECKPOINT.txt. Remaining25:13 scientific +12 data.
Next implementation: approved N08 exact selected template; read n08/CONTRACT_AND_BOUNDARY_REVIEW.txt and APPROVAL.txt.
I04/I11/I15/I17 source/protocol gaps recorded; not implemented or falsely closed.
A07 native-query API/catalog boundary proposed; approval pending. No public change yet.
External12 availability rechecked: actual COD/MTZ/NEF/example maps found; see
EXTERNAL_DATA_REASSESSMENT.json. Presence is not qualification; no external row closed.
N08 approved; P07 and A08 bounded contracts in review; V04,V18,G03,G05,G06 require continued review.
Preserve old scientific semantics and unrelated work. Do not claim program complete.
''')
p=f/'START_HERE.txt';p.write_text(f'''CURRENT RESUME — SUPPORTABLE PASS CONTINUES AFTER I14 — 2026-10-07
Read {rel(q/'CHECKPOINT.txt')} and REQUEST.txt first.
I14 source{v['sourceCommit'][:9]} passes2310+3 tests,18JVM pairs,65historical,25pins.
Counts59/12/13/12;remaining25;full96 incomplete. Next bounded implementation approved N08.
A07 public API/catalog boundary approval pending; independent work continues.
Initial613a56a40 push verified. Later commits local. Unrelated edits preserved.
Older blocks below are history, not new pending work.

'''+p.read_text())
subprocess.run(['python3',str(repo/'docs/manual/athena/render_reference.py')],check=True)
subprocess.run(['python3',str(q/'validate_preservation.py')],check=True)
print(json.dumps({'counts':counts,'remaining':25}))
