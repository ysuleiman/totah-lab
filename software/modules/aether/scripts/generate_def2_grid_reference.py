"""Keep M12 grid rules fixed; measure def2-SVP convergence separately from same-grid agreement."""
from dft_reference_common import REF,grid
import json,hashlib
import numpy as np
import pyscf
from pyscf import gto,dft,lib
assert pyscf.__version__ == '2.10.0', 'Pinned reference generation requires PySCF 2.10.0'
lib.num_threads(1)
specs=json.loads((REF/'def2-integrals-manifest.json').read_text())['systems']
axis=np.array([1.,2.,3.]);axis/=np.linalg.norm(axis);angle=.513
cross=np.array([[0,-axis[2],axis[1]],[axis[2],0,-axis[0]],[-axis[1],axis[0],0]])
rot=np.eye(3)*np.cos(angle)+(1-np.cos(angle))*np.outer(axis,axis)+np.sin(angle)*cross
for name in ('h2o','ch4','h2s'):
 rows=['variant,radial,angular,total,Exc,electrons'];s=specs[name]
 base=gto.M(atom=s['atom'],charge=s['charge'],spin=0,unit='Bohr',basis='def2-svp',cart=True,verbose=0)
 original=[(base.atom_symbol(i),base.atom_coord(i)) for i in range(base.natm)]
 for variant,nr,na in [('native',40,110),('native',80,302),('native',120,590),('native',160,974),('rotated',120,590)]:
  atoms=s['atom'] if variant=='native' else [(el,(rot@np.array(p)).tolist()) for el,p in original]
  m=gto.M(atom=atoms,charge=s['charge'],spin=0,unit='Bohr',basis='def2-svp',cart=True,verbose=0)
  mf=dft.RKS(m);mf.xc='LDA_X,LDA_C_PZ';mf.grids=grid(m,nr,na);mf.small_rho_cutoff=0
  mf.conv_tol=1e-12;mf.conv_tol_grad=1e-10;mf.max_cycle=128;mf.diis_space=8
  mf.kernel(dm0=mf.get_init_guess(key='1e'));assert mf.converged
  p=mf.make_rdm1();ne,exc,_=mf._numint.nr_rks(m,mf.grids,mf.xc,p)
  rows.append(f'{variant},{nr},{na},{mf.e_tot:.17g},{exc:.17g},{ne:.17g}')
  print(name,variant,nr,na,mf.e_tot,exc,ne,flush=True)
 content=('\n'.join(rows)+'\n').encode();(REF/f'def2-grid-{name}.csv').write_bytes(content)
 (REF/f'def2-grid-{name}.sha256').write_text(hashlib.sha256(content).hexdigest()+'\n')
