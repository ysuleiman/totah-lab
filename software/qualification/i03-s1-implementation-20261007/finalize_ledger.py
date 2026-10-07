"""Reconcile only I03 after successful qualification. Preserve historical gates and all other rows."""
from pathlib import Path
import collections,copy,hashlib,json,subprocess
repo=Path.cwd();q=repo/'software/qualification/i03-s1-implementation-20261007';f=repo/'software/qualification/chemistry-geometry-foundation-20261005'
relative=lambda p:str(p.relative_to(repo))
write=lambda p,x:p.write_text(json.dumps(x,indent=2)+'\n')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
v=json.loads((q/'QUALIFICATION.json').read_text());assert v['S1BoundedImplementationQualified'] and v['separateI03CompositionQualified'] and v['failures']==0
assert v['productionReceiptsIssued']==0 and v['S1AcceptanceMatrixCases']==94
checkpoint=relative(q/'CHECKPOINT.txt');qualification=relative(q/'QUALIFICATION.json')
ledger=json.loads((f/'CAPABILITY_LEDGER.json').read_text());row=next(r for r in ledger['entries'] if r['capabilityId']=='I03')
if 'historicalPreS1Gate' not in row:
 row['historicalPreS1Gate']={k:copy.deepcopy(row[k]) for k in ['currentDisposition','closure','currentWorkAssessment','remainingClosureDependency','remainingClosureAssessment','currentContractReview','proposedAssignmentSourceReview','sourceImplementation']}
row['currentDisposition']='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED'
row['closure'].update({'scope':'Approved I03-A six neutral amine donor/acceptor pairs; heavy-atom minimum-over-neighbors directional proxy with independently qualified bounded S1 source assignments. No observed/inferred H coordinates or physical hydrogen-bond assertion.',
 'qualificationBasis':f"{v['cleanCommittedSourceTests']} fresh committed-source tests +3 isolation;{v['S1AndCompositionTests']} S1/composition tests, including16 separately governed composition checks;94 S1 matrix cases;16 independent JVM pairs;13 legacy hashes unchanged;65 historical files exact;25 pins intact. No production authority.",
 'existingImplementation':'Opt-in athena.i03-n-sp3-s1/1 source producer and athena.implicit-h-proxy-s1/1 composition; historical I03-A version1.0.0 remains blocked and unchanged.',
 'qualificationEvidence':[qualification,relative(q/'MATRIX_EXECUTION.json'),relative(q/'REPLAY.json')],
 'outsideBoundedDomain':'No I03-B/C, inferred-H coordinates, SP/SP2 proxy, full ProLIF compatibility, new chemistry roles or universal hybridization. S1 exclusions remain outside this model. Real execution requires independent exact source-scope, S1 producer and composition qualification under current authority.',
 'scientificSupport':'User-approved I03-A contract and bounded S1 source-graph model; source differences and external-only RDKit/OCL probes preserved in linked review. No toolkit is an assignment authority.',
 'currentPolicyQualified':False})
