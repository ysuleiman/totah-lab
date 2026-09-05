#!/usr/bin/env python3
# DEPRECATED FOR V2 USE (2026-09-05): near-attack geometry in this script is superseded by
# athena.tmt.NearAttackGeometry / NearAttackAssessor / EnsembleNacAnalyzer (Java).
# See ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md. Retained for historical regression reproduction only.
"""Apply the frozen bilateral gate before any KLF4 surface inference."""
from pathlib import Path
from collections import defaultdict
import csv,json,math
import numpy as np
ROOT=Path(__file__).resolve().parents[3];BASE=ROOT/'research/mettl7-independent-audit/long-rna-interface/final-validation';TARGET=24
def atoms(p):
 out=[]
 for x in p.read_text().splitlines():
  if x.startswith(('ATOM  ','HETATM')):out.append({'name':x[12:16].strip(),'resname':x[17:20].strip(),'chain':x[21:22],'resid':int(x[22:26]),'el':(x[76:78].strip() or x[12]).upper(),'xyz':np.array([float(x[30:38]),float(x[38:46]),float(x[46:54])])})
 return out
def angle(a,b,c):
 x=a-b;y=c-b;return math.degrees(math.acos(np.clip(np.dot(x,y)/np.linalg.norm(x)/np.linalg.norm(y),-1,1)))
def main():
 rows=[]
 for d in sorted((BASE/'runs').glob('C*/METTL7?')):
  conf=int(d.parent.name[1:]);enz=d.name
  for p in sorted((d/'run/4_flexref').glob('flexref_*.pdb')):
   aa=atoms(p);prot=[a for a in aa if a['chain'] not in ('R','S') and a['el']!='H'];rna=[a for a in aa if a['chain']=='R' and a['el']!='H'];anc={a['resid']:a['xyz'] for a in aa if a['chain']=='S'};t=next(a for a in rna if a['resid']==TARGET and a['name']=='N7');dist=float(np.linalg.norm(t['xyz']-anc[1]));ang=angle(t['xyz'],anc[1],anc[2]);dw=2.7<=dist<=3.5;aw=145<=ang<=180;ok=dw and aw;D=np.linalg.norm(np.array([a['xyz'] for a in prot])[:,None]-np.array([a['xyz'] for a in rna])[None,:],axis=2)
   reason='ADMISSIBLE' if ok else ('DISTANCE_AND_ANGLE_WINDOW_FAILED' if not dw and not aw else 'DISTANCE_WINDOW_FAILED' if not dw else 'ANGLE_WINDOW_FAILED')
   rows.append({'enzyme':enz,'conformer':conf,'model':p.name,'distance_A':dist,'angle_deg':ang,'distance_window':dw,'angle_window':aw,'both_windows':ok,'admissibility':'ADMISSIBLE' if ok else 'REJECTED','rejection_reason':reason,'severe_clashes_lt_1_8A':int(np.count_nonzero(D<1.8)),'close_pairs_lt_2_4A':int(np.count_nonzero(D<2.4)),'model_path':str(p.relative_to(ROOT))})
 with (BASE/'model_metrics.csv').open('w',newline='') as h:w=csv.DictWriter(h,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
 arms={e:{'admissible_models':sum(r['enzyme']==e and r['both_windows'] for r in rows),'admissible_conformers':len({r['conformer'] for r in rows if r['enzyme']==e and r['both_windows']})} for e in ('METTL7A','METTL7B')};gate=all(v['admissible_models']>=5 and v['admissible_conformers']>=3 for v in arms.values())
 decision={'status':'COMPUTATIONAL_RNA_INTERFACE_ROUTE = CLOSED_PENDING_EXPERIMENTAL_CONSTRAINTS','predefined_gate':{'minimum_admissible_models_per_arm':5,'minimum_admissible_conformers_per_arm':3},'arms':arms,'gate_passed':gate,'residue_or_network_analysis_performed':False,'post_hoc_rescue_run_launched':False,'scientific_interpretation':'The current computational framework did not produce a sufficiently reproducible structural ensemble to support residue-level inference of a METTL7A or METTL7B RNA-binding interface.','ligand_program_separation':'This RNA-interface result does not invalidate the ligand/SAM-pocket program. Ligand-pocket selectivity residues are not RNA-selectivity determinants without experimental evidence.','reopen_only_with_new_experimental_constraints':['cross-linking or footprinting','RNA-binding mutagenesis','experimentally constrained or solved METTL7-RNA complex','defined RNA recognition element','equivalent residue-level data']};(BASE/'validation_decision.json').write_text(json.dumps(decision,indent=2)+'\n');print(json.dumps(decision,indent=2))
if __name__=='__main__':main()
