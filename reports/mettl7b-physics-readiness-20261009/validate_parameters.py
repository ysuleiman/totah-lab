"""Independent loads of candidate topologies; numerical QC, not energetic truth."""
import json,hashlib,sys
from pathlib import Path
import numpy as np
import parmed as pmd
from rdkit import Chem
import openmm as mm
from openmm import app,unit
import sander
ROOT=Path(__file__).resolve().parents[2]; HERE=Path(__file__).resolve().parent
OUT=HERE/'parameter-preflight'; results=[]
for name in ['parent_netarsudil','ar_13503_deesterified']:
    d=OUT/name; mol=Chem.SDMolSupplier(str(d/'input.sdf'),removeHs=False)[0]
    parm=pmd.load_file(str(d/'candidate.prmtop'),str(d/'candidate.inpcrd'))
    assert [a.atomic_number for a in parm.atoms]==[a.GetAtomicNum() for a in mol.GetAtoms()]
    edges={tuple(sorted((b.atom1.idx,b.atom2.idx))) for b in parm.bonds}
    source_edges={tuple(sorted((b.GetBeginAtomIdx(),b.GetEndAtomIdx()))) for b in mol.GetBonds()}
    assert edges==source_edges
    serialized_xyz=np.asarray(parm.coordinates); original=np.asarray(mol.GetConformer().GetPositions())
    moved=float(np.max(np.linalg.norm(serialized_xyz-original,axis=1)))
    # The generated mol2/topology coordinates are rounded to 0.001 A. Preserve
    # them as diagnostics; bind a NEW coordinate file to exact source coordinates.
    from decimal import Decimal
    assert all(abs(Decimal(str(x))-Decimal(str(y)))<=Decimal('0.0005')
               for x,y in zip(serialized_xyz.flat,original.flat))
    parm.coordinates=original.copy()
    bound=d/'source_bound.inpcrd'
    if not bound.exists(): parm.save(str(bound),format='rst7')
    checked=pmd.load_file(str(d/'candidate.prmtop'),str(bound))
    xyz=np.asarray(checked.coordinates); assert np.array_equal(xyz,original)
    Chem.AssignAtomChiralTagsFromStructure(mol,replaceExistingTags=True)
    stereo=Chem.MolToSmiles(Chem.RemoveHs(mol),isomericSmiles=True)
    assert stereo==json.loads((d/'INPUT.json').read_text())['source_3d_stereochemical_smiles']
    # Partial charges are rounded in mol2. Bound the sum by serialization precision.
    charge=sum(a.charge for a in parm.atoms)
    charge_gate=abs(charge)<=len(parm.atoms)*.5e-6
    # Keep the charge-admission failure explicit. Cross-engine single points
    # below diagnose this exact candidate Hamiltonian; they cannot qualify it.
    assert all(b.type is not None for b in parm.bonds)
    assert all(a.type is not None for a in parm.angles)
    assert all(a.type is not None for a in parm.dihedrals)
    options=sander.gas_input();options.cut=999.
    sander.setup(str(d/'candidate.prmtop'),xyz,None,options)
    energy,force=sander.energy_forces(as_numpy=True); e1=float(energy.tot); f1=np.asarray(force).reshape((-1,3));sander.cleanup()
    prmtop=app.AmberPrmtopFile(str(d/'candidate.prmtop'))
    system=prmtop.createSystem(nonbondedMethod=app.NoCutoff,constraints=None,rigidWater=False,removeCMMotion=False)
    integrator=mm.VerletIntegrator(.001*unit.picoseconds)
    context=mm.Context(system,integrator,mm.Platform.getPlatformByName('Reference'))
    context.setPositions(xyz*unit.angstrom)
    state=context.getState(getEnergy=True,getForces=True)
    e2=state.getPotentialEnergy().value_in_unit(unit.kilocalories_per_mole)
    f2=state.getForces(asNumpy=True).value_in_unit(unit.kilocalories_per_mole/unit.angstrom)
    assert np.isfinite(e1) and np.isfinite(e2) and np.isfinite(f1).all() and np.isfinite(f2).all()
    # Report actual cross-engine discrepancies; no invented molecular pass cutoff.
    sq=(d/'sqm.out').read_text(); assert 'geometry converged' in sq and 'Calculation Completed' in sq
    frc=(d/'candidate.frcmod').read_text();assert 'ATTN' not in frc
    analogies=[l for l in frc.splitlines() if 'same as' in l.lower() or 'general' in l.lower()]
    results.append(dict(compound=name,atoms=len(parm.atoms),bonds=len(parm.bonds),angles=len(parm.angles),dihedrals=len(parm.dihedrals),atom_order_elements_connectivity_preserved=True,raw_amber_coordinate_quantization_max_A=moved,source_bound_coordinate_max_change_A=0.0,source_3d_stereo_smiles=stereo,net_partial_charge=charge,net_charge_precision_gate_pass=charge_gate,charge_admission='PASS' if charge_gate else 'REJECTED_PENDING_CHARGE_SUM_PROVENANCE',sqm_geometry_converged=True,sander_vacuum_energy_kcal_mol=e1,openmm_reference_vacuum_energy_kcal_mol=e2,absolute_energy_difference_kcal_mol=abs(e1-e2),maximum_force_component_difference_kcal_mol_A=float(np.max(np.abs(f1-f2))),analogy_terms=analogies,production_qualified=False))
    del context,integrator,system
out=OUT/(sys.argv[1] if len(sys.argv)>1 else 'INDEPENDENT_VALIDATION.json')
out.write_text(json.dumps(results,indent=2)+'\n')
print(json.dumps([{k:v for k,v in r.items() if k not in ['analogy_terms','source_3d_stereo_smiles']} for r in results],indent=2))
