"""Audit-ready method assessment, retaining absent evidence and separate features."""
from pathlib import Path
import csv,hashlib,json,math
from collections import Counter
from m17_metrics import summarize,classify,sign_correct,d3_change,diagnosed_iteration_cap,NEUTRAL

ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'validation/milestone-17'
KCAL=627.5094740631
METHODS={'RHF':'RHF_CP','PBE':'PBE_CP','PBE_D3':'PBE_D3_CP'}
CLASSES=('HYDROGEN_BOND','DISPERSION','PI_PI','CATION_PI','IONIC','SULFUR','HALOGEN','MIXED')
specs=json.loads((OUT/'benchmark.json').read_text())
basis_plan=json.loads((OUT/'basis-plan.json').read_text())
basis_comparisons_expected=2*len(basis_plan['selected'])

def read_values(path):
    if not path.exists():return {}
    result={}
    for line in path.read_text().splitlines():
        if '=' in line:
            k,v=line.split('=',1)
            try:result[k]=float(v)
            except ValueError:result[k]=v
    return result

def external(name,basis,method):
    rows=[]
    for role in ('AB','A_GHOST_B','B_GHOST_A'):
        p=OUT/'external'/basis/name/(role+'-'+method+'.json')
        if not p.exists():return None
        x=json.loads(p.read_text())
        if not x['converged']:return None
        rows.append(x)
    return rows[0]['energy']-rows[1]['energy']-rows[2]['energy']

rows=[];finished=[];failed=[];component_failures=[]
for name,s in specs.items():
    p=OUT/'runs'/name/'execution.json'
    if p.exists():
        execution=json.loads(p.read_text());finished.append(name)
        if execution['exit_code']!=0:failed.append(name)
    values=read_values(OUT/'runs'/name/'interaction.txt')
    for role in ('AB','A_GHOST_B','B_GHOST_A'):
        for method in ('RHF','PBE'):
            component=read_values(OUT/'runs'/name/(role+'-'+method+'.txt'))
            if component.get('status') not in (None,'CONVERGED'):
                log=OUT/'runs'/name/'run.log'
                trajectory=[line for line in log.read_text().splitlines() if line.startswith(role+' '+method+' ') and 'iteration=' in line]
                component_failures.append({'system':name,'role':role,'method':method,'status':component['status'],
                    'final_ten_iterations':trajectory[-10:],
                    'interpretation':'No energy accepted; convergence failure is not a physical-method error or proof of an integral defect'})
    row={'system':name,'classes':s['classes'],'REFERENCE_ENERGY':s['reference_hartree']*KCAL,
         'reference_method':s['reference_method'],'units':'kcal/mol','scientific_status':'SCREENING_ONLY',
         'complete':all(k in values and math.isfinite(values[k]) for k in METHODS.values())}
    for method,key in METHODS.items():
        value=values.get(key)
        if isinstance(value,float) and math.isfinite(value):
            energy=value*KCAL;error=energy-row['REFERENCE_ENERGY']
            row[method]={'ENERGY':energy,'ERROR':error,'ABSOLUTE_ERROR':abs(error),
                         'SIGN_CORRECT':sign_correct(row['REFERENCE_ENERGY'],energy)}
    dispersion=read_values(OUT/'dispersion'/name/'dispersion.txt')
    for key in ('D3_ENERGY','D3_DELTA'):
        if key in dispersion:
            assert math.isfinite(dispersion[key])
            if key in values:assert values[key]==dispersion[key],(name,key,'D3 changed with SCF context')
            values[key]=dispersion[key]
    row['features_hartree']={k:values.get(k) for k in ('PBE_CP','D3_ENERGY','PBE_D3_CP','RHF_CP','D3_DELTA')}
    if 'PBE' in row and 'PBE_D3' in row:
        row['D3_CHANGE']=d3_change(row['REFERENCE_ENERGY'],row['PBE']['ENERGY'],row['PBE_D3']['ENERGY'])
    rows.append(row)

