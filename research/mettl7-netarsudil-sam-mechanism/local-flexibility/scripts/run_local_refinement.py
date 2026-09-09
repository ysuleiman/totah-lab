#!/usr/bin/env python3
"""Run the preregistered four-system, three-seed Vina local-only refinement."""
from __future__ import annotations
import hashlib,json,subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[4]
BASE=ROOT/'research/mettl7-netarsudil-sam-mechanism/local-flexibility'
PREP=BASE/'prepared'; CONFIG=BASE/'configs'; RAW=BASE/'raw'; LOG=BASE/'logs'
for d in (CONFIG,RAW,LOG):d.mkdir(parents=True,exist_ok=True)
VINA=Path('/Users/yazan/bin/vina'); SEEDS=(314159,271828,161803)
SYSTEMS={
 '7B_neutral':('7B','netarsudil_neutral_start.pdbqt'),
 '7B_plus1':('7B','netarsudil_plus1_start.pdbqt'),
 '7A_neutral_transfer':('7A','netarsudil_neutral_start_7A_transfer.pdbqt'),
 '7A_plus1_transfer':('7A','netarsudil_plus1_start_7A_transfer.pdbqt')}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def sam_rows(p):
 return [l for l in p.read_text().splitlines() if l.startswith(('ATOM','HETATM')) and l[17:20].strip()=='SAM']
def main():
 qc={};runs=[]
 for e in ('7A','7B'):
  rec=PREP/f'METTL{e}_localflex_rigid_with_SAM.pdbqt'; rows=sam_rows(rec); heavy=[l for l in rows if l.split()[-1] not in ('H','HD')]
  qc[e]={'receptor':str(rec.relative_to(ROOT)),'sha256':sha(rec),'sam_records':len(rows),'sam_heavy_atoms':len(heavy),'passed':len(heavy)==27}
  if not qc[e]['passed']:raise RuntimeError(f'{e} SAM QC')
 for system,(e,lig_name) in SYSTEMS.items():
  rec=PREP/f'METTL{e}_localflex_rigid_with_SAM.pdbqt'; flex=PREP/f'METTL{e}_localflex_flex.pdbqt'; lig=PREP/lig_name
  for seed in SEEDS:
   key=f'{system}_seed{seed}'; out=RAW/f'{key}.pdbqt'; log=LOG/f'{key}.log'; cfg=CONFIG/f'{key}.txt'
   cfg.write_text('\n'.join([f'receptor = {rec}',f'flex = {flex}',f'ligand = {lig}','local_only = true','autobox = true',f'seed = {seed}','cpu = 1','num_modes = 1'])+'\n')
   cmd=[str(VINA),'--config',str(cfg),'--out',str(out)]
   with log.open('w') as f:subprocess.run(cmd,stdout=f,stderr=subprocess.STDOUT,check=True)
   runs.append({'key':key,'system':system,'enzyme':e,'seed':seed,'config':str(cfg.relative_to(ROOT)),'config_sha256':sha(cfg),
    'rigid_receptor':str(rec.relative_to(ROOT)),'rigid_sha256':sha(rec),'flex_receptor':str(flex.relative_to(ROOT)),'flex_sha256':sha(flex),
    'ligand':str(lig.relative_to(ROOT)),'ligand_sha256':sha(lig),'output':str(out.relative_to(ROOT)),'output_sha256':sha(out),
    'log':str(log.relative_to(ROOT)),'log_sha256':sha(log),'command':cmd})
   print('completed',key,flush=True)
 manifest={'run_key':'METTL7_NETARSUDIL_LOCAL_FLEX_VALIDATION_2026_08_30','vina_version':subprocess.check_output([str(VINA),'--version'],text=True).strip(),
  'method':'local_only','global_redocking':False,'seeds':SEEDS,'sam_qc':qc,'runs':runs}
 (BASE/'run_manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
if __name__=='__main__':main()
