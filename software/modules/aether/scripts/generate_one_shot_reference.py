#!/usr/bin/env python3
"""Fresh PySCF one-shot F/orbitals and explicit SciPy Lowdin evidence for frozen supplied P.
No SCF kernel, occupations, density update or energy calculation is invoked.
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
from scipy.linalg import eigh

if pyscf.__version__ != '2.10.0':
    raise RuntimeError('Pinned PySCF version required')
lib.num_threads(1)
root = Path(__file__).resolve().parents[1]
reference = root / 'src/test/resources/totah/lab/aether/reference'
previous_file = reference / 'jk-manifest.json'
previous = json.loads(previous_file.read_text())
basis_file = root / 'src/main/resources/totah/lab/aether/basis/sto-3g-h.properties'
assert hashlib.sha256(basis_file.read_bytes()).hexdigest() == previous['basis_resource_sha256']
assert hashlib.sha256((reference / 'jk-density.csv').read_bytes()).hexdigest() == previous['hashes']['jk-density.csv']
bundled = dict(line.split('=', 1) for line in basis_file.read_text().splitlines() if line and not line.startswith('#'))
upstream = gto.basis.load('sto-3g', 'H')[0]
assert upstream[0] == 0
assert [p[0] for p in upstream[1:]] == list(map(float, bundled['exponents'].split(',')))
assert [p[1] for p in upstream[1:]] == list(map(float, bundled['coefficients'].split(',')))
molecules = {name: gto.M(atom=spec['atom'], basis=spec['basis'], unit='Bohr', charge=spec['charge'],
                         spin=spec['multiplicity']-1, cart=True, verbose=0)
             for name, spec in previous['systems'].items()}
rows, diagnostics = [], {}
for spec in previous['inputs']:
    name = spec['case']
    mol = molecules[spec['system']]
    density = np.array(spec['density'], dtype=np.float64)
    s = mol.intor('int1e_ovlp')
    hcore = mol.intor('int1e_kin') + mol.intor('int1e_nuc')
    j, k = scf.hf.get_jk(mol, density, hermi=1)
    f = (hcore + j) - 0.5 * k
    se, u = eigh(s, driver='evd')
    assert se.min() > max(1e-12, 1e-10 * se.max())
    x = (u * (se ** -0.5)) @ u.T
    fp = x.T @ f @ x
    # Independent generalized solver, not the Java transformation implementation.
    energies, coefficients = scf.hf.eig(f, s)
    transformed_energies, _ = eigh(fp, driver='evd')
    residual = float(np.max(np.abs(f @ coefficients - (s @ coefficients) * energies)))
    normalization = float(np.max(np.abs(coefficients.T @ s @ coefficients - np.eye(len(se)))))
    assert residual < 1e-11 and normalization < 1e-11
    assert np.max(np.abs(energies - transformed_energies)) < 1e-11
    # All reference cases are nondegenerate, permitting column comparisons modulo sign.
    gap = float(np.min(np.diff(energies)))
    assert gap > 1e-8
    diagnostics[name] = dict(max_residual=residual, max_orthonormality_error=normalization, minimum_orbital_gap=gap)
    for label, matrix in [('fock', f), ('overlap_eigenvalues', se[:, None]), ('orthogonalization', x),
                          ('orthogonal_fock', fp), ('orbital_energies', energies[:, None]), ('coefficients', coefficients)]:
        for i in range(matrix.shape[0]):
            for col in range(matrix.shape[1]):
                rows.append((name, label, i, col, format(matrix[i, col], '.17g')))
    if name == 'h2_rhf':
        print('H2 F:', f.tolist()); print('H2 S eigenvalues:', se.tolist()); print('H2 X:', x.tolist())
        print('H2 energies:', energies.tolist()); print('H2 C (external signs):', coefficients.tolist())
output = reference / 'one-shot.csv'
with output.open('w', newline='') as stream:
    writer = csv.writer(stream, lineterminator='\n')
    writer.writerow(['case', 'evidence', 'row', 'column', 'value'])
    writer.writerows(rows)
manifest = dict(oracle='Fresh PySCF/libcint integrals/get_jk; PySCF scf.hf.eig generalized solve; SciPy eigh explicit Lowdin',
                status='SCREENING_ONLY', pyscf=pyscf.__version__, python=platform.python_version(), platform=platform.platform(),
                packages={p: importlib.metadata.version(p) for p in ['numpy', 'scipy', 'h5py']}, threads=lib.num_threads(),
                entries=len(rows), absolute_reference_tolerance=1e-10, coefficient_comparison='independent column signs',
                inputs=previous['inputs'], systems=previous['systems'], diagnostics=diagnostics,
                basis_resource_sha256=previous['basis_resource_sha256'], basis_source=previous['basis_source'],
                units=dict(fock='hartree', orbital_energies='hartree', orthogonal_fock='hartree',
                           overlap_eigenvalues='dimensionless', orthogonalization='dimensionless', coefficients='dimensionless'),
                hashes={p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in [Path(__file__), previous_file, reference / 'jk-density.csv', output]})
(reference / 'one-shot-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('Entries:', len(rows)); print(json.dumps(manifest['hashes'], indent=2))