stats={}
for label in ('GLOBAL',)+CLASSES+('SULFUR_AROMATIC','SULFUR_POLAR'):
    members=[r for r in rows if label=='GLOBAL' or label in r['classes'] or
             (label=='SULFUR_AROMATIC' and r['system'] in ('des_h2s_benzene','des_methanethiol_benzene')) or
             (label=='SULFUR_POLAR' and r['system'] in ('nenci_067','nenci_096'))]
    done=[r for r in members if r['complete']]
    refs=[r['REFERENCE_ENERGY'] for r in done]
    count=len(set(specs[r['system']]['independent_pair'] for r in done))
    entry={'planned':len(members),'complete':len(done),'independent_pairs':count,
           'D3_COUNTS':dict(Counter(r['D3_CHANGE'] for r in members if 'D3_CHANGE' in r)),'methods':{}}
    for method in METHODS:
        method_done=[r for r in members if method in r]
        method_refs=[r['REFERENCE_ENERGY'] for r in method_done]
        method_count=len(set(specs[r['system']]['independent_pair'] for r in method_done))
        summary=summarize(method_refs,[r[method]['ENERGY'] for r in method_done])
        summary['independent_pairs']=method_count
        summary['STATUS']='NOT_APPLICABLE_GLOBAL_MIX' if label=='GLOBAL' else classify(summary,method_refs,method_count,len(method_done)!=len(members))
        entry['methods'][method]=summary
        for row in method_done:
            comparisons={other['system']:((row[method]['ENERGY']-other[method]['ENERGY'])*(row['REFERENCE_ENERGY']-other['REFERENCE_ENERGY'])>0)
                         for other in method_done if other is not row and abs(row['REFERENCE_ENERGY']-other['REFERENCE_ENERGY'])>NEUTRAL}
            row[method].setdefault('RANKING_CORRECT',{})[label]=comparisons
    stats[label]=entry

common_rows=[r for r in rows if r['complete']]
common_statistics={m:summarize([r['REFERENCE_ENERGY'] for r in common_rows],
                              [r[m]['ENERGY'] for r in common_rows]) for m in METHODS}

sensitivity=[];implementation=[];external_method_rows=[];component_agreement=[];d3_agreement=[]
for name,s in specs.items():
    row=next(r for r in rows if r['system']==name)
    external_row={'system':name,'REFERENCE_ENERGY':s['reference_hartree']*KCAL,'classes':s['classes'],
                  'provenance':'Independent PySCF equations/basis/grid evaluation; not a replacement for a failed native receipt'}
    for method in ('RHF','PBE'):
        svp=external(name,'def2-svp',method);tz=external(name,'def2-tzvp',method)
        if svp is not None:
            external_row[method]={'ENERGY':svp*KCAL,'ERROR':(svp-s['reference_hartree'])*KCAL}
            if method=='PBE':
                ds=[json.loads((OUT/'external/def2-svp'/name/(role+'-PBE.json')).read_text())['D3'] for role in ('AB','A_GHOST_B','B_GHOST_A')]
                if row['features_hartree']['D3_DELTA'] is not None:
                    d3_agreement.append({'system':name,'error_hartree':row['features_hartree']['D3_DELTA']-(ds[0]-ds[1]-ds[2])})
                corrected=svp+ds[0]-ds[1]-ds[2]
                external_row['PBE_D3']={'ENERGY':corrected*KCAL,'ERROR':(corrected-s['reference_hartree'])*KCAL}
        for role in ('AB','A_GHOST_B','B_GHOST_A'):
            native=read_values(OUT/'runs'/name/(role+'-'+method+'.txt'))
            path=OUT/'external/def2-svp'/name/(role+'-'+method+'.json')
            if native.get('status')=='CONVERGED' and path.exists():
                oracle=json.loads(path.read_text())
                if oracle['converged']:
                    component_agreement.append({'system':name,'role':role,'method':method,
                        'error_hartree':native['energy']-oracle['energy']})
        if svp is not None and method in row:
            implementation.append({'system':name,'method':method,'same_basis_error_hartree':row[method]['ENERGY']/KCAL-svp})
        if svp is not None and tz is not None:
            item={'system':name,'method':method,'SVP_CP':svp*KCAL,'TZVP_CP':tz*KCAL,
                'BASIS_SHIFT':(tz-svp)*KCAL,'SVP_ERROR':(svp-s['reference_hartree'])*KCAL,
                'TZVP_RESIDUAL_ERROR':(tz-s['reference_hartree'])*KCAL,
                'interpretation':'External-only; finite grid; TZVP is not CBS; residual is not pure functional error'}
            if method=='PBE':
                d=[json.loads((OUT/'external/def2-svp'/name/(role+'-PBE.json')).read_text())['D3'] for role in ('AB','A_GHOST_B','B_GHOST_A')]
                delta=d[0]-d[1]-d[2]
                item['PBE_D3_SVP_ERROR']=(svp+delta-s['reference_hartree'])*KCAL
                item['PBE_D3_TZVP_RESIDUAL_ERROR']=(tz+delta-s['reference_hartree'])*KCAL
            sensitivity.append(item)
    external_method_rows.append(external_row)
