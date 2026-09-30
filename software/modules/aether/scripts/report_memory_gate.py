"""Render only observed memory-gate outcomes; pending records never become passes."""
from pathlib import Path
import csv,hashlib,json,re

ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'validation/milestone-13';LOG=OUT/'memory'
def read_json(path,default=None):return json.loads(path.read_text()) if path.exists() else default
def text(path):return path.read_text(errors='replace') if path.exists() else ''
def outcome(label):
    value=read_json(LOG/(label+'.json'))
    return 'PENDING' if value is None else 'PASS' if value['exit_code']==0 else 'FAIL'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

compatibility=outcome('canonical-compatibility')
cases=re.findall(r'MEMORY_HASH_COMPATIBILITY system=(\S+) basis=(\S+)',text(LOG/'canonical-compatibility.log'))
if compatibility=='PASS' and len(set(cases))!=30:compatibility='FAIL'
if compatibility=='PASS' and len(re.findall(r'MEMORY_HASH_COMPATIBILITY .*byte_equivalent=true',text(LOG/'canonical-compatibility.log')))!=30:compatibility='FAIL'
serial=outcome('serial-historical-regression');replay=outcome('def2-replay')
counts=read_json(LOG/'serial-historical-regression-junit/counts.json',{})
if serial=='PASS' and counts!={'tests':317,'failures':0,'errors':0,'skipped':0}:serial='FAIL'

baseline=read_json(OUT/'pre-milestone-file-hashes.json',{})
frozen={name:sha(ROOT/name)==expected for name,expected in baseline.items() if name.endswith('.receipt') and (ROOT/name).exists()}
active={}
for name,old in read_json(OUT/'frozen-receipt-checks.json',{}).items():
    path=Path(name)
    if 'milestone-10.2' in name:target=ROOT/'target/diis-validation'/path.name.replace('-fixed','')
    elif 'milestone-11' in name:target=ROOT/'target/interaction-validation'/path.name
    else:target=ROOT/'target/ks-validation'/path.name
    active[name]=target.exists() and sha(target)==old['sha256']
unchanged=len(frozen)==54 and all(frozen.values()) and len(active)==32 and all(active.values())
m13_baseline=read_json(OUT/'pre-memory-gate-receipts.json',{})
m13_unchanged={name:(ROOT/'target/def2-validation'/Path(name).name).exists() and sha(ROOT/'target/def2-validation'/Path(name).name)==value
               for name,value in m13_baseline.items()}

systems=['h2o','dms','trimethylsulfonium','chlorobenzene','water_dimer','ammonium_benzene']
full_labels={'h2o':'h2o-energy-retained-arrays','dms':'dms-energy-retained-arrays',
 'trimethylsulfonium':'trimethylsulfonium-energy-retained-arrays','chlorobenzene':'chlorobenzene-lda-xmx2g',
 'water_dimer':'water-dimer-cp-xmx512m','ammonium_benzene':'ammonium-benzene-cp-xmx2g'}
working_heaps={'h2o':'512m','dms':'1g','trimethylsulfonium':'1536m','chlorobenzene':'2g','water_dimer':'512m','ammonium_benzene':'2g'}
rows=[]
for name in systems:
    probe=text(LOG/('components-'+name+'.log'))
    entries=re.findall(r'^MEMORY_COMPONENT (.+)$',probe,re.M)
    row={'system':name,'component_status':outcome('components-'+name),'full_status':outcome(full_labels[name])}
    if entries:
        for key,value in (part.split('=',1) for part in entries[-1].split()):
            row[key]=int(value) if re.fullmatch(r'-?\d+',value) else value
    full=text(LOG/(full_labels[name]+'.log'))
    peaks=re.findall(r'^MEMORY_HEAP .*sampled_peak_used_heap=(\d+).*max_heap=(\d+)',full,re.M)
    if peaks:row['FULL_SAMPLED_PEAK_USED_HEAP']=int(peaks[-1][0]);row['FULL_MAX_HEAP']=int(peaks[-1][1])
    record=read_json(LOG/(full_labels[name]+'.json'),{})
    row['FULL_PEAK_RSS_BYTES']=record.get('peak_rss_bytes','UNAVAILABLE')
    component_record=read_json(LOG/('components-'+name+'.json'),{})
    row['COMPONENT_PEAK_RSS_BYTES']=component_record.get('peak_rss_bytes','UNAVAILABLE')
    for mode,values in re.findall(r'^MEMORY_RETAINED_EVIDENCE \S+ (RHF|LDA|CP) \{([^}]+)\}',full,re.M):
        for key,value in (item.split('=') for item in values.split(', ')):row[mode+'_'+key]=int(value)
    row['MINIMUM_KNOWN_WORKING_FULL_HEAP']=working_heaps[name] if row['full_status']=='PASS' else 'PENDING'
    rows.append(row)

