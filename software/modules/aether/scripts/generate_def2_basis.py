"""Export installed PySCF basis data without hand-transcribed coefficients."""
import hashlib
import json
from pathlib import Path
import pyscf
from pyscf import gto
assert pyscf.__version__ == '2.10.0', 'Pinned reference generation requires PySCF 2.10.0'
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/totah/lab/aether/basis'
rows = ['atomic_number,shell,angular,exponent,coefficient']
manifest = {'family': 'def2-SVP', 'source': 'PySCF ' + pyscf.__version__,
            'source_url': 'https://github.com/pyscf/pyscf/blob/v2.10.0/pyscf/gto/basis/def2-svp.dat',
            'reference': 'https://doi.org/10.1039/B508541A',
            'representation': 'Cartesian; unit-normalized individual primitives and contractions',
            'shell_order': 'PySCF; Cartesian descending x, then descending y', 'elements': {}}
for element in ('H', 'C', 'N', 'O', 'P', 'S', 'Cl'):
    basis = gto.basis.load('def2-svp', element)
    manifest['elements'][element] = basis
    for shell, data in enumerate(basis):
        for primitive in data[1:]:
            assert len(primitive) == 2, 'General contractions require explicit expansion'
            rows.append(f'{gto.charge(element)},{shell},{data[0]},{primitive[0]:.17g},{primitive[1]:.17g}')
content = ('\n'.join(rows) + '\n').encode('ascii')
manifest['resource_sha256'] = hashlib.sha256(content).hexdigest()
source = Path(gto.basis.__file__).parent / 'def2-svp.dat'
manifest['upstream_file_sha256'] = hashlib.sha256(source.read_bytes()).hexdigest()
OUT.joinpath('def2-svp.csv').write_bytes(content)
OUT.joinpath('def2-svp-provenance.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(manifest['resource_sha256'])
