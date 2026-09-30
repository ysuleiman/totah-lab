#!/usr/bin/env python3
"""Nuclear-only libcint oracle plus 80-digit mpmath Boys F0 references.
Uses existing kinetic input specifications without changing frozen S/T fixtures.
Requires pyscf==2.10.0 and mpmath==1.3.0 in a disposable validation environment.
"""
import csv
import hashlib
import importlib.metadata
import json
import platform
from pathlib import Path
import mpmath as mp
import pyscf
from pyscf import gto, lib

if pyscf.__version__ != '2.10.0' or mp.__version__ != '1.3.0':
    raise RuntimeError('Pinned oracle versions required')
lib.num_threads(1)
mp.mp.dps = 80
root = Path(__file__).resolve().parents[1]
reference = root / 'src/test/resources/totah/lab/aether/reference'
previous = json.loads((reference / 'kinetic-manifest.json').read_text())
basis = root / 'src/main/resources/totah/lab/aether/basis/sto-3g-h.properties'
assert hashlib.sha256(basis.read_bytes()).hexdigest() == previous['basis_resource_sha256']
rows = []
inputs = []
for spec in previous['inputs']:
    molecule = gto.M(atom=spec['atom'], basis=spec['basis'], spin=spec['spin'], unit='Bohr', cart=True, verbose=0)
    attraction = molecule.intor('int1e_nuc')
    inputs.append(dict(spec, nuclei=[dict(center=list(map(float, pos)), charge=int(z))
                  for pos, z in zip(molecule.atom_coords(), molecule.atom_charges())]))
    for i in range(len(attraction)):
        for j in range(len(attraction)):
            rows.append((spec['case'], i, j, format(attraction[i, j], '.17g')))
molecule = gto.M(atom='H 0 0 0', basis={'H': [[0, [1., 1.]]]}, spin=1, unit='Bohr', cart=True, verbose=0)
for index, distance in enumerate([0., 1e-8, 0.05, 0.5, 3., 100., 1e6]):
    name = 'external_center_' + str(index)
    # Independent arbitrary Coulomb center; no basis or nuclear position is moved implicitly.
    with molecule.with_rinv_origin((distance, 0., 0.)):
        attraction = -molecule.intor('int1e_rinv')[0, 0]
    rows.append((name, 0, 0, format(attraction, '.17g')))
    inputs.append(dict(case=name, exponent=1., basis_center=[0., 0., 0.], nuclei=[dict(center=[distance, 0., 0.], charge=1)]))
with molecule.with_rinv_origin((0.4, -0.7, 1.1)):
    scaled = -2.5 * molecule.intor('int1e_rinv')[0, 0]
rows.append(('charge_scaled', 0, 0, format(scaled, '.17g')))
inputs.append(dict(case='charge_scaled', exponent=1., basis_center=[0., 0., 0.],
                   nuclei=[dict(center=[0.4, -0.7, 1.1], charge=2.5)]))
with (reference / 'nuclear.csv').open('w', newline='') as output:
    writer = csv.writer(output, lineterminator='\n')
    writer.writerow(['case', 'row', 'column', 'nuclear_hartree'])
    writer.writerows(rows)
# gammainc, evaluated at 80 digits, is independent of the Java piecewise series.
arguments = ['0', '5e-324', '1e-300', '1e-16', '1e-12', '1e-8', '0.0001', '0.01',
             '0.1', '0.49999999999999994', '0.5', '0.5000000000000001', '1', '2', '5',
             '10', '20', '35.99999999999999', '36', '36.00000000000001', '50', '100', '10000', '1e100', '1e300', '1.7976931348623157e308']
with (reference / 'boys-f0.csv').open('w', newline='') as output:
    writer = csv.writer(output, lineterminator='\n')
    writer.writerow(['t', 'f0'])
    for text in arguments:
        t = mp.mpf(float(text))  # Use the exact binary64 input represented in the Java test.
        value = mp.mpf(1) if not t else mp.gammainc(mp.mpf('0.5'), 0, t) / (2 * mp.sqrt(t))
        writer.writerow([text, mp.nstr(value, 40)])
manifest = dict(oracle='PySCF/libcint int1e_nuc and int1e_rinv; mpmath gammainc',
                status='SCREENING_ONLY', pyscf=pyscf.__version__, mpmath=mp.__version__, boys_precision_digits=80,
                python=platform.python_version(), platform=platform.platform(),
                packages={p: importlib.metadata.version(p) for p in ['numpy', 'scipy', 'h5py']},
                nuclear_entries=len(rows), boys_entries=len(arguments), nuclear_absolute_tolerance_hartree=2e-13,
                boys_relative_tolerance=3e-15, coordinate_unit='Bohr', result_unit='hartree',
                inputs=inputs, source=previous['basis_source'], basis_resource_sha256=previous['basis_resource_sha256'])
manifest['hashes'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in
                     [Path(__file__), reference / 'nuclear.csv', reference / 'boys-f0.csv', reference / 'kinetic-manifest.json']}
(reference / 'nuclear-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print((reference / 'nuclear.csv').read_text())
print(json.dumps(manifest['hashes'], indent=2))
