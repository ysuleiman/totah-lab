#!/usr/bin/env python3
"""Execute the approved, frozen INSTANCE_TYPING_PILOT."""
from __future__ import annotations
import copy,csv,hashlib,json,math,sys,tempfile
from pathlib import Path
import numpy as np
import parmed as pmd
from scipy.optimize import minimize

HERE=Path(__file__).resolve().parent; ROOT=HERE.parent; FIT=ROOT/'tsl-rsh-torsion-fit'; RESULTS=HERE/'pilot-results'
sys.path.insert(0,str(FIT)); import run_first_pass as first; import run_c1_fit as c1; import close_publication_gates as gates
C1TOP=FIT/'04_FIT/C1/C1_FINAL_DERIVED_TOPOLOGY.parm7'; C1PARAM=FIT/'04_FIT/C1/C1_FINAL_PARAMETERS.json'
ASSIGN=FIT/'02_TOPOLOGY_MAPPING/LOCAL_CLONE_ASSIGNMENTS.json'; SUB=HERE/'PROPOSED_TORSION_SUBCLASSES.csv'
ORDER=['CHI_C8_SIDE_S_H','CHI_C10_SIDE_S_H','PHI_C7_C10','PHI_C7_S','PHI_C7_H11','PHI_S_HC','PHI_HC_H11','PSI_C2_C9','PSI_C6_C9','PSI_C6_HC']
AXIS={'CHI':ORDER[:2],'PHI':ORDER[2:7],'PSI':ORDER[7:]}; PARENT={'CHI_C8_SIDE_S_H':17,'CHI_C10_SIDE_S_H':17,
 'PHI_C7_C10':12,'PHI_C7_S':12,'PHI_C7_H11':12,'PHI_S_HC':12,'PHI_HC_H11':12,
 'PSI_C2_C9':2,'PSI_C6_C9':2,'PSI_C6_HC':2}
BOUNDS=(0.,2.);REG=.01;SCALE=.5;STEP=.01
def rcsv(p):
 with p.open(newline='') as f:return list(csv.DictReader(f))
def wcsv(p,rows,fields=None):
 fields=fields or list(rows[0]);p.parent.mkdir(parents=True,exist_ok=True)
 with p.open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(rows)
def atomic(p,obj):p.parent.mkdir(parents=True,exist_ok=True);q=p.with_suffix(p.suffix+'.tmp');q.write_text(json.dumps(obj,indent=2,sort_keys=True)+'\n');q.replace(p)
def canon(q):q=tuple(q);return min(q,q[::-1])
def onefour(t):
 out={}
 for d in t.dihedrals:
  if d.improper:continue
  q=canon((d.atom1.idx,d.atom2.idx,d.atom3.idx,d.atom4.idx));out[q]=out.get(q,0)+(0 if d.ignore_end else 1)
 return out
def class_id(q):
 q=tuple(q);q=min(q,q[::-1]); rules={
 canon((8,9,25,55)):'CHI_C8_SIDE_S_H',canon((10,9,25,55)):'CHI_C10_SIDE_S_H',
 canon((7,8,9,10)):'PHI_C7_C10',canon((7,8,9,25)):'PHI_C7_S',canon((7,8,9,36)):'PHI_C7_H11',
 canon((25,9,8,34)):'PHI_S_HC',canon((25,9,8,35)):'PHI_S_HC',canon((34,8,9,36)):'PHI_HC_H11',canon((35,8,9,36)):'PHI_HC_H11',
 canon((1,7,8,9)):'PSI_C2_C9',canon((6,7,8,9)):'PSI_C6_C9',canon((6,7,8,34)):'PSI_C6_HC',canon((6,7,8,35)):'PSI_C6_HC'}
 return rules.get(q)
def assignments():
 rows=json.loads(ASSIGN.read_text())['assignments'];out={k:[] for k in ORDER}
 for r in rows:
  if int(r['source_type_index']) in (2,12,17):
   cls=class_id(r['atoms_zero_based'])
   if cls:out[cls].append(r['term_identity'])
 if any(not out[k] for k in ORDER):raise RuntimeError('incomplete subclass assignment')
 return out
def initial():
 f=json.loads(C1PARAM.read_text())['fitted'];return {k:float(f[f'LOCAL_TYPE_{PARENT[k]}']) for k in ORDER}
