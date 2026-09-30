"""Fresh PySCF/Libxc electronic oracle and separate compiled D3; serial components."""
from pathlib import Path
import sys,json,time
import numpy as np
from pyscf import gto,dft,lib
import pyscf
from dft_reference_common import grid
from dftd3.interface import DispersionModel,RationalDampingParam
r=Path(__file__).resolve().parents[1];out=r/'validation/milestone-16/pyscf';out.mkdir(exist_ok=True)
specs=json.loads((r/'src/test/resources/totah/lab/aether/reference/d3/systems.json').read_text())
lib.num_threads(1)
for name in sys.argv[1:]:
 s=specs[name];components=[('AB',s['atoms'],s['charge'])];cut=s.get('fragment_a_count',0)
 if cut:components.extend([('A',s['atoms'][:cut],s['charge_a']),('B',s['atoms'][cut:],s['charge_b'])])
 for role,atoms,charge in components:
  dest=out/(name+'-'+role+'.json')
  if dest.exists():continue
  t=time.perf_counter();m=gto.M(atom=atoms,unit='Bohr',charge=charge,spin=0,basis='def2-svp',cart=True,verbose=0,max_memory=256)
  mf=dft.RKS(m);mf.xc='GGA_X_PBE,GGA_C_PBE';mf.grids=grid(m,120,590);mf.small_rho_cutoff=0;mf.conv_tol=1e-12;mf.conv_tol_grad=1e-10;mf.max_cycle=128;mf.diis_space=8
  # Explicit full J each cycle avoids the documented incremental-J reference stagnation.
  mf.direct_scf=False;mf.direct_scf_tol=0;mf.max_memory=256
  if m.nao_nr()<=100:mf._eri=m.intor('int2e',aosym='s8')
  else:mf._opt[None]=mf.init_direct_scf(m) # PySCF 2.10 J-only path requires this even with incremental updates disabled.
  mf.callback=lambda env:print(name,role,'cycle',env.get('cycle'),'energy',env.get('e_tot'),'grad',env.get('norm_gorb'),flush=True)
  mf.kernel(dm0=mf.get_init_guess(key='minao'))
  if not mf.converged:raise RuntimeError(name+' '+role+' oracle did not converge')
  model=DispersionModel(m.atom_charges(),m.atom_coords());model.set_realspace_cutoff(60,40,40)
  ed=float(model.get_dispersion(RationalDampingParam(s6=1.,s8=.7875,a1=.4289,a2=4.4407,s9=0.),grad=False)['energy'])
  dest.write_text(json.dumps({'PBE':mf.e_tot,'D3':ed,'PBE_D3':mf.e_tot+ed,'converged':bool(mf.converged),'pyscf':pyscf.__version__,'libxc':dft.libxc.__version__,'basis':'def2-svp Cartesian','grid':'120x590 frozen unpruned','guess':'MINAO','full_j':True,'atoms_bohr':atoms,'charge':charge,'elapsed_seconds':time.perf_counter()-t,'status':'SCREENING_ONLY'},indent=2)+'\n')
  print('PASS',name,role,mf.e_tot,ed,flush=True)
