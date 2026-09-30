#!/usr/bin/env python3
"""Extract only P/S/Cl STO-3G shells from pinned PySCF; no fitted or modified coefficients."""
import csv
import hashlib
import json
from pathlib import Path
import pyscf
from pyscf import gto

assert pyscf.__version__ == '2.10.0'
root = Path(__file__).resolve().parents[1]
output = root / 'src/main/resources/totah/lab/aether/basis/sto-3g-spcl.csv'
rows = []
for z, element in [(15, 'P'), (16, 'S'), (17, 'Cl')]:
    shells = gto.basis.load('sto-3g', element)
    assert [shell[0] for shell in shells] == [0, 0, 0, 1, 1]
    for index, shell in enumerate(shells):
        assert len(shell[1:]) == 3
        for exponent, coefficient in shell[1:]:
            rows.append((z, index, shell[0], format(exponent, '.17g'), format(coefficient, '.17g')))
with output.open('w', newline='') as stream:
    writer = csv.writer(stream, lineterminator='\n')
    writer.writerow(['atomic_number', 'shell', 'angular_momentum', 'exponent', 'coefficient'])
    writer.writerows(rows)
upstream = Path(gto.basis.__file__).with_name('sto-3g.dat')
manifest = dict(source='PySCF 2.10.0 gto/basis/sto-3g.dat',
                source_url='https://github.com/pyscf/pyscf/blob/v2.10.0/pyscf/gto/basis/sto-3g.dat',
                upstream_header=upstream.read_text().splitlines()[:25],
                upstream_sha256=hashlib.sha256(upstream.read_bytes()).hexdigest(),
                generator_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                resource_sha256=hashlib.sha256(output.read_bytes()).hexdigest(),
                elements=['P', 'S', 'Cl'], primitive_rows=len(rows),
                convention='coefficients multiply normalized primitives; contractions independently normalized',
                shell_order='1s,2s,3s,2px,2py,2pz,3px,3py,3pz; Cartesian s/p only', status='SCREENING_ONLY')
output.with_suffix('.manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(manifest['resource_sha256'])
