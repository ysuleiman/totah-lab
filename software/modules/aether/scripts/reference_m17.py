"""External sensitivity measurements; no new production basis or functional."""
from pathlib import Path
import json,sys,time
import pyscf
from pyscf import gto,scf,dft,lib
from dftd3.interface import DispersionModel,RationalDampingParam
from dft_reference_common import grid

ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'validation/milestone-17'
specs=json.loads((OUT/'benchmark.json').read_text());lib.num_threads(1)
basis=sys.argv[1] if len(sys.argv)>1 else 'def2-svp'
names=sys.argv[2:] or (json.loads((OUT/'basis-plan.json').read_text())['selected'] if basis!='def2-svp' else sorted(specs,key=lambda n:(len(specs[n]['atoms']),n)))
for name in names:
    s=specs[name];cut=s['fragment_a_count'];a=s['atoms'][:cut];b=s['atoms'][cut:]
    for role,real,ghost,charge in [('AB',s['atoms'],[],s['charge']),('A_GHOST_B',a,b,s['charge_a']),('B_GHOST_A',b,a,s['charge_b'])]:
        target=OUT/'external'/basis/name;target.mkdir(parents=True,exist_ok=True)
        physical=gto.M(atom=real,unit='Bohr',charge=charge,spin=0,basis=basis,cart=True,verbose=0,max_memory=256)
        mol=gto.M(atom=real+[('ghost-'+el,p) for el,p in ghost],unit='Bohr',charge=charge,spin=0,basis=basis,cart=True,verbose=0,max_memory=256)
        for method in ('RHF','PBE'):
            dest=target/(role+'-'+method+'.json')
            if dest.exists():continue
            mf=scf.RHF(mol) if method=='RHF' else dft.RKS(mol)
            if method=='PBE':
                mf.xc='GGA_X_PBE,GGA_C_PBE';mf.grids=grid(physical,120,590);mf.small_rho_cutoff=0
            mf.conv_tol=1e-12;mf.conv_tol_grad=1e-10;mf.max_cycle=128;mf.diis_space=8
            mf.direct_scf=False;mf.direct_scf_tol=0
            if mol.nao_nr()<=100:mf._eri=mol.intor('int2e',aosym='s8')
            else:mf._opt[None]=mf.init_direct_scf(mol)
            mf.callback=lambda e:print(name,basis,role,method,e.get('cycle'),e.get('e_tot'),flush=True)
            start=time.perf_counter();mf.kernel(dm0=mf.get_init_guess(key='minao'))
            model=DispersionModel(physical.atom_charges(),physical.atom_coords());model.set_realspace_cutoff(60,40,40)
            ed=float(model.get_dispersion(RationalDampingParam(s6=1.,s8=.7875,a1=.4289,a2=4.4407,s9=0.),grad=False)['energy'])
            data={'converged':bool(mf.converged),'energy':float(mf.e_tot) if mf.converged else None,
                  'D3':ed,'electrons':mol.nelectron,'ENUC':float(mol.energy_nuc()),'basis':basis,
                  'representation':'Cartesian','grid':'120x590 physical atoms only','guess':'MINAO',
                  'pyscf':pyscf.__version__,'libxc':dft.libxc.__version__,'elapsed_seconds':time.perf_counter()-start,
                  'fixture_sha256':s['fixture_sha256'],'status':'SCREENING_ONLY'}
            dest.write_text(json.dumps(data,indent=2)+'\n');print('FINISH',name,basis,role,method,mf.converged,flush=True)
