"""Fresh CP electronic oracle: physical-center grid, full ghost basis, physical-only D3."""
from pathlib import Path
import sys,json,time
from pyscf import gto,dft,lib
import pyscf
from dft_reference_common import grid
from dftd3.interface import DispersionModel,RationalDampingParam
r=Path(__file__).resolve().parents[1];out=r/'validation/milestone-16/cp-reference';out.mkdir(exist_ok=True)
specs=json.loads((r/'src/test/resources/totah/lab/aether/reference/d3/systems.json').read_text());lib.num_threads(1)
for name in sys.argv[1:]:
 s=specs[name];cut=s['fragment_a_count'];a=s['atoms'][:cut];b=s['atoms'][cut:]
 for role,real,ghost,charge in [('A_GHOST_B',a,b,s['charge_a']),('B_GHOST_A',b,a,s['charge_b'])]:
  dest=out/f'{name}-{role}.json'
  if dest.exists():continue
  m=gto.M(atom=real+[('ghost-'+el,p) for el,p in ghost],unit='Bohr',charge=charge,spin=0,basis='def2-svp',cart=True,verbose=0,max_memory=256)
  physical=gto.M(atom=real,unit='Bohr',charge=charge,spin=0,basis='def2-svp',cart=True,verbose=0)
  mf=dft.RKS(m);mf.xc='GGA_X_PBE,GGA_C_PBE';mf.grids=grid(physical,120,590);mf.small_rho_cutoff=0
  mf.conv_tol=1e-12;mf.conv_tol_grad=1e-10;mf.max_cycle=128;mf.diis_space=8;mf.direct_scf=False;mf.direct_scf_tol=0
  if m.nao_nr()<=100:mf._eri=m.intor('int2e',aosym='s8')
  else:mf._opt[None]=mf.init_direct_scf(m)
  mf.callback=lambda env:print(name,role,'cycle',env.get('cycle'),'energy',env.get('e_tot'),'grad',env.get('norm_gorb'),flush=True)
  t=time.perf_counter();mf.kernel(dm0=mf.get_init_guess(key='minao'))
  if not mf.converged:raise RuntimeError(name+' '+role+' oracle did not converge')
  model=DispersionModel(physical.atom_charges(),physical.atom_coords());model.set_realspace_cutoff(60,40,40)
  ed=float(model.get_dispersion(RationalDampingParam(s6=1.,s8=.7875,a1=.4289,a2=4.4407,s9=0.),grad=False)['energy'])
  trace=float((mf.make_rdm1()*m.intor('int1e_ovlp').T).sum())
  assert abs(trace-physical.nelectron)<1e-9 and abs(m.energy_nuc()-physical.energy_nuc())<1e-12
  dest.write_text(json.dumps({'PBE':mf.e_tot,'D3':ed,'PBE_D3':mf.e_tot+ed,'ENUC':m.energy_nuc(),'electrons':m.nelectron,'tracePS':trace,'converged':bool(mf.converged),'pyscf':pyscf.__version__,'libxc':dft.libxc.__version__,'basis':'def2-SVP Cartesian','grid':'120x590 real centers only','real_atoms_bohr':real,'ghost_basis_centers_bohr':ghost,'charge':charge,'elapsed_seconds':time.perf_counter()-t,'status':'SCREENING_ONLY'},indent=2)+'\n')
  print('PASS',name,role,mf.e_tot,ed,flush=True)
