"""Select exact CCSD(T)/CBS rows, never the lower-level rows sharing their geometry names."""
from pathlib import Path
import pyarrow.parquet as pq,json,hashlib,sys
r=Path(__file__).resolve().parents[1];src=Path(sys.argv[1]);data=src.read_bytes()
assert hashlib.sha256(data).hexdigest()=='d1c2a27e0ea8b6de7c7de1515d8885dd9927c6d0ade64c62e2804eb4f64b66a2'
rows=pq.read_table(src).to_pylist();selections={1:'HYDROGEN_BOND',24:'PI_PI',30:'DISPERSION',67:'SULFUR',80:'HALOGEN'}
Z={1:'H',6:'C',7:'N',8:'O',15:'P',16:'S',17:'Cl'};result={}
for number,category in selections.items():
 candidates=[x for x in rows if x['names'][0].startswith(f'{number:03}_') and x['names'][0].endswith('_1.00__CCSD(T)/CBS') and '_A_' not in x['names'][0] and '_B_' not in x['names'][0] and x['method']=='CCSD(T)' and json.loads(x['property_metadata'])['basis-set']=='CBS']
 assert len(candidates)==1,(number,len(candidates));x=candidates[0];meta=json.loads(x['configuration_metadata']);assert meta['dimer-charge']==0 and meta['dimer-multiplicity']==1
 atoms=[(Z[z],[v*1.8897261254578281 for v in p]) for z,p in zip(x['atomic_numbers'],x['positions'])]
 cut=int(meta['num_atoms_monomer_a'])
 note='Verified positional partition against molecular composition'
 if number==80:
  assert x['atomic_numbers']==[8,1,1,17,6,1,1,1]
  cut=3
  note='Mirror size metadata is 5/3; coordinate order is water (OHH) then chloromethane (ClCHHH). Preserve coordinates and explicitly use 3/5 positional partition.'
 result[f'nenci_{number:03}']={'atoms':atoms,'charge':0,'fragment_a_count':cut,'source_monomer_counts':[int(meta['num_atoms_monomer_a']),int(meta['num_atoms_monomer_b'])],'partition_note':note,'charge_a':0,'charge_b':0,
 'category':category,'reference_interaction_ev':x['energy'],'reference_interaction_hartree':x['energy']/27.211386245988,
 'reference_method':'CCSD(T)/CBS composite, NENCI-2021','reference_name':x['names'][0],'property_id':x['property_id'],'configuration_id':x['configuration_id'],
 'source':'https://doi.org/10.1063/5.0068862; https://doi.org/10.60732/5d2a1ceb','mirror':'https://huggingface.co/datasets/colabfit/NENCI-2021','mirror_payload_sha256':hashlib.sha256(data).hexdigest(),
 'original_positions_angstrom':x['positions'],'license':'CC-BY-4.0','authors':'Sparrow, Ernst, Joo, Lao, DiStasio Jr','units_source':'https://materials.colabfit.org/docs/configuration_schema'}
out=r/'validation/milestone-16/nenci-selection.json';out.write_text(json.dumps(result,indent=2)+'\n');print(list(result))
