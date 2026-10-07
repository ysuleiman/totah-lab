"""Close only bounded P07 after verified qualification; preserve all other capability rows."""
from pathlib import Path
import collections,copy,hashlib,json,subprocess
repo=Path.cwd();q=repo/'software/qualification/foundation-supportable-pass-20261007';f=repo/'software/qualification/chemistry-geometry-foundation-20261005'
rel=lambda p:str(p.relative_to(repo));write=lambda p,v:p.write_text(json.dumps(v,indent=2)+'\n');sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
v=json.loads((q/'p07/QUALIFICATION.json').read_text());assert v['boundedImplementationQualified'] and v['failures']==0 and v['productionReceiptsIssued']==0
ledger=json.loads((f/'CAPABILITY_LEDGER.json').read_text());before=copy.deepcopy(ledger);row=next(r for r in ledger['entries'] if r['capabilityId']=='P07')
row.setdefault('historicalPreP07Gate',{k:copy.deepcopy(row[k]) for k in ['currentDisposition','closure','currentWorkAssessment','remainingClosureDependency','remainingClosureAssessment']})
row['currentDisposition']='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED'
row['closure'].update({'scope':'Six literal RDKit ZnBinder source-feature identities, seven explicit OCL transfer branches, in one complete neutral heavy C/N/O/S/P source component. OR union retains all correspondences; no actual zinc-binding/coordination assertion.',
'qualificationBasis':f"{v['cleanCommittedSourceTests']} fresh committed-source tests +3 isolation;65 focused P07;21 JVM pairs,20 prior hashes unchanged;65 historical files and25 pins exact.",'existingImplementation':'Opt-in athena.zn-source-features/1 and seven declarative group/4 branches; existing matcher, source-H consistency, scope and Research Gate unchanged.',
'qualificationEvidence':[rel(q/'p07/QUALIFICATION.json'),rel(q/'p07/MATRIX_EXECUTION.json'),rel(q/'p07/REPLAY.json')],'scientificSupport':'Pinned RDKit BaseFeatures.fdef and explicitly reviewed transferred queries;34 synthetic reference cases. Advisory literal feature identity only; no full-toolkit parity or chemical binding authority.',
'currentPolicyQualified':False,'newScientificDefinitionApproved':True,'reviewRequirements':[],
'outsideBoundedDomain':'No explicit-H targets, charged components, incomplete/conflicting source state, nonordinary connections, weighted centers, inferred H or actual zinc binding. Feature negatives require all original OR branches and independent current source/feature authority.'})
row['currentWorkAssessment']={'classification':'BOUNDED_SUPPORTED_DOMAIN_QUALIFIED','artifact':rel(q/'p07/CHECKPOINT.txt'),'remainingDependency':None};row['remainingClosureDependency']=None;row['remainingClosureAssessment']=rel(q/'p07/CHECKPOINT.txt')
row['currentContractReview']={'date':'2026-10-08','status':'BOUNDED_IMPLEMENTATION_QUALIFIED','contract':rel(q/'p07/CONTRACT.txt'),'authorization':rel(q/'REQUEST.txt'),'sourceCommit':v['sourceCommit'],'productionAuthority':False}
for old,new in zip(before['entries'],ledger['entries']):
 if old['capabilityId']!='P07':assert old==new,old['capabilityId']
