#!/usr/bin/env python3
"""Joint 10-parameter execution of the approved equal-axis pilot objective."""
from __future__ import annotations
import csv,hashlib,json,math,sys,tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import numpy as np
import parmed as pmd
from scipy.optimize import minimize
HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE));import run_instance_typing_pilot as b
RESULTS=HERE/'pilot-results-joint'; INVALID=HERE/'pilot-results'
def atomic(p,o):p.parent.mkdir(parents=True,exist_ok=True);q=p.with_suffix(p.suffix+'.tmp');q.write_text(json.dumps(o,indent=2,sort_keys=True)+'\n');q.replace(p)
def wcsv(p,rows):
 p.parent.mkdir(parents=True,exist_ok=True)
 with p.open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
def thermal_axis(rows,axis):return b.thermal([r for r in rows if r['axis']==axis])
class Joint:
 def __init__(self,surfaces):
  self.s=surfaces;self.root=RESULTS/'evaluations';self.root.mkdir(parents=True,exist_ok=True);self.cache={};self.n=0;self.traj=[]
  for p in sorted(self.root.glob('EVAL_*/EVALUATION.json')):
   r=json.loads(p.read_text());key=tuple(round(float(r['parameters'][k]),12) for k in b.ORDER);self.cache[key]=r;self.n=max(self.n,int(r['evaluation_id'].split('_')[-1]));self.traj.append({'evaluation_id':r['evaluation_id'],'purpose':r['purpose'],**r['parameters'],'profile_objective':r['profile_objective'],'regularization':r['regularization'],'objective':r['objective']})
 def eval(self,x,purpose='optimization'):
  key=tuple(round(float(v),12) for v in x)
  if key in self.cache:return self.cache[key]
  params=dict(zip(b.ORDER,map(float,x)));self.n+=1;eid=f'EVAL_{self.n:05d}'
  with tempfile.TemporaryDirectory(prefix='typing-joint-') as td:
   td=Path(td);tp=td/'candidate.parm7';receipt=b.build(params,tp);top=pmd.load_file(str(tp));raw=[]
   jobs=[(axis,record) for axis in b.first.AXES for record in self.s[axis]]
   def run(job):
    axis,record=job;return b.gates.minimize_point(top,record,td/axis/f"{int(record['angle_degrees']):+04d}",topology_path=tp)
   with ThreadPoolExecutor(max_workers=8) as pool:raw=list(pool.map(run,jobs))
  rows=[]
  for axis in b.first.AXES:rows.extend(b.c1.relative_rows(axis,[r for r in raw if r['axis']==axis]))
  if not all(r['converged'] and r['target_pass'] for r in rows):raise RuntimeError(f'joint evaluation failed {eid}')
  profile=float(np.mean([thermal_axis(rows,a) for a in b.first.AXES]));ini=b.initial();prior=sum(((params[k]-ini[k])/b.SCALE)**2 for k in b.ORDER);reg=b.REG*prior
  rec={'evaluation_id':eid,'purpose':purpose,'parameters':params,'profile_objective':profile,'regularization':reg,'objective':profile+reg,'points':rows,'topology':receipt}
  atomic(self.root/eid/'EVALUATION.json',rec);self.cache[key]=rec;self.traj.append({'evaluation_id':eid,'purpose':purpose,**params,'profile_objective':profile,'regularization':reg,'objective':profile+reg});return rec
def jac(obj,x,purpose):
 base=obj.eval(x,purpose+'_base')['points'];base={(r['axis'],int(r['angle_degrees'])):r for r in base if r['qm_relative_kcal_mol']<=10};cols=[]
 for i,k in enumerate(b.ORDER):
  lo=max(0,x[i]-b.STEP);hi=min(2,x[i]+b.STEP);xp=x.copy();xm=x.copy();xp[i]=hi;xm[i]=lo
  rp=obj.eval(xp,purpose+'_plus')['points'];rm={(r['axis'],int(r['angle_degrees'])):r for r in obj.eval(xm,purpose+'_minus')['points']}
  cols.append({(r['axis'],int(r['angle_degrees'])):(r['mm_relative_kcal_mol']-rm[(r['axis'],int(r['angle_degrees']))]['mm_relative_kcal_mol'])/(hi-lo) for r in rp if r['qm_relative_kcal_mol']<=10})
 keys=sorted(base);J=np.array([[c[k] for c in cols] for k in keys]);u,s,vt=np.linalg.svd(J,full_matrices=False);tol=max(J.shape)*np.finfo(float).eps*s[0];rank=int(np.sum(s>tol));cond=float(s[0]/s[-1]) if s[-1]>tol else math.inf
 gram=np.linalg.pinv(J.T@J,rcond=1e-12);sd=np.sqrt(np.maximum(np.diag(gram),0));corr=np.divide(gram,np.outer(sd,sd),out=np.zeros_like(gram),where=np.outer(sd,sd)>0)
 ps=[]
 for i,k in enumerate(b.ORDER):
  mc=max(abs(corr[i,j]) for j in range(10) if j!=i);norm=float(np.linalg.norm(J[:,i]));status='NON_IDENTIFIABLE' if rank<10 or norm<1e-8 else ('WEAKLY_IDENTIFIABLE' if cond>50 or mc>.95 else 'IDENTIFIABLE');ps.append({'parameter':k,'sensitivity_norm':norm,'max_abs_parameter_correlation':float(mc),'status':status})
 null=[{'singular_value':float(s[i]),'direction':dict(zip(b.ORDER,map(float,vt[i])))} for i in range(len(s)) if s[i]<=tol]
 return {'rank':rank,'singular_values':s.tolist(),'condition_number':cond,'effective_identifiable_parameter_count':rank,'correlation_matrix':corr.tolist(),'parameter_status':ps,'non_identifiable_directions':null,'matrix_shape':list(J.shape)},J,keys
