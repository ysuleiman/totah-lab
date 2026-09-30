"""Record the independently executed oracle and all M13 fixture hashes."""
from pathlib import Path
import hashlib,json,platform
import numpy,scipy,mpmath,pyscf
from pyscf import dft,gto
assert pyscf.__version__=='2.10.0'
root=Path(__file__).resolve().parents[1];ref=root/'src/test/resources/totah/lab/aether/reference'
native=Path(gto.moleintor.libcgto._name)
hashes={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(ref.glob('def2-*')) if p.is_file() and p.name!='def2-reference-manifest.json'}
for p in sorted((root/'scripts').glob('generate_def2_*.py')):hashes[str(p.relative_to(root))]=hashlib.sha256(p.read_bytes()).hexdigest()
manifest={'status':'SCREENING_ONLY','python':platform.python_version(),'pyscf':pyscf.__version__,'numpy':numpy.__version__,'scipy':scipy.__version__,'mpmath':mpmath.__version__,'libxc':dft.libxc.__version__,
 'native_integral_library':{'filename':native.name,'sha256':hashlib.sha256(native.read_bytes()).hexdigest()},
 'basis':'def2-SVP; all-electron H/C/N/O/P/S/Cl; cart=True; individually normalized Cartesian AOs',
 'normalization':'Dii=1/sqrt(S_libcint_ii); matrices D M D; density D^-1 P D^-1; ERI four D factors',
 'rhf':'restricted closed-shell; core guess; no density fitting; no damping or level shift',
 'lda':'LDA_X,LDA_C_PZ (original PZ81); restricted spin=0; no HF exchange',
 'reference_scf':{'energy_threshold':1e-12,'gradient_threshold':1e-10,'maximum_iterations':128,'diis_space':8},
 'grid':'M12 mapped Gauss-Legendre radial; Lebedev; original Becke; no radius adjustment; no pruning; 120x590 primary; 40x110/80x302/160x974 ladder',
 'geometry_sources':['diis-manifest.json','interaction-geometry.csv','interaction_benchmarks.py'],
 'hashes':hashes}
(ref/'def2-reference-manifest.json').write_text(json.dumps(manifest,indent=2,sort_keys=True)+'\n')