external_method_stats={}
for label in ('GLOBAL',)+CLASSES:
    external_method_stats[label]={}
    for method in METHODS:
        selected=[r for r in external_method_rows if method in r and (label=='GLOBAL' or label in r['classes'])]
        refs=[r['REFERENCE_ENERGY'] for r in selected]
        summary=summarize(refs,[r[method]['ENERGY'] for r in selected])
        planned=sum(label=='GLOBAL' or label in s['classes'] for s in specs.values())
        count=len(set(specs[r['system']]['independent_pair'] for r in selected))
        summary['STATUS']='NOT_APPLICABLE_GLOBAL_MIX' if label=='GLOBAL' else classify(summary,refs,count,len(selected)!=planned)
        summary['planned']=planned
        external_method_stats[label][method]=summary
baseline=json.loads((OUT/'frozen-manifest.json').read_text());changes=[];metadata_changes=[]
# Preserve the original manifest, including Finder metadata accidentally captured
# there. Report those exact metadata paths separately; every other hash remains
# a scientific/source freeze gate.
finder_metadata={'.DS_Store','validation/.DS_Store'}
basis_summary={}
for method in ('RHF','PBE','PBE_D3'):
    selected=[s for s in sensitivity if s['method']==('PBE' if method=='PBE_D3' else method)]
    if not selected:continue
    old='PBE_D3_SVP_ERROR' if method=='PBE_D3' else 'SVP_ERROR'
    new='PBE_D3_TZVP_RESIDUAL_ERROR' if method=='PBE_D3' else 'TZVP_RESIDUAL_ERROR'
    basis_summary[method]={'n':len(selected),'mean_absolute_basis_shift':sum(abs(s['BASIS_SHIFT']) for s in selected)/len(selected),
        'max_absolute_basis_shift':max(abs(s['BASIS_SHIFT']) for s in selected),
        'svp_MAE':sum(abs(s[old]) for s in selected)/len(selected),
        'tzvp_residual_MAE':sum(abs(s[new]) for s in selected)/len(selected),
        'absolute_error_improves':sum(abs(s[new])<abs(s[old])-NEUTRAL for s in selected)}
for name,digest in baseline.items():
    p=ROOT/name
    with p.open('rb') as f:actual=hashlib.file_digest(f,'sha256').hexdigest()
    if actual!=digest:
        if name in finder_metadata:
            metadata_changes.append({'path':name,'baseline_sha256':digest,'actual_sha256':actual})
        else:changes.append(name)
