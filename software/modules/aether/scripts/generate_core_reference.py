#!/usr/bin/env python3
"""Generate Hcore directly from fresh PySCF/libcint T and V evaluations; no SCF.

Reuses frozen input specifications, never precomputed T/V output or Aether values.
Requires pyscf==2.10.0 in the disposable validation environment.
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
    raise RuntimeError('Pinned PySCF version required')
lib.num_threads(1)
root = Path(__file__).resolve().parents[1]
reference = root / 'src/test/resources/totah/lab/aether/reference'
input_file = reference / 'kinetic-manifest.json'
previous = json.loads(input_file.read_text())
basis_file = root / 'src/main/resources/totah/lab/aether/basis/sto-3g-h.properties'
if hashlib.sha256(basis_file.read_bytes()).hexdigest() != previous['basis_resource_sha256']:
    raise RuntimeError('Bundled basis differs from frozen input specification')
bundled = dict(line.split('=', 1) for line in basis_file.read_text().splitlines()
               if line and not line.startswith('#'))
upstream = gto.basis.load('sto-3g', 'H')
assert len(upstream) == 1 and upstream[0][0] == 0
assert [p[0] for p in upstream[0][1:]] == list(map(float, bundled['exponents'].split(',')))
assert [p[1] for p in upstream[0][1:]] == list(map(float, bundled['coefficients'].split(',')))
rows = []
inputs = []
for spec in previous['inputs']:
    molecule = gto.M(atom=spec['atom'], basis=spec['basis'], spin=spec['spin'],
                     charge=0, unit='Bohr', cart=True, verbose=0)
    kinetic = molecule.intor('int1e_kin')
    attraction = molecule.intor('int1e_nuc')
    core = kinetic + attraction
    inputs.append(dict(spec, molecular_charge=0, multiplicity=spec['spin'] + 1,
                       nuclei=[dict(center=list(map(float, p)), charge=int(z))
                               for p, z in zip(molecule.atom_coords(), molecule.atom_charges())],
                       ao_labels=molecule.ao_labels()))
    for i in range(len(core)):
        for j in range(len(core)):
            rows.append((spec['case'], i, j, format(core[i, j], '.17g')))
output_file = reference / 'core.csv'
with output_file.open('w', newline='') as output:
    writer = csv.writer(output, lineterminator='\n')
    writer.writerow(['case', 'row', 'column', 'core_hartree'])
    writer.writerows(rows)
manifest = dict(oracle='Fresh PySCF/libcint int1e_kin + int1e_nuc (Cartesian); no SCF',
                status='SCREENING_ONLY', pyscf=pyscf.__version__,
                python=platform.python_version(), platform=platform.platform(),
                packages={p: importlib.metadata.version(p) for p in ['numpy', 'scipy', 'h5py']},
                threads=lib.num_threads(), entries=len(rows), absolute_tolerance_hartree=2e-13,
                coordinate_unit='Bohr', result_unit='hartree', inputs=inputs,
                basis_source=previous['basis_source'], basis_resource_sha256=previous['basis_resource_sha256'],
                hashes={p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in [Path(__file__), input_file, output_file]})
(reference / 'core-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(output_file.read_text())
print(json.dumps(manifest['hashes'], indent=2))
