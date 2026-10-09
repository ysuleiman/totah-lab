"""Pin the bounded derived diagnostic and update the existing living argument."""
import hashlib,json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).resolve().parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
d=HERE/'charge-conservation'
assert (d/'RESULTS.json').read_bytes()==(d/'REPLAY.json').read_bytes()
env=ROOT/'analysis/dcmb/selectivity_validation/.conda-md'
files=[env/'lib/python3.12/site-packages/openff/toolkit/topology/molecule.py',env/'lib/python3.12/site-packages/openff/toolkit/utils/ambertools_wrapper.py']
files+=list((env/'conda-meta').glob('openff-toolkit*.json'))
files+=[HERE/'charge_conservation.py',HERE/'CHARGE_CONSERVATION_CONTRACT.txt']
files+=list(p for p in d.rglob('*') if p.is_file())
original=json.loads((HERE/'PARAMETER_PROVENANCE.json').read_text())['sha256']
for path,h in original.items():assert sha(ROOT/path)==h,path
pres=json.loads((ROOT/'reports/mettl7b-cap-tsl-coexistence-20261009/PRESERVATION_BEFORE.json').read_text())['sha256']
for path,h in pres.items():assert sha(ROOT/path)==h,path
(HERE/'CHARGE_CONSERVATION_VALIDATION.json').write_text(json.dumps(dict(replay_byte_identical=True,original_candidate_pins_verified=len(original),preservation_pins_verified=len(pres),production_qualified=False,sha256={str(p.relative_to(ROOT)):sha(p) for p in files}),indent=2)+'\n')
p=ROOT/'reports/mettl7-netarsudil-evidence-discovery-20261002/CUMULATIVE_MECHANISTIC_STATE.txt'
prior=HERE/'CUMULATIVE_MECHANISTIC_STATE.before-charge-conservation.txt'
assert not prior.exists();shutil.copyfile(p,prior)
with p.open('a') as f:f.write('''

2026-10-09 — DERIVED CHARGE-CONSERVATION DIAGNOSTIC
Computational readiness strengthened; mechanistic hypotheses unchanged. The
installed OpenFF0.18.1 documented uniform-offset normalization was executed on
separate derived parent/AR neutral candidate charge vectors. No original charge,
source coordinate, graph or non-charge topology array changed. Serialized net
charges are -3.8634e-9 e and -1.23475e-8 e, within bounds calculated from the
actual Amber decimal tokens. Original approximately+0.002 e gate failures remain
preserved. This resolves a charge-conservation dependency, not physical accuracy.
Independent fresh-process diagnostics replay byte-identically; finite Amber and
OpenMM energies still differ by approximately0.00294/0.00269 kcal/mol. No invented
parity or physical-validation threshold. Analogy torsions, protonation choice,
sampling and reaction-model gates remain open; no binding energy or MD result.
Provenance: reports/mettl7b-physics-readiness-20261009/CHARGE_CONSERVATION_CONTRACT.txt,
CHARGE_CONSERVATION_VALIDATION.json and charge-conservation/RESULTS.json.
Prior living argument is preserved as CUMULATIVE_MECHANISTIC_STATE.before-charge-conservation.txt.
''')
print('Replay, original artifacts, and preservation pins verified; cumulative delta appended.')
