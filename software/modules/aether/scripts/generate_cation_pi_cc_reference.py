"""Independent finite-basis CCSD(T) validation reference, not an Aether runtime method."""
from pathlib import Path
import json,time
from pyscf import gto,scf,cc,lib
import pyscf
root=Path(__file__).resolve().parents[1];s=json.loads((root/'src/test/resources/totah/lab/aether/reference/d3/systems.json').read_text())['cation_pi_small']
out=root/'validation/milestone-16/cc-reference';out.mkdir(exist_ok=True);lib.num_threads(1)
a=s['atoms'][:5];b=s['atoms'][5:]
components=[('AB',a+b,1),('A',a,1),('B',b,0),('A_GHOST_B',a+[('ghost-'+el,p) for el,p in b],1),('B_GHOST_A',b+[('ghost-'+el,p) for el,p in a],0)]
for role,atoms,charge in components:
 dest=out/(role+'.json')
 if dest.exists():continue
 t=time.perf_counter();m=gto.M(atom=atoms,unit='Bohr',basis='def2-svp',cart=True,charge=charge,spin=0,verbose=4,max_memory=384)
 mf=scf.RHF(m);mf.conv_tol=1e-12;mf.conv_tol_grad=1e-9;mf.max_cycle=128;mf.kernel()
 assert mf.converged
 frozen=int(sum(z>2 for z in m.atom_charges()));c=cc.CCSD(mf,frozen=frozen);c.max_memory=384;c.conv_tol=1e-10;c.conv_tol_normt=1e-8;c.max_cycle=128;c.kernel();assert c.converged
 et=c.ccsd_t();energy=c.e_tot+et
 dest.write_text(json.dumps({'method':'frozen-core CCSD(T)/def2-SVP Cartesian','energy_hartree':energy,'CCSD':c.e_tot,'triples':et,'frozen_core_orbitals':frozen,'atoms_bohr':atoms,'charge':charge,'pyscf':pyscf.__version__,'elapsed_seconds':time.perf_counter()-t,'converged':True,'limitation':'finite-basis correlated reference; NOT CBS','status':'SCREENING_ONLY'},indent=2)+'\n')
 print('CC_REFERENCE_PASS',role,energy,flush=True)