write(f/'CAPABILITY_LEDGER.json',ledger)
counts=dict(collections.Counter(r['currentDisposition'] for r in ledger['entries']));assert sum(counts.values())==96 and counts['BOUNDED_SUPPORTED_DOMAIN_QUALIFIED']==62 and counts['SCIENTIFIC_REVIEW_REQUIRED']==10
progress=json.loads((f/'PROGRESS.json').read_text());progress['dispositionCounts']=counts;progress['remaining']=[x for x in progress['remaining'] if x!='P07'];assert len(progress['remaining'])==22
progress['programComplete']=False;progress['activeWork']='Supportable-foundation pass ongoing: P07 bounded implementation qualified; proceed G03 from explicit contracts; A07 public boundary approval pending; all other review/data rows remain open.'
progress['stopBoundary']='Continue authorized supportable rows family by family. No unapproved public API/schema change, invented chemistry/data, or production authority. Only initial613a56a40 push was performed.'
progress['finalCleanSourceCheckpoint']=rel(q/'p07/CHECKPOINT.txt');progress['finalCleanSourceValidation']=v;progress['newSourceWholeFoundationQualification']='IMPLEMENTED_BOUNDED_DOMAINS_PASS_2471_PLUS_3_ISOLATION; FULL96_INCOMPLETE'
progress['currentSupportablePass'].update({'status':'P07_QUALIFIED_CONTINUE_G03','remainingAssessment':rel(q/'REMAINING22.json'),'localExternalDataCharacterization':rel(q/'EXTERNAL_DATA_REASSESSMENT.json'),'pendingPublicBoundaryReview':rel(q/'a07/NATIVE_QUERY_BOUNDARY_REVIEW.txt')})
write(f/'PROGRESS.json',progress)
prior=q/'REMAINING23.json';remaining=json.loads(prior.read_text());remaining.update({'basis':'P07 bounded closure in ongoing supportable pass; all other rows and12 external requirements preserved. Availability reassessment is separate.','ledgerSha256':sha(f/'CAPABILITY_LEDGER.json'),'priorDependencyMap':rel(prior),'priorDependencyMapSha256':sha(prior),'closedInThisFamily':['P07']});remaining['definitionRepresentationRequirements']=[r for r in remaining['definitionRepresentationRequirements'] if r['capabilityId']!='P07'];assert len(remaining['definitionRepresentationRequirements'])==10;write(q/'REMAINING22.json',remaining)
summary=f'''QUALIFIED BOUNDED P07 — 2026-10-08
Source commit: {v['sourceCommit']}
Contract: CONTRACT.txt. Evidence: QUALIFICATION.json, MATRIX_EXECUTION.json, REPLAY.json.
2471 fresh committed-source tests +3 isolation;65 P07 tests pass, no failures/skips.
21 independent JVM pairs;20 previous hashes unchanged;65 historical files and25pins exact.
30294 protected tracked files unchanged, including unrelated dirty edits.
No new public payload/API or generic matcher/catalog; old roles/event/path/I02/water/S1/I03 unchanged.
Negatives apply only to literal named source features in the eligible selected component, never zinc binding or affinity.
No default activation/production authority/receipt. Later commits not pushed.
Full96 incomplete:62 bounded,12 architectural,10 scientific-review,12 external-data;22 unresolved.
Continue the authorized pass from ../CHECKPOINT.txt; this is not the end of the task.
'''
(q/'p07/CHECKPOINT.txt').write_text(summary)
(q/'CHECKPOINT.txt').write_text('''IN PROGRESS — authorized supportable-foundation pass, 2026-10-08
Initial qualified613a56a40 was pushed and remote-verified; see PUSH_VERIFICATION.json.
P07 clean-source qualified; read p07/CHECKPOINT.txt. Remaining22:10 scientific +12 data.
Next supportable implementation: G03 Met/Phe survey contact from its explicit chemical/source contract.
I04 primary methods recovered; explicit glycine option B awaits scientific approval (i04/EXPLICIT_GLYCINE_OPTION_REVIEW.txt). I11/I15/I17 source/protocol gaps recorded; none falsely closed.
A07 native-query API/catalog boundary proposed; approval pending. No public change yet.
External12 availability rechecked: actual COD/MTZ/NEF/example maps found; see
EXTERNAL_DATA_REASSESSMENT.json. Presence is not qualification; no external row closed.
P07 qualified. G03 bounded7A survey route has an explicit graph/identity contract. V04 depends on A07 boundary; V18/G05/G06 exact source/protocol gaps recorded.
Preserve old scientific semantics and unrelated work. Do not claim program complete.
''')
p=f/'START_HERE.txt';p.write_text(f'''CURRENT RESUME — SUPPORTABLE PASS CONTINUES AFTER P07 — 2026-10-08
Read {rel(q/'CHECKPOINT.txt')} and REQUEST.txt first.
P07 source{v['sourceCommit'][:9]} passes2471+3 tests,21JVM pairs,65historical,25pins.
Counts62/12/10/12;remaining22;full96 incomplete. Continue G03 bounded family; A07 public boundary remains pending.
A07 public API/catalog boundary approval pending; independent work continues.
Initial613a56a40 push verified. Later commits local. Unrelated edits preserved.
Older blocks below are history, not new pending work.

'''+p.read_text())
subprocess.run(['python3',str(repo/'docs/manual/athena/render_reference.py')],check=True)
subprocess.run(['python3',str(q/'validate_preservation.py')],check=True)
print(json.dumps({'counts':counts,'remaining':22}))
