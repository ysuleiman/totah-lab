#!/usr/bin/env python3
import argparse, hashlib, importlib, json, pathlib, subprocess, sys

PARENT = 'sha256:3644dfc04abfc9d1f9fe8653de165ffc196fbf6a5d8cb85b4ed10e1f50542ce8'
PACKAGE_SHA = '10012b2d6366781fd53f536fd3cf565f9fade1e3e293596966c3a08ac5b2b826'
PROTOCOL_SHA = '503294e0bbdc0207841c6116c164bf232cac381ce4d7b42ea53b03303816e369'
ROOT = pathlib.Path('/opt/stage-a')
PKG = ROOT/'TSL_RSH_PHIPSI_2D_GPU_PACKAGE'

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def current_manifest():
    parent=json.loads(pathlib.Path('/opt/STAGE_A_PARENT_SCIENTIFIC_MANIFEST.json').read_text())
    return {
      'python_executable': str(pathlib.Path(sys.executable).resolve()),
      'python_sha256': sha(pathlib.Path(sys.executable)),
      'module_paths': {m:str(pathlib.Path(importlib.import_module(m).__file__).resolve()) for m in parent['module_paths']},
      'module_sha256': {m:sha(pathlib.Path(importlib.import_module(m).__file__).resolve()) for m in parent['module_paths']},
      'pip_freeze': sorted(subprocess.check_output([sys.executable,'-m','pip','freeze','--all'],text=True).splitlines()),
      'scientific_parent_digest': PARENT
    }

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--offline-build',action='store_true');a=ap.parse_args()
    parent=json.loads(pathlib.Path('/opt/STAGE_A_PARENT_SCIENTIFIC_MANIFEST.json').read_text());now=current_manifest()
    checks={
      'SCIENTIFIC_PARENT_DIGEST_MATCH':parent['scientific_parent_digest']==PARENT,
      'PACKAGE_SHA256_MATCH':sha(ROOT/'TSL_RSH_PHIPSI_2D_GPU_PACKAGE.zip')==PACKAGE_SHA,
      'QM_EXECUTABLE_HASH_MATCH':now['python_sha256']==parent['python_sha256'],
      'QM_VERSION_MATCH':now['module_sha256']==parent['module_sha256'],
      'SCIENTIFIC_DEPENDENCY_MANIFEST_MATCH':now['pip_freeze']==parent['pip_freeze'],
      'QM_PROTOCOL_HASH_MATCH':sha(PKG/'FINAL_QM_PROTOCOL.json')==PROTOCOL_SHA,
      'PHI_PSI_DEFINITIONS_MATCH':True
    }
    import importlib.util
    spec=importlib.util.spec_from_file_location('runner',PKG/'run_2d_gpu.py');runner=importlib.util.module_from_spec(spec);sys.path.insert(0,str(PKG));spec.loader.exec_module(runner)
    checks['PHI_PSI_DEFINITIONS_MATCH']=runner.PHI==(25,9,8,7) and runner.PSI==(9,8,7,1)
    if not a.offline_build:
      import cupy as cp
      checks['CUDA_RUNTIME_EXPECTED']=cp.cuda.runtime.getDeviceCount()==1 and 'A100' in cp.cuda.runtime.getDeviceProperties(0)['name'].decode()
    if not all(checks.values()): raise SystemExit(json.dumps(checks,sort_keys=True))
    print(json.dumps(checks,sort_keys=True))
if __name__=='__main__':main()
