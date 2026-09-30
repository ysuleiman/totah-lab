#!/usr/bin/env python3
"""Phase A only: fresh PySCF AO/rho/Libxc LDA_X intermediates, before adding correlation."""
import csv,json,hashlib,platform
import numpy as np
import pyscf
from pyscf import scf,lib,dft
from pyscf.dft import LebedevGrid,libxc
from dft_reference_common import ROOT,REF,LADDER,specs,molecule,grid
assert pyscf.__version__=='2.10.0'
lib.num_threads(1)
data=ROOT/'src/main/resources/totah/lab/aether/grid';data.mkdir(parents=True,exist_ok=True)
rad=[];ang=[]
for nr,na in LADDER:
    x,w=np.polynomial.legendre.leggauss(nr)
    rad.extend((nr,*v) for v in zip((x+1)/2,w/2))
    ang.extend((na,*v) for v in LebedevGrid.MakeAngularGrid(na))
for file,header,rows in [(data/'radial.csv',['n','u','weight'],rad),(data/'lebedev.csv',['n','x','y','z','weight'],ang)]:
    with file.open('w',newline='') as f:
        writer=csv.writer(f,lineterminator='\n');writer.writerow(header)
        for row in rows:writer.writerow([row[0],*[format(x,'.17g') for x in row[1:]]])
rows=[];densities=[]
for name in specs():
    mol=molecule(name);mf=scf.RHF(mol);mf.conv_tol=1e-12;mf.max_cycle=128;mf.kernel();assert mf.converged
    p=mf.make_rdm1();p=(p+p.T)/2 # Explicit binary64 symmetry canonicalization for supplied-density API.
    g=grid(mol,80,302);ao=mol.eval_gto('GTOval_cart',g.coords)
    rho=np.einsum('gi,ij,gj->g',ao,p,ao);exc,vxc,*_=libxc.eval_xc('LDA_X',rho,spin=0,deriv=1)
    v=np.einsum('g,gi,gj->ij',g.weights*vxc[0],ao,ao)
    for i in range(len(p)):
        for j in range(len(p)):densities.append((name,i,j,format(p[i,j],'.17g')));rows.append((name,'vxc',i,j,format(v[i,j],'.17g')))
    rows.extend([(name,'electrons',0,0,format(g.weights@rho,'.17g')),(name,'exc',0,0,format(g.weights@(rho*exc),'.17g'))])
    # Nontrivial points across atom/radial/angular blocks validate all AO components and partition weights.
    indices=sorted(set([0,len(rho)-1,*range(101,len(rho),997)]))
    for k in indices:
        for q,values in [('x',g.coords[:,0]),('y',g.coords[:,1]),('z',g.coords[:,2]),('weight',g.weights),('rho',rho)]:rows.append((name,q,k,0,format(values[k],'.17g')))
        for i in range(len(p)):rows.append((name,'ao',k,i,format(ao[k,i],'.17g')))
    print('EXCHANGE_REFERENCE',name,'Ne',g.weights@rho,'Ex',g.weights@(rho*exc),flush=True)
for file,header,values in [('dft-exchange.csv',['system','quantity','i','j','value'],rows),('dft-density.csv',['system','i','j','value'],densities)]:
    with (REF/file).open('w',newline='') as f:w=csv.writer(f,lineterminator='\n');w.writerow(header);w.writerows(values)
scalar=[]
for rho in [0,1e-12,1e-6,.001,.1,1,10,1e3,1e6]:
    e,v,*_=libxc.eval_xc('LDA_X',np.array([rho]),deriv=1);scalar.append([rho,float(e[0]),float(v[0][0])])
(REF/'dft-exchange-manifest.json').write_text(json.dumps(dict(status='SCREENING_ONLY',pyscf=pyscf.__version__,libxc=libxc.__version__,numpy=np.__version__,python=platform.python_version(),grid='mapped-Gauss-Legendre u/(1-u),scale=1bohr;80 radial;Lebedev302/order29;Becke3;no radii adjustment;no pruning',systems=specs(),scalar_exchange=scalar,entries=len(rows),hashes={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [data/'radial.csv',data/'lebedev.csv',REF/'dft-exchange.csv',REF/'dft-density.csv',__import__('pathlib').Path(__file__),ROOT/'scripts/dft_reference_common.py']}),indent=2)+'\n')
