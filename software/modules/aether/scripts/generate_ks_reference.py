#!/usr/bin/env python3
"""Fresh Libxc/PySCF LDA_X+LDA_C_PZ intermediates and self-consistent grid ladder."""
import csv,json,hashlib,platform
from pathlib import Path
import numpy as np
import pyscf
from pyscf import dft,lib
from pyscf.dft import libxc
from dft_reference_common import ROOT,REF,LADDER,specs,molecule,grid
assert pyscf.__version__=='2.10.0';lib.num_threads(1)
scalar=[]
for rs in [1e-4,.01,.1,.9,.999999,1.000001,1.1,2,10,1e3]:
    rho=3/(4*np.pi*rs**3)
    for name,code in [('EXCHANGE','LDA_X'),('EXCHANGE_PZ81','LDA_X,LDA_C_PZ')]:
        e,v,*_=libxc.eval_xc(code,np.array([rho]),deriv=1);scalar.append((name,format(rho,'.17g'),format(e[0],'.17g'),format(v[0][0],'.17g')))
with (REF/'lda-scalar.csv').open('w',newline='') as f:w=csv.writer(f,lineterminator='\n');w.writerow(['functional','rho','epsilon','potential']);w.writerows(scalar)
values={}
for c in csv.DictReader((REF/'dft-density.csv').open()):values.setdefault(c['system'],[]).append(float(c['value']))
fixed=[];rows=[];details={}
for name in specs():
    mol=molecule(name);p=np.array(values[name]).reshape(mol.nao_nr(),mol.nao_nr());g=grid(mol,80,302)
    ne,exc,v=dft.numint.NumInt().nr_rks(mol,g,'LDA_X,LDA_C_PZ',p)
    fixed.extend([(name,'electrons',0,0,format(ne,'.17g')),(name,'exc',0,0,format(exc,'.17g'))])
    for i in range(len(p)):
        for j in range(len(p)):fixed.append((name,'vxc',i,j,format(v[i,j],'.17g')))
    configurations=LADDER if name in ('h2o','n2','h2s') else ((120,590),)
    for nr,na in configurations:
        mf=dft.RKS(mol);mf.xc='LDA_X,LDA_C_PZ';mf.grids=grid(mol,nr,na);mf.small_rho_cutoff=0
        mf.init_guess='1e';mf.conv_tol=1e-12;mf.conv_tol_grad=1e-11;mf.max_cycle=128;mf.diis_space=8;mf.damp=0;mf.level_shift=0
        mf.kernel();assert mf.converged,(name,nr,na,'reference did not converge')
        p=mf.make_rdm1();p=(p+p.T)/2;s=mf.get_ovlp();h=mf.get_hcore();j=mf.get_j(dm=p)
        ne,exc,v=mf._numint.nr_rks(mol,mf.grids,mf.xc,p)
        f=h+j+v;eps,c=mf.eig(f,s);updated=mf.make_rdm1(c,mf.mo_occ)
        residual=np.max(np.abs(updated-p));assert residual<1e-9,(name,nr,na,residual)
        vhf=mf.get_veff(dm=p);ee=mf.energy_elec(dm=p,h1e=h,vhf=vhf)[0];et=mf.energy_tot(dm=p,h1e=h,vhf=vhf)
        for quantity,value in [('electrons',ne),('exc',exc),('electronic',ee),('total',et),('nuclear',mol.energy_nuc())]:rows.append((name,nr,na,quantity,0,0,format(value,'.17g')))
        for quantity,matrix in [('density',p),('vxc',v),('fock',f),('energies',eps[:,None])]:
            for i in range(matrix.shape[0]):
                for k in range(matrix.shape[1]):rows.append((name,nr,na,quantity,i,k,format(matrix[i,k],'.17g')))
        details[f'{name}:{nr}:{na}']=dict(converged=bool(mf.converged),integrated_electrons=float(ne),xc_energy=float(exc),total_energy=float(et),density_residual=float(residual),grid_points=len(mf.grids.weights))
        print('KS_REFERENCE',name,nr,na,'Ne',ne,'Exc',exc,'Etot',et,'residual',residual,flush=True)
for file,header,values in [('dft-pz.csv',['system','quantity','i','j','value'],fixed),('ks.csv',['system','radial','angular','quantity','i','j','value'],rows)]:
    with (REF/file).open('w',newline='') as f:w=csv.writer(f,lineterminator='\n');w.writerow(header);w.writerows(values)
(REF/'ks-manifest.json').write_text(json.dumps(dict(status='SCREENING_ONLY',pyscf=pyscf.__version__,libxc=libxc.__version__,python=platform.python_version(),numpy=np.__version__,functional='LDA_X,LDA_C_PZ;unpolarized',energy_tolerance=1e-12,gradient_tolerance=1e-11,max_cycle=128,small_rho_cutoff=0,threads=1,systems=specs(),results=details,entries=len(rows),fixed_density_entries=len(fixed),hashes={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [REF/'ks.csv',REF/'dft-pz.csv',REF/'lda-scalar.csv',Path(__file__),ROOT/'scripts/dft_reference_common.py']}),indent=2)+'\n')
