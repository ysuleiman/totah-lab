"""Close only bounded N08 after verified qualification; preserve all other capability rows."""
from pathlib import Path
import collections,copy,hashlib,json,subprocess
repo=Path.cwd();q=repo/'software/qualification/foundation-supportable-pass-20261007';f=repo/'software/qualification/chemistry-geometry-foundation-20261005'
rel=lambda p:str(p.relative_to(repo));write=lambda p,v:p.write_text(json.dumps(v,indent=2)+'\n');sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
v=json.loads((q/'n08/QUALIFICATION.json').read_text());assert v['boundedImplementationQualified'] and v['failures']==0 and v['productionReceiptsIssued']==0
ledger=json.loads((f/'CAPABILITY_LEDGER.json').read_text());before=copy.deepcopy(ledger);row=next(r for r in ledger['entries'] if r['capabilityId']=='N08')
row.setdefault('historicalPreN08Gate',{k:copy.deepcopy(row[k]) for k in ['currentDisposition','closure','currentWorkAssessment','remainingClosureDependency','remainingClosureAssessment']})
row['currentDisposition']='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED'
row['closure'].update({'scope':'One explicitly selected ordered correspondence of three neutral carbonyl O features in one complete source component and three attributed query points; existing rigid RMSD <= supplied reviewed bound. No default tolerance or global search.',
'qualificationBasis':f"{v['cleanCommittedSourceTests']} fresh committed-source tests +3 isolation;58 focused N08;19 JVM pairs,18 prior hashes unchanged;65 historical files and25 pins exact.",'existingImplementation':'Opt-in athena.selected-pharmacophore/1; existing carbonyl identities and FeatureTemplateAlignmentEvaluator unchanged; query geometry remains distinct from observed source coordinates.',
'qualificationEvidence':[rel(q/'n08/QUALIFICATION.json'),rel(q/'n08/MATRIX_EXECUTION.json'),rel(q/'n08/REPLAY.json')],'scientificSupport':'Exact user-approved N08 contract and additive attributed query payload; exact query SHA-256 bound into independently reviewed manifest parameters; unchanged mathematical alignment. No empirical tolerance invented.',
'currentPolicyQualified':False,'newScientificDefinitionApproved':True,'reviewRequirements':[],
'outsideBoundedDomain':'No other roles, partial correspondence, collinear/coincident triplets, search, source-state mixing, inferred geometry, default tolerance or whole-molecule negative. Exact query and source-scope current authority independently required.'})
row['currentWorkAssessment']={'classification':'BOUNDED_SUPPORTED_DOMAIN_QUALIFIED','artifact':rel(q/'n08/CHECKPOINT.txt'),'remainingDependency':None};row['remainingClosureDependency']=None;row['remainingClosureAssessment']=rel(q/'n08/CHECKPOINT.txt')
row['currentContractReview']={'date':'2026-10-07','status':'BOUNDED_IMPLEMENTATION_QUALIFIED','contract':rel(q/'n08/CONTRACT_AND_BOUNDARY_REVIEW.txt'),'authorization':rel(q/'n08/APPROVAL.txt'),'sourceCommit':v['sourceCommit'],'productionAuthority':False}
for old,new in zip(before['entries'],ledger['entries']):
 if old['capabilityId']!='N08':assert old==new,old['capabilityId']
