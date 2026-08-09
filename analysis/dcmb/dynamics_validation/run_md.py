#!/usr/bin/env python3
"""Bounded, matched, unbiased OpenMM pilot for DCMB versus 2,4 isomer."""
from __future__ import annotations
import argparse,csv,hashlib,json,os,sys
from pathlib import Path
os.environ.setdefault('MPLCONFIGDIR','/private/tmp/dcmb-dynamics-mpl')
os.environ['PATH']=str(Path(sys.prefix)/'bin')+os.pathsep+os.environ.get('PATH','')
import numpy as np
from openff.toolkit import Molecule
from openff.units import unit as offunit
from openmm import CustomExternalForce,LangevinMiddleIntegrator,MonteCarloBarostat,Platform,XmlSerializer,unit
from openmm.app import DCDReporter,ForceField,HBonds,Modeller,PDBFile,PME,Simulation,StateDataReporter
from openmmforcefields.generators import GAFFTemplateGenerator
from pdbfixer import PDBFixer
from rdkit import Chem

ROOT=Path(__file__).resolve().parents[3]; HERE=Path(__file__).resolve().parent
FOCUS=ROOT/'analysis/dcmb/focused_validation'; SAR=ROOT/'analysis/dcmb/sar_experiment'
SYSTEMS={
 '7A_DCMB_S':('7A','DCMB_S',42,1), '7A_24DCMB_R':('7A','24DCMB_R',42,1),
 '7B_DCMB_R':('7B','DCMB_R',42,1), '7B_24DCMB_R':('7B','24DCMB_R',42,1),
}
REPLICAS=(1,2,3); BASE_SEED=20260820

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def ligand_path(para,cid,seed):return FOCUS/'raw'/f'{para}_SAM__{cid}__s{seed}.pdbqt'

def ligand_molecule(para,cid,seed,rank):
 path=ligand_path(para,cid,seed); lines=path.read_text().splitlines(); start=lines.index(f'MODEL {rank}');end=next(i for i in range(start+1,len(lines)) if lines[i].startswith('ENDMDL')); model=lines[start:end]
 source=(FOCUS/'ligands'/f'{cid}.sdf') if cid.startswith('CONH') else (SAR/'ligands'/f'{cid}.sdf')
 rd=Chem.RemoveHs(Chem.SDMolSupplier(str(source),removeHs=False)[0]);coords={}
 for x in model:
  if not x.startswith(('ATOM  ','HETATM')):continue
  name=x[12:16].strip()
  if name.upper().startswith('H'):continue
  digits=''.join(ch for ch in name if ch.isdigit());coords[int(digits)-1]=np.array([float(x[30:38]),float(x[38:46]),float(x[46:54])])
 if set(coords)!=set(range(rd.GetNumAtoms())):raise ValueError(f'heavy atom-name mapping failed for {cid}: {sorted(coords)}')
 conf=Chem.Conformer(rd.GetNumAtoms())
 for i in range(rd.GetNumAtoms()):conf.SetAtomPosition(i,coords[i])
 rd.RemoveAllConformers();rd.AddConformer(conf);rd=Chem.AddHs(rd,addCoords=True)
 mol=Molecule.from_rdkit(rd,hydrogens_are_explicit=True,allow_undefined_stereo=False);mol.name='LIG';return mol,path

def sam_molecule(para):
 rd=Chem.SDMolSupplier(str(ROOT/f'analysis/dcmb/controlled_campaign/prepared/{para}_SAM.sdf'),removeHs=False)[0]
 mol=Molecule.from_rdkit(rd,hydrogens_are_explicit=True,allow_undefined_stereo=False);mol.name='SAM';return mol

def positions(mol):return [np.array(x)*unit.angstrom for x in mol.conformers[0].m_as(offunit.angstrom)]

def device():return Platform.getPlatformByName('CPU'),{'Threads':'4'}

