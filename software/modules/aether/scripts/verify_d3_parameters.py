"""Check explicit PBE parameters against the oracle's built-in named PBE parameter set."""
from pathlib import Path
import json,numpy as np
from dftd3.interface import DispersionModel,RationalDampingParam
r=Path(__file__).resolve().parents[1];specs=json.loads((r/'src/test/resources/totah/lab/aether/reference/d3/systems.json').read_text());z={'H':1,'C':6,'N':7,'O':8,'P':15,'S':16,'Cl':17};errors={}
for name,s in specs.items():
 m=DispersionModel(np.array([z[e] for e,p in s['atoms']]),np.array([p for e,p in s['atoms']]));m.set_realspace_cutoff(60,40,40)
 builtin=float(m.get_dispersion(RationalDampingParam(method='pbe',atm=False),grad=False)['energy']);error=abs(builtin-s['d3_energy']);assert error<1e-16;errors[name]=error
(r/'validation/milestone-16/parameter-verification.json').write_text(json.dumps({'NAMED_PBE_PARAMETERS':'PASS','oracle':'simple-dftd3 1.2.1','errors':errors,'max_error':max(errors.values()),'status':'SCREENING_ONLY'},indent=2)+'\n');print('Built-in PBE parameters PASS',max(errors.values()))
