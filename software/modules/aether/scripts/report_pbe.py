"""Render the M15 decision only after all scheduled validation jobs finish."""
from pathlib import Path
import json,hashlib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1];out=root/'validation/milestone-15';s=json.loads((out/'summary.json').read_text())
expected=json.loads((out/'scf-cases.json').read_text())
assert all((out/'runs'/f'{case}.json').exists() for case in expected),'Unfinished SCF cases'
assert s['replay'] is not None,'Replay unfinished'
implementation=json.loads((out/'implementation-manifest.json').read_text())
changed=[p for p,h in implementation.items() if hashlib.sha256((root/p).read_bytes()).hexdigest()!=h]
by={r['case']:r for r in s['completed']}
reg={}
for name in ['AetherExactScfTest','AetherLdaExchangeTest','AetherLdaCorrelationTest','AetherSemiDirectCacheTest','AetherSemiDirectWorkflowTest']:
 p=next((out/'junit').glob('TEST-*.'+name+'.xml'));t=ET.parse(p).getroot();reg[name]=int(t.attrib['errors'])+int(t.attrib['failures'])==0
cache_regression=json.loads((out/'cache-regression.json').read_text())
refinement=all(g['last_step'][k]<abs(g['rows'][1][k]-g['rows'][0][k]) for g in s['grid_ladder'] for k in ['energy','Exc','electrons'])
passed=(len(by)==len(expected) and not s['failed'] and not s['test_failures'] and not s['frozen_changed'] and not changed
        and s['replay']['status']=='PASS' and max(s['max_errors'].values())<=1e-8 and len(s['intermediate'])==34
        and len(s['grid_ladder'])==3 and len(s['rotations'])==3 and all(reg.values()) and refinement
        and all(r['peakHeap']<=512*2**20 for r in s['completed']) and cache_regression['status']=='PASS')
m=s['max_intermediate'];errors=s['max_errors'];status='PASS' if passed else 'FAIL'
acceptance={'AO_DERIVATIVES_IMPLEMENTED':'YES','DENSITY_GRADIENT_IMPLEMENTED':'YES','PBE_EXCHANGE_IMPLEMENTED':'YES','PBE_CORRELATION_IMPLEMENTED':'YES',
 'GGA_VXC_IMPLEMENTED':'YES','KS_PBE_IMPLEMENTED':'YES','MAX_AO_DERIVATIVE_ERROR':m['maxAOderivative'],'MAX_RHO_GRADIENT_ERROR':m['maxRhoGradient'],
 'MAX_PBE_XC_ENERGY_ERROR':max(m['maxExc'],errors.get('Exc',0)),'MAX_PBE_VXC_ERROR':max(m['maxVxc'],errors.get('Vxc',0)),
 'MAX_PBE_TOTAL_ENERGY_ERROR':errors.get('total'),'GRID_CONVERGENCE_PBE':'REFINEMENT_OBSERVED; finite-grid error remains' if refinement else 'NOT_ESTABLISHED',
 'ROTATIONAL_GRID_ERROR_PBE':max(r['energy'] for r in s['rotations']),
 'BLOCKED_PBE_MEMORY_BOUNDED':'PASS' if all(r['peakHeap']<=512*2**20 for r in s['completed']) and not s['failed'] else 'REVIEW',
 'DETERMINISTIC_REPLAY':s['replay']['status'],'TOTAL_TESTS':s['tests'],'EXTERNAL_REFERENCE_SYSTEMS':13,'SCF_REFERENCE_CONFIGURATIONS':len(by),
 'M13_UNCHANGED':'PASS' if not s['frozen_changed'] else 'FAIL','M14_UNCHANGED':'PASS' if not s['frozen_changed'] else 'FAIL',
 'M14_1_UNCHANGED':'PASS' if not s['frozen_changed'] else 'FAIL','RHF_REGRESSION':'PASS' if reg['AetherExactScfTest'] else 'FAIL',
 'LDA_REGRESSION':'PASS' if all(reg[k] for k in ['AetherExactScfTest','AetherLdaExchangeTest','AetherLdaCorrelationTest']) else 'FAIL',
 'SEMI_DIRECT_CACHE_REGRESSION':'PASS' if reg['AetherSemiDirectCacheTest'] and reg['AetherSemiDirectWorkflowTest'] else 'FAIL',
 'MILESTONE_15_STATUS':status,'NEXT_MILESTONE':'DISPERSION' if passed else 'DFT_FOUNDATION_VALIDATION_REQUIRED','SCIENTIFIC_STATUS':'SCREENING_ONLY'}
