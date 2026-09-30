#!/usr/bin/env python3
"""Offline oracle regeneration: pip install pyscf==2.10.0; no Java code imported.
Run from any directory. Writes only Aether's basis/reference fixtures.
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
resource = root / 'src/main/resources/totah/lab/aether/basis'
reference = root / 'src/test/resources/totah/lab/aether/reference'
resource.mkdir(parents=True, exist_ok=True)
reference.mkdir(parents=True, exist_ok=True)
source = Path(gto.__file__).parent / 'basis/sto-3g.dat'
data = source.read_bytes()
h_shell = gto.basis.load('sto-3g', 'H')
assert len(h_shell) == 1 and h_shell[0][0] == 0
# Extract the source's decimal strings rather than reformatting floats.
lines = data.decode().splitlines()
start = next(i for i, line in enumerate(lines) if line.split() == ['H', 'S'])
rows = [line.split() for line in lines[start + 1:start + 4]]
content = ('source=https://github.com/pyscf/pyscf/blob/v2.10.0/pyscf/gto/basis/sto-3g.dat\n'
           'version=PySCF-2.10.0_EMSL-2014-09-15\n'
           'element=H\nshell=S\n'
           'exponents=' + ','.join(row[0] for row in rows) + '\n'
           'coefficients=' + ','.join(row[1] for row in rows) + '\n').encode()
(resource / 'sto-3g-h.properties').write_bytes(content)
digest = hashlib.sha256(content).hexdigest()
(resource / 'sto-3g-h.sha256').write_text(digest + '\n')

def mol(atom, basis, spin=0):
    return gto.M(atom=atom, basis=basis, unit='Bohr', spin=spin, cart=True, verbose=0)

cases = []
def add(name, molecule):
    overlap = molecule.intor('int1e_ovlp')
    for i in range(len(overlap)):
        for j in range(len(overlap)):
            cases.append((name, i, j, format(overlap[i, j], '.17g')))
add('primitive_self', mol('H 0 0 0', {'H': [[0, [1., 1.]]]}, spin=1))
add('primitive_separated', mol('H 0 0 0; H 1.4 0 0', {'H': [[0, [1., 1.]]]}))
add('primitive_unequal', mol('H 0 0 0; He 0.4 -0.7 1.1',
    {'H': [[0, [0.7, 1.]]], 'He': [[0, [1.3, 1.]]]}, spin=1))
add('sto3g_h', mol('H 0 0 0', 'sto-3g', spin=1))
add('sto3g_h2', mol('H 0 0 0; H 1.4 0 0', 'sto-3g'))
with (reference / 'overlap.csv').open('w', newline='') as f:
    writer = csv.writer(f, lineterminator='\n')
    writer.writerow(['case', 'row', 'column', 'overlap'])
    writer.writerows(cases)
manifest = {
    'oracle': 'PySCF/libcint int1e_ovlp (Cartesian)', 'pyscf': pyscf.__version__,
    'python': platform.python_version(), 'platform': platform.platform(),
    'packages': {p: importlib.metadata.version(p) for p in ['numpy', 'scipy', 'h5py']},
    'threads': 1, 'unit': 'Bohr', 'tolerance_absolute': 2e-13,
    'source_url': 'https://github.com/pyscf/pyscf/blob/v2.10.0/pyscf/gto/basis/sto-3g.dat',
    'source_sha256': hashlib.sha256(data).hexdigest(), 'basis_resource_sha256': digest,
    'reference_sha256': hashlib.sha256((reference / 'overlap.csv').read_bytes()).hexdigest(),
    'generator_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    'convention': 'Normalized primitives and normalized contractions; supplied decimal STO-3G data',
    'note': 'He labels an unequal custom s function only; no bundled helium basis or SCF is validated.'
}
(reference / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(json.dumps(manifest, indent=2))
print((reference / 'overlap.csv').read_text())
