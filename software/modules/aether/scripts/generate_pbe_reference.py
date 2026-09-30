"""M15 only: fresh Libxc/PySCF oracle; frozen M13 geometry, basis and grid definitions."""
from pathlib import Path
import sys,json,hashlib,os
import numpy as np
import pyscf
from pyscf import gto,dft,lib
from dft_reference_common import grid,LADDER,REF
assert pyscf.__version__=='2.10.0'
assert dft.libxc.__version__=='7.0.0'
lib.num_threads(1)
OUT=REF/'pbe15';OUT.mkdir(exist_ok=True)
SPECS=json.loads((REF/'def2-integrals-manifest.json').read_text())['systems']
NAMES=['h2','h2o','nh3','ch4','co','n2','h2s','ph3','hcl','ch3cl','dms','trimethylsulfonium','chlorobenzene']
def save(name,rows):
 data=('\n'.join(rows)+'\n').encode();(OUT/(name+'.csv')).write_bytes(data);(OUT/(name+'.sha256')).write_text(hashlib.sha256(data).hexdigest()+'\n')
def rotation():
 axis=np.array([1.,2.,3.])/np.sqrt(14);c=np.cos(.513);s=np.sin(.513)
 x,y,z=axis;return c*np.eye(3)+(1-c)*np.outer(axis,axis)+s*np.array([[0,-z,y],[z,0,-x],[-y,x,0]])
def molecule(name,transform):
 spec=SPECS[name];m=gto.M(atom=spec['atom'],charge=spec['charge'],spin=0,unit='Bohr',basis='def2-svp',cart=True,verbose=0,max_memory=256)
 if transform!='native':
  xyz=m.atom_coords();xyz=xyz@rotation().T if transform=='rotated' else xyz+np.array([.31,-1.27,.44])
  m.set_geom_([(m.atom_symbol(i),r) for i,r in enumerate(xyz)],unit='Bohr')
 return m
if sys.argv[1:]==['functional']:
 # Pointwise oracle reduces Libxc's gradient-magnitude floor to 1e-100 to test exact sigma=0.
 # Molecular PySCF references keep the library defaults, and measure that difference.
 import ctypes as ct
 xc=ct.CDLL(str(next((Path(pyscf.__file__).parent/'lib/deps/lib').glob('libxc.*'))))
 xc.xc_func_alloc.restype=ct.c_void_p
 xc.xc_func_init.argtypes=[ct.c_void_p,ct.c_int,ct.c_int]
 xc.xc_func_set_sigma_threshold.argtypes=[ct.c_void_p,ct.c_double]
 xc.xc_gga_exc_vxc.argtypes=[ct.c_void_p,ct.c_size_t]+[ct.POINTER(ct.c_double)]*5
 handles=[]
 for fid in [101,130]:
  f=xc.xc_func_alloc();assert xc.xc_func_init(f,fid,1)==0;xc.xc_func_set_sigma_threshold(f,1e-100);handles.append(f)
 def local(f,rho,sigma):
  n=ct.c_double(rho);s=ct.c_double(sigma);e=ct.c_double();vr=ct.c_double();vs=ct.c_double()
  xc.xc_gga_exc_vxc(f,1,ct.byref(n),ct.byref(s),ct.byref(e),ct.byref(vr),ct.byref(vs))
  return e.value,vr.value,vs.value
 rows=['rho,sigma,exchange,correlation,vrho,vsigma,vrho_component_bound,vsigma_component_bound']
 for rho in [0.,1e-16,1e-15,1e-14,1e-12,1e-10,1e-6,.001,.1,1.,10.,1000.,1e5]:
  for ratio in [0.,1e-12,.1,1.,10.,1e6]:
   sig=rho**(8/3)*ratio;r=np.array([[rho],[np.sqrt(sig)],[0.],[0.]])
   x=local(handles[0],rho,sig);c=local(handles[1],rho,sig)
   vals=[rho,sig,rho*x[0],rho*c[0],x[1]+c[1],x[2]+c[2],abs(x[1])+abs(c[1]),abs(x[2])+abs(c[2])]
   rows.append(','.join(f'{v:.17g}' for v in vals))
 save('functional',rows);sys.exit()
