#!/usr/bin/env python3
"""Validation-only supplied densities and fresh PySCF/libcint J/K references.

PySCF RHF is used only to obtain the external H2 density fixture. Aether neither
iterates a density nor computes Fock matrices, occupations or total energy.
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

if pyscf.__version__ != '2.10.0':
    raise RuntimeError('Pinned PySCF version required')
lib.num_threads(1)
root = Path(__file__).resolve().parents[1]
reference = root / 'src/test/resources/totah/lab/aether/reference'
previous_file = reference / 'eri-manifest.json'
previous = json.loads(previous_file.read_text())
basis_file = root / 'src/main/resources/totah/lab/aether/basis/sto-3g-h.properties'
if hashlib.sha256(basis_file.read_bytes()).hexdigest() != previous['basis_resource_sha256']:
    raise RuntimeError('Bundled basis differs from frozen provenance')
bundled = dict(line.split('=', 1) for line in basis_file.read_text().splitlines()
               if line and not line.startswith('#'))
upstream = gto.basis.load('sto-3g', 'H')[0]
assert upstream[0] == 0
assert [p[0] for p in upstream[1:]] == list(map(float, bundled['exponents'].split(',')))
assert [p[1] for p in upstream[1:]] == list(map(float, bundled['coefficients'].split(',')))
atoms = dict(h2='H 0 0 0; H 1.4 0 0',
             h4='H 0 0 0; H 0.4 -0.7 1.1; H -0.8 0.5 0.2; H 1.2 0.3 -0.6')
molecules = {name: gto.M(atom=atom, basis='sto-3g', unit='Bohr', charge=0, spin=0,
                         cart=True, verbose=0) for name, atom in atoms.items()}
# Independent external density generation, never part of the Java implementation.
rhf_reference = scf.RHF(molecules['h2'])
rhf_reference.conv_tol = 1e-13
rhf_reference.kernel()
if not rhf_reference.converged:
    raise RuntimeError('External H2 reference did not converge')
h2_density = rhf_reference.make_rdm1()
specs = [
    ('h2_zero', 'h2', np.zeros((2, 2))),
    ('h2_diagonal', 'h2', [[1.2, 0.], [0., 0.3]]),
    ('h2_arbitrary', 'h2', [[1.1, -0.27], [-0.27, 0.4]]),
    ('h2_off_diagonal', 'h2', [[0., 0.75], [0.75, 0.]]),
    ('h2_indefinite', 'h2', [[-0.6, 0.2], [0.2, 1.3]]),
    ('h2_rhf', 'h2', h2_density),
    ('h4_arbitrary', 'h4', [[0.8, 0.13, -0.22, 0.31], [0.13, 0.4, 0.07, -0.16],
                          [-0.22, 0.07, -0.2, 0.19], [0.31, -0.16, 0.19, 1.1]]),
    ('h4_off_diagonal', 'h4', [[0., 0.2, -0.3, 0.4], [0.2, 0., 0.1, -0.2],
                             [-0.3, 0.1, 0., 0.5], [0.4, -0.2, 0.5, 0.]])]
density_rows, jk_rows, inputs = [], [], []
for name, system, values in specs:
    density = np.array(values, dtype=np.float64)
    assert np.isfinite(density).all() and np.array_equal(density, density.T)
    j, k = scf.hf.get_jk(molecules[system], density, hermi=1)
    inputs.append(dict(case=name, system=system, density=density.tolist(),
                       source='PySCF RHF make_rdm1' if name == 'h2_rhf' else 'Explicit supplied symmetric matrix'))
    for i in range(len(density)):
        for col in range(len(density)):
            density_rows.append((name, system, i, col, format(density[i, col], '.17g')))
            jk_rows.append((name, i, col, format(j[i, col], '.17g'), format(k[i, col], '.17g')))
output_files = [reference / 'jk-density.csv', reference / 'jk.csv']
for path, header, rows in zip(output_files,
        [['case', 'system', 'row', 'column', 'density'], ['case', 'row', 'column', 'coulomb_hartree', 'exchange_hartree']],
        [density_rows, jk_rows]):
    with path.open('w', newline='') as output:
        writer = csv.writer(output, lineterminator='\n')
        writer.writerow(header)
        writer.writerows(rows)
manifest = dict(oracle='Fresh PySCF/libcint scf.hf.get_jk, hermi=1; supplied spin-summed densities',
                status='SCREENING_ONLY', pyscf=pyscf.__version__,
                python=platform.python_version(), platform=platform.platform(),
                packages={p: importlib.metadata.version(p) for p in ['numpy', 'scipy', 'h5py']},
                threads=lib.num_threads(), density_convention='P=2*sum_occupied(C_i C_i^T)',
                future_fock_convention='F=Hcore+J-0.5*K; not implemented in Aether',
                external_h2_rhf_converged=bool(rhf_reference.converged), external_h2_rhf_tolerance=1e-13,
                j_entries=len(jk_rows), k_entries=len(jk_rows), absolute_tolerance_hartree=5e-13,
                coordinate_unit='Bohr', result_unit='hartree', density_unit='dimensionless', inputs=inputs,
                systems={name: dict(atom=atoms[name], basis='sto-3g', charge=0, multiplicity=1,
                                   nuclei=[dict(center=list(map(float, p)), charge=int(z))
                                           for p, z in zip(mol.atom_coords(), mol.atom_charges())],
                                   ao_labels=mol.ao_labels()) for name, mol in molecules.items()},
                basis_source=previous['basis_source'], basis_resource_sha256=previous['basis_resource_sha256'],
                hashes={p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in [Path(__file__), previous_file, *output_files]})
(reference / 'jk-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('J entries:', len(jk_rows), 'K entries:', len(jk_rows))
print(json.dumps(manifest['hashes'], indent=2))
print('H2 P:', h2_density.tolist())
print('H2 J/K:', [row for row in jk_rows if row[0] == 'h2_rhf'])
