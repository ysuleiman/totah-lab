"""Append the execution delta to the single living argument, preserving history."""
import json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; HERE=Path(__file__).resolve().parent
p=ROOT/'reports/mettl7-netarsudil-evidence-discovery-20261002/CUMULATIVE_MECHANISTIC_STATE.txt'
prior=HERE/'CUMULATIVE_MECHANISTIC_STATE.before-execution.txt'
assert not prior.exists();shutil.copyfile(p,prior)
with p.open('a') as f:
    f.write('''

2026-10-09 — PHYSICS EXECUTION / PARAMETER CONSTRUCTION DELTA
Affected questions: differential binding, conformational organization, catalytic
perturbation. Mechanistic hypotheses UNCHANGED: no new binding free energy,
trajectory or reaction barrier. Prior version preserved in
reports/mettl7b-physics-readiness-20261009/CUMULATIVE_MECHANISTIC_STATE.before-execution.txt.

NEW executed evidence: installed AmberTools26 completed neutral parent and AR
AM1-BCC/GAFF2 candidate construction from exact pinned source SDFs. Both charge
optimizations converged. Complete assigned parameters include unvalidated analogy
terms. Independent Amber/OpenMM single-point diagnostics are finite and replay
byte-identically in a fresh process. This strengthens computational construction
readiness only; it does not establish physical parameter accuracy or affinity.

RETAINED failures: topology charges sum about+0.002 e rather than requested zero;
SQM printed atomic values already sum+0.002 while its printed total is0.000.
No silent correction or production admission. Amber coordinate rounding was
detected and raw outputs preserved; separate exact-source coordinate bindings
were validated. Neutral is a conditional source model, not an assay-state claim.
Details and actual energy/force discrepancies: PREFLIGHT_RESULTS.txt and
parameter-preflight/INDEPENDENT_VALIDATION.json under the same report directory.

SUPERSEDED engineering stop: native scientific-runtime writes and PySCF import
succeeded after initial ENOSPC probes (HDF5 version warning retained). On explicit
user authorization,32 abandoned Git temporary packs were removed, reclaiming
25,308,430,720 bytes;24.78 GiB became available. HEAD/refs/tracked status unchanged;
real packs and research data preserved. Storage is no longer the immediate gate.

NEXT computational dependency: documented charge precision/conservation protocol,
then independent torsion/conformer and protonation validation. Matched substrate-
free MD precedes alchemical production; reaction/proton-route benchmarks precede
enzyme QM/MM. Failed TSL classical parameters remain excluded. Foundation v2,
existing assays, negative cap-obstruction findings and203 evidence are unchanged.
''')
r=json.loads((HERE/'RUNTIME_PROBE.json').read_text())
r['subsequent_native_execution']={'pyscf_version':'2.14.0','pyscf_import':'PASS with HDF5 runtime2.2.0/build2.1.0 warning','small_scientific_runtime_writes':'PASS','neutral_ligand_charge_derivations_completed':2,'production_qualified':False,'git_garbage_cleanup_receipt':'GIT_GARBAGE_CLEANUP.json'}
(HERE/'RUNTIME_PROBE.json').write_text(json.dumps(r,indent=2)+'\n')
