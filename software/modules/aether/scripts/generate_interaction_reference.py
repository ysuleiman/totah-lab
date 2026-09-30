#!/usr/bin/env python3
"""Independent RHF/ghost counterpoise oracle; no Aether-derived energy inputs."""
import csv,json,hashlib,platform
from pathlib import Path
import numpy as np
import pyscf
from pyscf import gto,scf,lib
from interaction_benchmarks import systems
assert pyscf.__version__=='2.10.0'
lib.num_threads(1)
root=Path(__file__).resolve().parents[1];ref=root/'src/test/resources/totah/lab/aether/reference'
geometry=[];rows=[];details={}
for name,spec in systems().items():
    a,b=spec['a'],spec['b'];ca,cb=spec['charge_a'],spec['charge_b']
    for fragment,atoms,charge in [('A',a,ca),('B',b,cb)]:
        for i,(element,p) in enumerate(atoms):geometry.append((name,fragment,i,gto.charge(element),*[format(x,'.17g') for x in p],charge))
    models=[('COMPLEX',a+b,ca+cb),('A_OWN',a,ca),('B_OWN',b,cb),
            ('A_WITH_GHOST_B',a+[('ghost-'+el,p) for el,p in b],ca),('B_WITH_GHOST_A',b+[('ghost-'+el,p) for el,p in a],cb)]
    energies={};record={}
    for role,atoms,charge in models:
        mol=gto.M(atom=atoms,basis='sto-3g',unit='Bohr',cart=True,charge=charge,spin=0,verbose=0)
        mf=scf.RHF(mol);mf.init_guess='1e';mf.conv_tol=1e-12;mf.conv_tol_grad=1e-10;mf.max_cycle=128;mf.diis_space=8;mf.damp=0;mf.level_shift=0
        mf.kernel();kernel_converged=bool(mf.converged)
        # Reference precision refinement is explicit and independent of Java's iteration trajectory.
        p=mf.make_rdm1();s=mf.get_ovlp();h=mf.get_hcore();previous=None
        for refinement in range(128):
            vhf=mf.get_veff(dm=p);energy=mf.energy_tot(dm=p,h1e=h,vhf=vhf);eps,c=mf.eig(h+vhf,s);next_p=mf.make_rdm1(c,mf.mo_occ)
            residual=float(np.max(np.abs(next_p-p)))
            if previous is not None and abs(energy-previous)<=1e-12 and residual<=1e-12:break
            previous=energy;p=next_p
        else:raise AssertionError((name,role,'reference refinement failed'))
        energies[role]=float(energy)
        for quantity,value in [('total',energy),('nuclear',mol.energy_nuc()),('electronic',mf.energy_elec(dm=p,h1e=h,vhf=vhf)[0]),('electrons',mol.nelectron),('basis_functions',mol.nao_nr())]:rows.append((name,role,quantity,0,0,format(value,'.17g')))
        # Individual final density and Fock entries independently validate ghost-system operators.
        for quantity,matrix in [('density',p),('Fock',h+vhf)]:
            for i in range(matrix.shape[0]):
                for j in range(matrix.shape[1]):rows.append((name,role,quantity,i,j,format(matrix[i,j],'.17g')))
        record[role]=dict(total_energy=float(energy),nuclear_energy=float(mol.energy_nuc()),charge=charge,electrons=mol.nelectron,basis_functions=mol.nao_nr(),ao_labels=mol.ao_labels(),nuclear_charges=mol.atom_charges().tolist(),kernel_converged=kernel_converged,plain_refinement_iterations=refinement+1,final_density_residual=residual)
        print(name,role,energy,flush=True)
    unc=(energies['COMPLEX']-energies['A_OWN'])-energies['B_OWN'];cp=(energies['COMPLEX']-energies['A_WITH_GHOST_B'])-energies['B_WITH_GHOST_A']
    rows.extend([(name,'INTERACTION','uncorrected',0,0,format(unc,'.17g')),(name,'INTERACTION','counterpoise',0,0,format(cp,'.17g'))])
    details[name]=dict(spec,components=record,uncorrected=unc,counterpoise=cp)
for filename,header,values in [('interaction-geometry.csv',['system','fragment','atom','atomic_number','x','y','z','charge'],geometry),('interaction.csv',['system','component','quantity','i','j','value'],rows)]:
    with (ref/filename).open('w',newline='') as f:w=csv.writer(f,lineterminator='\n');w.writerow(header);w.writerows(values)
manifest=dict(status='SCREENING_ONLY',method='RHF/STO-3G Cartesian; Boys-Bernardi counterpoise; real atoms then ghost atoms',pyscf=pyscf.__version__,python=platform.python_version(),numpy=np.__version__,threads=1,core_guess=True,energy_tolerance=1e-12,gradient_tolerance=1e-10,max_cycle=128,reference_plain_refinement_cap=128,reference_density_tolerance=1e-12,systems=details,entries=len(rows),hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),root/'scripts/interaction_benchmarks.py',root/'scripts/spcl_benchmarks.py',ref/'interaction.csv',ref/'interaction-geometry.csv']})
(ref/'interaction-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
