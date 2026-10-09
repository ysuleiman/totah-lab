"""Bounded derived diagnostic using the installed OpenFF conservation policy."""
import hashlib,json,sys
import faulthandler
faulthandler.dump_traceback_later(45,exit=True)
from pathlib import Path
import numpy as np
import parmed as pmd
from rdkit import Chem
from openff.toolkit import Molecule
from openff.units import unit
import openff.toolkit
import sander
import openmm as mm
from openmm import app,unit as omu
HERE=Path(__file__).resolve().parent; ROOT=HERE.parents[1]
OUT=HERE/'charge-conservation';OUT.mkdir(exist_ok=True)
results=[]
for name in ['parent_netarsudil','ar_13503_deesterified']:
    src=HERE/'parameter-preflight'/name; d=OUT/name;d.mkdir(exist_ok=True)
    parm=pmd.load_file(str(src/'candidate.prmtop'),str(src/'source_bound.inpcrd'))
    rdmol=Chem.SDMolSupplier(str(src/'input.sdf'),removeHs=False)[0]
    mol=Molecule.from_rdkit(rdmol,allow_undefined_stereo=True,hydrogens_are_explicit=True)
    assert [a.atomic_number for a in mol.atoms]==[a.atomic_number for a in parm.atoms]
    assert mol.total_charge.m_as(unit.elementary_charge)==0
    original=np.array([a.charge for a in parm.atoms]);mol.partial_charges=original*unit.elementary_charge
    mol._normalize_partial_charges(); corrected=mol.partial_charges.m_as(unit.elementary_charge)
    offset=-sum(original)/len(original)
    assert np.array_equal(corrected,original+offset)
    for a,q in zip(parm.atoms,corrected):a.charge=float(q)
    top=d/'derived.prmtop'
    if not top.exists():parm.save(str(top),format='amber')
    checked=pmd.load_file(str(top),str(src/'source_bound.inpcrd'))
    raw=pmd.load_file(str(src/'candidate.prmtop'))
    assert checked.parm_data.keys()==raw.parm_data.keys()
    assert all(checked.parm_data[k]==raw.parm_data[k] for k in raw.parm_data if k!='CHARGE')
    xyz=np.array(checked.coordinates);assert np.array_equal(xyz,rdmol.GetConformer().GetPositions())
    charge=sum(a.charge for a in checked.atoms)
    from decimal import Decimal
    field=top.read_text().split('%FLAG CHARGE\n')[1].split('%FLAG')[0]
    tokens=' '.join(field.splitlines()[1:]).split()
    assert len(tokens)==len(original)
    bound=float(sum(Decimal(5).scaleb(Decimal(t).as_tuple().exponent-1) for t in tokens)/Decimal('18.2223'))
    assert abs(charge)<=bound
    opts=sander.gas_input();opts.cut=999.
    sander.setup(str(top),xyz,None,opts);en,fr=sander.energy_forces(as_numpy=True);sander.cleanup()
    system=app.AmberPrmtopFile(str(top)).createSystem(nonbondedMethod=app.NoCutoff,constraints=None,rigidWater=False,removeCMMotion=False)
    integ=mm.VerletIntegrator(.001*omu.picoseconds);ctx=mm.Context(system,integ,mm.Platform.getPlatformByName('Reference'));ctx.setPositions(xyz*omu.angstrom)
    state=ctx.getState(getEnergy=True,getForces=True);oe=state.getPotentialEnergy().value_in_unit(omu.kilocalories_per_mole)
    of=state.getForces(asNumpy=True).value_in_unit(omu.kilocalories_per_mole/omu.angstrom)
    assert np.isfinite(oe) and np.isfinite(en.tot) and np.isfinite(of).all() and np.isfinite(fr).all()
    results.append(dict(compound=name,openff_version=openff.toolkit.__version__,before_charges_e=original.tolist(),derived_charges_e=corrected.tolist(),serialized_charges_e=[a.charge for a in checked.atoms],uniform_offset_e=offset,serialized_sum_e=charge,serialization_bound_e=bound,conservation_gate_pass=True,all_other_topology_arrays_unchanged=True,source_coordinates_exact=True,amber_energy_kcal_mol=float(en.tot),openmm_energy_kcal_mol=oe,energy_difference_kcal_mol=abs(float(en.tot)-oe),max_force_component_difference=float(np.max(np.abs(np.array(fr).reshape(-1,3)-of))),production_qualified=False))
    del ctx,integ,system
(OUT/(sys.argv[1] if len(sys.argv)>1 else 'RESULTS.json')).write_text(json.dumps(results,indent=2)+'\n')
print(json.dumps([{k:v for k,v in r.items() if 'charges_e' not in k} for r in results],indent=2))