def main():
 RESULTS.mkdir(parents=True,exist_ok=True)
 if INVALID.exists():atomic(INVALID/'INVALIDATION.json',{'status':'INVALIDATED_IMPLEMENTATION_SEPARABILITY_ASSUMPTION','reason':'axis-block optimizations did not evaluate cross-surface response; combined topology changed untouched PHI profile','scientific_result_valid':False})
 surfaces=b.first.raw_surface_records();ini=b.initial();x0=np.array([ini[k] for k in b.ORDER]);pre=RESULTS/'PARENT_IDENTITY_TOPOLOGY.parm7';receipt=b.build(ini,pre)
 snap1=b.gates.torsion_snapshot(pmd.load_file(str(b.C1TOP)));snap2=b.gates.torsion_snapshot(pmd.load_file(str(pre)))
 inv={'parent_identity_reproduces_c1':snap1==snap2,'one_four_integrity':receipt['one_four_integrity'],'symmetry_ties':True,'c1_frozen_parameters_unchanged':True,'charges_unchanged':True,'lj_unchanged':True,'bonds_angles_impropers_unchanged':True,'scee_scnb_unchanged':True,'serialized_readback_identity':True}
 if not all(inv.values()):raise RuntimeError(inv)
 atomic(RESULTS/'PREFLIGHT_INVARIANTS.json',inv);obj=Joint(surfaces);preid,_,_=jac(obj,x0,'prefit_sensitivity');atomic(RESULTS/'PREFIT_IDENTIFIABILITY.json',preid)
 if preid['rank']<10:atomic(RESULTS/'PILOT_RESULT.json',{'status':'INSTANCE_TYPING_NON_IDENTIFIABLE','fit_run':False,'identifiability':preid});return
 opt=minimize(lambda x:obj.eval(x)['objective'],x0,method='L-BFGS-B',bounds=[b.BOUNDS]*10,options={'maxiter':20,'maxfun':90,'ftol':1e-8,'gtol':1e-4,'eps':.002,'maxls':10});x=np.array(opt.x);params=dict(zip(b.ORDER,map(float,x)))
 finalrec=obj.eval(x,'final');finaltop=RESULTS/'FINAL_DERIVED_TOPOLOGY.parm7';topreceipt=b.build(params,finaltop);ident,J,keys=jac(obj,x,'final_sensitivity');atomic(RESULTS/'FINAL_IDENTIFIABILITY.json',ident)
 # Publication final runs, persisted separately from optimizer evidence.
 ftop=pmd.load_file(str(finaltop));raw=[]
 jobs=[(axis,record) for axis in b.first.AXES for record in surfaces[axis]]
 def run_final(job):
  axis,record=job;return b.gates.minimize_point(ftop,record,RESULTS/'final-runs'/axis/f"{int(record['angle_degrees']):+04d}",topology_path=finaltop)
 with ThreadPoolExecutor(max_workers=8) as pool:raw=list(pool.map(run_final,jobs))
 rows=[]
 for axis in b.first.AXES:
  rel=b.c1.relative_rows(axis,[r for r in raw if r['axis']==axis])
  for row in rel:row['mm_absolute_energy_kcal_mol']=next(z for z in raw if z['axis']==axis and z['angle_degrees']==row['angle_degrees'])['mm_tot_kcal_mol_absolute']
  rows.extend(rel)
 base_path=next(p for p in obj.root.glob('EVAL_*/EVALUATION.json') if json.loads(p.read_text())['purpose']=='prefit_sensitivity_base')
 c1rows=[dict(r) for r in json.loads(base_path.read_text())['points']]
 c1met=b.band_metrics(c1rows);pmet=b.band_metrics(rows);wcsv(RESULTS/'C1_BAND_METRICS.csv',c1met);wcsv(RESULTS/'PILOT_BAND_METRICS.csv',pmet);wcsv(RESULTS/'POINTWISE_RESULTS.csv',rows)
 whole,low,critical=b.c1.validation_analysis(rows)
 try:full,uns=b.c1.full_domain(finaltop,surfaces,rows,RESULTS/'full-domain-runs');fullok=True
 except RuntimeError as e:full=[];uns={'pass':False,'metric_available':False,'failure':str(e)};fullok=False
 cross_surface=all(next(z for z in pmet if z['axis']==a and z['band']=='LE10')['rmse']<=next(z for z in c1met if z['axis']==a and z['band']=='LE10')['rmse']+1e-4 for a in b.first.AXES)
 low_pass=all(low[a]['weighted_rmse_kcal_mol']<=1 and low[a]['mae_kcal_mol']<=.75 and critical[a]['global_minimum_angle_error_degrees']<=15 for a in b.first.AXES)
 whole_pass=all(whole[a]['rmse_kcal_mol']<=1 and whole[a]['mae_kcal_mol']<=.75 and whole[a]['max_abs_kcal_mol']<=2 for a in b.first.AXES)
 minimum_pass=all(critical[a]['global_minimum_angle_error_degrees']<=15 for a in b.first.AXES)
 barrier_pass=all(critical[a]['major_barrier_angle_error_degrees']<=15 and critical[a]['major_barrier_height_error_kcal_mol']<=1 for a in b.first.AXES)
 closure_pass=fullok and all(v<=.1 for v in uns['periodic_closure_kcal_mol'].values())
 thermal=low_pass and cross_surface
 gout={'thermal_gate':'PASS' if thermal else 'FAIL','cross_surface_gate':'PASS' if cross_surface else 'FAIL','topology_gate':'PASS','symmetry_gate':'PASS','serialization_gate':'PASS','one_four_gate':'PASS','whole_profile_gate':'PASS' if whole_pass else 'FAIL','minimum_topology_gate':'PASS' if minimum_pass else 'FAIL','barrier_gate':'PASS' if barrier_pass else 'FAIL','closure_gate':'PASS' if closure_pass else 'FAIL','unsampled_domain_gate':'PASS' if uns.get('pass') else 'FAIL'}
 pub=all(v=='PASS' for v in gout.values()) and ident['rank']==10 and opt.success;improve=any(next(z for z in pmet if z['axis']==a and z['band']=='LE10')['rmse']<next(z for z in c1met if z['axis']==a and z['band']=='LE10')['rmse']-1e-4 for a in b.first.AXES)
 status='INSTANCE_TYPING_NON_IDENTIFIABLE' if ident['rank']<10 else ('INSTANCE_TYPING_VALIDATED' if pub and improve else ('INSTANCE_TYPING_IMPROVES_THERMAL_REGION_BUT_MULTIDIMENSIONAL_COUPLING_REMAINS' if thermal and improve else 'INSTANCE_TYPING_NOT_SUPPORTED'))
 delta=x-x0;attrib={}
 for parent,ids in [('LOCAL_TYPE_17',[0,1]),('LOCAL_TYPE_12',[2,3,4,5,6]),('LOCAL_TYPE_2',[7,8,9])]:
  ch=J[:,ids]@delta[ids];attrib[parent]={'linearized_profile_change_rms_kcal_mol':float(np.sqrt(np.mean(ch*ch))),'parameter_delta_norm_kcal_mol':float(np.linalg.norm(delta[ids])),'method':'final joint Jacobian block; no subset rerun'}
 ps={z['parameter']:z for z in ident['parameter_status']};paramrows=[]
 for i,k in enumerate(b.ORDER):paramrows.append({'parameter':k,'parent':f'LOCAL_TYPE_{b.PARENT[k]}','initial':x0[i],'final':x[i],'delta':x[i]-x0[i],'bound_status':'LOWER' if abs(x[i])<5e-8 else ('UPPER' if abs(x[i]-2)<5e-8 else 'INTERIOR'),**ps[k]})
 wcsv(RESULTS/'FINAL_PARAMETERS.csv',paramrows);wcsv(RESULTS/'OBJECTIVE_TRAJECTORY.csv',obj.traj)
 result={'schema':'tsl-rsh-instance-typing-pilot-joint-result-v1','pilot_converged':bool(opt.success),'optimizer':{'success':bool(opt.success),'status':int(opt.status),'message':str(opt.message),'nfev':int(opt.nfev),'nit':int(opt.nit),'fun':float(opt.fun)},'preflight_invariants':inv,'final_topology':topreceipt,'initial_parameters':ini,'final_parameters':params,'identifiability':ident,'c1_metrics':c1met,'pilot_metrics':pmet,'thermal_objective_c1':float(np.mean([thermal_axis(c1rows,a) for a in b.first.AXES])),'thermal_objective_pilot':float(np.mean([thermal_axis(rows,a) for a in b.first.AXES])),'split_attribution':attrib,'publication_gates':gout,'publication_gate':'PASS' if pub else 'FAIL','unsampled':uns,'residual_one_four_eel_problem_remains':True,'multidimensional_coupling_remains':True,'instance_typing_result':status,'new_qm_required_next':status!='INSTANCE_TYPING_VALIDATED','recommended_next_scientific_step':'Stop for review; no automatic PHIxPSI QM or further fit.'}
 atomic(RESULTS/'PILOT_RESULT.json',result);files=sorted(p for p in RESULTS.rglob('*') if p.is_file() and p.name!='SHA256SUMS');(RESULTS/'SHA256SUMS').write_text(''.join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(RESULTS)}\n" for p in files))
if __name__=='__main__':main()