replay=OUT/'replay/result.json';replay_pass=replay.exists() and json.loads(replay.read_text()).get('status')=='PASS'
d3_replay=OUT/'replay-d3/result.json'
d3_replay_pass=d3_replay.exists() and json.loads(d3_replay.read_text()).get('status')=='PASS'
historical={}
cp_context=[]
for name in ('nenci_001','nenci_067','nenci_080','nenci_024','nenci_030'):
    current=OUT/'runs'/name/'AB-PBE.receipt'
    if current.exists():historical[name]=current.read_bytes()==(ROOT/'validation/milestone-16/runs'/(name+'.receipt')).read_bytes()
    row=next(r for r in rows if r['system']==name)
    own=read_values(ROOT/'validation/milestone-16/runs'/(name+'-interaction.txt'))
    if 'PBE' in row and 'PBE_D3' in row:
        cp_context.append({'system':name,'own_PBE_error':own['PBE']*KCAL-row['REFERENCE_ENERGY'],
            'CP_PBE_error':row['PBE']['ERROR'],'own_PBE_D3_error':own['PBE_D3']*KCAL-row['REFERENCE_ENERGY'],
            'CP_PBE_D3_error':row['PBE_D3']['ERROR'],'electronic_CP_shift':row['PBE']['ENERGY']-own['PBE']*KCAL})
complete=len(finished)==len(specs)
useful=[c for c in CLASSES+('SULFUR_POLAR','SULFUR_AROMATIC') if stats[c]['methods']['PBE_D3']['STATUS'] in ('VALIDATED_USEFUL','QUALITATIVE_ONLY')]
domain=[]
for c in useful:
    s=stats[c]['methods']['PBE_D3']
    if s['STATUS']=='VALIDATED_USEFUL':scope='sampled energy/sign/order criteria met; no broad transfer claim'
    elif s['pairwise_rank_comparisons'] and s['pairwise_rank_correct']==s['pairwise_rank_comparisons']:
        scope='attraction signs and observed pair order only; exploratory'
    else:scope='attraction signs only; energetic ordering is not reliable'
    domain.append(c+': '+scope)
regression=OUT/'regression-complete.json'
regression_pass=regression.exists() and json.loads(regression.read_text()).get('status')=='PASS'
external_done=len(list((OUT/'external/def2-svp').glob('*/*.json')))==102
basis_done=all((OUT/'external/def2-tzvp'/name/(role+'-'+method+'.json')).exists()
               for name in basis_plan['selected'] for role in ('AB','A_GHOST_B','B_GHOST_A') for method in ('RHF','PBE'))
ready=complete and external_done and basis_done and replay_pass and d3_replay_pass and regression_pass
agreement_pass=all(abs(x['same_basis_error_hartree'])<1e-7 for x in implementation) and all(abs(x['error_hartree'])<1e-7 for x in component_agreement) and all(abs(x['error_hartree'])<1e-12 for x in d3_agreement)
# M17 assesses a domain of validity, not universal SCF success. The preregistered
# protocol permits explicitly diagnosed scientific failures; their energies stay
# absent and affected class evidence stays insufficient. Harness failures do not
# qualify. This does not change accuracy tiers or any production calculation.
diagnosed_failures=[]
for name in failed:
    components=[read_values(OUT/'runs'/name/(role+'-'+method+'.txt'))
                for role in ('AB','A_GHOST_B','B_GHOST_A') for method in ('RHF','PBE')]
    if diagnosed_iteration_cap([c.get('status') for c in components]):
        diagnosed_failures.append(name)
