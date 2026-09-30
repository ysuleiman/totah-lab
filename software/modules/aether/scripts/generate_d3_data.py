"""Extract (never hand-transcribe) supported-element D3 data from pinned upstream source."""
from pathlib import Path
import re,json,hashlib,sys,shutil
src=Path(sys.argv[1]);root=Path(__file__).resolve().parents[1]
out=root/'src/main/resources/totah/lab/aether/dispersion';out.mkdir(parents=True,exist_ok=True)
def nums(text):return [float(x) for x in re.findall(r'([+-]?[0-9]+\.[0-9]+)_wp',text)]
ref=(src/'src/dftd3/reference.f90').read_text()
cn=nums(ref.split('reference_cn(max_ref, max_elem) = reshape([')[1].split('],')[0])
c6=nums(ref.split('c6ab_view(1:2000) =')[1])
assert len(cn)==103*7 and len(c6)==103*104//2*49,(len(cn),len(c6))
rv=nums((src/'src/dftd3/data/r4r2.f90').read_text().split('r4_over_r2(max_elem) = [')[1].split(']')[0])
cv=nums((src/'src/dftd3/data/covrad.f90').read_text().split('covalent_rad_2009(max_elem) = aatoau * [')[1].split(']')[0])
elements=[1,6,7,8,15,16,17];rows=['# simple-dftd3 v1.2.1 atomic data; LGPL-3.0-or-later; see LICENSE and provenance.json']
for z in elements:
 refs=[x for x in cn[(z-1)*7:z*7] if x>=0]
 rows.append(','.join(map(str,['ATOM',z,cv[z-1],rv[z-1],*refs])))
for z in elements:
 for w in elements:
  if w>z:continue
  k=w+z*(z-1)//2-1
  for a in range(sum(x>=0 for x in cn[(z-1)*7:z*7])):
   for b in range(sum(x>=0 for x in cn[(w-1)*7:w*7])):
    v=c6[k*49+b*7+a];assert v>0
    rows.append(','.join(map(str,['C6',z,w,a,b,v])))
data=('\n'.join(rows)+'\n').encode();(out/'d3-reference.csv').write_bytes(data)
files=['src/dftd3/reference.f90','src/dftd3/data/r4r2.f90','src/dftd3/data/covrad.f90','assets/parameters.toml']
(out/'provenance.json').write_text(json.dumps({'upstream':'https://github.com/dftd3/simple-dftd3/tree/v1.2.1','version':'1.2.1','source_sha256':{f:hashlib.sha256((src/f).read_bytes()).hexdigest() for f in files},'payload_sha256':hashlib.sha256(data).hexdigest(),'elements':elements,'license':'LGPL-3.0-or-later','generation':'scripts/generate_d3_data.py'},indent=2)+'\n')
for name in ['COPYING','COPYING.LESSER']:
 if (src/name).exists():shutil.copyfile(src/name,out/name)
print(len(rows),hashlib.sha256(data).hexdigest())
