"""Fresh libcint Cartesian def2-SVP references transformed to unit-normalized AOs."""
from dft_reference_common import ROOT, REF, grid
import json, hashlib
import numpy as np
import pyscf
from pyscf import gto, lib
assert pyscf.__version__ == '2.10.0', 'Pinned reference generation requires PySCF 2.10.0'
lib.num_threads(1)
names=('h2','h2o','nh3','ch4','co','n2','h2s','ph3','hcl','ch3cl','chlorobenzene','dms','trimethylsulfonium')
specs=json.loads((REF/'diis-manifest.json').read_text())['systems']
rows=['system,kind,i,j,k,l,value']; metadata={}
for name in names:
 s=specs[name];m=gto.M(atom=s['atom'],charge=s['charge'],spin=0,unit='Bohr',basis='def2-svp',cart=True,verbose=0)
 ov=m.intor('int1e_ovlp');scale=1/np.sqrt(ov.diagonal());n=m.nao_nr()
 for kind,intor in [('S','int1e_ovlp'),('T','int1e_kin'),('V','int1e_nuc')]:
  mat=m.intor(intor)*scale[:,None]*scale[None,:]
  for i in range(n):
   for j in range(i+1):rows.append(f'{name},{kind},{i},{j},0,0,{mat[i,j]:.17g}')
 # Deterministic shell-quartet selection covers every AO self quartet and all angular combinations.
 loc=m.ao_loc_nr(); selected=set((i,i,i,i) for i in range(m.nbas))
 for q in range(80):selected.add(tuple((q*a+b)%m.nbas for a,b in [(1,0),(3,1),(5,2),(7,3)]))
 for shells in sorted(selected):
  values=m.intor_by_shell('int2e_cart',shells)
  for ijkl in np.ndindex(values.shape):
   ao=tuple(int(loc[s]+i) for s,i in zip(shells,ijkl))
   value=values[ijkl]*np.prod(scale[list(ao)])
   rows.append(f'{name},ERI,{ao[0]},{ao[1]},{ao[2]},{ao[3]},{value:.17g}')
 # Fixed laboratory points sample signs and all d components, independent of Java grid placement.
 coords=np.array([[.13,-.27,.41],[1.1,.7,-.3],[-2.1,.4,1.3],[0,0,0]])
 ao=m.eval_gto('GTOval_cart',coords)*scale
 for g in range(len(coords)):
  for i in range(n): rows.append(f'{name},AO,{g},{i},0,0,{ao[g,i]:.17g}')
 actual_grid=grid(m,120,590)
 indices=[a*120*590+r*590+angular for a in range(m.natm) for r,angular in [(30,37),(60,218),(90,514)]]
 grid_ao=m.eval_gto('GTOval_cart',actual_grid.coords[indices])*scale
 for g,index in enumerate(indices):
  for i in range(n):rows.append(f'{name},GRID_AO,{index},{i},0,0,{grid_ao[g,i]:.17g}')
 metadata[name]={'basis_functions':n,'ao_labels':m.ao_labels(),'normalization_factors':scale.tolist(),'atom':s['atom'],'charge':s['charge']}
 print(name,n,flush=True)
content=('\n'.join(rows)+'\n').encode();(REF/'def2-integrals.csv').write_bytes(content)
(REF/'def2-integrals-manifest.json').write_text(json.dumps({'sha256':hashlib.sha256(content).hexdigest(),'systems':metadata,'ao_points':coords.tolist(),'normalization':'chi_Aether_i = chi_libcint_i / sqrt(S_libcint_ii)'},indent=2)+'\n')
print(hashlib.sha256(content).hexdigest(),len(rows)-1)