name=sys.argv[1];transform=sys.argv[2] if len(sys.argv)>2 else 'native';nr=int(sys.argv[3]) if len(sys.argv)>3 else 120;na=int(sys.argv[4]) if len(sys.argv)>4 else 590
stem=f'{name}-{transform}-{nr}-{na}'
if (OUT/(stem+'.csv')).exists():raise SystemExit('Refusing overwrite: '+stem)
m=molecule(name,transform);scale=1/np.sqrt(m.intor('int1e_ovlp').diagonal());mf=dft.RKS(m);mf.xc='GGA_X_PBE,GGA_C_PBE';mf.grids=grid(m,nr,na);mf.small_rho_cutoff=0
mf.max_memory=256;mf.conv_tol=1e-12;mf.conv_tol_grad=1e-10;mf.max_cycle=128;mf.diis_space=8
full_j=os.environ.get('AETHER_PBE_ORACLE_FULL_J','0')=='1'
if full_j:
 mf.direct_scf=False;mf.direct_scf_tol=0;mf._eri=m.intor('int2e',aosym='s8')
mf.callback=lambda env: print(stem,'iteration',env.get('cycle'),'energy',env.get('e_tot'),'orbitalGradient',env.get('norm_gorb'),'densityChange',env.get('norm_ddm'),flush=True)
guess=os.environ.get('AETHER_PBE_ORACLE_GUESS','1e')
mf.kernel(dm0=mf.get_init_guess(key=guess))
if not mf.converged:raise RuntimeError(stem+' reference not converged')
p=mf.make_rdm1();ne,exc,vxc=mf._numint.nr_rks(m,mf.grids,mf.xc,p,max_memory=128)
f=mf.get_fock(dm=p);n=m.nao_nr();rows=['kind,i,j,value']
for kind,value in [('total',mf.e_tot),('electronic',mf.energy_elec(p)[0]),('Exc',exc),('electrons',ne)]:rows.append(f'{kind},0,0,{value:.17g}')
for kind,mat in [('density',p/scale[:,None]/scale[None,:]),('Fock',f*scale[:,None]*scale[None,:]),('Vxc',vxc*scale[:,None]*scale[None,:])]:
 for i in range(n):
  for j in range(n):rows.append(f'{kind},{i},{j},{mat[i,j]:.17g}')
for i,e in enumerate(mf.mo_energy):rows.append(f'orbital,{i},0,{e:.17g}')
save(stem,rows)
# Independent analytic AO derivatives at arbitrary and actual quadrature points.
xyz=np.concatenate((np.array([[-.73,.22,1.11],[0,0,0],[1.3,-.87,.31],[3.1,2.7,-1.1]]),mf.grids.coords[np.linspace(0,len(mf.grids.coords)-1,32,dtype=int)]))
ao=m.eval_gto('GTOval_cart_deriv1',xyz);norm=ao*scale[None,None,:]
rho=dft.numint.eval_rho(m,ao,p,xctype='GGA');rows=['point,x,y,z,ao,value,dx,dy,dz,rho,gx,gy,gz,sigma']
for g,point in enumerate(xyz):
 for i in range(n):
  vals=[g,*point,i,*norm[:,g,i],*rho[:,g],np.dot(rho[1:,g],rho[1:,g])]
  rows.append(','.join(f'{v:.17g}' for v in vals))
save(stem+'-ao',rows)
(OUT/(stem+'.json')).write_text(json.dumps({'pyscf':pyscf.__version__,'libxc':dft.libxc.__version__,'geometry_bohr':m.atom_coords().tolist(),'symbols':[m.atom_symbol(i) for i in range(m.natm)],'charge':m.charge,'basis':'def2-svp','cartesian':True,'normalized_cartesian_AOs':True,'radial':nr,'angular':na,'functional':mf.xc,'initial_guess':guess,'forced_full_j':full_j,'converged':mf.converged,'scientific_status':'SCREENING_ONLY'},indent=2)+'\n')
print(stem,'PASS',mf.e_tot,'Exc',exc,'electronCount',ne,flush=True)