expected_comparisons=sum(method in row for row in rows for method in ('RHF','PBE'))
external_converged=external_done and all(json.loads(p.read_text())['converged'] for p in (OUT/'external/def2-svp').glob('*/*.json'))
status='PASS' if ready and not changes and useful and agreement_pass and len(sensitivity)==basis_comparisons_expected and len(implementation)==expected_comparisons and external_converged and set(diagnosed_failures)==set(failed) and len(historical)==5 and all(historical.values()) else 'FAIL' if ready else 'IN_PROGRESS'
result={'status':status,'scientific_status':'SCREENING_ONLY','completed_native':len(finished),
        'planned_native':len(specs),'failed_native':failed,'component_failures':component_failures,'rows':rows,'statistics':stats,
        'common_cohort_systems':[r['system'] for r in common_rows],'common_cohort_statistics':common_statistics,
        'diagnosed_native_convergence_failures':diagnosed_failures,'all_native_cases_converged':complete and not failed,
        'acceptance_scope':'Method-domain characterization; a PASS never asserts universal native convergence or universal accuracy',
        'basis_sensitivity':sensitivity,'basis_summary':basis_summary,'basis_plan':basis_plan,'implementation_agreement':implementation,
        'component_agreement':component_agreement,'external_method_rows':external_method_rows,'external_method_statistics':external_method_stats,
        'D3_oracle_agreement':d3_agreement,
        'frozen_changes':changes,'filesystem_metadata_changes':metadata_changes,
        'frozen_non_metadata_file_count':len(set(baseline)-finder_metadata),
        'historical_PBE_receipt_comparisons':historical,'deterministic_replay':'PASS' if replay_pass and d3_replay_pass else 'PENDING',
        'D3_deterministic_replay':'PASS' if d3_replay_pass else 'PENDING','M16_own_basis_vs_M17_CP':cp_context,
        'useful_sampled_classes':useful,'regression':'PASS' if regression_pass else 'PENDING',
        'next_milestone':'ATHENA_AETHER_FRAGMENT_INTERFACE' if status=='PASS' else None}
(OUT/'assessment.json').write_text(json.dumps(result,indent=2,allow_nan=False)+'\n')
(OUT/'d3-oracle-agreement.json').write_text(json.dumps({
    'comparisons':d3_agreement,
    'units':'hartree',
    'quantity':'D3_AB - D3_A - D3_B',
    'max_absolute_error':max((abs(x['error_hartree']) for x in d3_agreement),default=None),
    'status':'SCREENING_ONLY'},indent=2,allow_nan=False)+'\n')
with (OUT/'features.csv').open('w',newline='') as f:
    writer=csv.writer(f);writer.writerow(['system','reference_hartree','PBE_CP_INTERACTION_ENERGY','D3_ENERGY','PBE_D3_CP_INTERACTION_ENERGY','RHF_CP_INTERACTION_ENERGY','D3_DELTA','RHF_EVALUATED','PBE_EVALUATED','D3_EVALUATED','ALL_METHODS_COMPLETE','status'])
    for row in rows:writer.writerow([row['system'],specs[row['system']]['reference_hartree'],*row['features_hartree'].values(),
        'RHF' in row,'PBE' in row,row['features_hartree']['D3_ENERGY'] is not None,row['complete'],'SCREENING_ONLY'])
lines=['# Aether M17 — physical interaction-energy validation','',f'**{status} — SCREENING_ONLY**',
       '',f'Completed native cases: {len(finished)}/{len(specs)}. Energies/errors below are kcal/mol.',
       f'Native convergence failures: {len(failed)}. A characterization PASS does not certify these failed calculations; their affected interaction energies remain unavailable and their domains remain explicitly limited.',
       'Native methods use Cartesian def2-SVP and electronic counterpoise. PBE uses the frozen 120×590 physical-center grid. D3(BJ) is pairwise with the frozen PBE parameters; no ATM term.',
       'This is a small, selected-geometry gas-phase benchmark. It does not validate protein binding, solvation, or broad chemical ranking.',
       '', '## Per-system method errors', '', '| System | Reference | RHF error | PBE error | PBE-D3 error | D3 effect |', '|---|---:|---:|---:|---:|---|']
for row in rows:
    def absent(method):
        scf_method='PBE' if method=='PBE_D3' else method
        known_failure=any(f['system']==row['system'] and f['method']==scf_method for f in component_failures)
        return 'UNAVAILABLE (SCF failure)' if known_failure else 'PENDING'
    e=[f"{row[m]['ERROR']:.6f}" if m in row else absent(m) for m in METHODS]
    system_label=f"{row['system']} — {specs[row['system']]['name']}"
    lines.append(f"| {system_label} | {row['REFERENCE_ENERGY']:.6f} | {' | '.join(e)} | {row.get('D3_CHANGE',absent('PBE'))} |")
lines+=['','## Global comparison on the same available systems','',
        'This shared cohort permits a like-for-like method comparison when SCF failures give methods different availability. It still excludes missing cases and is subject to convergence-selection bias.',
        '', '| Method | Common cases | MAE | RMSE | Max abs error | Bias | Spearman |',
        '|---|---:|---:|---:|---:|---:|---:|']
