"""Render measured M16 gates; never substitute a final iterate for a converged energy."""
from pathlib import Path
import json,csv,subprocess,sys
r=Path(__file__).resolve().parents[1];out=r/'validation/milestone-16'
subprocess.run([sys.executable,str(r/'scripts/report_d3.py')],check=True)
subprocess.run([sys.executable,str(r/'scripts/verify_m16_receipts.py')],check=True)
subprocess.run([sys.executable,str(r/'scripts/verify_ghost_physics.py')],check=True)
def read(name):return json.loads((out/name).read_text())
def fields(p):return dict(line.split('=',1) for line in p.read_text().splitlines() if '=' in line)
s=read('summary.json');own=read('own-interaction-comparison.json')['systems'];tests=read('test-summary.json');cp=read('counterpoise-comparison.json')['systems'];method=read('method-comparison.json')['systems'];perf=read('performance.json')['systems']
with (out/'replay/1/dispersion.csv').open() as f:disp=list(csv.DictReader(f))
native_names='h2o water_dimer methane_dimer water_ammonia methanethiol_water dms hcl_water chlorobenzene benzene_methane benzene_dimer ammonium_benzene chlorobenzene_water nenci_001 nenci_024 nenci_030 nenci_067 nenci_080 cation_pi_small'.split()
cp_names='water_dimer water_ammonia hcl_water methanethiol_water ammonium_benzene chlorobenzene_water'.split()
finished=all((out/'runs'/f'{n}.json').exists() for n in native_names) and all((out/'cp-runs'/f'{n}.json').exists() for n in cp_names)
failures=[]
for folder,names in [('runs',native_names),('cp-runs',cp_names)]:
 for n in names:
  p=out/folder/f'{n}.json'
  if p.exists() and json.loads(p.read_text())['exit_code']!=0:failures.append(folder+'/'+n)
replay=all((out/p/'result.json').exists() and read(p+'/result.json').get(key)=='PASS' for p,key in [
 ('replay','D3_FRESH_JVM_REPLAY'),('replay-pbe-post-api','PBE_D3_FRESH_JVM_REPLAY'),('replay-cp','GHOST_PBE_D3_FRESH_JVM_REPLAY'),('replay-cp-aggregate','TYPED_CP_AGGREGATE_FRESH_JVM_REPLAY')])
passed=finished and len(own)==15 and max((x['PBE_D3_error'] for x in own),default=1)<1e-8 and s['max_PBE_error']<1e-8 and s['max_D3_error']<1e-12 and not failures and len(cp)==6 and len(method)==6 and len(s['components_compared'])==48 and not s['unexpected_frozen_changes'] and s['approved_API_change_matches_tested_patch'] and replay and s['max_PBE_D3_error']<1e-8 and max(x['CP_error'] for x in cp)<1e-8 and tests['total_tests']>=81 and all(int(x['failures'])==0 and int(x['errors'])==0 and int(x['skipped'])==0 for x in tests['suites']) and read('parameter-verification.json')['NAMED_PBE_PARAMETERS']=='PASS' and read('non-ghost-receipt-regression.json')['result']=='PASS' and read('ghost-physical-validation.json')['result']=='PASS'
status='PASS' if passed else ('FAIL' if finished else 'IN_PROGRESS')
s['MILESTONE_16_STATUS']=status;s['CP_VALIDATION']='PASS' if len(cp)==6 and max(x['CP_error'] for x in cp)<1e-8 else 'IN_PROGRESS'
(out/'summary.json').write_text(json.dumps(s,indent=2)+'\n')
def interaction(name,key):
 p=out/'runs'/f'{name}-interaction.txt'
 return fields(p)[key] if p.exists() else 'UNAVAILABLE_PENDING_OR_FAILED_SCF'
