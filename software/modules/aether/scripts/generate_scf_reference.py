#!/usr/bin/env python3
"""Fresh plain PySCF RHF references for the frozen hydrogen benchmarks, no DIIS/mixing."""
import csv
import hashlib
import importlib.metadata
import json
import platform
from pathlib import Path
import numpy as np
import pyscf
from pyscf import gto, lib, scf

assert pyscf.__version__ == '2.10.0'
lib.num_threads(1)
root = Path(__file__).resolve().parents[1]
reference = root / 'src/test/resources/totah/lab/aether/reference'
previous_path = reference / 'jk-manifest.json'
previous = json.loads(previous_path.read_text())
basis_path = root / 'src/main/resources/totah/lab/aether/basis/sto-3g-h.properties'
assert hashlib.sha256(basis_path.read_bytes()).hexdigest() == previous['basis_resource_sha256']
bundled = dict(line.split('=', 1) for line in basis_path.read_text().splitlines() if line and not line.startswith('#'))
upstream = gto.basis.load('sto-3g', 'H')[0]
assert [p[0] for p in upstream[1:]] == list(map(float, bundled['exponents'].split(',')))
assert [p[1] for p in upstream[1:]] == list(map(float, bundled['coefficients'].split(',')))
rows, diagnostics = [], {}
for name, spec in previous['systems'].items():
    mol = gto.M(atom=spec['atom'], basis=spec['basis'], unit='Bohr', charge=spec['charge'],
                spin=spec['multiplicity']-1, cart=True, verbose=0)
    mf = scf.RHF(mol)
    mf.diis = False; mf.damp = 0; mf.level_shift = 0; mf.max_cycle = 128
    mf.conv_tol = 1e-13; mf.conv_tol_grad = 1e-10
    # Require BOTH energy and density criteria, tighter than Aether's frozen thresholds.
    mf.check_convergence = lambda env: (abs(env['e_tot']-env['last_hf_e']) <= 1e-13
                                        and np.max(np.abs(env['dm']-env['dm_last'])) <= 1e-12)
    h, s = mf.get_hcore(), mf.get_ovlp()
    eps0, c0 = mf.eig(h, s)
    occ = np.zeros(len(eps0)); occ[:mol.nelectron//2] = 2
    p0 = mf.make_rdm1(c0, occ)
    trajectory = []
    mf.callback = lambda env: trajectory.append(dict(iteration=int(env['cycle'])+1, total_energy=float(env['e_tot']),
                                                    delta_energy=float(abs(env['e_tot']-env['last_hf_e'])),
                                                    density_residual=float(np.max(np.abs(env['dm']-env['dm_last'])))))
    mf.kernel(dm0=p0)
    assert mf.converged, f'{name}: plain SCF did not converge'
    p = mf.make_rdm1()
    vhf = mf.get_veff(dm=p); f = h+vhf
    eps, c = mf.eig(f, s)
    electronic = mf.energy_elec(dm=p, h1e=h, vhf=vhf)[0]
    total = mf.energy_tot(dm=p, h1e=h, vhf=vhf)
    occupied = c[:, :mol.nelectron//2]
    # AO representation of the occupied-space projector, invariant to occupied rotations/signs.
    projector = occupied @ occupied.T @ s
    fixed_point_residual = float(np.max(np.abs(mf.make_rdm1(c, occ)-p)))
    assert fixed_point_residual < 1e-11
    diagnostics[name] = dict(converged=bool(mf.converged), iterations=mf.cycles, electrons=mol.nelectron,
                             total_energy=float(total), fixed_point_residual=fixed_point_residual, trajectory=trajectory)
    for label, value in [('electronic', electronic), ('nuclear', mol.energy_nuc()), ('total', total), ('converged', 1)]:
        rows.append((name,label,0,0,format(value,'.17g')))
    for label, matrix in [('density',p),('fock',f),('orbital_energies',eps[:,None]),('occupied_projector',projector),('core_guess_density',p0)]:
        for i in range(matrix.shape[0]):
            for j in range(matrix.shape[1]): rows.append((name,label,i,j,format(matrix[i,j],'.17g')))
    print(name, 'iterations=',mf.cycles,'E=',total,'fixed-point residual=',fixed_point_residual)
output = reference/'scf.csv'
with output.open('w',newline='') as stream:
    writer=csv.writer(stream,lineterminator='\n'); writer.writerow(['system','evidence','row','column','value']);writer.writerows(rows)
manifest = dict(status='SCREENING_ONLY', oracle='Fresh PySCF plain RHF; libcint; energy_elec/energy_tot; core guess',
                pyscf=pyscf.__version__, python=platform.python_version(), platform=platform.platform(),
                packages={p:importlib.metadata.version(p) for p in ['numpy','scipy','h5py']}, threads=lib.num_threads(),
                diis=False, damping=0, level_shift=0, energy_threshold=1e-13, density_max_abs_threshold=1e-12, maximum_iterations=128,
                reference_comparison_tolerance=1e-8, entries=len(rows), systems=previous['systems'], diagnostics=diagnostics,
                basis_resource_sha256=previous['basis_resource_sha256'], units='bohr;hartree;doubled RHF density',
                hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),previous_path,output]})
(reference/'scf-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('entries=',len(rows));print(manifest['hashes'])
