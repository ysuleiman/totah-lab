"""Close only bounded I10 after verified qualification; preserve all other capability rows."""
from pathlib import Path
import collections,copy,hashlib,json,subprocess
repo=Path.cwd();q=repo/'software/qualification/foundation-supportable-pass-20261007';f=repo/'software/qualification/chemistry-geometry-foundation-20261005'
rel=lambda p:str(p.relative_to(repo));write=lambda p,v:p.write_text(json.dumps(v,indent=2)+'\n');sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
v=json.loads((q/'i10/QUALIFICATION.json').read_text());assert v['boundedImplementationQualified'] and v['failures']==0 and v['productionReceiptsIssued']==0
ledger=json.loads((f/'CAPABILITY_LEDGER.json').read_text());before=copy.deepcopy(ledger);row=next(r for r in ledger['entries'] if r['capabilityId']=='I10')
row.setdefault('historicalPreI10Gate',{k:copy.deepcopy(row[k]) for k in ['currentDisposition','closure','currentWorkAssessment','remainingClosureDependency','remainingClosureAssessment']})
row['currentDisposition']='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED'
row['closure'].update({'scope':'Exact selected carbon-bound neutral Cl/Br/I–neutral-carbonyl tuple under the explicitly adopted ProLIF numeric window; separate element and carbon aromaticity retained. Structural candidate only, not physical bond or energy.',
'qualificationBasis':f"{v['cleanCommittedSourceTests']} fresh committed-source tests +3 isolation;45 focused I10;17 JVM pairs,16 prior hashes unchanged;65 historical files and25 pins exact.",'existingImplementation':'Opt-in athena.halogen-carbonyl/1; legacy HalogenBondDetector and INT.HALOGEN.001 unchanged.',
'qualificationEvidence':[rel(q/'i10/QUALIFICATION.json'),rel(q/'i10/MATRIX_EXECUTION.json'),rel(q/'i10/REPLAY.json')],'scientificSupport':'Exact pinned ProLIF DoubleAngle window3.5A/130..180/80..140; restricted unchanged source roles. PLIP and survey definitions preserved separately in CONTRACT.txt. Adopted under REQUEST.txt authorization.',
'currentPolicyQualified':False,'newScientificDefinitionApproved':True,'reviewRequirements':[],
'outsideBoundedDomain':'No fluorine/At/noncarbon donor/other acceptor/pi/same-component/charged or radical core; no whole-system negative, energy, biological assertion or full ProLIF/PLIP parity. Independent current source-scope and I10 authority required.'})
row['currentWorkAssessment']={'classification':'BOUNDED_SUPPORTED_DOMAIN_QUALIFIED','artifact':rel(q/'i10/CHECKPOINT.txt'),'remainingDependency':None};row['remainingClosureDependency']=None;row['remainingClosureAssessment']=rel(q/'i10/CHECKPOINT.txt')
row['currentContractReview']={'date':'2026-10-07','status':'BOUNDED_IMPLEMENTATION_QUALIFIED','contract':rel(q/'i10/CONTRACT.txt'),'authorization':rel(q/'REQUEST.txt'),'sourceCommit':v['sourceCommit'],'productionAuthority':False}
for old,new in zip(before['entries'],ledger['entries']):
 if old['capabilityId']!='I10':assert old==new,old['capabilityId']