report={
'DISPERSION_METHOD':'PAIRWISE_D3; ATM_NOT_IMPLEMENTED','DAMPING':'BJ; PBE s6=1 s8=0.7875 a1=0.4289 a2=4.4407',
'PBE_DISPERSION_IMPLEMENTED':'YES','MAX_PAIR_TERM_ERROR':max(float(x['pair_error']) for x in disp),'MAX_TOTAL_DISPERSION_ERROR':max(float(x['total_error']) for x in disp),
'MAX_PBE_D3_TOTAL_ENERGY_ERROR':s['max_PBE_D3_error'],
'MAX_CP_CORRECTED_INTERACTION_ERROR':max((x['CP_error'] for x in cp),default=None),
'BENZENE_DIMER_PBE':interaction('benzene_dimer','PBE'),'BENZENE_DIMER_PBE_D3':interaction('benzene_dimer','PBE_D3'),
'AMMONIUM_BENZENE_PBE':interaction('ammonium_benzene','PBE'),'AMMONIUM_BENZENE_PBE_D3':interaction('ammonium_benzene','PBE_D3'),
'SULFUR_MODEL_PBE':interaction('methanethiol_water','PBE'),'SULFUR_MODEL_PBE_D3':interaction('methanethiol_water','PBE_D3'),
'CHLORINE_MODEL_PBE':interaction('chlorobenzene_water','PBE'),'CHLORINE_MODEL_PBE_D3':interaction('chlorobenzene_water','PBE_D3'),
'EXTERNAL_INTERACTION_BENCHMARK':f'{len(method)}/6 native cases compared; five CCSD(T)/CBS and one finite-basis CCSD(T)',
'DISPERSION_RUNTIME_OVERHEAD':{x['system']:x['overhead_percent'] for x in perf},'DETERMINISTIC_REPLAY':'PASS' if replay else 'PENDING',
'TOTAL_TESTS':tests['total_tests'],'MILESTONE_16_STATUS':status}
if passed:report['NEXT_MILESTONE']='INTERACTION_ENERGY_BENCHMARK'
(out/'acceptance.json').write_text(json.dumps({'scientific_status':'SCREENING_ONLY','report':report,'failed_runs':failures,'completed':finished,'native_CP_cases_compared':len(cp)},indent=2)+'\n')
lines=['# Aether Milestone 16 — PBE-D3(BJ)','',f'**{status} — SCREENING_ONLY.** All energies below are hartree.','',
 'Pairwise D3 is added after the PBE calculation. E_PBE denotes the total KS energy, including physical nuclear repulsion. PBE density, equations, basis, grid, SCF thresholds and initial guess are unchanged. ATM three-body dispersion is not implemented.', '',
 'The requested headline interaction values below are explicitly **own-basis, not CP-corrected**. Electronic counterpoise values are listed separately; D3 always uses only physical atoms.', '', '```ini']
lines += [f'{k} = {v}' for k,v in report.items()];lines += ['```','','## Electronic counterpoise with physical-only dispersion','',
 'Eint_CP = E_PBE(AB) - E_PBE(A with ghost B) - E_PBE(B with ghost A) + D3(real AB) - D3(real A) - D3(real B).',
 'The monomer grid is centered on its physical atoms; donor atoms supply AOs only. Ghosts add no nuclear charge, electrons, occupation or nuclear repulsion. D3 coordination numbers are recomputed for each physical system, and the dispersion difference is added once.', '',
 '| System | PBE CP | Physical ΔD3 | PBE-D3 CP | External CP error |','|---|---:|---:|---:|---:|']
for x in cp:lines.append(f"| {x['system']} | {x['PBE_CP']:.15g} | {x['physical_D3_difference']:.15g} | {x['PBE_D3_CP']:.15g} | {x['CP_error']:.3g} |")
lines += ['',f'{len(cp)}/6 CP cases compared. Raw component energies, physical electron counts and nuclear repulsion are in `cp-runs/` and `cp-reference/`. No final unconverged iterate is reported as an energy result.','',
 '## Higher-level interaction benchmark','',
 'These errors are distinct from implementation-validation error. Table errors are signed (Aether minus reference); MAE uses absolute errors. The five NENCI references are CCSD(T)/CBS at the exact published geometries. The native comparison uses finite Cartesian def2-SVP with own-basis electronic energies, so method, basis incompleteness, finite-grid quadrature and BSSE contributions remain combined. The ammonium–ethylene comparison is a separate fresh frozen-core CCSD(T)/def2-SVP reference, **not CBS**, and is excluded from CBS statistics. NENCI rows use their published geometries, distinct from the M13 dimers. These are not protein binding energies.','',
 '| System | Reference | PBE | PBE-D3 | PBE error | PBE-D3 error |','|---|---:|---:|---:|---:|---:|']
