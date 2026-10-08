"""Integrity audit of review artifacts only; not V09/V10 implementation qualification."""
import sys
sys.dont_write_bytecode=True
from pathlib import Path
import json,hashlib,gzip,struct,importlib.util
root=Path(__file__).resolve().parent;oracle=root/'oracle';decision=root.parent/'v09-v10-decision-20261008'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for m in json.loads((decision/'SOURCES.json').read_text()):assert sha(decision/m['file'])==m['sha256']
headers=json.loads((oracle/'UPSTREAM_HEADERS.json').read_text())['files']
for m in headers:assert sha(oracle/'upstream'/m['path'])==m['sha256']
generated=json.loads((oracle/'GENERATED_HEADERS.json').read_text())
for m in generated:assert sha(oracle/m['path'])==m['sha256']
manifest=json.loads((oracle/'FIXTURE_MANIFEST.json').read_text());blob=oracle/'UPSTREAM_FIXTURES.jsonl.gz'
assert sha(blob)==manifest['gzipSha256']
raw=gzip.decompress(blob.read_bytes());assert hashlib.sha256(raw).hexdigest()==manifest['uncompressedSha256']
records=[json.loads(l) for l in raw.splitlines()];assert len(records)==196725==manifest['records']
for r in records:
 if 'qHex' in r:assert float.fromhex(r['qHex'])==r['q']
 if r.get('angleHex') is not None:assert float.fromhex(r['angleHex'])==r['angle']
assert sum(r['kind']=='rama' and r['probe']=='all_centers' for r in records)==194400
for c in range(6):
 assert len({r['qHex'] for r in records if r['kind']=='rama' and r['class']==c and abs(r['phi'])==180 and abs(r['psi'])==180})==1
replay=json.loads((oracle/'REPLAY.json').read_text());assert all(r['sha256']==manifest['gzipSha256'] for r in replay['runs'])
branches=json.loads((oracle/'CLASS_ORACLE.json').read_text());assert len(branches['cases'])==580
assert branches['sourceSha256']==sha(decision/'reference/cctbx--mmtbx__validation__ramalyze.py')
# Independently verify native storage at every rotamer table slot, including sparse zeros.
spec=importlib.util.spec_from_file_location('pinned_ndim',decision/'reference/cctbx--mmtbx__rotamer__n_dim_table.py');mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
slots=0
for residue in ['ser','thr','val']:
 p=decision/f'reference/reference_data--Top8000__Top8000_rotamer_pct_contour_grids__rota8000-{residue}.data'
 with p.open() as f:t=mod.NDimTable.createFromText(f)
 expected=[0.0]*360
 for line in p.read_text().splitlines():
  if not line.strip() or line.startswith('#'):continue
  a,q=map(float,line.split());expected[int(a)]=struct.unpack('f',struct.pack('f',q))[0]
 assert len(t.lookupTable)==360
 for i,q in enumerate(expected):assert t.lookupTable[i]==q;slots+=1
matrix=json.loads((root/'SOURCE_PAYLOAD_MATRIX.json').read_text());assert len(matrix['cases'])==59
fields=json.loads((root/'FIELD_SET_INDEX.json').read_text())['fieldSets']
assert all(len(v)==len(set(v)) for v in fields.values())
assert 'factWitnesses' not in fields['athena-residue-context-binding/1']
result={'status':'PASS_REVIEW_ARTIFACT_INTEGRITY_ONLY','unchangedPrimaryArtifacts':31,'upstreamHeaderAndGeneratorFiles':len(headers),'generatedHeaders':len(generated),'oracleRecords':len(records),'allRamaCenters':194400,'nativeBinary32SlotsVerified':slots,'upstreamBranchProbes':580,'upstreamProcessReplay':'BYTE_IDENTICAL','originalPlannedAcceptanceCases':125,'additionalPlannedAcceptanceCases':59,'nativeAthenaTestsImplementedOrRun':0,'schemaImplementation':False,'productionActivation':False,'geometryCompositionApprovalRequired':True}
(root/'REVIEW_CHECKS.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