write(f/'CAPABILITY_LEDGER.json',ledger)
counts=dict(collections.Counter(r['currentDisposition'] for r in ledger['entries']));assert sum(counts.values())==96 and counts['BOUNDED_SUPPORTED_DOMAIN_QUALIFIED']==58 and counts['SCIENTIFIC_REVIEW_REQUIRED']==14
progress=json.loads((f/'PROGRESS.json').read_text());progress['dispositionCounts']=counts;progress['remaining']=[x for x in progress['remaining'] if x!='I10'];assert len(progress['remaining'])==26
progress['programComplete']=False;progress['activeWork']='Supportable-foundation pass ongoing: I10 bounded implementation qualified; proceed I14 from explicit contract; A07 public boundary approval pending; all other review/data rows remain open.'
progress['stopBoundary']='Continue authorized supportable rows family by family. No unapproved public API/schema change, invented chemistry/data, or production authority. Only initial613a56a40 push was performed.'
progress['finalCleanSourceCheckpoint']=rel(q/'i10/CHECKPOINT.txt');progress['finalCleanSourceValidation']=v;progress['newSourceWholeFoundationQualification']='IMPLEMENTED_BOUNDED_DOMAINS_PASS_2271_PLUS_3_ISOLATION; FULL96_INCOMPLETE'
progress['currentSupportablePass'].update({'status':'I10_QUALIFIED_CONTINUE_I14','remainingAssessment':rel(q/'REMAINING26.json'),'localExternalDataCharacterization':rel(q/'EXTERNAL_DATA_REASSESSMENT.json'),'pendingPublicBoundaryReview':rel(q/'a07/NATIVE_QUERY_BOUNDARY_REVIEW.txt')})
write(f/'PROGRESS.json',progress)
prior=repo/'software/qualification/i03-s1-implementation-20261007/REMAINING27.json';remaining=json.loads(prior.read_text());remaining.update({'basis':'I10 bounded closure in ongoing supportable pass; all other rows and12 external requirements preserved. Availability reassessment is separate.','ledgerSha256':sha(f/'CAPABILITY_LEDGER.json'),'priorDependencyMap':rel(prior),'priorDependencyMapSha256':sha(prior),'closedInThisFamily':['I10']});remaining['definitionRepresentationRequirements']=[r for r in remaining['definitionRepresentationRequirements'] if r['capabilityId']!='I10'];assert len(remaining['definitionRepresentationRequirements'])==14;write(q/'REMAINING26.json',remaining)
summary=f'''QUALIFIED BOUNDED I10 — 2026-10-07
Source commit: {v['sourceCommit']}
Contract: CONTRACT.txt. Evidence: QUALIFICATION.json, MATRIX_EXECUTION.json, REPLAY.json.
2271 fresh committed-source tests +3 isolation;45 I10 tests pass, no failures/skips.
17 independent JVM pairs;16 previous hashes unchanged;65 historical files and25pins exact.
30294 protected tracked files unchanged, including unrelated dirty edits.
No public API/schema, role, event/path, I02, water or S1/I03 modification.
Only exact selected eligible tuples receive negatives; no universal halogen-bond or energy claim.
No default activation/production authority/receipt. Later commits not pushed.
Full96 incomplete:58 bounded,12 architectural,14 scientific-review,12 external-data;26 unresolved.
Continue the authorized pass from ../CHECKPOINT.txt; this is not the end of the task.
'''
(q/'i10/CHECKPOINT.txt').write_text(summary)
(q/'CHECKPOINT.txt').write_text('''IN PROGRESS — authorized supportable-foundation pass, 2026-10-07
Initial qualified613a56a40 was pushed and remote-verified; see PUSH_VERIFICATION.json.
I10 clean-source qualified; read i10/CHECKPOINT.txt. Remaining26:14 scientific +12 data.
Next implementation: I14 bounded Zn2+/carbonyl proximity, exact i14/CONTRACT.txt.
I04/I11/I15/I17 source/protocol gaps recorded; not implemented or falsely closed.
A07 native-query API/catalog boundary proposed; approval pending. No public change yet.
External12 availability rechecked: actual COD/MTZ/NEF/example maps found; see
EXTERNAL_DATA_REASSESSMENT.json. Presence is not qualification; no external row closed.
P07,N08,V04,V18,A08,G03,G05,G06 still require continued review/adoption work.
Preserve old scientific semantics and unrelated work. Do not claim program complete.
''')
p=f/'START_HERE.txt';p.write_text(f'''CURRENT RESUME — SUPPORTABLE PASS CONTINUES AFTER I10 — 2026-10-07
Read {rel(q/'CHECKPOINT.txt')} and REQUEST.txt first.
I10 source{v['sourceCommit'][:9]} passes2271+3 tests,17JVM pairs,65historical,25pins.
Counts58/12/14/12;remaining26;full96 incomplete. Next bounded implementation I14.
A07 public API/catalog boundary approval pending; independent work continues.
Initial613a56a40 push verified. Later commits local. Unrelated edits preserved.
Older blocks below are history, not new pending work.

'''+p.read_text())
subprocess.run(['python3',str(repo/'docs/manual/athena/render_reference.py')],check=True)
subprocess.run(['python3',str(q/'validate_preservation.py')],check=True)
print(json.dumps({'counts':counts,'remaining':26}))