(out/'acceptance.json').write_text(json.dumps(acceptance,indent=2)+'\n')
lines=[f'# Aether Milestone 15 — {status}','','SCREENING_ONLY.','','```ini']
lines += [f'{k} = {v}' for k,v in acceptance.items()];lines+=['```','',
 'PBE is an additive restricted KS path. Frozen integral, RHF, LDA, DIIS, eigensolver, grid and cache implementations are unchanged. The old ScfCycles engine owns all iterations. The new Coulomb-only reader consumes the existing binary cache format and never builds K.', '',
 f'{s["tests"]} JUnit cases passed with {s["test_failures"]} failures/errors. The full same-density intermediate suite covers 34 geometry/grid configurations; the SCF suite covers 35 configurations including the explicit basis permutation. Numerical reference comparisons use fresh PySCF 2.10.0 / Libxc 7.0.0 on normalized Cartesian def2-SVP AOs. See [PBE_PROTOCOL.md](PBE_PROTOCOL.md) for the equations, primary sources, constants, density-tail convention and conditioning-aware local derivative checks.', '',
 '## Independent implementation error', '',
 '| Quantity | Maximum absolute error | Comparison |','|---|---:|---|',
 f'| AO first derivative | {m["maxAOderivative"]:.12e} | Arbitrary and quadrature points, including translated/rotated systems |',
 f'| Density / Cartesian gradient | {m["maxRhoGradient"]:.12e} | Same supplied density and AO ordering |',
 f'| XC energy | {m["maxExc"]:.12e} | Same supplied density, same grid |',
 f'| XC potential entry | {m["maxVxc"]:.12e} | Every entry, same supplied density and grid |',
 f'| Converged total energy | {errors.get("total",float("nan")):.12e} | Independently converged finite-grid solution |',
 f'| Converged density entry | {errors.get("density",float("nan")):.12e} | Independently converged finite-grid solution |',
 f'| Orbital energy | {errors.get("orbital",float("nan")):.12e} | Independently converged finite-grid solution |','',
 'The density/gradient maximum is over the individual rho, gx, gy and gz components in their respective atomic units, not a mixed-unit Euclidean norm. The report header conservatively includes both same-density XC error and the propagated XC differences between independently converged densities. Squared-gradient sigma has different units and magnitude; its raw errors and the pointwise absolute-plus-relative test are in the intermediate evidence, not conflated with Cartesian gradient error.', '',
 '## Native molecular results', '', '| System | AO count | Iterations | Total PBE energy / hartree | Total-energy reference error | Grid electron-count error |', '|---|---:|---:|---:|---:|---:|']
for name in 'h2 h2o nh3 ch4 co n2 h2s ph3 hcl ch3cl dms trimethylsulfonium chlorobenzene'.split():
 r=by.get(name+'-native-120-590')
 if r:
  meta=json.loads((root/f'src/test/resources/totah/lab/aether/reference/pbe15/{name}-native-120-590.json').read_text())
  z={'H':1,'C':6,'N':7,'O':8,'P':15,'S':16,'Cl':17};electrons=sum(z[x] for x in meta['symbols'])-meta['charge']
  lines.append(f'| {name} | {r["aoCount"]} | {r["iterations"]} | {r["energy"]:.14f} | {r["errors"]["total"]:.6e} | {abs(r["electrons"]-electrons):.6e} |')
 else:lines.append(f'| {name} | — | — | FAILED; see trajectory | — | — |')
lines += ['', '## Finite-grid convergence and rotation', '',
 'The unchanged ladder is 40×110, 80×302, 120×590 and 160×974. For each of energy, XC energy and electron count, the final refinement change is smaller than the first on all three systems; individual steps need not be monotonic. These are unpruned Gauss-Legendre r=u/(1-u) / Lebedev grids with the frozen Becke partition. Differences to the finest sampled grid are refinement evidence, not a certified continuum quadrature error. Same-grid agreement with PySCF does not remove these differences.', '',
 '| System | Grid | Total energy | XC energy | Integrated electrons |', '|---|---|---:|---:|---:|']
for group in s['grid_ladder']:
 for r in group['rows']:lines.append(f'| {group["system"]} | {"×".join(r["case"].split("-")[-2:])} | {r["energy"]:.12f} | {r["Exc"]:.12f} | {r["electrons"]:.12f} |')
