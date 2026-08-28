#!/usr/bin/env python3
import argparse,hashlib,json,subprocess,sys,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--offline',action='store_true');a=ap.parse_args()
 required=['run_2d_gpu.py','wavefront2d.py','qualified_level5_derivative_core.py','FINAL_QM_PROTOCOL.json','FINAL_2D_WORKFLOW_PREREGISTRATION.json','input/MIN01_verified.xyz','input/MIN02_verified.xyz','input/MIN04_verified.xyz','input/ATOM_ORDER.csv','input/MASS_VECTOR.json']
 missing=[x for x in required if not (ROOT/x).is_file()]
 if missing:raise RuntimeError(f'missing {missing}')
 manifest=ROOT/'PACKAGE_SHA256SUMS'
 if manifest.is_file():
  for row in manifest.read_text().splitlines():
   digest,relative=row.split(maxsplit=1);path=ROOT/relative
   if not path.is_file() or sha(path)!=digest:raise RuntimeError(f'package checksum failure: {path}')
 protocol=json.loads((ROOT/'FINAL_QM_PROTOCOL.json').read_text());assert protocol['grid_level']==5 and protocol['grid_response_gradient'] is True and protocol['constraints']['two_dihedrals_fixed_simultaneously'] is True
 subprocess.check_call([sys.executable,str(ROOT/'test_wavefront2d.py')])
 subprocess.check_call([sys.executable,str(ROOT/'test_external_cell_watchdog.py')])
 subprocess.check_call([sys.executable,str(ROOT/'test_propagated_geometry_paths.py')])
 if not a.offline:
  import cupy as cp
  if cp.cuda.runtime.getDeviceCount()<1:raise RuntimeError('GPU absent')
 print(json.dumps({'status':'SELF_VERIFY_PASS','offline':a.offline,'protocol_sha256':sha(ROOT/'FINAL_QM_PROTOCOL.json'),'phi':[25,9,8,7],'psi':[9,8,7,1]}))
if __name__=='__main__':main()
