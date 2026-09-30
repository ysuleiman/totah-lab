#!/usr/bin/env python3
"""Independent diagnostic only: inspect a supplied Aether density with PySCF."""
import json
from pathlib import Path
import numpy as np
from pyscf import gto, scf
root=Path(__file__).resolve().parents[1]
folder=root/'validation/milestone-10.1'
p=np.loadtxt(folder/'n2-diis-density.csv',delimiter=',')
mol=gto.M(atom='N 0 0 0; N 2.07 0 0',basis='sto-3g',unit='Bohr',cart=True,spin=0,verbose=0)
mf=scf.RHF(mol);s=mf.get_ovlp();h=mf.get_hcore();j,k=mf.get_jk(dm=p);f=h+j-.5*k
energies,c=mf.eig(f,s);next_p=2*c[:,:mol.nelectron//2]@c[:,:mol.nelectron//2].T
print(json.dumps(dict(status='SCREENING_ONLY',pyscf_supplied_density_energy=mf.energy_tot(dm=p,h1e=h,vhf=j-.5*k),
    density_fixed_point_residual=float(np.max(np.abs(next_p-p))),commutator_max=float(np.max(np.abs(f@p@s-s@p@f))),
    trace_ps=float(np.trace(p@s)),orbital_energies=energies.tolist()),indent=2))
