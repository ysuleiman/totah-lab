#!/usr/bin/env python3
"""Fresh PySCF/libcint SPCl validation, with an explicit audit of frozen core-guess SCF.
No DIIS, damping, mixing, optimization or Java-derived oracle values.
"""
import csv
import hashlib
import importlib.metadata
import json
import platform
from pathlib import Path
import numpy as np
import pyscf
from pyscf import gto, lib, scf
from spcl_benchmarks import systems, ANGSTROM_TO_BOHR

assert pyscf.__version__ == '2.10.0'
lib.num_threads(1)
root = Path(__file__).resolve().parents[1]
ref = root / 'src/test/resources/totah/lab/aether/reference'
basis_path = root / 'src/main/resources/totah/lab/aether/basis/sto-3g-spcl.csv'
assert hashlib.sha256(basis_path.read_bytes()).hexdigest() == 'db8e15f19d4b5a859b52dcc9c4b62392f510e10df7f396d8c7ab97df27b29586'
for row in csv.DictReader(basis_path.read_text().splitlines()):
    shell = gto.basis.load('sto-3g', {15:'P',16:'S',17:'Cl'}[int(row['atomic_number'])])[int(row['shell'])]
    assert shell[0] == int(row['angular_momentum'])
    assert [float(row['exponent']), float(row['coefficient'])] in shell[1:]


def plain(mf, p, s, h, occ, density_tolerance):
    previous = None
    trajectory = []
    for iteration in range(1, 129):
        vhf = mf.get_veff(dm=p)
        energy = mf.energy_tot(dm=p, h1e=h, vhf=vhf)
        eps, c = mf.eig(h + vhf, s)
        next_p = mf.make_rdm1(c, occ)
        delta = None if previous is None else abs(energy - previous)
        residual = float(np.max(np.abs(next_p - p)))
        trajectory.append(dict(iteration=iteration, total_energy=float(energy), delta_energy=None if delta is None else float(delta), density_residual=residual))
        if delta is not None and delta <= 1e-12 and residual <= density_tolerance:
            return p, True, trajectory
        previous = energy
        if iteration < 128:
            p = next_p
    return p, False, trajectory


