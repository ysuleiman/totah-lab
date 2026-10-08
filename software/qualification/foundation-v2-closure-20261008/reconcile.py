"""Reconcile the immutable v1 ledger with qualified bounded v2 checkpoints."""
from pathlib import Path
import json,hashlib,collections
ROOT=Path(__file__).resolve().parents[3];OUT=Path(__file__).resolve().parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def load(p):return json.loads((ROOT/p).read_text())
def pin(p):return {'path':p,'sha256':sha(ROOT/p)}
v1='software/qualification/foundation-v1-rc-20261008/FOUNDATION_V1_RELEASE_LEDGER.json';base=load(v1)
updates={
'V07':('software/qualification/foundation-v2-closure-20261008/v07-public-contract/QUALIFICATION.json','b49558e9e82e64d5f2a14514e2d0b85ed68f939f',['ATHENA.V07.MONOMER_LIBRARY_STV_INTERNAL_HEAVY_RESTRAINT_DEVIATION/1'],'Exact36 central STV heavy-atom restraint rows; continuous residuals, source-specific current TRANS applicability; no H/interresidue/statistical classifier.'),
'V08':('software/qualification/foundation-v2-closure-20261008/v08-ordered-sign/QUALIFICATION.json','b49558e9e82e64d5f2a14514e2d0b85ed68f939f',['ATHENA.V08.STV_ORDERED_CHIRAL_SIGN_JAVA21/1'],'Exact four STV sign-specific ordered numerical determinant rows; zero comparison UNKNOWN; no ideal-volume magnitude, planarity, robust near-zero stereochemistry, or VAL beta both-sign classification.'),
'V11':('software/qualification/foundation-v2-closure-20261008/v11/QUALIFICATION.json','b3ee8cfd3754531b802087b4ee83523cc21d64e1',['ATHENA.V11.L_SER_THR_VAL_UNCORRECTED_CB_DEVIATION/1'],'Ordinary internal L-SER/native L-THR/L-VAL; exact reviewed source context; uncorrected C-beta reconstruction and approved Java21 profile only.'),
'G05':('software/qualification/g05-bounded-implementation-20261008/QUALIFICATION.json','1bbbd6be562f0d2df3e88974aa6a2c888aff441e',['ATHENA.G05.SAM_CHEBI_142094_SOURCE_IDENTITY/1','ATHENA.G05.SAM_PHE_CONTINUOUS_GEOMETRY/1'],'Exact ChEBI:142094 source identity and selected Phe continuous geometry only; CCD SAM, SAH, other states and aromatic residues excluded.'),
'G06':('software/qualification/g06-water-tetrel-20261008/QUALIFICATION.json','705a2d78efc441a1b79eb661577cfe063899b465',['ATHENA.G06.SAM_WATER_METHYL_TETREL_GEOMETRIC_CANDIDATE/1'],'Exact G05 SAM and independently qualified explicit-source-H neutral I12 water only; catecholate/NAC stays blocked.'),
'V09':('software/qualification/v09-v10-implementation-20261008/QUALIFICATION.json','297736797',['ATHENA.V09.TOP8000_CCTBX_COMPILED_SIX_CLASS/1'],'Selected independently reviewed source residue context; six pinned grids; existing Athena torsions scored at identical binary64 angles; no scope expansion.'),
'V10':('software/qualification/v09-v10-implementation-20261008/QUALIFICATION.json','297736797',['ATHENA.V10.TOP8000_CCTBX_SER_THR_VAL_CHI1/1'],'Ordinary native L-SER/L-THR/L-VAL chi1 only; no named rotamers/multi-chi/other residue expansion.')}
entries=[]
for e in base['entries']:
 r={'capabilityId':e['capabilityId'],'capability':e['capability'],'v1Disposition':e['currentDisposition'],'currentDisposition':e['currentDisposition'],'scope':e.get('closure',{}).get('scope'),'v1EntryReference':e['capabilityId'],'releaseQualification':'V1_FROZEN' if e['currentDisposition']=='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED' else 'NONE','productionAuthorityImplied':False}
 if e['capabilityId'] in updates:
  p,commit,defs,scope=updates[e['capabilityId']];q=load(p)
  assert q.get('tier3Run',False)==False
  r.update(currentDisposition='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED',scope=scope,definitions=defs,qualificationEvidence=pin(p),sourceCommit=commit,releaseQualification='TIER1_TIER2_QUALIFIED_TIER3_PENDING',unrestrictedParentClosure=False)
 entries.append(r)
counts=dict(collections.Counter(e['currentDisposition'] for e in entries));assert len(entries)==96 and len({e['capabilityId'] for e in entries})==96
assert counts=={'BOUNDED_SUPPORTED_DOMAIN_QUALIFIED':75,'EXPLICIT_ARCHITECTURAL_DISPOSITION':12,'SCIENTIFIC_REVIEW_REQUIRED':2,'REQUIRES_EXTERNAL_REFERENCE_DATA':7}
ledger={'schema':'foundation-v2-ledger/1','basis':pin(v1),'asOfQualifiedSourceCommit':'b49558e9e82e64d5f2a14514e2d0b85ed68f939f','counts':counts,'full96ScientificCompletion':False,'v2Sealed':False,'countMeaning':'75 rows have a qualified bounded domain. This is not 75 unrestricted rules. Release status is separately bound to exact Tier3 evidence.','entries':entries}
seal=ROOT/'software/qualification/foundation-v2-seal-20261008/release/QUALIFICATION.json'
if seal.exists():
 seal_data=json.loads(seal.read_text())
 assert seal_data['failures']==seal_data['skips']==0 and seal_data['historicalComparisons']==65 and seal_data['preservationPins']==25
 assert seal_data['counts']==counts and seal_data['v2Replay']['status']=='PASS'
 ledger.update(v2Sealed=True,asOfQualifiedSourceCommit=seal_data['sourceCommit'],releaseQualification=pin(str(seal.relative_to(ROOT))),countMeaning='75 rows have qualified bounded domains within a sealed v2 release. 12 architectural dispositions and 9 scientific exclusions remain explicit; no unrestricted 96-row completion or production activation.')
 for entry in entries:
  if entry['currentDisposition']=='BOUNDED_SUPPORTED_DOMAIN_QUALIFIED':entry['releaseQualification']='V2_BOUNDED_TIER3_QUALIFIED'
(OUT/'FOUNDATION_V2_LEDGER.json').write_text(json.dumps(ledger,indent=2)+'\n')
print(counts)
