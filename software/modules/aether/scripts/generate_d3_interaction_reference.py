from pathlib import Path
import json,numpy as np
from dftd3.interface import DispersionModel,RationalDampingParam
r=Path(__file__).resolve().parents[1];folder=r/'src/test/resources/totah/lab/aether/reference/d3';specs=json.loads((folder/'systems.json').read_text());z={'H':1,'C':6,'N':7,'O':8,'P':15,'S':16,'Cl':17}
param=RationalDampingParam(method='pbe',atm=False)
def energy(atoms):
 m=DispersionModel(np.array([z[e] for e,p in atoms]),np.array([p for e,p in atoms]));m.set_realspace_cutoff(60,40,40);return float(m.get_dispersion(param,grad=False)['energy'])
rows=['name,AB,A,B,interaction']
for name,s in specs.items():
 cut=s.get('fragment_a_count',0)
 if not cut:continue
 atoms=s['atoms'];ab=energy(atoms);a=energy(atoms[:cut]);b=energy(atoms[cut:]);rows.append(','.join(map(str,[name,ab,a,b,(ab-a)-b])))
(folder/'interaction.csv').write_text('\n'.join(rows)+'\n');print(len(rows)-1,'independent D3 interaction references')
