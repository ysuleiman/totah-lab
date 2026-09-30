#!/usr/bin/env python3
"""Read-only numerical diagnosis of preserved Java trajectories with fresh PySCF."""
import csv,json,re
from pathlib import Path
import numpy as np
from scipy.linalg import expm
from pyscf import gto,scf
from pyscf.soscf import newton_ah
root=Path(__file__).resolve().parents[1];folder=root/'validation/milestone-10.2'
data={}
for r in csv.DictReader((folder/'n2-matrices.csv').open()):
    key=(r['policy'],int(r['iteration']),r['quantity'])
    if key not in data:data[key]=np.zeros((10,1 if r['quantity']=='eps' else 10))
    data[key][int(r['i']),int(r['j'])]=float(r['value'])
s=data['initial',0,'S'];occ=np.r_[np.full(7,2.),np.zeros(3)]
mol=gto.M(atom='N 0 0 0; N 2.07 0 0',basis='sto-3g',unit='Bohr',cart=True,spin=0,verbose=0)
mf=scf.RHF(mol);h=mf.get_hcore()
# Rotation by 90 degrees about the molecular x axis, in the fixed Cartesian AO basis.
u=np.eye(10)
for offset in [0,5]:u[offset+3,offset+3]=u[offset+4,offset+4]=0;u[offset+3,offset+4]=-1;u[offset+4,offset+3]=1
w,v=np.linalg.eigh(s);sqrt_s=(v*np.sqrt(w))@v.T
rows=[];final={}
for policy in ['plain','diis']:
    numbers=sorted(k[1] for k in data if k[0]==policy and k[2]=='P');previous=None
    for it in numbers:
        p=data[policy,it,'P'];f=data[policy,it,'F'];c=data[policy,it,'C'];eps=data[policy,it,'eps'][:,0]
        q=.5*sqrt_s@p@sqrt_s
        vals,vec=np.linalg.eigh(q);subspace=vec[:,-7:]
        singular=np.linalg.svd(previous.T@subspace,compute_uv=False) if previous is not None else np.ones(7)
        previous=subspace
        rows.append(dict(policy=policy,iteration=it,pi_y_electrons=float(sum((p@s)[i,i] for i in [3,8])),pi_z_electrons=float(sum((p@s)[i,i] for i in [4,9])),
            commutator_max=float(np.max(np.abs(f@p@s-s@p@f))),commutator_frobenius=float(np.linalg.norm(f@p@s-s@p@f)),
            homo_lumo_gap=float(eps[7]-eps[6]),occupied_subspace_min_cosine=float(singular[-1]),rotation_density_difference=float(np.max(np.abs(u@p@u.T-p))),orbital_energies=eps.tolist()))
    p=data[policy,numbers[-1],'P'];f=h+mf.get_veff(dm=p);eps,c=mf.eig(f,s)
    mf.mo_coeff=c;mf.mo_occ=occ;mf.mo_energy=eps
    g,hop,diag=newton_ah.gen_g_hop_rhf(mf,c,occ,with_symmetry=False)
    identity=np.eye(len(g));hessian=np.column_stack([2*hop(col).real for col in identity]);eigen=np.linalg.eigvalsh((hessian+hessian.T)/2)
    # Independent finite-difference Jacobian of one plain Roothaan occupied-subspace update.
    def jacobian(step):
        columns=[]
        for a in range(3):
            for i in range(7):
                outputs=[]
                for sign in [-1,1]:
                    rotation=np.zeros((10,10));rotation[7+a,i]=sign*step;rotation[i,7+a]=-sign*step
                    trial_c=c@expm(rotation);trial_p=2*trial_c[:,:7]@trial_c[:,:7].T
                    trial_f=h+mf.get_veff(dm=trial_p);_,next_c=mf.eig(trial_f,s)
                    next_p=2*next_c[:,:7]@next_c[:,:7].T
                    outputs.append((.5*c[:,7:].T@s@next_p@s@c[:,:7]).ravel())
                columns.append((outputs[1]-outputs[0])/(2*step))
        return np.column_stack(columns)
    jac=jacobian(1e-5);jac_check=jacobian(1e-6)
    jac_eigen=np.linalg.eigvals(jac)
    replay=scf.RHF(mol);replay.conv_tol=1e-12;replay.conv_tol_grad=1e-10;replay.max_cycle=128
    en=replay.kernel(dm0=p)
    final[policy]=dict(energy=float(mf.energy_tot(dm=p,h1e=h,vhf=f-h)),trace_ps=float(np.trace(p@s)),idempotency_max=float(np.max(np.abs(p@s@p-2*p))),
        physical_density_residual=float(np.max(np.abs(2*c[:,:7]@c[:,:7].T-p))),internal_hessian_eigenvalues=eigen.tolist(),
        roothaan_jacobian_spectral_radius=float(np.max(np.abs(jac_eigen))),jacobian_step_check_max_difference=float(np.max(np.abs(jac-jac_check))),hessian_asymmetry=float(np.max(np.abs(hessian-hessian.T))),pyscf_replay_converged=bool(replay.converged),pyscf_replay_energy=float(en),pyscf_replay_density_difference=float(np.max(np.abs(replay.make_rdm1()-p))))
initial=data['initial',0,'eps'][:,0];coeff=[]
receipt=(folder/'n2-diis-bad.receipt').read_text()
# Fields contain nested lists: extract each update up to the next trajectory newline.
for line in receipt.splitlines():
    if line.startswith('DiisUpdate['):
        entry={}
        for name in ['historyIterations','coefficients','events']:
            m=re.search(name+r'=\[(.*?)\]',line);entry[name]=m.group(1) if m else ''
        for name in ['errorMaximum','reciprocalCondition']:
            m=re.search(name+r'=([^,]+)',line);entry[name]=float(m.group(1))
        indices=[int(v) for v in entry['historyIterations'].split(',') if v.strip()]
        weights=[float(v) for v in entry['coefficients'].split(',') if v.strip()]
        entry['historyIterations']=indices;entry['coefficients']=weights
        if weights:
            update_f=sum(weight*data['diis',iteration,'F'] for weight,iteration in zip(weights,indices))
            update_eps,update_c=mf.eig(update_f,s);update_p=2*update_c[:,:7]@update_c[:,:7].T
            entry['extrapolated_orbital_energies']=update_eps.tolist()
            entry['coefficient_sum_error']=abs(sum(weights)-1)
            entry['occupied_density_reconstruction_error']=float(np.max(np.abs(update_p-data['diis',len(coeff)+1,'nextP'])))
        coeff.append(entry)
result=dict(status='SCREENING_ONLY',initial_energies=initial.tolist(),initial_homo_lumo_gap=float(initial[7]-initial[6]),
    initial_ctsc_error=float(np.max(np.abs(data['initial',0,'C'].T@s@data['initial',0,'C']-np.eye(10)))),initial_rotation_density_difference=float(np.max(np.abs(u@data['initial',0,'P']@u.T-data['initial',0,'P']))),final=final,trajectories=rows,diis_updates=coeff)
(folder/'n2-diagnosis.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(dict(initial_energies=result['initial_energies'],initial_gap=result['initial_homo_lumo_gap'],final=final,diis_updates=coeff),indent=2))
for r in rows:print(r['policy'],r['iteration'],'pi',r['pi_y_electrons'],r['pi_z_electrons'],'comm',r['commutator_max'],'gap',r['homo_lumo_gap'],'subspace',r['occupied_subspace_min_cosine'])
