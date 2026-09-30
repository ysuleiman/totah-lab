"""Outcome-independent benchmark selection; never fit or select by Aether error."""
from pathlib import Path
import csv, hashlib, io, json, math, zipfile
import numpy as np
import pyarrow.parquet as pq
from pyscf.data import elements, radii

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'validation/milestone-17'
FIX = ROOT / 'src/test/resources/totah/lab/aether/reference/m17'
FIX.mkdir(parents=True, exist_ok=True)
HARTREE_KCAL = 627.5094740631
ANG_BOHR = 1.8897261254578281

def sha(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()

def partition(z, xyz):
    """Verify two covalent components, preserving source atom ordering."""
    xyz = np.asarray(xyz)
    adjacency = np.linalg.norm(xyz[:,None]-xyz[None,:], axis=2) < 1.25*(radii.COVALENT[z][:,None]+radii.COVALENT[z][None,:])
    seen = set(); groups = []
    for i in range(len(z)):
        if i in seen: continue
        group = {i}; todo = [i]; seen.add(i)
        while todo:
            for j in np.flatnonzero(adjacency[todo.pop()]):
                j=int(j)
                if j not in seen: seen.add(j); group.add(j); todo.append(j)
        groups.append(sorted(group))
    assert len(groups)==2, groups
    assert groups[0]==list(range(len(groups[0]))) and groups[1]==list(range(len(groups[0]),len(z))), groups
    return len(groups[0])

selection = {1:['HYDROGEN_BOND'], 3:['HYDROGEN_BOND'],
             24:['PI_PI'],47:['PI_PI'],30:['DISPERSION'],54:['MIXED'],
             67:['SULFUR'],96:['SULFUR'],80:['HALOGEN'],87:['HALOGEN','MIXED'],
             92:['IONIC'],95:['IONIC']}
source = Path('/private/tmp/aether-nenci.parquet')
assert sha(source)=='d1c2a27e0ea8b6de7c7de1515d8885dd9927c6d0ade64c62e2804eb4f64b66a2'
systems = {}
for row in pq.read_table(source).to_pylist():
    name = row['names'][0]; number = int(name[:3])
    if number not in selection or not name.endswith('_1.00__CCSD(T)/CBS') or '_A_' in name or '_B_' in name: continue
    if row['method']!='CCSD(T)' or json.loads(row['property_metadata'])['basis-set']!='CBS': continue
    z=row['atomic_numbers']; xyz=np.asarray(row['positions'])*ANG_BOHR; cut=partition(z,xyz)
    charge=int(json.loads(row['configuration_metadata'])['dimer-charge'])
    # The connected charged methylammonium/acetate fragment has 8/7 atoms respectively.
    qa=charge if number in (92,95) and cut==(8 if number==92 else 7) else 0
    s={'name':name,'classes':selection[number],'independent_pair':str(number),
       'atoms':[[elements.ELEMENTS[n],p.tolist()] for n,p in zip(z,xyz)],'fragment_a_count':cut,
       'charge_a':qa,'charge_b':charge-qa,'charge':charge,
       'reference_hartree':row['energy']/27.211386245988,'reference_original_ev':row['energy'],
       'reference_method':'CCSD(T)/CBS composite','source':'https://doi.org/10.1063/5.0068862',
       'mirror':'https://doi.org/10.60732/5d2a1ceb','source_sha256':sha(source),
       'source_configuration_id':row['configuration_id'],'source_property_id':row['property_id'],
       'source_metadata':json.loads(row['configuration_metadata']),'original_positions_angstrom':row['positions']}
    systems[f'nenci_{number:03}']=s
assert len(systems)==len(selection)

des=Path('/private/tmp/aether-m17-DES370K.zip')
with des.open('rb') as stream:
    assert hashlib.file_digest(stream,'md5').hexdigest()=='b2b2bf8bc0dd436cfc1ab159d86421fd'
selected={'[NH4+]':('ammonium_benzene',['CATION_PI']),
          'C[NH3+]':('methylammonium_benzene',['CATION_PI']),
          'CS':('methanethiol_benzene',['SULFUR']),
          'S':('h2s_benzene',['SULFUR']),
          'C':('methane_benzene',['DISPERSION'])}
with zipfile.ZipFile(des) as archive:
    for n in ('LICENSE.txt','README.md','DES370K_meta.csv'):
        (OUT/('DES370K-'+n)).write_bytes(archive.read(n))
    rows=csv.DictReader(io.TextIOWrapper(archive.open('DES370K.csv')))
    for row in rows:
        if row['smiles1']!='c1ccccc1' or row['smiles0'] not in selected or row['k_index']!='0' or row['group_orig']!='qm_opt_dimer': continue
        short,classes=selected[row['smiles0']]; key='des_'+short
        assert key not in systems
        xyz=np.asarray(list(map(float,row['xyz'].split()))).reshape(-1,3)*ANG_BOHR
        symbols=row['elements'].split();z=[elements.charge(el) for el in symbols]
        cut=partition(z,xyz);assert cut==int(row['natoms0'])
        systems[key]={'name':short,'classes':classes,'independent_pair':row['system_id'],
            'atoms':[[el,p.tolist()] for el,p in zip(symbols,xyz)],'fragment_a_count':cut,
            'charge_a':int(row['charge0']),'charge_b':int(row['charge1']),
            'charge':int(row['charge0'])+int(row['charge1']),
            'reference_hartree':float(row['cbs_CCSD(T)_all'])/HARTREE_KCAL,
            'reference_original_kcal_mol':float(row['cbs_CCSD(T)_all']),
            'reference_method':'CCSD(T)/CBS composite; not SNS-MP2 prediction',
            'source':'https://doi.org/10.1038/s41597-021-00833-x',
            'archive':'https://doi.org/10.5281/zenodo.5676266','source_sha256':sha(des),'source_row':row}
assert len(systems)==17
for name,s in systems.items():
    assert math.isfinite(s['reference_hartree'])
    cut=s['fragment_a_count']
    for atoms,charge in ((s['atoms'][:cut],s['charge_a']),(s['atoms'][cut:],s['charge_b'])):
        assert (sum(elements.charge(el) for el,p in atoms)-charge)%2==0
    fixture=f"{cut},{s['charge_a']},{s['charge_b']}\n"+''.join(f'{elements.charge(el)},{p[0]},{p[1]},{p[2]}\n' for el,p in s['atoms'])
    (FIX/(name+'.atoms')).write_text(fixture)
    s['fixture_sha256']=hashlib.sha256(fixture.encode()).hexdigest()
(OUT/'benchmark.json').write_text(json.dumps(systems,indent=2)+'\n')
print('Frozen',len(systems),'systems; source partitions verified against connected components')
