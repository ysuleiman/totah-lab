#!/usr/bin/env python3
"""Matched multi-seed BA/DCMB/2,4-isomer docking in fixed SAM receptors."""
from __future__ import annotations
import hashlib,json,subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]; HERE=Path(__file__).resolve().parent
VINA=Path('/Users/yazan/bin/vina'); SEEDS=(1,7,42); EXHAUSTIVENESS=16; MODES=12; MAX_PARALLEL=8
BOX={'7A':(1.8020,-3.9254,-6.7763,28.452,22.0,26.506),'7B':(2.8444,-2.1005,-4.2105,25.334,22.0,23.923)}
RECEPTORS={p:ROOT/f'analysis/dcmb/sar_experiment/prepared_receptors/{p}_SAM.pdbqt' for p in ('7A','7B')}
LIGANDS={c:ROOT/f'analysis/dcmb/sar_experiment/ligands/{c}.pdbqt' for c in ('BA','DCMB_R','DCMB_S','24DCMB_R','24DCMB_S')}

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

def main():
 raw=HERE/'raw'; raw.mkdir(parents=True,exist_ok=True); jobs=[]
 for para,rec in RECEPTORS.items():
  cx,cy,cz,sx,sy,sz=BOX[para]
  for cid,lig in LIGANDS.items():
   for seed in SEEDS:
    out=raw/f'{para}_SAM__{cid}__s{seed}.pdbqt'; log=raw/f'{para}_SAM__{cid}__s{seed}.log'
    if out.exists() and log.exists() and 'Writing output' in log.read_text(errors='ignore'): continue
    cmd=[str(VINA),'--receptor',str(rec),'--ligand',str(lig),'--center_x',str(cx),'--center_y',str(cy),'--center_z',str(cz),'--size_x',str(sx),'--size_y',str(sy),'--size_z',str(sz),'--exhaustiveness',str(EXHAUSTIVENESS),'--num_modes',str(MODES),'--seed',str(seed),'--cpu','1','--out',str(out)]
    jobs.append((cmd,log))
 running=[]
 for cmd,log in jobs:
  fh=log.open('w'); running.append((subprocess.Popen(cmd,stdout=fh,stderr=subprocess.STDOUT),fh,cmd))
  if len(running)>=MAX_PARALLEL:
   p,fh,c=running.pop(0); rc=p.wait(); fh.close()
   if rc: raise RuntimeError('failed: '+' '.join(c))
 for p,fh,c in running:
  rc=p.wait(); fh.close()
  if rc: raise RuntimeError('failed: '+' '.join(c))
 manifest={'engine':subprocess.run([str(VINA),'--version'],capture_output=True,text=True,check=True).stdout.strip(),'seeds':SEEDS,'exhaustiveness':EXHAUSTIVENESS,'num_modes':MODES,'boxes':BOX,'sam_policy':'fixed validated coordinates; no movement or minimization','protonation':'identical +1 prepared ligands from SAR checkpoint','receptors':{k:{'path':str(v),'sha256':sha(v),'sam_atom_records':sum(' SAM ' in x for x in v.read_text().splitlines())} for k,v in RECEPTORS.items()},'ligands':{k:{'path':str(v),'sha256':sha(v)} for k,v in LIGANDS.items()},'completed_jobs':len(list(raw.glob('*.pdbqt')))}
 (HERE/'docking_manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
if __name__=='__main__': main()
