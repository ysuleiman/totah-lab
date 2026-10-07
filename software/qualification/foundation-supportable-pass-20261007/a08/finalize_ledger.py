"""Close only bounded A08 after verified qualification; preserve all other capability rows."""
from pathlib import Path
import collections,copy,hashlib,json,subprocess
repo=Path.cwd();q=repo/'software/qualification/foundation-supportable-pass-20261007';f=repo/'software/qualification/chemistry-geometry-foundation-20261005'
rel=lambda p:str(p.relative_to(repo));write=lambda p,v:p.write_text(json.dumps(v,indent=2)+'\n');sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
v=json.loads((q/'a08/QUALIFICATION.json').read_text());assert v['boundedImplementationQualified'] and v['failures']==0 and v['productionReceiptsIssued']==0
ledger=json.loads((f/'CAPABILITY_LEDGER.json').read_text());before=copy.deepcopy(ledger);row=next(r for r in ledger['entries'] if r['capabilityId']=='A08')
row.setdefault('historicalPreA08Gate',{k:copy.deepcopy(row[k]) for k in ['currentDisposition','closure','currentWorkAssessment','remainingClosureDependency','remainingClosureAssessment']})
row['currentDisposition']='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED'
row['closure'].update({'scope':'Optional unique-largest eligible heavy-source component view over one complete declared state; exact source graph/atom/bond lineage and all nonselected components retained. No normalization or tie-break.',
'qualificationBasis':f"{v['cleanCommittedSourceTests']} fresh committed-source tests +3 isolation;38 focused A08;20 JVM pairs,19 prior hashes unchanged;65 historical files and25 pins exact.",'existingImplementation':'Opt-in athena.source-fragment-parent/1; existing immutable state snapshot, source coverage/scope, correspondence and Research Gate; no graph edits.',
'qualificationEvidence':[rel(q/'a08/QUALIFICATION.json'),rel(q/'a08/MATRIX_EXECUTION.json'),rel(q/'a08/REPLAY.json')],'scientificSupport':'Pinned RDKit Fragment.cpp comparison; explicit bounded unique heavy-count source policy under REQUEST.txt. No RDKit normalization/parity, chemical validity, salt or biological inference.',
'currentPolicyQualified':False,'newScientificDefinitionApproved':True,'reviewRequirements':[],
'outsideBoundedDomain':'No explicit-H vertices, incomplete/overlapping source universe, unresolved source chemistry, known nonordinary connection, query/dummy/unrepresented stereo, tie choice, tautomerization or neutralization. Independent current source and parent-policy authority required.'})
row['currentWorkAssessment']={'classification':'BOUNDED_SUPPORTED_DOMAIN_QUALIFIED','artifact':rel(q/'a08/CHECKPOINT.txt'),'remainingDependency':None};row['remainingClosureDependency']=None;row['remainingClosureAssessment']=rel(q/'a08/CHECKPOINT.txt')
row['currentContractReview']={'date':'2026-10-08','status':'BOUNDED_IMPLEMENTATION_QUALIFIED','contract':rel(q/'a08/CONTRACT.txt'),'authorization':rel(q/'REQUEST.txt'),'sourceCommit':v['sourceCommit'],'productionAuthority':False}
for old,new in zip(before['entries'],ledger['entries']):
 if old['capabilityId']!='A08':assert old==new,old['capabilityId']