def add_equil_restraints(system,pos,topology,lig_indices,sam_indices):
 force=CustomExternalForce('k_pos*((x-x0)^2+(y-y0)^2+(z-z0)^2)');force.addGlobalParameter('k_pos',1000.0*unit.kilojoule_per_mole/unit.nanometer**2)
 for p in ('x0','y0','z0'):force.addPerParticleParameter(p)
 selected=set(lig_indices)|set(sam_indices)
 for a in topology.atoms():
  if a.name=='CA' and a.residue.name not in {'SAM','LIG'}:selected.add(a.index)
 for i in sorted(selected):force.addParticle(i,pos[i].value_in_unit(unit.nanometer))
 system.addForce(force)

def prepare(system_id):
 para,cid,dock_seed,rank=SYSTEMS[system_id];out=HERE/'systems'/system_id;out.mkdir(parents=True,exist_ok=True)
 receptor=SAR/'receptors'/f'WT_METTL{para}_SAM.pdb';lig,pose_source=ligand_molecule(para,cid,dock_seed,rank);sam=sam_molecule(para)
 fixer=PDBFixer(filename=str(receptor));fixer.removeHeterogens(keepWater=False);fixer.findMissingResidues();fixer.missingResidues={};fixer.findMissingAtoms();fixer.addMissingAtoms();fixer.addMissingHydrogens(7.4)
 mod=Modeller(fixer.topology,fixer.positions);protein_atoms=mod.topology.getNumAtoms();mod.add(sam.to_topology().to_openmm(),positions(sam));sam_idx=list(range(protein_atoms,mod.topology.getNumAtoms()));before=mod.topology.getNumAtoms();mod.add(lig.to_topology().to_openmm(),positions(lig));lig_idx=list(range(before,mod.topology.getNumAtoms()))
 ff=ForceField('amber14/protein.ff14SB.xml','amber14/tip3p.xml');cache=HERE/'gaff-template-cache.json';gaff=GAFFTemplateGenerator(molecules=[sam,lig],forcefield='gaff-2.11',cache=str(cache));ff.registerTemplateGenerator(gaff.generator);mod.addSolvent(ff,model='tip3p',padding=1.0*unit.nanometer,ionicStrength=.15*unit.molar,neutralize=True)
 system=ff.createSystem(mod.topology,nonbondedMethod=PME,nonbondedCutoff=1.0*unit.nanometer,constraints=HBonds,rigidWater=True,removeCMMotion=True);system.addForce(MonteCarloBarostat(1*unit.bar,300*unit.kelvin,25));add_equil_restraints(system,mod.positions,mod.topology,lig_idx,sam_idx)
 integ=LangevinMiddleIntegrator(300*unit.kelvin,1/unit.picosecond,.002*unit.picoseconds);plat,props=device();sim=Simulation(mod.topology,system,integ,plat,props);sim.context.setPositions(mod.positions);sim.minimizeEnergy(maxIterations=3000);st=sim.context.getState(getPositions=True,getEnergy=True)
 with (out/'solvated_initial.pdb').open('w') as f:PDBFile.writeFile(mod.topology,mod.positions,f,keepIds=True)
 with (out/'minimized.pdb').open('w') as f:PDBFile.writeFile(mod.topology,st.getPositions(),f,keepIds=True)
 (out/'system.xml').write_text(XmlSerializer.serialize(system));np.save(out/'minimized_positions_nm.npy',np.array([p.value_in_unit(unit.nanometer) for p in st.getPositions()]))
 atoms=list(mod.topology.atoms());heavy=[i for i in lig_idx if atoms[i].element.symbol!='H'];meta={'system_id':system_id,'paralog':para,'compound_id':cid,'stereochemistry':cid.rsplit('_',1)[-1],'ligand_formal_charge':int(lig.total_charge.m_as(offunit.elementary_charge)),'receptor':str(receptor),'receptor_sha256':sha(receptor),'pose_family':next(r['family_id'] for r in csv.DictReader(open(FOCUS/'per_seed_pose_metrics.csv')) if r['paralog']==para and r['compound_id']==cid and int(r['seed'])==dock_seed and int(r['rank'])==rank),'docking_seed':dock_seed,'docking_rank':rank,'pose_source':str(pose_source),'pose_source_sha256':sha(pose_source),'sam_policy':'SAM present; exact starting heavy coordinates; dynamics unrestrained in production','protein_forcefield':'Amber ff14SB','small_molecule_forcefield':'GAFF 2.11 / AM1-BCC','water':'TIP3P, 1.0 nm padding','ions':'neutralized, 0.15 M','temperature_K':300,'pressure_bar':1,'timestep_fs':2,'ligand_indices':lig_idx,'ligand_heavy_indices':heavy,'sam_indices':sam_idx,'atoms':mod.topology.getNumAtoms(),'waters':sum(r.name=='HOH' for r in mod.topology.residues()),'minimized_potential_kJ_mol':st.getPotentialEnergy().value_in_unit(unit.kilojoule_per_mole),'platform':plat.getName(),'status':'prepared'};(out/'metadata.json').write_text(json.dumps(meta,indent=2)+'\n');return meta

