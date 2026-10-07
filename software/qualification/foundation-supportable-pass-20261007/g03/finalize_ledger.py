"""Record only G03's bounded closure after clean-source qualification."""
from pathlib import Path
import collections,copy,hashlib,json,subprocess
repo=Path.cwd();q=repo/'software/qualification/foundation-supportable-pass-20261007';f=repo/'software/qualification/chemistry-geometry-foundation-20261005'
rel=lambda p:str(p.relative_to(repo));write=lambda p,v:p.write_text(json.dumps(v,indent=2)+'\n');sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
v=json.loads((q/'g03/QUALIFICATION.json').read_text());assert v['boundedImplementationQualified'] and v['failures']==0 and v['productionReceiptsIssued']==0
ledger=json.loads((f/'CAPABILITY_LEDGER.json').read_text());before=copy.deepcopy(ledger);row=next(r for r in ledger['entries'] if r['capabilityId']=='G03')
row.setdefault('historicalPreG03Gate',{k:copy.deepcopy(row[k]) for k in ['currentDisposition','closure','currentWorkAssessment','remainingClosureDependency','remainingClosureAssessment']})
row['currentDisposition']='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED'
row['closure'].update({'scope':'Exact selected source MET/PHE pair under the finite neutral amide-context graph contract, identical ring isotope descriptors, and qualified positive SD-centroid distance <=7 A. Raw normal angle is diagnostic only. Chalcogen-O is outside this closure.',
'qualificationBasis':f"{v['cleanCommittedSourceTests']} fresh committed-source tests +3 isolation;{v['focusedG03Tests']} focused G03;22 JVM pairs,21 prior hashes unchanged;65 historical files and25 pins exact.",
'existingImplementation':'Opt-in athena.met-phe-survey/1 leaf and two declarative group/4 source contexts. Existing geometry/3 POINT_PAIR_GROUP, matching, source scope, correspondence and Research Gate reused unchanged.',
'qualificationEvidence':[rel(q/'g03/QUALIFICATION.json'),rel(q/'g03/MATRIX_EXECUTION.json'),rel(q/'g03/REPLAY.json')],
'scientificSupport':'Pinned L04 source survey convention, intentionally bounded Phe-only source graph. No energy, affinity, biological role, sulfur-state transfer or chalcogen-O inference.',
'currentPolicyQualified':False,'newScientificDefinitionApproved':True,'reviewRequirements':[],
'outsideBoundedDomain':'Chalcogen-O, Tyr/Trp/fused/hetero rings, sulfur oxidation/charge/extra substituents, free termini, explicit-H source graphs, missing/conflicting source facts, mixed ring isotope descriptors and nonordinary connections. No whole-system absence, no energy, occupancy or biological interpretation.'})
row['currentWorkAssessment']={'classification':'BOUNDED_SUPPORTED_DOMAIN_QUALIFIED','artifact':rel(q/'g03/CHECKPOINT.txt'),'remainingDependency':None};row['remainingClosureDependency']=None;row['remainingClosureAssessment']=rel(q/'g03/CHECKPOINT.txt')
row['currentContractReview']={'date':'2026-10-08','status':'BOUNDED_IMPLEMENTATION_QUALIFIED','contract':rel(q/'g03/CONTRACT.txt'),'authorization':rel(q/'REQUEST.txt'),'sourceCommit':v['sourceCommit'],'productionAuthority':False}
for old,new in zip(before['entries'],ledger['entries']):
 if old['capabilityId']!='G03':assert old==new,old['capabilityId']