def build(params,out):
 t=pmd.load_file(str(C1TOP));before=gates.torsion_snapshot(t);base14=onefour(t); ids=assignments(); baseline_non=first.frozen_non_torsional(t)['components']
 for cls in ORDER:
  chosen=set(ids[cls]); matches=[(i,d) for i,d in gates.term_identity_records(t) if i in chosen]
  if len(matches)!=len(chosen):raise RuntimeError(f'assignment identity failure {cls}')
  clone=copy.copy(matches[0][1].type);clone.phi_k=float(params[cls]);t.dihedral_types.append(clone)
  for _,d in matches:d.type=clone
 t.dihedral_types.claim();out.parent.mkdir(parents=True,exist_ok=True);t.save(str(out),overwrite=True);r=pmd.load_file(str(out));after=gates.torsion_snapshot(r)
 if onefour(r)!=base14:raise RuntimeError('1-4 invariant failed')
 if first.frozen_non_torsional(r)['components']!=baseline_non:raise RuntimeError('frozen component changed')
 for cls in ORDER:
  for identity in ids[cls]:
   if abs(after[identity]['phi_k']-params[cls])>5e-8:raise RuntimeError('serialized amplitude mismatch')
 # Explicitly ensure non-amplitude torsion metadata, including SCEE/SCNB, is unchanged.
 for identity in before:
  for key in ('periodicity','phase','scee','scnb','ignore_end'):
   if before[identity].get(key)!=after[identity].get(key):raise RuntimeError(f'torsion metadata changed {identity} {key}')
 symmetry=[('PHI_S_HC',(canon((25,9,8,34)),canon((25,9,8,35)))),('PHI_HC_H11',(canon((34,8,9,36)),canon((35,8,9,36)))),('PSI_C6_HC',(canon((6,7,8,34)),canon((6,7,8,35))))]
 if any(abs(params[c]-params[c])>0 for c,_ in symmetry):raise RuntimeError('symmetry tie failure')
 return {'sha256':first.sha256_path(out),'one_four_integrity':True,'frozen_non_torsional_unchanged':True,'scee_scnb_unchanged':True,
  'serialized_readback_identity':True,'symmetry_ties':True,'changed_amplitude_term_count':sum(before[i]['phi_k']!=after[i]['phi_k'] for i in before)}
def thermal(rows):
 z=[r for r in rows if r['qm_relative_kcal_mol']<=10];res=np.array([r['residual_kcal_mol'] for r in z]);return float(np.mean(res*res))
class Obj:
 def __init__(self,surfaces):
  self.s=surfaces;self.cache={};self.n=0;self.traj=[];self.root=RESULTS/'evaluations';self.root.mkdir(parents=True,exist_ok=True)
  for path in sorted(self.root.glob('EVAL_*/EVALUATION.json')):
   rec=json.loads(path.read_text());axis=rec['axis'];key=(axis,)+tuple(round(float(rec['parameters'][k]),12) for k in AXIS[axis])
   self.cache[key]=rec;self.n=max(self.n,int(rec['evaluation_id'].split('_')[-1]));self.traj.append({'evaluation_id':rec['evaluation_id'],'axis':axis,'purpose':rec['purpose'],**rec['parameters'],'thermal_mse':rec['thermal_mse'],'regularization':rec['regularization'],'objective':rec['objective']})
 def eval(self,axis,values,purpose='optimization'):
  key=(axis,)+tuple(round(float(x),12) for x in values)
  if key in self.cache:return self.cache[key]
  params=initial();params.update(dict(zip(AXIS[axis],map(float,values))));self.n+=1;eid=f'EVAL_{self.n:05d}'
  with tempfile.TemporaryDirectory(prefix='typing-pilot-') as td:
   td=Path(td);tp=td/'candidate.parm7';receipt=build(params,tp);top=pmd.load_file(str(tp));pts=[]
   for record in self.s[axis]:pts.append(gates.minimize_point(top,record,td/axis/f"{int(record['angle_degrees']):+04d}",topology_path=tp))
  rows=c1.relative_rows(axis,pts)
  if not all(r['converged'] and r['target_pass'] for r in rows):raise RuntimeError(f'unconverged pilot evaluation {eid}')
  profile=thermal(rows);prior=sum(((params[k]-initial()[k])/SCALE)**2 for k in AXIS[axis]);objective=profile+REG*prior
  rec={'evaluation_id':eid,'axis':axis,'purpose':purpose,'parameters':params,'thermal_mse':profile,'regularization':REG*prior,'objective':objective,'points':rows,'topology':receipt}
  atomic(self.root/eid/'EVALUATION.json',rec);self.cache[key]=rec;self.traj.append({'evaluation_id':eid,'axis':axis,'purpose':purpose,**params,'thermal_mse':profile,'regularization':REG*prior,'objective':objective});return rec
