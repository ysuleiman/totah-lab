"""Summarize completed M14 runs; missing runs stay explicitly pending."""
from pathlib import Path
import argparse, csv, hashlib, json, re, statistics

root=Path(__file__).resolve().parents[1]
evidence=root/'validation/milestone-14'
parser=argparse.ArgumentParser();parser.add_argument('--execution-subdirectory',default='parallel');args=parser.parse_args()
out=evidence/args.execution_subdirectory;out.mkdir(parents=True,exist_ok=True)
systems='h2 h2o nh3 ch4 co n2 h2s ph3 hcl ch3cl dms trimethylsulfonium chlorobenzene'.split()
dimers=['water_dimer','ammonium_benzene','chlorobenzene_water']
summary={'scientific_status':'SCREENING_ONLY','completed':[],'pending':[],'failed':[],
         'max_rhf_vs_m13':None,'max_lda_vs_m13':None,'max_cp_vs_m13':None,'max_uncorrected_vs_m13':None}

def old_receipt(name,method):
    path=root/f'validation/milestone-13/{name}-{method.lower()}.receipt'
    if not path.exists():
        path=evidence/'m13-recovered-receipts'/f'{name}-{method.lower()}.receipt'
        pinned=json.loads((root/'validation/milestone-13/pre-memory-gate-receipts.json').read_text())
        key=f'software/modules/aether/target/def2-validation/{path.name}'
        digest=hashlib.sha256(path.read_bytes()).hexdigest()
        if digest!=pinned[key]:raise AssertionError('Recovered historical receipt changed: '+key)
        summary.setdefault('recovered_m13_receipts',{})[str(path.relative_to(root))]=digest
    return path.read_text()

def old_energy(name,method):
    text=old_receipt(name,method)
    if method=='LDA':return float(re.findall(r'totalEnergy=([-+0-9.Ee]+)',text)[-1])
    rows=re.findall(r'\n\d+\n(-?0x[^\n]+)\n(-?0x[^\n]+)\n',text)
    return float.fromhex(rows[-1][1])

for mode,names in [('RHF',systems),('LDA',systems),('CP',dimers)]:
    for name in names:
        record=out/f'benchmark-{mode}-{name}-DIRECT_EXACT.json'
        if not record.exists():summary['pending'].append(f'{mode}:{name}');continue
        process=json.loads(record.read_text())
        if process['exit_code']!=0:summary['failed'].append(f'{mode}:{name}');continue
        suffix='cp' if mode=='CP' else mode
        text=(out/f'{name}-{suffix}-DIRECT_EXACT.txt').read_text()
        item={'system':name,'method':mode,'peak_rss_bytes':process['peak_rss_bytes'],'wall_seconds':process['elapsed_seconds'],
              'heap_limit':process['heap'],'peak_used_heap':int(re.search(r'peakHeap=(\d+)',text)[1])}
        process_log=(out/f'benchmark-{mode}-{name}-DIRECT_EXACT.log').read_text()
        peaks=re.findall(r'sampled_peak_used_heap=(\d+)',process_log)
        if peaks:item['peak_used_heap']=max(item['peak_used_heap'],*(int(p) for p in peaks))
        item['performance_nanos']={key:sum(int(v) for v in re.findall(key+r'=(\d+)',text)) for key in
            ['integralSetupNanos','jkNanos','xcNanos','eigensolveNanos','densityConstructionNanos','directIntegralNanos','directAccumulationNanos','totalNanos']}
        if mode=='CP':
            item['calculation_elapsed_nanos']=int(re.search(r'elapsedNanos=(\d+)',text)[1])
            previous=old_receipt(name,'CP')
            for field,old_field,key in [('uncorrected','uncorrectedHartree','max_uncorrected_vs_m13'),('cp','counterpoiseHartree','max_cp_vs_m13')]:
                actual=float(re.search(field+r'=OptionalDouble\[([^]]+)\]',text)[1])
                expected=float(re.search(old_field+r'=OptionalDouble\[([^]]+)\]',previous)[1])
                item[field]=actual;item[field+'_vs_m13']=abs(actual-expected);summary[key]=max(summary[key] or 0.0,abs(actual-expected))
        else:
            value=float(re.search(r'\nenergy=([^\n]+)',text)[1]);previous=old_energy(name,mode)
            item.update(ao_count=int(re.search(r'AO=(\d+)',text)[1]),iterations=int(re.search(r'iterations=(\d+)',text)[1]),
                        energy=value,m13_energy=previous,energy_difference=abs(value-previous))
            key='max_rhf_vs_m13' if mode=='RHF' else 'max_lda_vs_m13';summary[key]=max(summary[key] or 0.0,abs(value-previous))
        summary['completed'].append(item)