def run(system_id,replica,production_ns=.2):
 out=HERE/'systems'/system_id;rd=HERE/'trajectories'/system_id/f'replica_{replica}';rd.mkdir(parents=True,exist_ok=True);meta=json.loads((out/'metadata.json').read_text());pdb=PDBFile(str(out/'solvated_initial.pdb'));system=XmlSerializer.deserialize((out/'system.xml').read_text());seed=BASE_SEED+100*list(SYSTEMS).index(system_id)+replica
 integ=LangevinMiddleIntegrator(300*unit.kelvin,1/unit.picosecond,.002*unit.picoseconds);integ.setRandomNumberSeed(seed);plat,props=device();sim=Simulation(pdb.topology,system,integ,plat,props);sim.context.setPositions(np.load(out/'minimized_positions_nm.npy')*unit.nanometer);sim.context.setVelocitiesToTemperature(100*unit.kelvin,seed)
 sim.context.setParameter('k_pos',1000.0);sim.step(5000);sim.context.setVelocitiesToTemperature(300*unit.kelvin,seed+1);sim.context.setParameter('k_pos',100.0);sim.step(5000);sim.context.setParameter('k_pos',0.0);sim.step(15000)
 interval=1000;sim.reporters.append(DCDReporter(str(rd/'production.dcd'),interval));sim.reporters.append(StateDataReporter(str(rd/'state.csv'),interval,step=True,time=True,potentialEnergy=True,temperature=True,volume=True,density=True,speed=True,separator=','));steps=int(production_ns*500000);sim.step(steps);st=sim.context.getState(getPositions=True,getEnergy=True);sim.saveCheckpoint(str(rd/'final.chk'))
 with (rd/'final.pdb').open('w') as f:PDBFile.writeFile(pdb.topology,st.getPositions(),f,keepIds=True)
 runmeta={**meta,'replica':replica,'simulation_seed':seed,'equilibration_ps':50,'production_ns':production_ns,'output_interval_ps':2,'production_frames':steps//interval,'production_restraints':'none','final_potential_kJ_mol':st.getPotentialEnergy().value_in_unit(unit.kilojoule_per_mole),'status':'production_complete'};(rd/'metadata.json').write_text(json.dumps(runmeta,indent=2)+'\n')

def main():
 p=argparse.ArgumentParser();p.add_argument('stage',choices=['prepare','run']);p.add_argument('--system',choices=list(SYSTEMS),required=True);p.add_argument('--replica',type=int,choices=REPLICAS);p.add_argument('--production-ns',type=float,default=.2);a=p.parse_args()
 if a.stage=='prepare':print(json.dumps(prepare(a.system),indent=2))
 else:
  if a.replica is None:raise SystemExit('--replica required')
  run(a.system,a.replica,a.production_ns)
if __name__=='__main__':main()