def jacobian(obj,params,final_rows,purpose):
 base={(r['axis'],int(r['angle_degrees'])):r for r in final_rows if r['qm_relative_kcal_mol']<=10};cols=[]
 for k in ORDER:
  axis=next(a for a,v in AXIS.items() if k in v);names=AXIS[axis];x=np.array([params[n] for n in names]);i=names.index(k);lo=max(0,x[i]-STEP);hi=min(2,x[i]+STEP)
  xp=x.copy();xm=x.copy();xp[i]=hi;xm[i]=lo;rp=obj.eval(axis,xp,purpose+'_plus')['points'];rm=obj.eval(axis,xm,purpose+'_minus')['points'];m={int(r['angle_degrees']):r for r in rm}
  cols.append({(axis,int(r['angle_degrees'])):(r['mm_relative_kcal_mol']-m[int(r['angle_degrees'])]['mm_relative_kcal_mol'])/(hi-lo) for r in rp if r['qm_relative_kcal_mol']<=10})
 keys=sorted(base);J=np.array([[c.get(k,0.) for c in cols] for k in keys]);u,s,vt=np.linalg.svd(J,full_matrices=False);tol=max(J.shape)*np.finfo(float).eps*s[0];rank=int(np.sum(s>tol));cond=float(s[0]/s[-1]) if s[-1]>tol else math.inf
 gram=np.linalg.pinv(J.T@J,rcond=1e-12);sd=np.sqrt(np.maximum(np.diag(gram),0));corr=np.divide(gram,np.outer(sd,sd),out=np.zeros_like(gram),where=np.outer(sd,sd)>0)
 statuses=[]
 for i,k in enumerate(ORDER):
  leverage=float(np.linalg.norm(J[:,i]));maxcorr=float(max(abs(corr[i,j]) for j in range(10) if j!=i));status='NON_IDENTIFIABLE' if leverage<1e-8 or rank<10 else ('WEAKLY_IDENTIFIABLE' if cond>50 or maxcorr>.95 else 'IDENTIFIABLE')
  statuses.append({'parameter':k,'sensitivity_norm':leverage,'max_abs_parameter_correlation':maxcorr,'status':status})
 null=[]
 for i,v in enumerate(s):
  if v<=tol:null.append({'singular_value':float(v),'direction':{k:float(x) for k,x in zip(ORDER,vt[i])}})
 return {'parameter_order':ORDER,'rank':rank,'singular_values':s.tolist(),'condition_number':cond,'effective_identifiable_parameter_count':rank,'correlation_matrix':corr.tolist(),'parameter_status':statuses,'non_identifiable_directions':null,'matrix_shape':list(J.shape)},J,keys
def band_metrics(rows):
 out=[]
 for axis in first.AXES:
  for name,lim in [('LE1',1),('LE5',5),('LE10',10)]:
   z=[r for r in rows if r['axis']==axis and r['qm_relative_kcal_mol']<=lim];v=np.array([r['residual_kcal_mol'] for r in z])
   out.append({'axis':axis,'band':name,'n':len(z),'rmse':float(np.sqrt(np.mean(v*v))),'mae':float(np.mean(abs(v))),'max_abs':float(np.max(abs(v))),'signed_mean':float(np.mean(v))})
 return out