write(f/'CAPABILITY_LEDGER.json',ledger)
counts=dict(collections.Counter(r['currentDisposition'] for r in ledger['entries']));assert sum(counts.values())==96 and counts['BOUNDED_SUPPORTED_DOMAIN_QUALIFIED']==63 and counts['SCIENTIFIC_REVIEW_REQUIRED']==9
progress=json.loads((f/'PROGRESS.json').read_text());progress['dispositionCounts']=counts;progress['remaining']=[x for x in progress['remaining'] if x!='G03'];assert len(progress['remaining'])==21
progress['programComplete']=False;progress['activeWork']='G03 bounded Met/Phe source-survey contact clean-source qualified. Remaining supportable proposals I04 option B and A07 native-query boundary await explicit approval; other exact scientific/protocol blockers recorded. Full96 incomplete.'
progress['stopBoundary']='Do not implement unapproved I04 definition or A07 public API/schema changes. Preserve every external-data row, old scientific semantics, and unrelated edits. No further push or production authority.'
progress['finalCleanSourceCheckpoint']=rel(q/'g03/CHECKPOINT.txt');progress['finalCleanSourceValidation']=v;progress['newSourceWholeFoundationQualification']=f"IMPLEMENTED_BOUNDED_DOMAINS_PASS_{v['cleanCommittedSourceTests']}_PLUS_3_ISOLATION; FULL96_INCOMPLETE"
progress['currentSupportablePass'].update({'status':'G03_QUALIFIED_PENDING_APPROVALS_AND_EXACT_SCIENTIFIC_DATA_BLOCKERS','remainingAssessment':rel(q/'REMAINING21.json'),'localExternalDataCharacterization':rel(q/'EXTERNAL_DATA_REASSESSMENT.json'),'pendingPublicBoundaryReview':rel(q/'a07/NATIVE_QUERY_BOUNDARY_REVIEW.txt'),'pendingScientificDefinitionReview':rel(q/'i04/EXPLICIT_GLYCINE_OPTION_REVIEW.txt')})
write(f/'PROGRESS.json',progress)
prior=q/'REMAINING22.json';remaining=json.loads(prior.read_text());remaining.update({'basis':'Bounded G03 Met/Phe source-survey contact closed; chalcogen-O outside this closure. All other rows and12 external requirements preserved.','ledgerSha256':sha(f/'CAPABILITY_LEDGER.json'),'priorDependencyMap':rel(prior),'priorDependencyMapSha256':sha(prior),'closedInThisFamily':['G03']});remaining['definitionRepresentationRequirements']=[r for r in remaining['definitionRepresentationRequirements'] if r['capabilityId']!='G03'];assert len(remaining['definitionRepresentationRequirements'])==9;write(q/'REMAINING21.json',remaining)
(q/'g03/CHECKPOINT.txt').write_text(f'''QUALIFIED BOUNDED G03 — 2026-10-08
Source commit: {v['sourceCommit']}
Contract: CONTRACT.txt. Qualification: QUALIFICATION.json, MATRIX_EXECUTION.json, REPLAY.json.
{v['cleanCommittedSourceTests']} fresh committed-source tests +3 isolation;{v['focusedG03Tests']} G03 tests pass, no failures/skips.
22 independent JVM pairs;21 previous hashes unchanged;65 historical files and25pins exact.
30294 protected tracked files unchanged, including unrelated tracked edits.
No new public payload/API or generic geometry/matcher; I02/water/S1/I03/roles/event/path unchanged.
Selected Met/Phe survey contact only. No chalcogen-O, energy, occupancy, biological role or system-wide absence claim.
No production authority/receipt/default activation. No later push.
Full96 incomplete:63 bounded,12 architectural,9 scientific-review,12 external-data;21 unresolved.
''')
(q/'CHECKPOINT.txt').write_text('''QUALIFIED SUPPORTABLE PASS CHECKPOINT — 2026-10-08
Initial613a56a40 pushed and remote-verified; see PUSH_VERIFICATION.json. Later commits remain local.
Read g03/CHECKPOINT.txt for the latest sealed source; REMAINING21.json is current.
Closed this pass: I10, I14, N08 (explicit user approval), A08, P07, bounded G03 Met/Phe contact.
63 bounded qualified /12 architectural /9 scientific-review /12 external-data. Full96 incomplete.
Approval remains pending for:
- I04 option B: i04/EXPLICIT_GLYCINE_OPTION_REVIEW.txt (material scientific-definition choice).
- A07: a07/NATIVE_QUERY_BOUNDARY_REVIEW.txt (public native-query/catalog contract).
N08 approval applies only to N08 and has already been implemented/qualified.
I11/I15/I17/V18/G05/G06 exact source/protocol gaps are recorded in their family directories.
V04 native-query representability depends on the unapproved A07 boundary; no physical-query chemistry equivalence is inferred.
External12 availability rechecked: actual local COD/MTZ/NEF/example maps characterized, none silently qualified.
See EXTERNAL_DATA_REASSESSMENT.json. Availability is separate from a selected attributable protocol.
G03 chalcogen-O and broader sulfur/ring chemistry are explicitly outside its qualified bounded contact.
Preserve old scientific semantics, all unrelated edits, historical replay and pins. No further push or production authority.
''')
p=f/'START_HERE.txt';p.write_text(f'''CURRENT RESUME — G03 CLEAN-SOURCE QUALIFIED; PENDING APPROVALS — 2026-10-08
Read {rel(q/'CHECKPOINT.txt')} and REQUEST.txt first.
G03 source{v['sourceCommit'][:9]} passes{v['cleanCommittedSourceTests']}+3 tests,22JVM pairs,65historical,25pins.
Counts63/12/9/12;remaining21;full96 incomplete. See REMAINING21.json.
I04 option B scientific choice and A07 public API/catalog boundary remain pending; N08 approval already implemented.
Initial613a56a40 push verified. Later commits local. Unrelated edits preserved.
Older blocks below are history, not pending work.

'''+p.read_text())
subprocess.run(['python3',str(repo/'docs/manual/athena/render_reference.py')],check=True)
subprocess.run(['python3',str(q/'validate_preservation.py')],check=True)
print(json.dumps({'counts':counts,'remaining':21}))