full_ok=all(row['full_status']=='PASS' for row in rows)
bounded_hash=all('RECEIPT_HASH_PEAK_DELTA' in row and row['RECEIPT_HASH_PEAK_DELTA']<=40*1024**2
                 and row['RECEIPT_HASH_RETAINED_DELTA']<=1024**2
                 and row['PEAK_USED_HEAP']>=row['ERI_PACKED_BYTES']+row['GRID_STORAGE_BYTES'] for row in rows)
m13_frozen=len(m13_unchanged)==31 and all(m13_unchanged.values())
fields={
 'STREAMED_RECEIPT_HASHING':compatibility,'CANONICAL_HASH_COMPATIBILITY':compatibility,
 'FULL_TEXT_ERI_MATERIALIZATION_REMAINING':'NO (production; historical oracle remains test-only)',
 'DUPLICATE_LARGE_BYTE_BUFFERS_REMAINING':'NO (production audit)',
 'CHLOROBENZENE_XMX2G':outcome(full_labels['chlorobenzene']),
 'AMMONIUM_BENZENE_RETRY':outcome(full_labels['ammonium_benzene']),
 'CHILD_JVM_FAILURE_CAUSE':'OTHER — original stderr unavailable; cause undetermined; serial rerun passed' if serial=='PASS' else 'OTHER — original stderr unavailable; cause undetermined',
 'SERIAL_REGRESSION':serial,
 'PEAK_MEMORY_DOMINATED_BY_PACKED_ERI':'NO — AO/grid arrays dominate mandatory LDA storage; ERIs dominate CP tensor storage',
 'SCIENTIFIC_RESULTS_CHANGED':'NO' if unchanged and m13_frozen and full_ok and serial=='PASS' and compatibility=='PASS' else 'PENDING post-repair gates',
 'HISTORICAL_RECEIPTS_CHANGED':'NO' if unchanged and serial=='PASS' else 'PENDING post-repair gates',
 'STREAMED_HASH_BYTE_EQUIVALENT':'YES' if compatibility=='PASS' else compatibility,
 'HISTORICAL_RECEIPT_HASHES_CHANGED':'NO' if unchanged and serial=='PASS' else 'PENDING',
 'NEXT_PERFORMANCE_ARCHITECTURE_NEEDED':'Evaluate exact direct-SCF ERI handling and blocked AO/grid evaluation in a separate validated milestone; not implemented'}
for name,key in [('h2o','H2O'),('chlorobenzene','CHLOROBENZENE'),('ammonium_benzene','AMMONIUM_BENZENE')]:
    row=next(row for row in rows if row['system']==name)
    fields['RECEIPT_EXTRA_MEMORY_'+key]=(f"peak={row['RECEIPT_HASH_PEAK_DELTA']} bytes; retained={row['RECEIPT_HASH_RETAINED_DELTA']} bytes"
        if 'RECEIPT_HASH_PEAK_DELTA' in row else 'PENDING')
passed=compatibility=='PASS' and serial=='PASS' and replay=='PASS' and unchanged and all(
 row['component_status']=='PASS' and row['full_status']=='PASS' and 'FULL_SAMPLED_PEAK_USED_HEAP' in row for row in rows)
passed=passed and bounded_hash and m13_frozen
result={'status':'SCREENING_ONLY','passed':passed,'serial_regression':serial,'deterministic_replay':replay,
 'fields':fields,'components':rows,'canonical_system_basis_cases':cases,'historical_files':frozen,'active_receipts':active,
 'm13_receipts':m13_unchanged,'bounded_hash_memory':bounded_hash,
 'hash_memory_limits_bytes':{'transient':40*1024**2,'retained':1024**2}}