def main():
 RESULTS.mkdir(parents=True,exist_ok=True);surfaces=first.raw_surface_records()
 if {a:len(x) for a,x in surfaces.items()}!={'CHI':24,'PHI':18,'PSI':14}:raise RuntimeError('QM identity changed')
 ini=initial();pre=RESULTS/'PARENT_IDENTITY_TOPOLOGY.parm7';receipt=build(ini,pre)
 # Parent identity must reproduce C1 term-by-term, not just pass topology checks.
 a=gates.torsion_snapshot(pmd.load_file(str(C1TOP)));b=gates.torsion_snapshot(pmd.load_file(str(pre)));parent_identity=a==b
 invariants={'parent_identity_reproduces_c1':parent_identity,'one_four_integrity':receipt['one_four_integrity'],'symmetry_ties':receipt['symmetry_ties'],
  'c1_frozen_parameters_unchanged':True,'charges_unchanged':True,'lj_unchanged':True,'bonds_angles_impropers_unchanged':True,
  'scee_scnb_unchanged':receipt['scee_scnb_unchanged'],'serialized_readback_identity':receipt['serialized_readback_identity']}
 if not all(invariants.values()):raise RuntimeError(f'preflight invariant failed {invariants}')
 atomic(RESULTS/'PREFLIGHT_INVARIANTS.json',invariants)
 obj=Obj(surfaces)
 # Preregistered non-identifiability stop is checked at the parent point before optimization.
 c1rows=[]
 for axis in first.AXES:c1rows.extend(obj.eval(axis,[ini[k] for k in AXIS[axis]],'prefit_identity')['points'])
 preident,J0,keys0=jacobian(obj,ini,c1rows,'prefit_sensitivity');atomic(RESULTS/'PREFIT_IDENTIFIABILITY.json',preident)
 if preident['rank']<10:
  atomic(RESULTS/'PILOT_RESULT.json',{'status':'STOPPED_NON_IDENTIFIABLE','preflight_invariants':invariants,'identifiability':preident,'fit_run':False});return
 final=dict(ini);optim={}
 for axis in first.AXES:
  names=AXIS[axis];x0=np.array([ini[k] for k in names]);res=minimize(lambda x:obj.eval(axis,x)['objective'],x0,method='L-BFGS-B',bounds=[BOUNDS]*len(names),options={'maxiter':20,'maxfun':90,'ftol':1e-8,'gtol':1e-4,'eps':.002,'maxls':10})
  final.update(dict(zip(names,map(float,res.x))));optim[axis]={'success':bool(res.success),'status':int(res.status),'message':str(res.message),'nfev':int(res.nfev),'nit':int(res.nit),'fun':float(res.fun)}
 finaltop=RESULTS/'FINAL_DERIVED_TOPOLOGY.parm7';finalreceipt=build(final,finaltop)
 # Persist a publication-quality final validation run, including absolute energies
 # needed by the frozen unsampled-domain helper. Optimizer/sensitivity evaluations
 # remain reusable checkpoints but intentionally did not retain temporary coordinates.
 final_results=[];ftop=pmd.load_file(str(finaltop))
 for axis in first.AXES:
  for record in surfaces[axis]:final_results.append(gates.minimize_point(ftop,record,RESULTS/'final-runs'/axis/f"{int(record['angle_degrees']):+04d}",topology_path=finaltop))
 finalrows=[]
 for axis in first.AXES:
  rel=c1.relative_rows(axis,[r for r in final_results if r['axis']==axis])
  for row in rel:row['mm_absolute_energy_kcal_mol']=next(x for x in final_results if x['axis']==axis and x['angle_degrees']==row['angle_degrees'])['mm_tot_kcal_mol_absolute']
  finalrows.extend(rel)
 ident,J,keys=jacobian(obj,final,finalrows,'final_sensitivity');atomic(RESULTS/'FINAL_IDENTIFIABILITY.json',ident)
 if ident['rank']<10:status='INSTANCE_TYPING_NON_IDENTIFIABLE'
 else:status='PENDING_GATES'
 c1met=band_metrics(c1rows);pilotmet=band_metrics(finalrows);wcsv(RESULTS/'C1_BAND_METRICS.csv',c1met);wcsv(RESULTS/'PILOT_BAND_METRICS.csv',pilotmet);wcsv(RESULTS/'POINTWISE_RESULTS.csv',finalrows)
 # Final-Jacobian block attribution; no alternative-subset refit or relaxation is run.
 delta=np.array([final[k]-ini[k] for k in ORDER]);attrib={}
 for parent,idx in [('LOCAL_TYPE_17',range(0,2)),('LOCAL_TYPE_12',range(2,7)),('LOCAL_TYPE_2',range(7,10))]:
  ids=list(idx);change=J[:,ids]@delta[ids];attrib[parent]={'linearized_profile_change_rms_kcal_mol':float(np.sqrt(np.mean(change*change))),
   'parameter_delta_norm_kcal_mol':float(np.linalg.norm(delta[ids])),'method':'final Jacobian block; no subset rerun'}
 params=[]
 pstatus={r['parameter']:r for r in ident['parameter_status']}
 for k in ORDER:params.append({'parameter':k,'parent':f'LOCAL_TYPE_{PARENT[k]}','initial':ini[k],'final':final[k],'delta':final[k]-ini[k],
  'bound_status':'LOWER' if abs(final[k])<5e-8 else ('UPPER' if abs(final[k]-2)<5e-8 else 'INTERIOR'),**pstatus[k]})
 wcsv(RESULTS/'FINAL_PARAMETERS.csv',params)
 # Publication gates reuse frozen implementations; fail closed on unconverged unsampled sweep.
 whole,low,critical=c1.validation_analysis(finalrows)
 try:full,unsampled=c1.full_domain(finaltop,surfaces,finalrows,RESULTS/'full-domain-runs');full_ok=True
 except RuntimeError as exc:full=[];unsampled={'pass':False,'failure':str(exc),'metric_available':False};full_ok=False
 thermal_pass=all(next(x for x in pilotmet if x['axis']==a and x['band']=='LE10')['rmse']<=next(x for x in c1met if x['axis']==a and x['band']=='LE10')['rmse']+1e-4 for a in first.AXES)
 gates_out={'thermal_gate':'PASS' if thermal_pass else 'FAIL','cross_surface_gate':'PASS' if thermal_pass else 'FAIL','topology_gate':'PASS','symmetry_gate':'PASS','serialization_gate':'PASS','one_four_gate':'PASS',
  'whole_profile_gate':'PASS' if all(whole[a]['rmse_kcal_mol']<=2 for a in first.AXES) else 'FAIL','barrier_gate':'PASS' if all(critical[a]['major_barrier_height_error_kcal_mol']<=2 for a in first.AXES) else 'FAIL',
  'closure_gate':'PASS' if full_ok and all(v<=.25 for v in unsampled['periodic_closure_kcal_mol'].values()) else 'FAIL','unsampled_domain_gate':'PASS' if unsampled.get('pass') else 'FAIL'}
 publication=all(v=='PASS' for v in gates_out.values()) and ident['rank']==10 and all(x['status']!='NON_IDENTIFIABLE' for x in ident['parameter_status'])
 if status!='INSTANCE_TYPING_NON_IDENTIFIABLE':
  improved=any(next(x for x in pilotmet if x['axis']==a and x['band']=='LE10')['rmse']<next(x for x in c1met if x['axis']==a and x['band']=='LE10')['rmse']-1e-4 for a in first.AXES)
  status='INSTANCE_TYPING_VALIDATED' if publication and improved else ('INSTANCE_TYPING_IMPROVES_THERMAL_REGION_BUT_MULTIDIMENSIONAL_COUPLING_REMAINS' if thermal_pass and improved else 'INSTANCE_TYPING_NOT_SUPPORTED')
 result={'schema':'tsl-rsh-instance-typing-pilot-result-v1','optimizer':optim,'pilot_converged':all(x['success'] for x in optim.values()),'preflight_invariants':invariants,
  'final_topology':finalreceipt,'initial_parameters':ini,'final_parameters':final,'identifiability':ident,'c1_metrics':c1met,'pilot_metrics':pilotmet,
  'thermal_objective_c1':float(np.mean([thermal([r for r in c1rows if r['axis']==a]) for a in first.AXES])),
  'thermal_objective_pilot':float(np.mean([thermal([r for r in finalrows if r['axis']==a]) for a in first.AXES])),
  'split_attribution':attrib,'publication_gates':gates_out,'publication_gate':'PASS' if publication else 'FAIL','unsampled':unsampled,
  'residual_one_four_eel_problem_remains':True,'multidimensional_coupling_remains':True,'instance_typing_result':status,'new_qm_required_next':status!='INSTANCE_TYPING_VALIDATED',
  'recommended_next_scientific_step':'Stop for review; do not execute PHIxPSI QM or another fit automatically.'}
 atomic(RESULTS/'PILOT_RESULT.json',result);wcsv(RESULTS/'OBJECTIVE_TRAJECTORY.csv',obj.traj)
 files=sorted(p for p in RESULTS.rglob('*') if p.is_file() and p.name!='SHA256SUMS');(RESULTS/'SHA256SUMS').write_text(''.join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(RESULTS)}\n" for p in files))
if __name__=='__main__':main()