for method,s in common_statistics.items():
    vals=[f"{s[k]:.5f}" if k in s and s[k] is not None else 'NA' for k in ('MAE','RMSE','MAX_ERROR','BIAS','SPEARMAN')]
    lines.append(f"| {method} | {s['n']}/{len(specs)} | {' | '.join(vals)} |")
lines+=['','## Class-specific assessment','','| Class | Complete/planned | Method | MAE | RMSE | Max abs error | Bias | Spearman | Status |','|---|---:|---|---:|---:|---:|---:|---:|---|']
for label,entry in stats.items():
    for method,s in entry['methods'].items():
        vals=[f"{s[k]:.5f}" if k in s and s[k] is not None else 'NA' for k in ('MAE','RMSE','MAX_ERROR','BIAS','SPEARMAN')]
        lines.append(f"| {label} | {s['n']}/{entry['planned']} | {method} | {' | '.join(vals)} | {s['STATUS']} |")
lines+=['','Spearman is undefined for n<2 or constant ranks; n=2 necessarily gives a trivial ±1, not robust ranking validation. Individual signs, absolute errors, and pairwise ordering checks are retained in assessment.json. Class labels overlap; global rows count each geometry once. Two-example conclusions are exploratory.',
        '', '## Basis sensitivity (external PySCF only)', '', '| System | Method | SVP CP | TZVP CP | Basis shift | SVP error | TZVP residual error | D3-corrected TZVP error |','|---|---|---:|---:|---:|---:|---:|---:|']
for s in sensitivity:lines.append('| '+s['system']+' | '+s['method']+' | '+' | '.join(f'{s[k]:.6f}' for k in ('SVP_CP','TZVP_CP','BASIS_SHIFT','SVP_ERROR','TZVP_RESIDUAL_ERROR'))+' | '+(f"{s['PBE_D3_TZVP_RESIDUAL_ERROR']:.6f}" if 'PBE_D3_TZVP_RESIDUAL_ERROR' in s else 'NA')+' |')
for method,s in basis_summary.items():
    lines+=['',f"{method}: mean absolute basis shift {s['mean_absolute_basis_shift']:.4f}, maximum {s['max_absolute_basis_shift']:.4f} kcal/mol. Subset MAE changes from {s['svp_MAE']:.4f} to {s['tzvp_residual_MAE']:.4f}; absolute error improves on {s['absolute_error_improves']}/{s['n']} cases."]
lines+=['','The two-basis shift measures sensitivity, not CBS convergence. Residual error combines functional/method, remaining basis and finite-grid errors. Anions can require diffuse functions absent from both bases. No production basis or functional was added.',
        'The optional larger-basis subset is limited to four smaller preselected dimers. Ammonium–benzene and H2S–benzene were deferred on cost grounds before any TZVP results; aromatic/cation–pi basis sensitivity is unassessed. See basis-plan.json and basis-cost-projection.json. No primary benchmark case was removed.',
        '', '## Evidence and limits', '',f'Frozen scientific/source file changes: {len(changes)} across {result["frozen_non_metadata_file_count"]} files. Replay: {result["deterministic_replay"]}.',
        f'Finder metadata changes: {len(metadata_changes)}. Original and current hashes are retained in assessment.json; only the two explicitly named .DS_Store paths are excluded from the scientific freeze gate. The original frozen manifest is unchanged.',
        f'Exact M16 PBE receipt comparisons: {sum(historical.values())}/{len(historical)} completed; five planned. These compare full canonical iteration evidence, not just rounded energies.',
        f'Independent geometry-only D3 replay over all 17 cases: {result["D3_deterministic_replay"]}. The full RHF/PBE CP replay uses water as a bounded representative; it is not a claim that all large SCF jobs were executed twice.',
        'All features remain separate in features.csv. D3_ENERGY is physical AB dispersion; D3_DELTA is D3_AB-D3_A-D3_B. Electronic energies alone receive counterpoise.',
        f"D3 improvement/worsening counts assess {sum(stats['GLOBAL']['D3_COUNTS'].values())}/{len(specs)} native cases with both PBE and PBE-D3 energies. Missing SCF results are excluded, not counted as neutral.",
        'Independent mathematical-method results, including systems unavailable from the frozen native SCF workflow, are reported separately in [EXTERNAL_METHOD_REPORT.md](EXTERNAL_METHOD_REPORT.md). They never replace missing native energies.',
        'Selection, assessment thresholds and citations: [PROTOCOL.md](PROTOCOL.md). Source rows/geometry hashes: [benchmark.json](benchmark.json). Machine-readable results: [assessment.json](assessment.json).',
        'No M18 work is started.']
