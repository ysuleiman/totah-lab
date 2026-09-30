"""Assemble measured M13 results; missing gates remain explicitly pending."""
from pathlib import Path
import argparse,csv,json,re,hashlib
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('log_directory',type=Path);a=p.parse_args()
ref=ROOT/'src/test/resources/totah/lab/aether/reference';out=ROOT/'validation/milestone-13';out.mkdir(parents=True,exist_ok=True)
specs=json.loads((ref/'def2-integrals-manifest.json').read_text())['systems']
energies={};cp={};metrics={};peaks={};grid=[];replay={};ao_profiles={}
for file in [*sorted(a.log_directory.glob('*.log')),*sorted((out/'memory').glob('*.log'))]:
 for line in file.read_text(errors='replace').splitlines():
  m=re.match(r'M13 ENERGY (\S+).*errors=\{([^}]+)\}',line)
  if m:
   values={k:float(v) for k,v in (item.split('=') for item in m[2].split(', '))}
   target=energies.setdefault(m[1],{})
   for k,v in values.items():target[k]=max(target.get(k,0),v)
  m=re.match(r'M13 (RHF|LDA) (\S+) CONVERGED .*?(PerformanceCounters|Performance)\[(.*?)\](.*)',line)
  if m:
   fields={k:v for k,v in re.findall(r'(\w+)=(\d+)',m[4]+m[5])}
   key=m[2]+':'+m[1]
   if key not in metrics or 'integralNanos' in fields:metrics[key]=fields
  m=re.match(r'M13 PEAK_HEAP_UPPER_ESTIMATE (\S+) (\d+)',line)
  if m:peaks[m[1]]=max(peaks.get(m[1],0),int(m[2]))
  m=re.match(r'M13 AO_PROFILE (\S+) gridPoints=(\d+) aoNanos=(\d+) aoHash=(\w+)',line)
  if m:ao_profiles[m[1]]={'grid_points':int(m[2]),'ao_nanos':int(m[3]),'ao_hash':m[4]}
  m=re.match(r'M13 CP RESULT (\S+) unc=OptionalDouble\[([^]]+)\] cp=OptionalDouble\[([^]]+)\] maxError=(\S+) seconds=(\S+)',line)
  if m:cp[m[1]]={'uncorrected':float(m[2]),'counterpoise':float(m[3]),'max_component_error':float(m[4]),'seconds':float(m[5])}
  if line.startswith('M13 GRID '):grid.append(line)
  m=re.match(r'M13 REPLAY \[([^]]+)\] (\w+)',line)
  if m:replay[m[1]]=m[2]
maxima={}
for system,values in energies.items():
 for key,value in values.items():maxima[key]=max(maxima.get(key,0),value)
pending=[name+':'+method for name in specs for method in ['RHF','LDA'] if method+'_total' not in energies.get(name,{})]
for name in specs:
 for method in ['RHF','LDA']:
  if method+'_total' in energies.get(name,{}) and 'integralNanos' not in metrics.get(name+':'+method,{}):
   pending.append(name+':'+method+' performance')
for name in ['water_dimer','water_ammonia','methanethiol_water','ammonium_benzene','chlorobenzene_water']:
 if name not in cp:pending.append(name+':counterpoise')
summary={'status':'SCREENING_ONLY','pending':pending,'energy_reference_errors':energies,'max_errors':maxima,'counterpoise':cp,'performance':metrics,'observed_heap_pool_peak_upper_estimates':peaks,'ao_profiles':ao_profiles,'grid':sorted(set(grid)),'replay':replay}
(out/'summary.json').write_text(json.dumps(summary,indent=2,sort_keys=True)+'\n')
with (out/'performance.csv').open('w',newline='') as f:
 w=csv.writer(f);w.writerow(['system','method','basis_functions','primitive_Gaussians','unique_ERI_slots','primitive_quartets','integral_seconds','grid_points','AO_grid_seconds','JK_seconds','XC_seconds','eigensolve_seconds','SCF_iterations','total_scf_seconds','heap_pool_peak_upper_estimate_bytes'])
 for name,spec in specs.items():
  n=spec['basis_functions'];pairs=n*(n+1)//2;slots=pairs*(pairs+1)//2
  for method in ['RHF','LDA']:
   v=metrics.get(name+':'+method,{})
   seconds=lambda *keys:next((int(v[k])/1e9 for k in keys if k in v),'UNAVAILABLE')
   w.writerow([name,method,n,v.get('primitiveGaussians','UNAVAILABLE'),slots,v.get('primitiveQuartets','UNAVAILABLE'),seconds('integralNanos'),v.get('gridPoints',ao_profiles.get(name,{}).get('grid_points','UNAVAILABLE')),seconds('aoGridNanos') if 'aoGridNanos' in v else ao_profiles.get(name,{}).get('ao_nanos',0)/1e9 if name in ao_profiles else 'UNAVAILABLE',seconds('totalJkNanos','jkNanos'),seconds('xcNanos'),seconds('totalEigensolveNanos','eigensolveNanos'),v.get('iterationCount',v.get('iterations','UNAVAILABLE')),seconds('totalScfNanos','totalNanos'),peaks.get(name,'UNAVAILABLE')])
print('Pending:',pending);print('Maximum errors:',maxima)
