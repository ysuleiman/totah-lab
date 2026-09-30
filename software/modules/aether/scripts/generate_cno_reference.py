#!/usr/bin/env python3
"""Fresh libcint s/p tensors and plain RHF references. No Java numbers are an oracle."""
import csv, hashlib, importlib.metadata, json, platform
from pathlib import Path
import numpy as np
import mpmath as mp
import pyscf
from pyscf import gto,scf,lib
assert pyscf.__version__=='2.10.0'
lib.num_threads(1)
root=Path(__file__).resolve().parents[1]; ref=root/'src/test/resources/totah/lab/aether/reference'
systems={
'ch4':'C 0 0 0; H 1.2 1.2 1.2; H 1.2 -1.2 -1.2; H -1.2 1.2 -1.2; H -1.2 -1.2 1.2',
'nh3':'N 0 0 0; H 1.8 0 .7; H -.9 1.558845726812 .7; H -.9 -1.558845726812 .7',
'h2o':'O 0 0 0; H 1.43 0 1.11; H -1.43 0 1.11',
'co':'C 0 0 0; O 2.13 0 0',
'n2':'N 0 0 0; N 2.07 0 0',
'ethene':'C -1.26 0 0; C 1.26 0 0; H -2.3 1.76 0; H -2.3 -1.76 0; H 2.3 1.76 0; H 2.3 -1.76 0'}
basis_path=root/'src/main/resources/totah/lab/aether/basis/sto-3g-cno.csv'
assert hashlib.sha256(basis_path.read_bytes()).hexdigest()=='e7de8394ca8ff2fba0ee8750a890bfff98147d23faae1450261494c5f7ea1546'
for row in list(csv.DictReader(basis_path.open())):
 shell=gto.basis.load('sto-3g',{6:'C',7:'N',8:'O'}[int(row['atomic_number'])])[int(row['shell'])]
 assert int(row['angular_momentum'])==shell[0]
 assert [float(row['exponent']),float(row['coefficient'])] in shell[1:]
rows=[]; geometry=[]; details={}
for name,atom in systems.items():
 mol=gto.M(atom=atom,basis='sto-3g',unit='Bohr',cart=True,charge=0,spin=0,verbose=0)
 for i in range(mol.natm):geometry.append((name,i,mol.atom_charge(i),*map(lambda x:format(x,'.17g'),mol.atom_coord(i))))
 s=mol.intor('int1e_ovlp');t=mol.intor('int1e_kin');v=mol.intor('int1e_nuc');h=t+v;eri=mol.intor('int2e');n=mol.nao_nr()
 mf=scf.RHF(mol);mf.diis=False;mf.damp=0;mf.level_shift=0;mf.max_cycle=128;mf.conv_tol=1e-13
 mf.check_convergence=lambda env: abs(env['e_tot']-env['last_hf_e'])<=1e-13 and np.max(abs(env['dm']-env['dm_last']))<=1e-12
 eps,c=mf.eig(h,s);occ=np.zeros(n);occ[:mol.nelectron//2]=2;mf.kernel(dm0=mf.make_rdm1(c,occ));assert mf.converged,name
 p=mf.make_rdm1();vhf=mf.get_veff(dm=p);f=h+vhf;eps,c=mf.eig(f,s)
 for label,matrix in [('S',s),('T',t),('V',v),('Hcore',h),('density',p),('Fock',f),('orbital_energies',eps[:,None])]:
  for i in range(matrix.shape[0]):
   for j in range(matrix.shape[1]):rows.append((name,label,i,j,0,0,format(matrix[i,j],'.17g')))
 unique=0
 for i in range(n):
  for j in range(i+1):
   for k in range(i+1):
    for l in range(k+1):
     if k*(k+1)//2+l>i*(i+1)//2+j:break
     rows.append((name,'ERI',i,j,k,l,format(eri[i,j,k,l],'.17g')));unique+=1
 for label,value in [('electronic',mf.energy_elec(dm=p,h1e=h,vhf=vhf)[0]),('nuclear',mol.energy_nuc()),('total',mf.energy_tot(dm=p,h1e=h,vhf=vhf)),('converged',1)]:rows.append((name,label,0,0,0,0,format(value,'.17g')))
 details[name]=dict(atom=atom,unit='Bohr',basis='sto-3g',cartesian=True,charge=0,multiplicity=1,electrons=mol.nelectron,ao_labels=mol.ao_labels(),converged=bool(mf.converged),cycles=mf.cycles,basis_functions=n,unique_eri=unique,total_energy=float(mf.e_tot))
 print(name,n,unique,mf.cycles,mf.e_tot,flush=True)
for filename,header,data in [('cno.csv',['system','evidence','i','j','k','l','value'],rows),('cno-geometry.csv',['system','nucleus','charge','x','y','z'],geometry)]:
 with (ref/filename).open('w',newline='') as out:
  writer=csv.writer(out,lineterminator='\n');writer.writerow(header);writer.writerows(data)
mp.mp.dps=80
boys=[]
for n in range(1,5):
 for t in [0,1e-16,1e-8,.1,1,15.999999,16,16.000001,36,100,1e4]:
  x=mp.mpf(t);value=mp.mpf(1)/(2*n+1) if x==0 else mp.gammainc(n+mp.mpf('.5'),0,x)/(2*x**(n+mp.mpf('.5')))
  boys.append((n,format(t,'.17g'),mp.nstr(value,40)))
with (ref/'boys-orders.csv').open('w',newline='') as out:
 writer=csv.writer(out,lineterminator='\n');writer.writerow(['order','T','value']);writer.writerows(boys)
manifest=dict(oracle='Fresh PySCF 2.10.0/libcint Cartesian integrals and plain RHF; mpmath 80-digit Boys',status='SCREENING_ONLY',python=platform.python_version(),platform=platform.platform(),packages={p:importlib.metadata.version(p) for p in ['pyscf','numpy','scipy','h5py','mpmath']},threads=lib.num_threads(),systems=details,entries=len(rows),boys_entries=len(boys),energy_threshold=1e-13,density_threshold=1e-12,maximum_iterations=128,diis=False,damping=0,level_shift=0,hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),basis_path,ref/'cno.csv',ref/'cno-geometry.csv',ref/'boys-orders.csv']})
(ref/'cno-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(manifest['hashes'],flush=True)