(OUT/'memory-gate.json').write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
columns=['system','AO_COUNT','UNIQUE_ERI_SLOTS','ERI_PACKED_BYTES','GRID_COORDINATE_WEIGHT_BYTES','AO_GRID_BYTES','GRID_STORAGE_BYTES',
 'PEAK_USED_HEAP','FULL_SAMPLED_PEAK_USED_HEAP','FULL_PEAK_RSS_BYTES','RECEIPT_HASH_PEAK_DELTA','RECEIPT_HASH_RETAINED_DELTA',
 'RHF_SCF_MATRIX_BYTES','LDA_SCF_MATRIX_BYTES','CP_SCF_MATRIX_BYTES','MINIMUM_KNOWN_WORKING_FULL_HEAP']
with (OUT/'memory-components.csv').open('w',newline='') as stream:
 writer=csv.DictWriter(stream,fieldnames=columns,extrasaction='ignore');writer.writeheader()
 for row in rows:writer.writerow({key:row.get(key,'N/A' if key.endswith('_SCF_MATRIX_BYTES') and row['full_status']=='PASS' else 'PENDING') for key in columns})
report=['# Aether bounded memory gate','','**SCREENING_ONLY**. '+('Gate passed.' if passed else '**Gate pending or failed; do not advance.**'),'', '```ini']
report += [f'{key} = {value}' for key,value in fields.items()]
report += ['```','','The [protocol](MEMORY_HARDENING_PROTOCOL.md) defines the unchanged canonical bytes and measurement boundaries. '
 'The [component table](validation/milestone-13/memory-components.csv) separates actual molecular component probes from full RHF/LDA/CP validation. '
 'Heap peaks are sampled every 5 ms; external peak RSS is separate. Independently timed heap-pool maxima are not added and called a simultaneous peak.','',
 '| System | ERI bytes | Grid/AO bytes | Full sampled heap | Hash peak delta | Hash retained delta | Known working full heap |',
 '|---|---:|---:|---:|---:|---:|---|']
for row in rows:
 report.append('| '+' | '.join(str(row.get(key,'PENDING')) for key in ['system','ERI_PACKED_BYTES','GRID_STORAGE_BYTES','FULL_SAMPLED_PEAK_USED_HEAP','RECEIPT_HASH_PEAK_DELTA','RECEIPT_HASH_RETAINED_DELTA','MINIMUM_KNOWN_WORKING_FULL_HEAP'])+' |')
report += ['','Hash transient peaks include disposable scalar-formatting strings under the fixed 32 MiB young generation; retained deltas after collection measure retained overhead. '
 'A small negative retained delta is collection/JVM noise. Neither metric is cumulative allocation. The complete numerical tensor is retained throughout the hash measurement.','',
 'Known working heaps are measured bounds, not a claim that every smaller heap fails. Component-only probe heaps do not establish full CP completion. '
 'SCF matrix counts are distinct primitive arrays reachable from completed evidence, excluding object headers and temporary solver workspaces. '
 'N/A in method-specific CSV columns means that method was not part of that full-run memory measurement.','',
 '## Exact packed storage projection','', '| AOs | Unique slots | Raw bytes | GiB |','|---:|---:|---:|---:|']
with (OUT/'packed-eri-scaling.csv').open() as stream:
 for row in csv.DictReader(stream):report.append(f'| {row["AO_COUNT"]} | {row["UNIQUE_ERI_SLOTS"]} | {row["RAW_DOUBLE_STORAGE_BYTES"]} | {float(row["RAW_DOUBLE_STORAGE_GIB"]):.6f} |')
report += ['','At 200 AOs the tensor alone uses about 1.51 GiB, leaving little room in a 2 GiB heap. At 250 AOs it uses about 3.67 GiB; at 300 AOs about 7.59 GiB. '
 'Grid/AO storage, SCF evidence, solver workspaces and JVM overhead are additional. Actual limits therefore depend on the calculation, not AO count alone.','',
 'The original two replay failures cannot be assigned a more specific cause from the retained logs. New serial diagnostics retain stderr; a successful rerun does not retroactively prove memory pressure or a heap-reservation failure.','']
(ROOT/'MEMORY_HARDENING_REPORT.md').write_text('\n'.join(report))
print('MEMORY_GATE_PASSED =',passed)