write(f/'CAPABILITY_LEDGER.json',ledger)
counts=dict(collections.Counter(r['currentDisposition'] for r in ledger['entries']));assert sum(counts.values())==96 and counts['BOUNDED_SUPPORTED_DOMAIN_QUALIFIED']==60 and counts['SCIENTIFIC_REVIEW_REQUIRED']==12
progress=json.loads((f/'PROGRESS.json').read_text());progress['dispositionCounts']=counts;progress['remaining']=[x for x in progress['remaining'] if x!='N08'];assert len(progress['remaining'])==24
progress['programComplete']=False;progress['activeWork']='Supportable-foundation pass ongoing: N08 bounded implementation qualified; proceed A08/P07 from explicit contracts; A07 public boundary approval pending; all other review/data rows remain open.'
progress['stopBoundary']='Continue authorized supportable rows family by family. No unapproved public API/schema change, invented chemistry/data, or production authority. Only initial613a56a40 push was performed.'
progress['finalCleanSourceCheckpoint']=rel(q/'n08/CHECKPOINT.txt');progress['finalCleanSourceValidation']=v;progress['newSourceWholeFoundationQualification']='IMPLEMENTED_BOUNDED_DOMAINS_PASS_2368_PLUS_3_ISOLATION; FULL96_INCOMPLETE'
progress['currentSupportablePass'].update({'status':'N08_QUALIFIED_CONTINUE_A08_P07','remainingAssessment':rel(q/'REMAINING24.json'),'localExternalDataCharacterization':rel(q/'EXTERNAL_DATA_REASSESSMENT.json'),'pendingPublicBoundaryReview':rel(q/'a07/NATIVE_QUERY_BOUNDARY_REVIEW.txt')})
write(f/'PROGRESS.json',progress)
prior=q/'REMAINING25.json';remaining=json.loads(prior.read_text());remaining.update({'basis':'N08 bounded closure in ongoing supportable pass; all other rows and12 external requirements preserved. Availability reassessment is separate.','ledgerSha256':sha(f/'CAPABILITY_LEDGER.json'),'priorDependencyMap':rel(prior),'priorDependencyMapSha256':sha(prior),'closedInThisFamily':['N08']});remaining['definitionRepresentationRequirements']=[r for r in remaining['definitionRepresentationRequirements'] if r['capabilityId']!='N08'];assert len(remaining['definitionRepresentationRequirements'])==12;write(q/'REMAINING24.json',remaining)
summary=f'''QUALIFIED BOUNDED N08 — 2026-10-07
Source commit: {v['sourceCommit']}
Contract: CONTRACT_AND_BOUNDARY_REVIEW.txt. Evidence: QUALIFICATION.json, MATRIX_EXECUTION.json, REPLAY.json.
2368 fresh committed-source tests +3 isolation;58 N08 tests pass, no failures/skips.
19 independent JVM pairs;18 previous hashes unchanged;65 historical files and25pins exact.
30294 protected tracked files unchanged, including unrelated dirty edits.
Only approved additive query payload; old alignment, roles, event/path, I02, water and S1/I03 unchanged.
Only exact selected eligible tuples receive negatives; no global pharmacophore, affinity or energy claim.
No default activation/production authority/receipt. Later commits not pushed.
Full96 incomplete:60 bounded,12 architectural,12 scientific-review,12 external-data;24 unresolved.
Continue the authorized pass from ../CHECKPOINT.txt; this is not the end of the task.
'''
(q/'n08/CHECKPOINT.txt').write_text(summary)
(q/'CHECKPOINT.txt').write_text('''IN PROGRESS — authorized supportable-foundation pass, 2026-10-07
Initial qualified613a56a40 was pushed and remote-verified; see PUSH_VERIFICATION.json.
N08 clean-source qualified; read n08/CHECKPOINT.txt. Remaining24:12 scientific +12 data.
Next supportable implementations: A08 unique source component parent and P07 source-feature catalog from explicit contracts, one qualified family at a time.
I04/I11/I15/I17 source/protocol gaps recorded; not implemented or falsely closed.
A07 native-query API/catalog boundary proposed; approval pending. No public change yet.
External12 availability rechecked: actual COD/MTZ/NEF/example maps found; see
EXTERNAL_DATA_REASSESSMENT.json. Presence is not qualification; no external row closed.
N08 qualified; P07 and A08 bounded contracts defined. G03 bounded7A survey route remains in review. V04 depends on A07 boundary; V18/G05/G06 exact source/protocol gaps recorded.
Preserve old scientific semantics and unrelated work. Do not claim program complete.
''')
p=f/'START_HERE.txt';p.write_text(f'''CURRENT RESUME — SUPPORTABLE PASS CONTINUES AFTER N08 — 2026-10-07
Read {rel(q/'CHECKPOINT.txt')} and REQUEST.txt first.
N08 source{v['sourceCommit'][:9]} passes2368+3 tests,19JVM pairs,65historical,25pins.
Counts60/12/12/12;remaining24;full96 incomplete. Continue A08/P07 bounded families; A07 public boundary remains pending.
A07 public API/catalog boundary approval pending; independent work continues.
Initial613a56a40 push verified. Later commits local. Unrelated edits preserved.
Older blocks below are history, not new pending work.

'''+p.read_text())
subprocess.run(['python3',str(repo/'docs/manual/athena/render_reference.py')],check=True)
subprocess.run(['python3',str(q/'validate_preservation.py')],check=True)
print(json.dumps({'counts':counts,'remaining':24}))
