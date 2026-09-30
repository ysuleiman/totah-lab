"""Fresh RHF and LDA_X+LDA_C_PZ references; unchanged explicit M12 quadrature."""
from dft_reference_common import REF,grid
import json,sys,hashlib
import numpy as np
import pyscf
from pyscf import gto,scf,dft,lib
assert pyscf.__version__ == '2.10.0', 'Pinned reference generation requires PySCF 2.10.0'
lib.num_threads(1)
specs=json.loads((REF/'def2-integrals-manifest.json').read_text())['systems']
for name in sys.argv[1:] or specs:
 s=specs[name];m=gto.M(atom=s['atom'],charge=s['charge'],spin=0,unit='Bohr',basis='def2-svp',cart=True,verbose=0)
 scale=1/np.sqrt(m.intor('int1e_ovlp').diagonal());rows=['method,kind,i,j,value']
 for method in ['RHF','LDA']:
  mf=scf.RHF(m) if method=='RHF' else dft.RKS(m)
  if method=='LDA':mf.xc='LDA_X,LDA_C_PZ';mf.grids=grid(m,120,590);mf.small_rho_cutoff=0
  mf.conv_tol=1e-12;mf.conv_tol_grad=1e-10;mf.max_cycle=128;mf.diis_space=8
  mf.kernel(dm0=mf.get_init_guess(key='1e'))
  p=mf.make_rdm1();f=mf.get_fock(dm=p);n=m.nao_nr()
  rows += [f'{method},converged,0,0,{int(mf.converged)}',f'{method},total,0,0,{mf.e_tot:.17g}',f'{method},electronic,0,0,{mf.energy_elec(p)[0]:.17g}']
  for kind,mat in [('density',p/scale[:,None]/scale[None,:]),('Fock',f*scale[:,None]*scale[None,:])]:
   for i in range(n):
    for j in range(n):rows.append(f'{method},{kind},{i},{j},{mat[i,j]:.17g}')
  for i,e in enumerate(mf.mo_energy):rows.append(f'{method},orbital,{i},0,{e:.17g}')
  if method=='LDA':
   ao=m.eval_gto('GTOval_cart',mf.grids.coords);rho=np.einsum('gi,ij,gj->g',ao,p,ao)
   rows.append(f'{method},electrons,0,0,{np.dot(rho,mf.grids.weights):.17g}')
   rows.append(f'{method},Exc,0,0,{mf._numint.nr_rks(m,mf.grids,mf.xc,p)[1]:.17g}')
  print(name,method,mf.converged,mf.e_tot,flush=True)
 content=('\n'.join(rows)+'\n').encode();(REF/f'def2-energy-{name}.csv').write_bytes(content)
 (REF/f'def2-energy-{name}.sha256').write_text(hashlib.sha256(content).hexdigest()+'\n')
