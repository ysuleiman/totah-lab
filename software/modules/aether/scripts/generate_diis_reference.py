#!/usr/bin/env python3
"""Fresh PySCF CDIIS oracle; frozen earlier geometries, no Java-derived values."""
import csv, hashlib, json, platform
from pathlib import Path
import numpy as np
import pyscf
from pyscf import gto, scf, lib
assert pyscf.__version__ == '2.10.0'
lib.num_threads(1)
root=Path(__file__).resolve().parents[1]
ref=root/'src/test/resources/totah/lab/aether/reference'
specs={}; source_hashes={}
for milestone in ('scf','cno','spcl'):
    path=ref/(milestone+'-manifest.json'); source_hashes[path.name]=hashlib.sha256(path.read_bytes()).hexdigest()
    for name,spec in json.loads(path.read_text())['systems'].items():
        specs[name]={'atom':spec.get('atom',spec.get('atoms')), 'charge':spec['charge']}
rows=[]; details={}
for name,spec in sorted(specs.items()):
    mol=gto.M(atom=spec['atom'],unit='Bohr',basis='sto-3g',cart=True,charge=spec['charge'],spin=0,verbose=0)
    mf=scf.RHF(mol);mf.init_guess='1e';mf.diis_space=8;mf.diis_start_cycle=1
    mf.conv_tol=1e-12;mf.conv_tol_grad=1e-11;mf.max_cycle=128;mf.damp=0;mf.level_shift=0
    energy=mf.kernel(); kernel_converged=bool(mf.converged)
    # Common independent physical-state refinement, also avoiding backend gradient-flag ambiguity.
    p=mf.make_rdm1();s=mf.get_ovlp();h=mf.get_hcore();previous=None
    for refinement in range(128):
        j,k=mf.get_jk(dm=p);f=h+j-.5*k;eps,c=mf.eig(f,s)
        en=mf.energy_tot(dm=p,h1e=h,vhf=j-.5*k);next_p=mf.make_rdm1(c,mf.mo_occ)
        if previous is not None and abs(en-previous)<=1e-12 and np.max(np.abs(next_p-p))<=1e-12:break
        previous=en;p=next_p
    else:raise AssertionError((name,'independent physical-state refinement failed'))
    energy=en
    j,k=mf.get_jk(dm=p);f=h+j-.5*k;eps,c=mf.eig(f,s)
    fixed=mf.make_rdm1(c,mf.mo_occ)
    assert np.max(np.abs(fixed-p))<=1e-12,(name,'PySCF density residual')
    for label,m in [('density',p),('Fock',f),('orbital_energies',eps[:,None])]:
        for i in range(m.shape[0]):
            for z in range(m.shape[1]):rows.append((name,label,i,z,format(m[i,z],'.17g')))
    for label,v in [('total',mf.energy_tot(dm=p,h1e=h,vhf=j-.5*k)),('electronic',mf.energy_elec(dm=p,h1e=h,vhf=j-.5*k)[0]),('nuclear',mol.energy_nuc())]:
        rows.append((name,label,0,0,format(v,'.17g')))
    details[name]=dict(spec,converged=True,kernel_converged=kernel_converged,plain_refinement_iterations=refinement+1,total_energy=float(energy),density_residual=float(np.max(np.abs(fixed-p))),basis_functions=mol.nao_nr(),electrons=mol.nelectron,ao_labels=mol.ao_labels())
    print(name,energy,flush=True)
path=ref/'diis.csv'
with path.open('w',newline='') as stream:
    writer=csv.writer(stream,lineterminator='\n');writer.writerow(['system','quantity','i','j','value']);writer.writerows(rows)
manifest=dict(status='SCREENING_ONLY',pyscf=pyscf.__version__,python=platform.python_version(),numpy=np.__version__,oracle='Fresh PySCF RHF CDIIS followed by plain physical-state refinement; libcint; core guess; no damping/shift',refinement_max_iterations=128,refinement_energy_threshold=1e-12,refinement_density_threshold=1e-12,generator_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),threads=1,energy_threshold=1e-12,gradient_threshold=1e-11,max_cycle=128,source_geometry_manifest_hashes=source_hashes,systems=details,entries=len(rows),sha256=hashlib.sha256(path.read_bytes()).hexdigest())
(ref/'diis-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
