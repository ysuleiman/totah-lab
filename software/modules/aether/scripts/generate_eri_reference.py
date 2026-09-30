#!/usr/bin/env python3
"""Fresh individual s ERIs from PySCF 2.10.0/libcint int2e; no SCF or J/K."""
import csv
import hashlib
import importlib.metadata
import itertools
import json
import platform
from pathlib import Path

import pyscf
from pyscf import gto, lib

if pyscf.__version__ != '2.10.0':
    raise RuntimeError('Pinned PySCF version required')
lib.num_threads(1)
root = Path(__file__).resolve().parents[1]
reference = root / 'src/test/resources/totah/lab/aether/reference'
input_file = reference / 'core-manifest.json'
previous = json.loads(input_file.read_text())
basis_file = root / 'src/main/resources/totah/lab/aether/basis/sto-3g-h.properties'
if hashlib.sha256(basis_file.read_bytes()).hexdigest() != previous['basis_resource_sha256']:
    raise RuntimeError('Bundled basis differs from frozen input specification')
bundled = dict(line.split('=', 1) for line in basis_file.read_text().splitlines()
               if line and not line.startswith('#'))
upstream = gto.basis.load('sto-3g', 'H')[0]
assert upstream[0] == 0
assert [p[0] for p in upstream[1:]] == list(map(float, bundled['exponents'].split(',')))
assert [p[1] for p in upstream[1:]] == list(map(float, bundled['coefficients'].split(',')))
specs = [{k: spec[k] for k in ['case', 'atom', 'basis', 'spin']} for spec in previous['inputs']]
specs += [dict(case='near_coincident', atom='H 0 0 0; H 1e-8 0 0',
               basis={'H': [[0, [1., 1.]]]}, spin=0),
          dict(case='large_separation', atom='H 0 0 0; H 10000 0 0',
               basis={'H': [[0, [1., 1.]]]}, spin=0),
          dict(case='four_distinct', atom='H1 0 0 0; H2 0.4 -0.7 1.1; H3 -0.8 0.5 0.2; H4 1.2 0.3 -0.6',
               basis={f'H{i+1}': [[0, [a, 1.]]] for i, a in enumerate([0.7, 1.3, 0.5, 2.])}, spin=0)]
rows = []
inputs = []
for spec in specs:
    molecule = gto.M(atom=spec['atom'], basis=spec['basis'], spin=spec['spin'],
                     charge=0, unit='Bohr', cart=True, verbose=0)
    # s1 exposes all individual entries, independently checking the Java packed remapping.
    eri = molecule.intor('int2e', aosym='s1')
    inputs.append(dict(spec, molecular_charge=0, multiplicity=spec['spin'] + 1,
                       nuclei=[dict(center=list(map(float, p)), charge=int(z))
                               for p, z in zip(molecule.atom_coords(), molecule.atom_charges())],
                       ao_labels=molecule.ao_labels(), shape=list(eri.shape)))
    for i, j, k, l in itertools.product(range(molecule.nao_nr()), repeat=4):
        rows.append((spec['case'], i, j, k, l, format(eri[i, j, k, l], '.17g')))
output_file = reference / 'eri.csv'
with output_file.open('w', newline='') as output:
    writer = csv.writer(output, lineterminator='\n')
    writer.writerow(['case', 'i', 'j', 'k', 'l', 'eri_hartree'])
    writer.writerows(rows)
manifest = dict(oracle='Fresh PySCF/libcint int2e, aosym=s1, chemists order; no SCF/J/K',
                status='SCREENING_ONLY', pyscf=pyscf.__version__,
                python=platform.python_version(), platform=platform.platform(),
                packages={p: importlib.metadata.version(p) for p in ['numpy', 'scipy', 'h5py']},
                threads=lib.num_threads(), entries=len(rows), absolute_tolerance_hartree=2e-13,
                coordinate_unit='Bohr', result_unit='hartree', inputs=inputs,
                basis_source=previous['basis_source'], basis_resource_sha256=previous['basis_resource_sha256'],
                hashes={p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in [Path(__file__), input_file, output_file]})
(reference / 'eri-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('ERI entries:', len(rows))
print(json.dumps(manifest['hashes'], indent=2))
print('H2 entries:', [row for row in rows if row[0] == 'sto3g_h2'])