write(f/'CAPABILITY_LEDGER.json',ledger)
counts=dict(collections.Counter(r['currentDisposition'] for r in ledger['entries']));assert sum(counts.values())==96 and counts['BOUNDED_SUPPORTED_DOMAIN_QUALIFIED']==61 and counts['SCIENTIFIC_REVIEW_REQUIRED']==11
progress=json.loads((f/'PROGRESS.json').read_text());progress['dispositionCounts']=counts;progress['remaining']=[x for x in progress['remaining'] if x!='A08'];assert len(progress['remaining'])==23
progress['programComplete']=False;progress['activeWork']='Supportable-foundation pass ongoing: A08 bounded implementation qualified; proceed P07/G03 from explicit contracts; A07 public boundary approval pending; all other review/data rows remain open.'
progress['stopBoundary']='Continue authorized supportable rows family by family. No unapproved public API/schema change, invented chemistry/data, or production authority. Only initial613a56a40 push was performed.'
progress['finalCleanSourceCheckpoint']=rel(q/'a08/CHECKPOINT.txt');progress['finalCleanSourceValidation']=v;progress['newSourceWholeFoundationQualification']='IMPLEMENTED_BOUNDED_DOMAINS_PASS_2406_PLUS_3_ISOLATION; FULL96_INCOMPLETE'
progress['currentSupportablePass'].update({'status':'A08_QUALIFIED_CONTINUE_P07_G03','remainingAssessment':rel(q/'REMAINING23.json'),'localExternalDataCharacterization':rel(q/'EXTERNAL_DATA_REASSESSMENT.json'),'pendingPublicBoundaryReview':rel(q/'a07/NATIVE_QUERY_BOUNDARY_REVIEW.txt')})
write(f/'PROGRESS.json',progress)
prior=q/'REMAINING24.json';remaining=json.loads(prior.read_text());remaining.update({'basis':'A08 bounded closure in ongoing supportable pass; all other rows and12 external requirements preserved. Availability reassessment is separate.','ledgerSha256':sha(f/'CAPABILITY_LEDGER.json'),'priorDependencyMap':rel(prior),'priorDependencyMapSha256':sha(prior),'closedInThisFamily':['A08']});remaining['definitionRepresentationRequirements']=[r for r in remaining['definitionRepresentationRequirements'] if r['capabilityId']!='A08'];assert len(remaining['definitionRepresentationRequirements'])==11;write(q/'REMAINING23.json',remaining)
summary=f'''QUALIFIED BOUNDED A08 — 2026-10-08
Source commit: {v['sourceCommit']}
Contract: CONTRACT.txt. Evidence: QUALIFICATION.json, MATRIX_EXECUTION.json, REPLAY.json.
2406 fresh committed-source tests +3 isolation;38 A08 tests pass, no failures/skips.
20 independent JVM pairs;19 previous hashes unchanged;65 historical files and25pins exact.
30294 protected tracked files unchanged, including unrelated dirty edits.
No new public payload/API; roles, event/path, I02, water and S1/I03 unchanged. I14 coverage helper body unchanged.
No molecular interaction negative or chemical normalization is claimed; all original components remain referenced.
No default activation/production authority/receipt. Later commits not pushed.
Full96 incomplete:61 bounded,12 architectural,11 scientific-review,12 external-data;23 unresolved.
Continue the authorized pass from ../CHECKPOINT.txt; this is not the end of the task.
'''
(q/'a08/CHECKPOINT.txt').write_text(summary)
(q/'CHECKPOINT.txt').write_text('''IN PROGRESS — authorized supportable-foundation pass, 2026-10-08
Initial qualified613a56a40 was pushed and remote-verified; see PUSH_VERIFICATION.json.
A08 clean-source qualified; read a08/CHECKPOINT.txt. Remaining23:11 scientific +12 data.
Next supportable implementations: P07 source-feature catalog and G03 Met/Phe survey contact from explicit contracts, one qualified family at a time.
I04 primary methods recovered; preparation/domain choice remains open (i04/METHODS_RECOVERY_REVIEW.txt). I11/I15/I17 source/protocol gaps recorded; none falsely closed.
A07 native-query API/catalog boundary proposed; approval pending. No public change yet.
External12 availability rechecked: actual COD/MTZ/NEF/example maps found; see
EXTERNAL_DATA_REASSESSMENT.json. Presence is not qualification; no external row closed.
A08 qualified; P07 bounded contract defined. G03 bounded7A survey route has an explicit graph/identity contract. V04 depends on A07 boundary; V18/G05/G06 exact source/protocol gaps recorded.
Preserve old scientific semantics and unrelated work. Do not claim program complete.
''')
p=f/'START_HERE.txt';p.write_text(f'''CURRENT RESUME — SUPPORTABLE PASS CONTINUES AFTER A08 — 2026-10-08
Read {rel(q/'CHECKPOINT.txt')} and REQUEST.txt first.
A08 source{v['sourceCommit'][:9]} passes2406+3 tests,20JVM pairs,65historical,25pins.
Counts61/12/11/12;remaining23;full96 incomplete. Continue P07/G03 bounded families; A07 public boundary remains pending.
A07 public API/catalog boundary approval pending; independent work continues.
Initial613a56a40 push verified. Later commits local. Unrelated edits preserved.
Older blocks below are history, not new pending work.

'''+p.read_text())
subprocess.run(['python3',str(repo/'docs/manual/athena/render_reference.py')],check=True)
subprocess.run(['python3',str(q/'validate_preservation.py')],check=True)
print(json.dumps({'counts':counts,'remaining':23}))
