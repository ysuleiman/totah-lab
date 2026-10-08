"""Reconcile the immutable v1 ledger with qualified bounded v2 checkpoints."""
from pathlib import Path
import json,hashlib,collections
ROOT=Path(__file__).resolve().parents[3];OUT=Path(__file__).resolve().parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def load(p):return json.loads((ROOT/p).read_text())
def pin(p):return {'path':p,'sha256':sha(ROOT/p)}
v1='software/qualification/foundation-v1-rc-20261008/FOUNDATION_V1_RELEASE_LEDGER.json';base=load(v1)
updates={
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
assert counts=={'BOUNDED_SUPPORTED_DOMAIN_QUALIFIED':72,'EXPLICIT_ARCHITECTURAL_DISPOSITION':12,'SCIENTIFIC_REVIEW_REQUIRED':2,'REQUIRES_EXTERNAL_REFERENCE_DATA':10}
ledger={'schema':'foundation-v2-ledger/1','basis':pin(v1),'asOfQualifiedRemoteHead':'cc04752752bbe2e002cb223568f217b759766e8b','counts':counts,'full96ScientificCompletion':False,'v2Sealed':False,'countMeaning':'72 rows have a qualified bounded domain. This is not 72 unrestricted rules and not a v2 Tier3 release certificate.','entries':entries}
(OUT/'FOUNDATION_V2_LEDGER.json').write_text(json.dumps(ledger,indent=2)+'\n')
(OUT/'REMOTE_HEAD.json').write_text(json.dumps({'remote':'origin','branch':'codex/mmp-chemistry-roundtrip','verifiedRemoteHead':'cc04752752bbe2e002cb223568f217b759766e8b','verification':'git ls-remote origin refs/heads/codex/mmp-chemistry-roundtrip','unrelatedWorkingTreeEditsIncluded':False},indent=2)+'\n')
print(counts)
