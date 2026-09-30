#!/usr/bin/env python3
"""Generate kinetic-only oracle fixtures with PySCF 2.10.0, without running Java.
Does not modify the frozen overlap fixtures or bundled basis definition.
"""
import csv
import hashlib
import importlib.metadata
import json
import platform
from pathlib import Path

import pyscf
from pyscf import gto, lib

if pyscf.__version__ != '2.10.0':
    raise RuntimeError('Reference generation requires PySCF 2.10.0')
lib.num_threads(1)
root = Path(__file__).resolve().parents[1]
reference = root / 'src/test/resources/totah/lab/aether/reference'
basis_file = root / 'src/main/resources/totah/lab/aether/basis/sto-3g-h.properties'
properties = dict(line.split('=', 1) for line in basis_file.read_text().splitlines())
exponents = list(map(float, properties['exponents'].split(',')))
coefficients = list(map(float, properties['coefficients'].split(',')))
assert gto.basis.load('sto-3g', 'H') == [[0, *map(list, zip(exponents, coefficients))]]

specifications = [
    ('primitive_self', 'H 0 0 0', {'H': [[0, [1., 1.]]]}, 1),
    ('primitive_separated', 'H 0 0 0; H 1.4 0 0', {'H': [[0, [1., 1.]]]}, 0),
    ('primitive_unequal', 'H1 0 0 0; H2 0.4 -0.7 1.1',
     {'H1': [[0, [0.7, 1.]]], 'H2': [[0, [1.3, 1.]]]}, 0),
    ('sto3g_h', 'H 0 0 0', 'sto-3g', 1),
    ('sto3g_h2', 'H 0 0 0; H 1.4 0 0', 'sto-3g', 0),
    ('primitive_negative', 'H 0 0 0; H 3 0 0', {'H': [[0, [1., 1.]]]}, 0),
    ('signed_contraction', 'H 0 0 0; H 1.4 0 0', {'H': [[0, [0.5, -0.2], [2., 0.8]]]}, 0),
]
rows = []
for name, atom, basis, spin in specifications:
    molecule = gto.M(atom=atom, basis=basis, unit='Bohr', spin=spin, cart=True, verbose=0)
    kinetic = molecule.intor('int1e_kin')
    for i in range(len(kinetic)):
        for j in range(len(kinetic)):
            rows.append((name, i, j, format(kinetic[i, j], '.17g')))
with (reference / 'kinetic.csv').open('w', newline='') as output:
    writer = csv.writer(output, lineterminator='\n')
    writer.writerow(['case', 'row', 'column', 'kinetic_hartree'])
    writer.writerows(rows)
source = Path(gto.__file__).parent / 'basis/sto-3g.dat'
manifest = {
    'oracle': 'PySCF/libcint int1e_kin (Cartesian)', 'pyscf': pyscf.__version__,
    'status': 'SCREENING_ONLY', 'python': platform.python_version(), 'platform': platform.platform(),
    'packages': {p: importlib.metadata.version(p) for p in ['numpy', 'scipy', 'h5py']},
    'threads_requested': 1, 'threads_reported': lib.num_threads(),
    'geometry_unit': 'Bohr', 'integral_unit': 'hartree', 'absolute_tolerance': 2e-13,
    'basis_source': properties['source'], 'basis_version': properties['version'],
    'basis_resource_sha256': hashlib.sha256(basis_file.read_bytes()).hexdigest(),
    'upstream_basis_file_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
    'reference_sha256': hashlib.sha256((reference / 'kinetic.csv').read_bytes()).hexdigest(),
    'generator_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    'inputs': [dict(case=name, atom=atom, basis=basis, spin=spin) for name, atom, basis, spin in specifications],
    'convention': 'Normalized primitives and contractions; no SCF or other integrals evaluated',
}
(reference / 'kinetic-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print((reference / 'kinetic.csv').read_text())
print('reference_sha256=' + manifest['reference_sha256'])