lines+=['','## Why the M16 own-basis comparison is not the M17 CP comparison','',
        'These five geometries are shared with M16. Historical own-basis values are read unchanged; the new electronic counterpoise shift is shown explicitly. D3 uses the same physical geometry in both conventions.',
        '', '| System | Own PBE error | CP PBE error | Own PBE-D3 error | CP PBE-D3 error | Electronic CP shift |',
        '|---|---:|---:|---:|---:|---:|']
for s in cp_context:lines.append('| '+s['system']+' | '+' | '.join(f'{s[k]:.6f}' for k in ('own_PBE_error','CP_PBE_error','own_PBE_D3_error','CP_PBE_D3_error','electronic_CP_shift'))+' |')
if component_failures:
    lines+=['','## Convergence limitations','',
            'These are missing calculations, not numerical interaction energies. Frozen SCF settings were retained. Final trajectories are in assessment.json; no integral defect is inferred from nonconvergence.',
            'Native global metrics use only available converged cases, with method-specific denominators shown above. They are subject to convergence-selection bias and are not full-benchmark accuracy estimates when cases are missing.']
    for f in component_failures:lines.append(f"- {f['system']} / {f['role']} / {f['method']}: {f['status']}.")
    lines+=['','Final criterion values and the distinct failure patterns are recorded in [SCF_LIMITATIONS.md](SCF_LIMITATIONS.md).']
    lines+=['','An independent PySCF evaluation of the same equations/basis/grid is retained separately in assessment.json (external_method_rows/statistics). It can distinguish mathematical-method error from native SCF failure, but is never substituted into native features or receipts.']
headline={'BENCHMARK_SYSTEMS':f'{len(finished)}/{len(specs)} finished; {len(component_failures)} nonconverged components',
          'INTERACTION_CLASSES':', '.join(CLASSES)}
for method in METHODS:
    g=stats['GLOBAL']['methods'][method]
    headline[method+'_GLOBAL_MAE']=f"{g['MAE']:.6f} kcal/mol ({g['n']}/{len(specs)} available)" if 'MAE' in g else 'PENDING'
for effect,key in [('IMPROVES','D3_IMPROVED_COUNT'),('WORSENS','D3_WORSENED_COUNT'),('NEUTRAL','D3_NEUTRAL_COUNT')]:
    headline[key]=stats['GLOBAL']['D3_COUNTS'].get(effect,0)
for c in CLASSES:headline[c+'_STATUS']=stats[c]['methods']['PBE_D3']['STATUS']
headline.update({'BASIS_SENSITIVITY':f'{len(sensitivity)}/{basis_comparisons_expected} method/system comparisons; external-only def2-TZVP; aromatic/cation-pi sensitivity unassessed',
    'FUNCTIONAL_METHOD_ERROR':'Residual error is not uniquely separable from remaining basis/grid error',
    'AETHER_PHYSICS_DOMAIN_OF_VALIDITY':'; '.join(domain) if ready else 'ASSESSMENT_IN_PROGRESS',
    'MILESTONE_17_STATUS':status})