manifest=json.loads((evidence/'m13-frozen-manifest.json').read_text())
frozen={p:h for p,h in manifest.items() if p.startswith(('validation/','src/main/resources/','src/test/resources/'))}
summary['changed_m13_evidence_or_resources']=[p for p,h in frozen.items() if hashlib.sha256((root/p).read_bytes()).hexdigest()!=h]
summary['frozen_evidence_and_resource_files_checked']=len(frozen)
implementation=json.loads((evidence/'final-implementation-manifest.json').read_text())
summary['implementation_changed_since_final_tests']=[p for p,h in implementation.items() if hashlib.sha256((root/p).read_bytes()).hexdigest()!=h]
current_sources={str(p.relative_to(root)) for folder in ['src/main/java','src/test/java'] for p in (root/folder).rglob('*.java')}
summary['implementation_added_since_final_tests']=sorted(current_sources-set(implementation))
summary['production_changed_since_final_tests']=[p for p in summary['implementation_changed_since_final_tests'] if p.startswith('src/main/java/')]
summary['m13_source_archive_sha256']=hashlib.sha256((evidence/'m13-reference-source.tar.gz').read_bytes()).hexdigest()

summary['j_only_component_measurements']=[]
for name in ['h2o','dms']:
    file=out/f'measurement-contractions-{name}-DIRECT_EXACT.log'
    if not file.exists():file=evidence/file.name
    if not file.exists():continue
    text=file.read_text();trials=re.findall(r'round=(\d+) jkNanos=(\d+) jOnlyNanos=(\d+)',text)
    warm=[x for x in trials if int(x[0])>=2]
    if len(warm)!=3:continue
    old=statistics.median(int(x[1]) for x in warm);new=statistics.median(int(x[2]) for x in warm)
    errors=re.search(r'maxJ=([^ ]+) maxK=([^ ]+)',text)
    summary['j_only_component_measurements'].append({'system':name,'measurement_source':str(file.relative_to(root)),'paired_jk_ns':old,'coulomb_only_ns':new,
        'paired_over_coulomb_only':old/new,'max_j_difference':float(errors[1]),'max_k_difference':float(errors[2])})

summary['lda_fixed_density_step_measurements']=[]
for name in ['h2o','dms']:
    file=out/f'lda-iteration-{name}.log'
    if not file.exists():continue
    trials=[]
    for line in file.read_text().splitlines():
        if not line.startswith('LDA_ITERATION_TRIAL '):continue
        values=dict(re.findall(r'(\w+)=([^ ]+)',line))
        if int(values['round'])>=2:trials.append(values)
    if len(trials)!=3:continue
    if any(t['stateBitIdentical']!='true' for t in trials):raise AssertionError('LDA step changed')
    medians={key:statistics.median(int(t[key]) for t in trials) for key in
             ['withDiscardedKContractionNanos','jOnlyContractionNanos','withDiscardedKStepNanos','jOnlyStepNanos']}
    summary['lda_fixed_density_step_measurements'].append({'system':name,
        'measurement_source':str(file.relative_to(root)),**medians,
        'step_speedup':medians['withDiscardedKStepNanos']/medians['jOnlyStepNanos'],
        'contraction_speedup':medians['withDiscardedKContractionNanos']/medians['jOnlyContractionNanos'],
        'scope':'FIXED_CORE_GUESS_DENSITY_ONE_SHOT_NO_DIIS','state_bit_identical':True})

