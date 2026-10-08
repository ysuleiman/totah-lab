"""Review-only grid integrity/representation audit; no residue classification or evaluator implementation."""
from pathlib import Path
import json,re,hashlib,struct,math
folder=Path(__file__).resolve().parent;r=folder/'reference';sources=json.loads((folder/'SOURCES.json').read_text())
for s in sources:assert hashlib.sha256((folder/s['file']).read_bytes()).hexdigest()==s['sha256']
header=(r/'cctbx--mmtbx__validation__ramachandran__rama8000_tables.h').read_text()
names={'general-noGPIVpreP':'general','gly-sym':'glycine','cispro':'cis_pro','transpro':'trans_pro','prepro-noGP':'pre_pro','ileval-nopreP':'ile_val'}
results=[]
for source in sources:
 if not source['path'].endswith('.data'):continue
 lines=(folder/source['file']).read_text().splitlines();dims=int(lines[1].split(':')[1]);axes=[]
 for l in lines[3:3+dims]:
  f=l.split(':')[1].split();axes.append({'min':float(f[0]),'max':float(f[1]),'bins':int(f[2]),'wrap':f[3]})
 counts=math.prod(a['bins'] for a in axes);grid=[0.0]*counts;seen=set();rawValues=[]
 for l in lines:
  if l.startswith('#'):continue
  assert l.strip(),'blank lines unsupported by the pinned upstream text parser'
  parts=list(map(float,l.split()));assert len(parts)==dims+1
  idx=0
  for v,a in zip(parts,axes):
   width=(a['max']-a['min'])/a['bins'];b=math.floor((v-a['min'])/width);assert 0<=b<a['bins'];assert v==a['min']+width*(b+.5)
   idx=idx*a['bins']+b
  assert idx not in seen;seen.add(idx);grid[idx]=parts[-1];rawValues.append(parts[-1]);assert 0<=parts[-1]<=1
 row={'file':source['file'],'sha256':source['sha256'],'axes':axes,'logicalBins':counts,'listedBins':len(seen),'unlistedBins':counts-len(seen),'explicitZeroBins':sum(v==0 for v in rawValues),'duplicateBins':0,'offCenterCoordinates':0,'minListed':min(rawValues),'maxListed':max(rawValues)}
 if 'rama8000-' in source['path']:
  key=source['path'].split('rama8000-')[1].split('.data')[0];label=names[key]
  arr=re.search(r'const double linear_table_'+label+r'\[\] = \{(.*?)\};',header,re.S);assert arr
  compiled=[float(v) for v in arr.group(1).replace('\n','').split(',') if v.strip()];assert len(compiled)==counts
  rounded=[float(format(v,'.6g')) for v in grid]
  mismatches=[i for i,(a,b) in enumerate(zip(rounded,compiled)) if a!=b]
  row.update(cctbxTable=label,compiledBins=len(compiled),sixSignificantDigitMismatchCount=len(mismatches),fullPrecisionVsCompiledDifferentBins=sum(a!=b for a,b in zip(grid,compiled)),maxAbsoluteRawVsCompiledDifference=max(abs(a-b) for a,b in zip(grid,compiled)))
  if mismatches:row['firstMismatch']={'index':mismatches[0],'roundedText':rounded[mismatches[0]],'compiled':compiled[mismatches[0]]}
 else:
  fp32=[struct.unpack('f',struct.pack('f',v))[0] for v in grid]
  row.update(storage='upstream flex.float (binary32), then double interpolation',maxAbsoluteTextVsBinary32Difference=max(abs(a-b) for a,b in zip(grid,fp32)))
 results.append(row)
(folder/'GRID_AUDIT.json').write_text(json.dumps({'purpose':'Artifact integrity and storage-format comparison only; not implementation qualification','grids':results},indent=2)+'\n')
print(json.dumps(results,indent=2))