for m in ('M13','M14','M14_1','M15','M16'):headline[m+'_UNCHANGED']='PASS' if not changes else 'FAIL'
headline['DETERMINISTIC_REPLAY']=result['deterministic_replay']
if status=='PASS':headline['NEXT_MILESTONE']='ATHENA_AETHER_FRAGMENT_INTERFACE'
lines+=['','## Required report','',
    'The compact class-status fields below refer to PBE-D3(BJ)/def2-SVP + CP. They do not transfer to RHF or uncorrected PBE; the class table above assesses each method separately.',
    '', '```ini']+[f'{k} = {v}' for k,v in headline.items()]+['```']
lines+=['','## Interpretation boundary','',
    'VALIDATED_USEFUL denotes only the small sampled domain under the preregistered criteria. QUALITATIVE_ONLY permits cautious sign/trend interpretation, not quantitative energy ranking. UNRELIABLE classes must not be used as calibrated physical predictors; INSUFFICIENT_EVIDENCE supplies no accuracy claim.',
    'The tested domain is fixed, published, approximately equilibrium gas-phase dimers with closed-shell fragments. It does not cover pose scans, dissociation curves, solvent, protein polarization, many-body nonadditivity, proton transfer, open shells, or ligand binding free energies.',
    'IONIC here samples ion–water interactions, not ion–ion salt bridges. HALOGEN is a chlorine-containing composition category: the chlorobenzene–water geometry has C–Cl–O ≈180°, whereas chloromethane–water has ≈73°. One linear halogen-bond example cannot validate halogen bonding generally. PI_PI includes one stacked and one T-shaped benzene pair. CATION_PI covers central ammonium/methylammonium contacts, not all charged aromatic geometries. See motif-geometry-audit.json.',
    'Sulfur coverage is neutral H2S/thiol/thioether chemistry. Sulfonium, oxidized sulfur and phosphorus interaction accuracy are not established by this benchmark, even though some such atoms/systems are supported computationally.',
    'RHF/PBE/PBE-D3 values and the geometry-only D3 correction remain separate features. This benchmark creates no master score and no AI or Athena integration.']
(OUT/'MILESTONE_17_REPORT.md').write_text('\n'.join(lines)+'\n')
supplement=['# Independent mathematical-method evidence','',
    'SCREENING_ONLY. These are PySCF calculations of the same RHF/PBE equations, Cartesian def2-SVP, physical-center 120×590 grid and electronic CP convention, with the M16 physical-only D3 correction.',
    'They use PySCF’s MINAO initial guess/SCF implementation. They are not native Aether results and never replace missing native features or receipts. This separation matters when the frozen Aether core-guess workflow fails to converge.',
    'Errors are relative to the published CCSD(T)/CBS benchmarks, in kcal/mol. This is method-error evidence, distinct from Java-versus-PySCF numerical agreement.',
    '', '| System | Reference | RHF error | PBE error | PBE-D3 error |', '|---|---:|---:|---:|---:|']
for r in external_method_rows:
    supplement.append('| '+r['system']+f" | {r['REFERENCE_ENERGY']:.6f} | "+' | '.join(f"{r[m]['ERROR']:.6f}" if m in r else 'PENDING' for m in METHODS)+' |')
supplement+=['','| Class | Method | n/planned | MAE | RMSE | Max | Bias | Spearman | Sampled method status |','|---|---|---:|---:|---:|---:|---:|---:|---|']
for label,methods in external_method_stats.items():
    for method,s in methods.items():
        supplement.append(f"| {label} | {method} | {s['n']}/{s['planned']} | "+' | '.join(f'{s[k]:.5f}' if k in s and s[k] is not None else 'NA' for k in ('MAE','RMSE','MAX_ERROR','BIAS','SPEARMAN'))+f" | {s['STATUS']} |")
supplement+=['','The same preregistered accuracy tiers apply, with the same small-sample limitations. These tiers characterize mathematical-method evidence; they do not override native convergence failures or the M17 acceptance decision. No functional or parameter has been fitted.']
(OUT/'EXTERNAL_METHOD_REPORT.md').write_text('\n'.join(supplement)+'\n')
print(status,'native',len(finished),'/',len(specs),'basis rows',len(sensitivity),'frozen changes',changes)
