"""Replay the existing historical protocol plus the unchanged water and additive I03 entry points in fresh JVMs."""
from pathlib import Path
import json, subprocess, hashlib, sys
repo=Path.cwd();config=Path(sys.argv[1]).resolve();out=Path(sys.argv[2]).resolve();out.mkdir(exist_ok=False)
cmd=json.loads(config.read_text());cp=cmd[cmd.index('--class-path')+1];java=cmd[0]
source_root=Path(json.loads((config.parent/'BUILD.json').read_text())['source']) if (config.parent/'BUILD.json').exists() else repo
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
base=json.loads((repo/'software/qualification/foundation-supportable-pass-20261007/p07/REPLAY.json').read_text())['independentJvmComparisons']
result={}
for name in list(base)+['MetPheSurveyAcceptanceTest']:
 paths=[]
 for n in (1,2):
  target=out/(name+str(n)+'.json');args=[str(target)]
  if name in ['WaterBridgeCurrentPipelineTest','ImplicitHProxyCurrentPipelineTest','S1SourceScopeV2Test','S1ImplicitHCompositionTest','HalogenCarbonylAcceptanceTest','ZincCarbonylAcceptanceTest','SelectedPharmacophoreAcceptanceTest','SourceFragmentParentAcceptanceTest','ZnSourceFeatureAcceptanceTest','MetPheSurveyAcceptanceTest']:args=[str(out/(name+str(n)+'-catalog')),str(target)]
  command=[java,'-Xmx768m','-cp',cp,'totah.lab.daedalus.system.'+name,*args]
  with (out/(name+str(n)+'.log')).open('w') as f:subprocess.run(command,cwd=source_root,check=True,stdout=f,stderr=subprocess.STDOUT)
  paths.append(target)
 assert paths[0].read_bytes()==paths[1].read_bytes(),name
 if name in base:assert sha(paths[0])==base[name],name
 result[name]=sha(paths[0]);print(name,'PASS',flush=True)
for n in (1,2):
 with (out/('historical'+str(n)+'.log')).open('w') as f:subprocess.run([java,'-Xmx768m','-cp',cp,'totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest',str(out/('historical'+str(n)))],cwd=source_root,check=True,stdout=f,stderr=subprocess.STDOUT)
baseline=repo/'software/qualification/ocl-query-b00-corrected-20261005/final-run/scientific-replay-one'
files=[p.relative_to(baseline) for p in baseline.rglob('*') if p.is_file()];assert len(files)==65
for p in files:assert (baseline/p).read_bytes()==(out/'historical1'/p).read_bytes()==(out/'historical2'/p).read_bytes(),p
pins=json.loads((repo/'software/qualification/water-bridge-contract-20261006/PRESERVATION_BEFORE.json').read_text())['sha256']
assert len(pins)==25
for p,h in pins.items():assert sha(repo/p)==h,p
(out/'REPLAY.json').write_text(json.dumps({'independentJvmComparisons':result,'historicalFiles':65,'historicalByteIdentical':True,'preservationPins':25,'preservationPass':True},indent=2)+'\n')
print('65 historical comparisons and 25 preservation pins PASS',flush=True)