(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')

old_perf={(r['system'],r['method']):r for r in csv.DictReader((root/'validation/milestone-13/performance.csv').open())}
old_memory={r['system']:r for r in csv.DictReader((root/'validation/milestone-13/memory-components.csv').open())}
density={r['system']:r for r in csv.DictReader((evidence/'density-construction-measurements.csv').open())}
fields=['system','method','AO_COUNT','OLD_PEAK_HEAP','NEW_PEAK_HEAP','OLD_RSS','NEW_RSS','OLD_HEAP_BUDGET','NEW_HEAP_BUDGET','OLD_MEMORY_MEASUREMENT_SCOPE',
        'OLD_DENSITY_BUILD_CHECKS_MEDIAN_NS_PER_CALL','NEW_DENSITY_BUILD_CHECKS_MEDIAN_NS_PER_CALL',
        'OLD_PACKED_INTEGRAL_SECONDS','NEW_DIRECT_INTEGRAL_SUM_WORKER_SECONDS','NEW_JK_ACCUMULATION_SECONDS','OLD_JK_SECONDS','NEW_DIRECT_JK_SECONDS','OLD_AO_GRID_SECONDS','OLD_XC_SECONDS','NEW_BLOCKED_XC_SECONDS_INCLUDING_AO',
        'OLD_TOTAL_SECONDS','NEW_TOTAL_SECONDS','OLD_OVER_NEW_RUNTIME','PACKED_ERI_BYTES_REMOVED','NEW_GRID_BLOCK_MEMORY_BOUND_ESTIMATE_BYTES']
with (out/'performance-comparison.csv').open('w') as stream:
    writer=csv.DictWriter(stream,fieldnames=fields);writer.writeheader()
    for item in summary['completed']:
        name=item['system'];method=item['method'];old=old_perf.get((name,method),{});memory=old_memory.get(name,{})
        row={key:'UNAVAILABLE' for key in fields};row.update(system=name,method=method,AO_COUNT=item.get('ao_count',memory.get('AO_COUNT','UNAVAILABLE')),
            NEW_PEAK_HEAP=item['peak_used_heap'],NEW_RSS=item['peak_rss_bytes'],NEW_HEAP_BUDGET=item['heap_limit'],
            OLD_PEAK_HEAP=memory.get('FULL_SAMPLED_PEAK_USED_HEAP','UNAVAILABLE'),OLD_RSS=memory.get('FULL_PEAK_RSS_BYTES','UNAVAILABLE'),
            OLD_HEAP_BUDGET=memory.get('MINIMUM_KNOWN_WORKING_FULL_HEAP','UNAVAILABLE'),
            OLD_MEMORY_MEASUREMENT_SCOPE='COMBINED_RHF_LDA' if name in ['h2o','dms','trimethylsulfonium'] else ('LDA' if name=='chlorobenzene' else 'CP'))
        if name in density:
            row['OLD_DENSITY_BUILD_CHECKS_MEDIAN_NS_PER_CALL']=density[name]['old_median_ns']
            row['NEW_DENSITY_BUILD_CHECKS_MEDIAN_NS_PER_CALL']=density[name]['new_median_ns']
        row['OLD_AO_GRID_SECONDS']=old.get('AO_grid_seconds','UNAVAILABLE')
        row['OLD_PACKED_INTEGRAL_SECONDS']=old.get('integral_seconds','UNAVAILABLE')
        row['OLD_JK_SECONDS']=old.get('JK_seconds','UNAVAILABLE');row['OLD_XC_SECONDS']=old.get('XC_seconds','NOT_APPLICABLE' if method=='CP' else 'UNAVAILABLE')
        row['OLD_TOTAL_SECONDS']=old.get('total_scf_seconds','UNAVAILABLE')
        if name in ['h2o','dms','trimethylsulfonium','chlorobenzene']:
            path=root/'validation/milestone-13/memory'/('chlorobenzene-lda-xmx2g.log' if name=='chlorobenzene' else name+'-energy-retained-arrays.log')
            lines=[line for line in path.read_text().splitlines() if line.startswith('M13 '+method+' '+name+' ')]
            if lines:
                time_key='totalScfNanos' if method=='RHF' else 'totalNanos';jk_key='totalJkNanos' if method=='RHF' else 'jkNanos'
                row['OLD_TOTAL_SECONDS']=int(re.search(time_key+r'=(\d+)',lines[0])[1])/1e9
                row['OLD_JK_SECONDS']=int(re.search(jk_key+r'=(\d+)',lines[0])[1])/1e9
                integral=re.search(r'integralNanos=(\d+)',lines[0])
                if integral:row['OLD_PACKED_INTEGRAL_SECONDS']=int(integral[1])/1e9
                ao_grid=re.search(r'aoGridNanos=(\d+)',lines[0])
                if ao_grid:row['OLD_AO_GRID_SECONDS']=int(ao_grid[1])/1e9
                if method=='LDA':row['OLD_XC_SECONDS']=int(re.search(r'xcNanos=(\d+)',lines[0])[1])/1e9
        if method=='CP' and name in dimers:
            if name=='chlorobenzene_water':
                path=root/'validation/milestone-13/cp-chlorobenzene_water-recovered.log'
            else:
                filename='water-dimer-cp-xmx512m.log' if name=='water_dimer' else 'ammonium-benzene-cp-xmx2g.log'
                path=root/'validation/milestone-13/memory'/filename
            text=path.read_text()
            row['OLD_TOTAL_SECONDS']=float(re.search(r'M13 CP RESULT .*seconds=([^\s]+)',text)[1])
            row['OLD_JK_SECONDS']=sum(int(v) for v in re.findall(r'totalJkNanos=(\d+)',text))/1e9
        perf=item['performance_nanos'];row['NEW_DIRECT_JK_SECONDS']=perf['jkNanos']/1e9
        row['NEW_DIRECT_INTEGRAL_SUM_WORKER_SECONDS']=perf['directIntegralNanos']/1e9
        row['NEW_JK_ACCUMULATION_SECONDS']=perf['directAccumulationNanos']/1e9
        row['NEW_TOTAL_SECONDS']=item.get('calculation_elapsed_nanos',perf['totalNanos'])/1e9
        row['NEW_BLOCKED_XC_SECONDS_INCLUDING_AO']=perf['xcNanos']/1e9 if method=='LDA' else 'NOT_APPLICABLE'
        if isinstance(row['OLD_TOTAL_SECONDS'],(float,int)) or str(row['OLD_TOTAL_SECONDS']).replace('.','',1).isdigit():
            row['OLD_OVER_NEW_RUNTIME']=float(row['OLD_TOTAL_SECONDS'])/row['NEW_TOTAL_SECONDS']
        if row['AO_COUNT']!='UNAVAILABLE':
            n=int(row['AO_COUNT']);pairs=n*(n+1)//2
            row['PACKED_ERI_BYTES_REMOVED']=8*(pairs*(pairs+1)//2)
            row['NEW_GRID_BLOCK_MEMORY_BOUND_ESTIMATE_BYTES']=16*512*(8*(n+4)+48) if method=='LDA' else 'NOT_APPLICABLE'
        writer.writerow(row)
print('Completed',len(summary['completed']),'pending',len(summary['pending']),'failed',summary['failed'])