for x in method:lines.append(f"| {x['system']} | {x['reference_hartree']:.12g} | {x['PBE']:.12g} | {x['PBE_D3']:.12g} | {x['PBE_error_hartree']:.5g} | {x['PBE_D3_error_hartree']:.5g} |")
cbs=[x for x in method if x['reference_method'].startswith('CCSD(T)/CBS')]
if len(cbs)==5:
 before=sum(abs(x['PBE_error_hartree']) for x in cbs)/5;after=sum(abs(x['PBE_D3_error_hartree']) for x in cbs)/5
 improved=sum(abs(x['PBE_D3_error_hartree'])<abs(x['PBE_error_hartree']) for x in cbs)
 lines += ['',f'For the five CBS-reference cases, own-basis MAE changes from {before:.10g} to {after:.10g} hartree. Absolute errors improve on {improved}/5 cases. This small finite-basis/grid benchmark is not a general accuracy guarantee.']
lines += ['', 'No claim is made that dispersion improves every interaction. Examine signed errors and the CP comparison before attributing overbinding to the functional alone.', '',
 'Reference provenance: `nenci-selection.json`, [NENCI-2021](https://doi.org/10.1063/5.0068862), [author-attributed mirror](https://doi.org/10.60732/5d2a1ceb), and `cc-reference/`. Compiled simple-dftd3 1.2.1 is the independent dispersion oracle; fresh PySCF 2.10.0 / Libxc 7.0.0 supplies electronic references.', '',
 '## Performance','', '| System | D3 seconds | PBE seconds | Overhead % | Peak heap bytes | Process peak RSS bytes |','|---|---:|---:|---:|---:|---:|']
for x in perf:lines.append(f"| {x['system']} | {x['dispersion_seconds']:.6g} | {x['PBE_seconds']:.6g} | {x['overhead_percent']:.6g} | {x['peak_heap_bytes']} | {x['peak_process_RSS_bytes']} |")
lines += ['', 'Evaluation overhead = 100 × D3 evaluation time / PBE time; small table-loading cost is excluded. Heap covers the benchmark process; RSS may include sequential monomer calculations. Independent oracle/test jobs can overlap, so these are observed validation timings, not an isolated hardware comparison. Native heaps are bounded at 512 MiB.', '',
 '## Regression and provenance','',f"{tests['total_tests']} recorded JUnit tests. Fresh ghost and non-ghost replay: {'PASS' if replay else 'PENDING'}. Unexpected frozen file changes: {len(s['unexpected_frozen_changes'])}.",
 'The only approved historical source change is the additive PbeScf ghost overload. `approved-api-change.json` proves that reversing exactly the approved patch recovers the historical source hash. Existing signatures and non-ghost protocol/receipts remain unchanged. Frozen M13/M14/M14.1/M15 evidence files are not rewritten.',
 'Post-approval RHF/LDA/PBE/cache regression is retained under `junit/`; water non-ghost receipts match their pre-overload baseline byte-for-byte. D3 and ghost receipts reproduce across fresh Java 21 JVMs. Details: `replay/`, `replay-pbe-post-api/`, `replay-cp/`, `counterpoise-comparison.json`, `summary.json`.', '',
 'The method and atomic-table hashes, cutoff/CN definitions and source licenses are in `../../D3_PROTOCOL.md` and `../../src/main/resources/totah/lab/aether/dispersion/`. No M17 work is started.']
if failures:lines += ['', 'Failed native runs: '+', '.join(failures)]
(out/'MILESTONE_16_REPORT.md').write_text('\n'.join(lines)+'\n')
print(status, 'CP cases',len(cp),'method cases',len(method),'failed',failures)
