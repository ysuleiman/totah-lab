"""Read-only baseline comparison; writes only M14.1 summaries."""
from pathlib import Path
import csv,hashlib,json,re
root=Path(__file__).resolve().parents[1];out=root/'validation/milestone-14.1'
previous=json.loads((root/'validation/milestone-14/parallel/summary.json').read_text())
baseline={(x['system'],x['method']):x for x in previous['completed']}
cases=[('dms','RHF'),('trimethylsulfonium','RHF'),('chlorobenzene','RHF'),('ammonium_benzene','CP'),('chlorobenzene_water','CP')]
summary={'scientific_status':'SCREENING_ONLY','completed':[],'pending':[],'failed':[]}
fields=['system','method','role','ao','iterations','cacheIdentity','cacheBytes','generated','eriGenerationCount','generationNanos','writeNanos','cacheVerificationNanos','cacheOpenNanos','readNanos','pageVerificationNanos','meanReadNanosPerIteration','meanReadAndVerificationNanosPerIteration','bytesRead','jkNanos','scfTotalNanos']
components=[]
def metrics(line):
 return {k:v for k,v in re.findall(r'(\w+)=([^ ]+)',line.strip())}
def historical_cp(name):
 path=root/f'validation/milestone-13/{name}-cp.receipt'
 if not path.exists():path=root/f'validation/milestone-14/m13-recovered-receipts/{name}-cp.receipt'
 return float(re.search(r'counterpoiseHartree=OptionalDouble\[([^]]+)\]',path.read_text())[1])
for name,method in cases:
 p=out/f'{method}-{name}.json'
 if not p.exists():summary['pending'].append(name);continue
 process=json.loads(p.read_text())
 if process['exit_code']!=0:summary['failed'].append(name);continue
 text=(out/f'{name}-{method}.txt').read_text();base=baseline[name,method]
 rows=[]
 for line in text.splitlines():
  if 'cacheIdentity=' not in line:continue
  values=metrics(line);row={key:values.get(key,'') for key in fields};row.update(system=name,method=method,role='RHF' if method=='RHF' else line.split()[0])
  count=int(values['iterations']);row['meanReadNanosPerIteration']=int(values['readNanos'])/count
  row['meanReadAndVerificationNanosPerIteration']=(int(values['readNanos'])+int(values['pageVerificationNanos']))/count
  components.append(row);rows.append(row)
 total=int(re.search(r'(?:^|\n)totalNanos=(\d+)',text)[1]) if method=='CP' else int(rows[0]['scfTotalNanos'])
 pilot=0
 if name=='dms':
  line=(out/'READERS-dms.log').read_text().splitlines()[0];pilot=int(metrics(line)['totalNanos'])
 peaks=[int(re.search(r'peakHeap=(\d+)',text)[1])]+[int(x) for x in re.findall(r'sampled_peak_used_heap=(\d+)',(out/f'{method}-{name}.log').read_text())]
 peak=max(peaks);rss=process['peak_rss_bytes']
 if name=='dms':
  pilot_log=(out/'READERS-dms.log').read_text()
  # Pilot combines mmap/reference instrumentation: report its peak separately rather than pretending it is production-only.
  pilot_heap=max(map(int,re.findall(r'sampled_peak_used_heap=(\d+)',pilot_log)))
 else:pilot_heap=None
 actual=float(re.search(r'(?:^|\n)'+('energy' if method=='RHF' else 'cp')+r'=([^\n]+)',text)[1])
 expected=base['m13_energy'] if method=='RHF' else historical_cp(name)
 old=base.get('calculation_elapsed_nanos',base['performance_nanos']['totalNanos'])/1e9
 item={'system':name,'method':method,'calculation_seconds':total/1e9,'first_population_seconds':(total+pilot)/1e9,'pilot_cache_creation_seconds':pilot/1e9,
       'direct_seconds':old,'direct_over_semi_speedup':old/((total+pilot)/1e9),'peak_heap':peak,'peak_rss':rss,'direct_peak_heap':base['peak_used_heap'],
       'direct_peak_rss':base['peak_rss_bytes'],'pilot_combined_instrumentation_peak_heap':pilot_heap,'value':actual,'frozen_packed_value':expected,'error':abs(actual-expected),
       'cache_bytes_distinct_in_case':sum(int(next(r['cacheBytes'] for r in rows if r['cacheIdentity']==key)) for key in {r['cacheIdentity'] for r in rows}),
       'component_cache_identities':{r['role']:r['cacheIdentity'] for r in rows},'full_basis_reuse':None}
 if method=='CP':
  full=[r for r in rows if r['role'] in ['COMPLEX','A_WITH_GHOST_B','B_WITH_GHOST_A']]
  item['full_basis_reuse']=len(full)==3 and len({r['cacheIdentity'] for r in full})==1 and all(r['generated']=='false' and r['eriGenerationCount']=='0' for r in full if r['role']!='COMPLEX')
 summary['completed'].append(item)
manifest=json.loads((out/'m14-frozen-manifest.json').read_text())
summary['m14_changed']=[p for p,h in manifest.items() if hashlib.file_digest((root/p).open('rb'),'sha256').hexdigest()!=h]
summary['m14_files_checked']=len(manifest)
summary['max_rhf_error']=max((x['error'] for x in summary['completed'] if x['method']=='RHF'),default=None)
summary['max_cp_error']=max((x['error'] for x in summary['completed'] if x['method']=='CP'),default=None)
if len(summary['completed'])==5:
 summary['total_speedup']=sum(x['direct_seconds'] for x in summary['completed'])/sum(x['first_population_seconds'] for x in summary['completed'])
 summary['performance_gate_pass']=summary['total_speedup']>=1.25
inventory=[]
for p in sorted((out/'cache').glob('*/manifest.txt')):
 if not re.fullmatch('[0-9a-f]{64}',p.parent.name):continue
 lines=p.read_text().splitlines();inventory.append({'identity':lines[1],'ao':int(lines[2]),'unique_slots':int(lines[3]),'payload_sha256':lines[4],
  'payload_bytes':p.with_name('eri.bin').stat().st_size,'all_file_bytes':sum(x.stat().st_size for x in p.parent.iterdir() if x.is_file())})
summary['cache_inventory']=inventory;summary['unique_disk_payload_bytes']=sum(x['payload_bytes'] for x in inventory)
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
with (out/'components.csv').open('w') as f:
 w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(components)
with (out/'performance.csv').open('w') as f:
 keys=['system','method','direct_seconds','first_population_seconds','direct_over_semi_speedup','peak_heap','peak_rss','direct_peak_heap','direct_peak_rss','error','cache_bytes_distinct_in_case']
 w=csv.DictWriter(f,fieldnames=keys,extrasaction='ignore');w.writeheader();w.writerows(summary['completed'])
print('Completed',len(summary['completed']),'pending',summary['pending'],'failed',summary['failed'])
