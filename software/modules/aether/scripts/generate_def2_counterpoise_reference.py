"""Independent def2-SVP RHF/ghost counterpoise; same frozen M11 geometries."""
from interaction_benchmarks import systems
from dft_reference_common import REF
import json,hashlib,sys
import pyscf
from pyscf import gto,scf,lib
assert pyscf.__version__ == '2.10.0', 'Pinned reference generation requires PySCF 2.10.0'
lib.num_threads(1)
for name in sys.argv[1:] or ('water_dimer','water_ammonia','methanethiol_water','ammonium_benzene','chlorobenzene_water'):
 s=systems()[name];a,b=s['a'],s['b'];ca,cb=s['charge_a'],s['charge_b'];rows=['component,quantity,value'];values={}
 for role,atoms,charge in [('COMPLEX',a+b,ca+cb),('A_OWN',a,ca),('B_OWN',b,cb),('A_WITH_GHOST_B',a+[('ghost-'+el,p) for el,p in b],ca),('B_WITH_GHOST_A',b+[('ghost-'+el,p) for el,p in a],cb)]:
  m=gto.M(atom=atoms,charge=charge,spin=0,basis='def2-svp',cart=True,unit='Bohr',verbose=0)
  mf=scf.RHF(m);mf.conv_tol=1e-12;mf.conv_tol_grad=1e-10;mf.max_cycle=128;mf.diis_space=8
  mf.kernel(dm0=mf.get_init_guess(key='1e'))
  values[role]=mf.e_tot
  rows += [f'{role},converged,{int(mf.converged)}',f'{role},total,{mf.e_tot:.17g}',f'{role},electronic,{mf.energy_elec()[0]:.17g}',f'{role},nuclear,{m.energy_nuc():.17g}']
  print(name,role,m.nao_nr(),mf.converged,mf.e_tot,flush=True)
 rows += [f'INTERACTION,uncorrected,{values["COMPLEX"]-values["A_OWN"]-values["B_OWN"]:.17g}',f'INTERACTION,counterpoise,{values["COMPLEX"]-values["A_WITH_GHOST_B"]-values["B_WITH_GHOST_A"]:.17g}']
 content=('\n'.join(rows)+'\n').encode();(REF/f'def2-cp-{name}.csv').write_bytes(content)
 (REF/f'def2-cp-{name}.sha256').write_text(hashlib.sha256(content).hexdigest()+'\n')