rows, geometry, inputs, details = [], [], [], {}
for name, spec in systems().items():
    mol = gto.M(atom=spec['atoms'], basis='sto-3g', unit='Bohr', charge=spec['charge'], spin=0, cart=True, verbose=0)
    n = mol.nao_nr(); mf = scf.RHF(mol)
    mf.diis = False; mf.damp = 0; mf.level_shift = 0
    s = mf.get_ovlp(); t = mol.intor('int1e_kin'); v = mol.intor('int1e_nuc'); h = t + v
    eps, c = mf.eig(h, s); occ = np.zeros(n); occ[:mol.nelectron//2] = 2
    core_p = mf.make_rdm1(c, occ)
    baseline_p, baseline_converged, baseline_trajectory = plain(mf, core_p, s, h, occ, 1e-10)
    # Different external guess is solely an independent reference, never an Aether policy change.
    reference_guess = 'CORE_CONTINUATION' if baseline_converged else 'MINAO_EXTERNAL_REFERENCE_ONLY'
    start_p = baseline_p if baseline_converged else mf.get_init_guess(key='minao')
    p, converged, trajectory = plain(mf, start_p, s, h, occ, 1e-12)
    assert converged, f'{name}: independent reference did not converge without DIIS'
    assert np.array_equal(p, p.T), 'Density fixture requires exact symmetry'
    j, k = scf.hf.get_jk(mol, p, hermi=1); f = (h + j) - .5*k; eps, coefficients = mf.eig(f, s)
    for atom in range(mol.natm):
        geometry.append((name, atom, mol.atom_charge(atom), *[format(x,'.17g') for x in mol.atom_coord(atom)], spec['charge']))
    for i in range(n):
        for col in range(n): inputs.append((name,i,col,format(p[i,col],'.17g')))
    for label, matrix in [('S',s),('T',t),('V',v),('Hcore',h),('J',j),('K',k),('Fock',f),('density',p),('orbital_energies',eps[:,None])]:
        for i in range(matrix.shape[0]):
            for col in range(matrix.shape[1]): rows.append((name,label,i,col,0,0,format(matrix[i,col],'.17g')))
    eri = mol.intor('int2e', aosym='s8')
    pair_count = n*(n+1)//2; unique = pair_count*(pair_count+1)//2
    assert len(eri) == unique
    # Full unique tensors through 18 AOs; larger systems use reproducible coverage of every AO pair,
    # pair diagonals, four distinct-index quartets and uniform packed slots, without random sampling.
    selected = set(range(unique)) if n <= 18 else set(range(0, unique, max(1, unique//4096)))
    for pair in range(pair_count):
        selected.add(pair*(pair+1)//2)
        selected.add(pair*(pair+1)//2+pair)
    slots = 0; packed = 0
    for i in range(n):
        for col in range(i+1):
            ij = i*(i+1)//2+col
            for a in range(i+1):
                for b in range(a+1):
                    ab = a*(a+1)//2+b
                    if ab > ij: break
                    assert packed == ij*(ij+1)//2+ab
                    if packed in selected:
                        rows.append((name,'ERI',i,col,a,b,format(eri[packed],'.17g'))); slots += 1
                    packed += 1
    assert packed == unique
    for label, value in [('electronic',mf.energy_elec(dm=p,h1e=h,vhf=j-.5*k)[0]),('nuclear',mol.energy_nuc()),
                          ('total',mf.energy_tot(dm=p,h1e=h,vhf=j-.5*k)),('electrons',mol.nelectron),('occupied',mol.nelectron//2),
                          ('core_converged',int(baseline_converged))]:
        rows.append((name,label,0,0,0,0,format(value,'.17g')))
    details[name] = dict(category=spec['category'], atoms=spec['atoms'], unit='Bohr', basis='sto-3g', cartesian=True,
                         charge=spec['charge'], multiplicity=1, electrons=mol.nelectron, occupied=mol.nelectron//2,
                         ao_labels=mol.ao_labels(), basis_functions=n, unique_eri=unique, reference_eri_entries=slots,
                         core_guess_converged=baseline_converged, core_guess_trajectory=baseline_trajectory,
                         independent_reference_guess=reference_guess, independent_reference_converged=converged,
                         independent_reference_trajectory=trajectory,
                         total_energy=float(mf.energy_tot(dm=p,h1e=h,vhf=j-.5*k)))
    print(name, n, 'ERI references', slots, 'core', baseline_converged, len(baseline_trajectory),
          'reference', reference_guess, 'energy', details[name]['total_energy'], flush=True)
for filename, header, data in [('spcl.csv',['system','evidence','i','j','k','l','value'],rows),
                               ('spcl-geometry.csv',['system','nucleus','nuclear_charge','x','y','z','molecular_charge'],geometry),
                               ('spcl-density.csv',['system','row','column','value'],inputs)]:
    with (ref/filename).open('w',newline='') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(header);writer.writerows(data)
manifest = dict(status='SCREENING_ONLY',oracle='Fresh PySCF/libcint; plain RHF; explicitly separate core-policy audit and independent reference',
                python=platform.python_version(),platform=platform.platform(),packages={p:importlib.metadata.version(p) for p in ['pyscf','numpy','scipy','h5py']},
                threads=lib.num_threads(),systems=details,entries=len(rows),angstrom_to_bohr=ANGSTROM_TO_BOHR,
                geometry_source='Idealized fixed geometries constructed by spcl_benchmarks.py; no optimization',
                core_energy_threshold=1e-12,core_density_threshold=1e-10,reference_density_threshold=1e-12,maximum_iterations=128,
                diis=False,damping=0,level_shift=0,eri_selection='All unique for n<=18; else every AO-pair self/first-pair slot plus evenly spaced packed slots',
                hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),Path(__file__).with_name('spcl_benchmarks.py'),basis_path,
                       ref/'spcl.csv',ref/'spcl-geometry.csv',ref/'spcl-density.csv']})
(ref/'spcl-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(manifest['hashes'],flush=True)