row['currentWorkAssessment']={'classification':'BOUNDED_SUPPORTED_DOMAIN_QUALIFIED','artifact':checkpoint,'remainingDependency':None}
row['remainingClosureDependency']=None;row['remainingClosureAssessment']=checkpoint
row['currentContractReview'].update({'date':'2026-10-07','status':'APPROVED_BOUNDED_S1_AND_SEPARATE_I03_COMPOSITION_QUALIFIED','dispositionChanged':True,'qualification':qualification})
row['proposedAssignmentSourceReview'].update({'status':'APPROVED_IMPLEMENTED_AND_BOUNDED_IMPLEMENTATION_QUALIFIED','qualifiedProducer':True,'qualificationScope':'Implementation within user-approved model; no production authority or source receipt','newScientificPayloadSubcontract':'Approved ATHENA.I03.SP3_SOURCE_SCOPE/1 and additive /2; exact source witness review remains independently required'})
row['sourceImplementation'].update({'status':'BOUNDED_SOURCE_AND_COMPOSITION_QUALIFICATION_PASSED','sourceCommit':v['sourceCommit'],'qualification':qualification,'productionAuthority':False})
row['note']='Bounded implementation closure only. Source-scope review, producer qualification and I03 composition authority remain independent at each real invocation.'
write(f/'CAPABILITY_LEDGER.json',ledger)
progress=json.loads((f/'PROGRESS.json').read_text())
progress.setdefault('historicalPreS1Progress',{k:copy.deepcopy(progress[k]) for k in ['activeWork','stopBoundary','pendingReview','currentI03Phase','currentI03SourceReview','finalCleanSourceCheckpoint','finalCleanSourceValidation','newSourceWholeFoundationQualification']})
counts=dict(collections.Counter(r['currentDisposition'] for r in ledger['entries']));assert sum(counts.values())==96
progress['dispositionCounts']=counts
progress['remaining']=[x for x in progress['remaining'] if x!='I03'];assert len(progress['remaining'])==27
progress['activeWork']='Approved S1/source-scope /1,/2 and separate I03-A composition implemented and qualified. This phase is complete.'
progress['stopBoundary']='No next-family implementation authorized by this checkpoint. I04 remains scientifically blocked;12 external-data rows untouched. Real production activation requires independent current scientific/source authority; no receipts issued. No push.'
progress['pendingReview']['capabilities']=[x for x in progress['pendingReview']['capabilities'] if x!='I03']
progress['pendingReview']['I03']='Bounded implementation qualified; broader definitions unapproved and current production authority not issued.'
progress['currentI03Phase']={'artifact':checkpoint,'status':'BOUNDED_IMPLEMENTATION_QUALIFIED','scientificActivation':'OPT_IN_COMPOSITION_REQUIRES_INDEPENDENT_CURRENT_AUTHORITY; HISTORICAL_BLOCK_PRESERVED','I04Implemented':False}
progress['currentI03SourceReview']={'artifact':checkpoint,'status':'S1_SOURCE_SCOPE_1_AND_2_IMPLEMENTED_AND_QUALIFIED','implemented':True,'producerQualified':True,'qualificationScope':'Bounded implementation; no production authority','productionAuthority':False}
progress['finalCleanSourceCheckpoint']=checkpoint;progress['finalCleanSourceValidation']=v
progress['newSourceWholeFoundationQualification']=f"IMPLEMENTED_BOUNDED_DOMAINS_PASS_{v['cleanCommittedSourceTests']}_PLUS_3_ISOLATION; FULL96_INCOMPLETE"
progress['remaining27Assessment']=relative(q/'REMAINING27.json');progress['programComplete']=False
write(f/'PROGRESS.json',progress)
previous=repo/'software/qualification/water-bridge-implementation-20261006/REMAINING28.json'
remaining=json.loads(previous.read_text())
remaining['basis']='Derived tracking extract after bounded S1/I03-A implementation closure. Prior maps preserved; no new scientific criteria or datasets.'
remaining['ledgerSha256']=sha(f/'CAPABILITY_LEDGER.json');remaining['priorDependencyMap']=relative(previous);remaining['priorDependencyMapSha256']=sha(previous)
remaining['closedInThisFamily']=['I03'];remaining['definitionRepresentationRequirements']=[r for r in remaining['definitionRepresentationRequirements'] if r['capabilityId']!='I03']
assert len(remaining['definitionRepresentationRequirements'])==15 and len(remaining['externalReferenceDataRequirements'])==12
write(q/'REMAINING27.json',remaining)
summary=f'''QUALIFIED BOUNDED S1 SOURCE AND SEPARATE I03-A COMPOSITION — 2026-10-07
Source commit: {v['sourceCommit']}
Read QUALIFICATION.json, MATRIX_EXECUTION.json, REPLAY.json and IMPLEMENTATION_NOTES.txt.

{v['cleanCommittedSourceTests']} fresh committed-source tests and3 isolated Mnemosyne tests pass; no failures/skips.
{v['S1AndCompositionTests']} S1/composition tests: {v['S1ProducerScopeAndPredicateTests']} producer/scope/predicate and16 separate composition checks.
94 S1 review cases mapped to executed checks.59 historical I03 and98 water tests remain passing.
16 independent JVM pairs;13 prior hashes unchanged;65 historical files byte-identical;25 preservation pins intact.
{v['protectedTrackedFilesUnchanged']} protected tracked files unchanged, including pre-existing unrelated edits.

Approved S1 formal source-graph classifier and source-scope /1 plus additive /2 implemented.
KNOWN_NON_ORDINARY_CONNECTION requires independent exact source witness review;
UNKNOWN is never reinterpreted as known coordination or ordinary covalent connectivity.
S1/source scope and I03 composition qualifications are separate. No geometry in S1.
New opt-in I03 composition1.1.0 can evaluate only with independent current qualifications;
historical I031.0.0 unconditional block and historical replay are preserved.
No production receipt, scientific authority, default toolkit source or new chemistry role.

I03 is bounded implementation-qualified under the standing ledger categories, not production-activated.
Full96 remains incomplete:57 bounded,12 architectural,15 scientific-review,12 external-data rows.
REMAINING27.json preserves the12 external-data rows separately. I04 remains scientifically blocked.
I02, I12/I13, role manifests, event/path infrastructure and unrelated work unchanged.
Local commits only; no push. Do not begin a different family from this checkpoint without new instruction.
'''
(q/'CHECKPOINT.txt').write_text(summary)
resume=f'''CURRENT VERIFIED RESUME — S1 AND SEPARATE I03-A COMPOSITION QUALIFIED — 2026-10-07
Read {checkpoint} first; IMPLEMENTATION_NOTES.txt explains authority separation.
Source {v['sourceCommit'][:9]};{v['cleanCommittedSourceTests']} clean-source+3 isolation;{v['S1AndCompositionTests']} S1/composition tests;94 S1 cases.
16 JVM pairs;13 prior hashes unchanged;65 historical comparisons;25 pins pass.
Source scope /1 preserved; approved /2 distinguishes known nonordinary source connections from UNKNOWN.
Opt-in I03 composition independently gated; historical I03-A block unchanged; no production authority issued.
I04/external12/I02/water/roles/unrelated edits preserved. Counts57/12/15/12;remaining27;full96 incomplete.
No push. This approved phase is complete; no other scientific family implemented.
Older resume blocks below are preserved history, not pending instructions.

'''
p=f/'START_HERE.txt';text=p.read_text()
if not text.startswith('CURRENT VERIFIED RESUME — S1 AND SEPARATE'):p.write_text(resume+text)
subprocess.run(['python3',str(repo/'docs/manual/athena/render_reference.py')],check=True)
subprocess.run(['python3',str(q/'validate_preservation.py')],check=True)
print(json.dumps({'counts':counts,'remaining':27,'checkpoint':checkpoint},indent=2))