lines += ['', '| System | 120×590 → 160×974 ΔE | ΔExc | Δelectron count | Rotation ΔE at 120×590 |','|---|---:|---:|---:|---:|']
rot={r['system']:r for r in s['rotations']}
for g in s['grid_ladder']:
 d=g['last_step'];lines.append(f'| {g["system"]} | {d["energy"]:.8e} | {d["Exc"]:.8e} | {d["electrons"]:.8e} | {rot[g["system"]]["energy"]:.8e} |')
translations=[]
for name in ['h2o','ch4','nh3','h2s','ph3','hcl']:
 a=by.get(name+'-native-120-590');b=by.get(name+'-translated-120-590')
 if a and b:translations.append(abs(a['energy']-b['energy']))
if translations:lines+=['',f'Maximum translation energy residual: {max(translations):.8e} hartree. Basis permutation is checked element-by-element in the XC tests and against the remapped complete water SCF reference. Rotation residuals use a fixed lab-oriented angular grid, so they include finite angular quadrature effects.']
lines += ['', '## Bounded-memory performance', '',
 'All Aether benchmark JVMs used Java 21 and -Xmx512m, run serially. AO, derivative, rho/gradient, functional and Vxc times below are summed worker-stage elapsed seconds; they are neither CPU-time counters nor additive wall-clock timings. J and total SCF times are measured elapsed seconds. The total includes cache setup, quadrature setup, solvers, receipt hashing and other work. Cache-generation flags and complete per-case measurements remain in the raw TXT/JSON and performance CSV.', '',
 '| System | AO work s | Derivative work s | rho/gradient work s | PBE kernel work s | Vxc work s | J elapsed s | SCF elapsed s | Observed heap peak MiB | Peak RSS MiB |',
 '|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|']
for name in ['h2o','dms','chlorobenzene']:
 r=by.get(name+'-native-120-590')
 if r:
  fields=['aoNanos','derivativeNanos','rhoGradientNanos','functionalNanos','VxcNanos','JNanos','totalNanos']
  lines.append('| '+name+' | '+' | '.join(f'{r[k]/1e9:.3f}' for k in fields)+f' | {r["peakHeap"]/2**20:.2f} | {r["rss_bytes"]/2**20:.2f} |')
lines += ['', 'The working grid has 256 points per block, eight workers and at most sixteen in-flight blocks. Only block-local AO values, three derivative arrays and partial matrices are retained. Storage scales with block size and AO dimension, not total grid size. Cache payload remains on disk; the OS may retain reclaimable file pages outside Java heap and process RSS. These measurements do not bound the kernel page cache. No full-grid AO/derivative storage or quartic heap tensor was introduced.', '',
 '## Oracle convergence and preserved evidence', '',
 'Two PySCF chlorobenzene attempts using incremental Coulomb updates (core and MINAO guesses) exhausted 128 cycles. MINAO with a packed Libcint Coulomb recomputation each cycle passed the same strict external thresholds. All logs are retained. This changed only the reference execution; Aether still uses the frozen core guess, DIIS policy, 1e-12 hartree energy change, 1e-10 physical density residual and 128-iteration cap. Aether never accepts stationary energy alone.', '',
 f'All {s["frozen_files"]} frozen source/fixture/evidence file hashes remain unchanged. New implementation file changes during benchmarking: {changed}. Three common ERI payloads were rehashed and matched frozen M14.1 SHA-256 values byte-for-byte. Prior performance jobs were not rerun; selected regression tests wrote only fresh M15/build outputs. Fresh-JVM PBE receipts were compared byte-for-byte for water, N2 and hydrogen sulfide, including cold/warm cache use.', '',
 'New APIs: AoFirstDerivatives, DensityGradient, PbeFunctional, BlockedPbe, CachedCoulomb and PbeScf (KS_PBE). No existing public API or dependency changed. No Athena, METTL7, docking, model-training, hybrid-functional or dispersion work was performed.', '',
 'Machine-readable evidence: [acceptance](validation/milestone-15/acceptance.json), [summary](validation/milestone-15/summary.json), [performance](validation/milestone-15/performance.csv), [replay](validation/milestone-15/replay/result.json), and JUnit snapshots under validation/milestone-15/junit. The next milestone is proposed only; execution stops here.']
(root/'MILESTONE_15_REPORT.md').write_text('\n'.join(lines)+'\n');print(status)
