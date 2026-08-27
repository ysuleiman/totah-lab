#!/usr/bin/env python3
import hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;PKG=ROOT/'TSL_RSH_PHIPSI_2D_GPU_PACKAGE';OUT=ROOT/'TSL_RSH_PHIPSI_2D_GPU_PACKAGE.zip'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
seal={'schema':'tsl-rsh-phipsi-2d-gpu-package-v1','scientific_protocol_sha256':sha(PKG/'FINAL_QM_PROTOCOL.json'),'production_grid_authorized':False,'benchmark_authorized':True,'phi':[25,9,8,7],'psi':[9,8,7,1],'grid_spacing_degrees':30,'container_build_required_before_gpu':True}
(PKG/'PACKAGE_SEAL.json').write_text(json.dumps(seal,indent=2,sort_keys=True)+'\n')
files=sorted(p for p in PKG.rglob('*') if p.is_file() and p.name!='PACKAGE_SHA256SUMS' and '__pycache__' not in p.parts)
(PKG/'PACKAGE_SHA256SUMS').write_text(''.join(f'{sha(p)}  {p.relative_to(PKG)}\n' for p in files))
members=sorted(p for p in PKG.rglob('*') if p.is_file() and '__pycache__' not in p.parts)
with zipfile.ZipFile(OUT,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for p in members:
  info=zipfile.ZipInfo(str(Path(PKG.name)/p.relative_to(PKG)),(1980,1,1,0,0,0));info.external_attr=0o100644<<16;z.writestr(info,p.read_bytes())
print(f'ZIP_PATH={OUT}\nZIP_SHA256={sha(OUT)}')
