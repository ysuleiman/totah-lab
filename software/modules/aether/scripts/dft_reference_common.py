"""Explicit M12 quadrature and frozen earlier geometries, independently constructed in PySCF."""
from pathlib import Path
import json
import numpy as np
from pyscf import gto,dft
from pyscf.dft import LebedevGrid,gen_grid
ROOT=Path(__file__).resolve().parents[1]
REF=ROOT/'src/test/resources/totah/lab/aether/reference'
NAMES=('h2','ch4','nh3','h2o','co','n2','h2s','ch3cl')
LADDER=((40,110),(80,302),(120,590),(160,974))
def specs():
    all_specs=json.loads((REF/'diis-manifest.json').read_text())['systems']
    return {name:all_specs[name] for name in NAMES}
def molecule(name):
    s=specs()[name]
    return gto.M(atom=s['atom'],unit='Bohr',basis='sto-3g',cart=True,charge=s['charge'],spin=0,verbose=0)
def grid(mol,nr,na):
    x,w=np.polynomial.legendre.leggauss(nr);u=(x+1)/2
    r=u/(1-u);rw=w/2/(1-u)**2*r*r
    a=LebedevGrid.MakeAngularGrid(na)
    coords=(r[:,None,None]*a[None,:,:3]).reshape(-1,3)
    weights=(rw[:,None]*a[None,:,3]*4*np.pi).ravel()
    tab={mol.atom_symbol(i):(coords,weights) for i in range(mol.natm)}
    xyz,vol=gen_grid.get_partition(mol,tab,radii_adjust=None,becke_scheme=gen_grid.original_becke)
    result=dft.gen_grid.Grids(mol);result.coords=xyz;result.weights=vol;result.non0tab=None
    return result
