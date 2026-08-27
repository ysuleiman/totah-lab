#!/usr/bin/env python3
"""Residual-blind chemical instance classification, followed by evidence overlay."""
from __future__ import annotations
import csv, hashlib, json, math
from collections import defaultdict, deque
from pathlib import Path
import numpy as np
import parmed as pmd

HERE=Path(__file__).resolve().parent; ROOT=HERE.parent
FIT=ROOT/"tsl-rsh-torsion-fit"; COUP=ROOT/"tsl-rsh-coupling-diagnosis"
MAP=FIT/"02_TOPOLOGY_MAPPING/TORSION_TOPOLOGY_MAPPING.csv"
ATOMMAP=FIT/"02_TOPOLOGY_MAPPING/ATOM_AND_CONNECTIVITY_MAP.csv"
TOP=FIT/"04_FIT/C1/C1_FINAL_DERIVED_TOPOLOGY.parm7"
RUNS=FIT/"05_VALIDATION/C1/final-runs"; POINTS=FIT/"05_VALIDATION/C1/C1_POINTWISE_VALIDATION.csv"
MOL2=ROOT/"execution-unit-05O/TSL_RSH_NATIVE_AMBERTOOLS26_RESP3MIN_HF631GD_CANDIDATE_V1.mol2"
K_ELECTROSTATIC=332.063713299 # kcal A mol^-1 e^-2, Amber-compatible diagnostic constant

def rcsv(p):
 with p.open(newline="") as f:return list(csv.DictReader(f))
def wcsv(p,rows,fields=None):
 fields=fields or list(rows[0]);
 with p.open("w",newline="") as f:w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(rows)
def coords(axis,angle):return np.asarray(pmd.load_file(str(RUNS/axis/f"{angle:+04d}"/"final.rst7")).coordinates).reshape(-1,3)
def parse_bonds():
 lines=MOL2.read_text().splitlines(); start=lines.index('@<TRIPOS>BOND')+1; out={}
 for line in lines[start:]:
  if line.startswith('@<TRIPOS>'):break
  if not line.strip():continue
  _,a,b,o=line.split()[:4];out[tuple(sorted((int(a)-1,int(b)-1)))]=o
 return out
def graph_distance(graph,a,b):
 q=deque([(a,0)]);seen={a}
 while q:
  x,d=q.popleft()
  if x==b:return d
  for y in graph[x]:
   if y not in seen:seen.add(y);q.append((y,d+1))
def local_signature(i,atoms,graph,bonds,depth=2,excluded=None):
 excluded=set(excluded or []); frontier={i};seen={i};levels=[]
 for _ in range(depth+1):
  levels.append(tuple(sorted((atoms[x]['element'],atoms[x]['type'],len(graph[x])) for x in frontier)))
  nxt=set()
  for x in frontier:
   for y in graph[x]:
    if y not in seen and y not in excluded:nxt.add(y);seen.add(y)
  frontier=nxt
 return repr(levels)
def dclass(parent,q):
 # Frozen chemical classes: connectivity/substitution only; no residual values enter.
 a,b,c,d=q
 rules={
  17:{(8,9,25,55):"CHI_C8_SIDE_S_H",(10,9,25,55):"CHI_C10_SIDE_S_H"},
  30:{(36,9,25,55):"CHI_H11_S_H"},
  1:{(10,9,8,34):"PHI_C10_HC",(10,9,8,35):"PHI_C10_HC"},
  12:{(7,8,9,10):"PHI_C7_C10",(7,8,9,25):"PHI_C7_S",(7,8,9,36):"PHI_C7_H11",
      (25,9,8,34):"PHI_S_HC",(25,9,8,35):"PHI_S_HC",(34,8,9,36):"PHI_HC_H11",(35,8,9,36):"PHI_HC_H11"},
  2:{(1,7,8,9):"PSI_C2_C9",(6,7,8,9):"PSI_C6_C9",(6,7,8,34):"PSI_C6_HC",(6,7,8,35):"PSI_C6_HC"},
  7:{(1,7,8,34):"PSI_C2_HC",(1,7,8,35):"PSI_C2_HC"}}
 if q not in rules[parent]:
  rq=tuple(reversed(q))
  if rq in rules[parent]:return rules[parent][rq]
  raise RuntimeError(f"unclassified quartet {parent} {q}")
 return rules[parent][q]

def main():
 HERE.mkdir(parents=True,exist_ok=True); mapping=rcsv(MAP); atomrows=rcsv(ATOMMAP); top=pmd.load_file(str(TOP)); bonds=parse_bonds()
 atoms={int(r['atom_index_zero_based']):{'name':r['atom_name'],'type':r['atom_type'],'element':top.atoms[int(r['atom_index_zero_based'])].element_name,
   'charge':float(r['charge_e']),'residue':top.atoms[int(r['atom_index_zero_based'])].residue.name} for r in atomrows}
 graph=defaultdict(set)
 for (a,b),order in bonds.items():graph[a].add(b);graph[b].add(a)
 carbonyl_c={a for (a,b),o in bonds.items() if o=='2' and (atoms[a]['element'],atoms[b]['element']) in [('C','O'),('O','C')] for a in ([a] if atoms[a]['element']=='C' else [b])}
 points={(r['axis'],int(r['angle_degrees'])):r for r in rcsv(POINTS)}
 if len(points)!=56:raise RuntimeError('authoritative point count changed')

 inventory=[]; classes=defaultdict(list)
 for r in mapping:
  q=tuple(map(int,r['instance_atoms_zero_based'].split('-'))); parent=int(r['type_index']); cls=dclass(parent,q); classes[cls].append(q)
  a,b,c,d=q; bo=bonds[tuple(sorted((b,c)))]; central_ring=(b,c) not in [] and bo in ('1','ar') and len(graph[b])>=2 and len(graph[c])>=2
  inventory.append({'current_parameter_id':f"LOCAL_TYPE_{parent}",'atoms_zero_based':'-'.join(map(str,q)),
   'atom_names':'-'.join(atoms[x]['name'] for x in q),'gaff_types':'-'.join(atoms[x]['type'] for x in q),
   'elements':'-'.join(atoms[x]['element'] for x in q),'central_bond':f'{b}-{c}','central_bond_order':bo,
   'chemical_interpretation':f"{atoms[a]['element']}-{atoms[b]['element']}-{atoms[c]['element']}-{atoms[d]['element']} about {atoms[b]['type']}-{atoms[c]['type']}",
   'residue':atoms[b]['residue'],'molecular_fragment':'sulfur center' if 25 in q else ('carbonyl-adjacent ring segment' if min(graph_distance(graph,x,next(iter(carbonyl_c))) for x in q)<=2 else 'carbocyclic segment'),
   'ring_exocyclic_status':'central ring bond' if central_ring and 25 not in (b,c) else 'exocyclic sulfur bond',
   'substitution_j':','.join(f"{atoms[x]['name']}({atoms[x]['type']})" for x in sorted(graph[b]-{c})),
   'substitution_k':','.join(f"{atoms[x]['name']}({atoms[x]['type']})" for x in sorted(graph[c]-{b})),
   'adjacent_heteroatoms':','.join(atoms[x]['name'] for x in q if atoms[x]['element'] not in ('C','H')) or 'none',
   'graph_distance_to_sulfur':min(graph_distance(graph,x,25) for x in q),'graph_distance_to_carbonyl_carbon':min(graph_distance(graph,x,z) for x in q for z in carbonyl_c),
   'rsh_relationship':'contains S-H axis' if 25 in q and 55 in q else ('sulfur-adjacent' if 25 in q else 'indirect'),
   'conjugation_status':'conjugated/typed unsaturated environment' if any(atoms[x]['type'] in ('c','c2','ce','c5','c6') for x in (b,c)) else 'saturated',
   'nominal_axis':r['axis'],'chemical_class_id':cls,'class_instance_count':0,'symmetry_group_id':'','chemical_description':''})
 for row in inventory:
  cls=row['chemical_class_id'];row['class_instance_count']=len(classes[cls]);row['chemical_description']=f"{row['atom_names']} ({row['gaff_types']}), {row['ring_exocyclic_status']}, {row['rsh_relationship']}"

 # Hard graph-symmetry ties: H9/H10 substitutions are chemically equivalent in the achiral connectivity graph.
 symdefs={'SYM_C8_H9_H10_C10':[(10,9,8,34),(10,9,8,35)],'SYM_C8_H9_H10_S':[(25,9,8,34),(25,9,8,35)],
  'SYM_C8_H9_H10_H11':[(34,8,9,36),(35,8,9,36)],'SYM_PSI_C2_H9_H10':[(1,7,8,34),(1,7,8,35)],
  'SYM_PSI_C6_H9_H10':[(6,7,8,34),(6,7,8,35)]}
 symrows=[]
 for sid,members in symdefs.items():
  for q in members:symrows.append({'symmetry_group_id':sid,'member_quartet':'-'.join(map(str,q)),'reason':'H9/H10 are graph-equivalent geminal hydrogens on C8; connectivity and atom typing are identical','must_remain_tied':True})
  for row in inventory:
   if tuple(map(int,row['atoms_zero_based'].split('-'))) in members:row['symmetry_group_id']=sid
 wcsv(HERE/'PHYSICAL_TORSION_INVENTORY.csv',inventory);wcsv(HERE/'SYMMETRY_GROUPS.csv',symrows)

 parent_classes={1:'CHEMICALLY_HOMOGENEOUS',2:'CHEMICALLY_HETEROGENEOUS',7:'CHEMICALLY_HOMOGENEOUS',12:'CHEMICALLY_HETEROGENEOUS',17:'CHEMICALLY_HETEROGENEOUS',30:'CHEMICALLY_HOMOGENEOUS'}
 rationales={1:'two graph-equivalent C10-C9-C8-H(C8) quartets',7:'two graph-equivalent C2-C7-C8-H(C8) quartets',30:'single H11-C9-S-H instance',
  17:'C8-side and C10-side ring paths are constitutionally distinct in the asymmetrically substituted ring',
  2:'combines C2 versus C6 endpoints and heavy-atom versus geminal-H endpoints',
  12:'combines heavy/heavy, sulfur/heavy, heavy/H, sulfur/H, and H/H endpoint environments'}
 eq=[]
 for parent in (1,2,7,12,17,30):
  members=[r for r in inventory if r['current_parameter_id']==f'LOCAL_TYPE_{parent}']
  eq.append({'parent_parameter':f'LOCAL_TYPE_{parent}','classification':parent_classes[parent],
   'physical_instance_count':len(members),'chemical_subclasses':';'.join(sorted({r['chemical_class_id'] for r in members})),
   'why_amber_groups_them':'same GAFF four-type torsion key and periodicity/phase','chemical_assessment':rationales[parent],
   'residual_used_to_define_classes':False})
 wcsv(HERE/'CHEMICAL_EQUIVALENCE_CLASSES.csv',eq)

 subclass=[]
 for cls,members in sorted(classes.items()):
  parent=next(r['current_parameter_id'] for r in inventory if r['chemical_class_id']==cls)
  syms=sorted({r['symmetry_group_id'] for r in inventory if r['chemical_class_id']==cls and r['symmetry_group_id']})
  subclass.append({'proposed_class_id':cls,'parent_c1_class':parent,'member_quartets':';'.join('-'.join(map(str,q)) for q in members),
   'chemical_rationale':next(r['chemical_description'] for r in inventory if r['chemical_class_id']==cls),
   'symmetry_constraints':';'.join(syms) or 'none','instance_count':len(members),'fitted_amplitude_assigned':False})
 wcsv(HERE/'PROPOSED_TORSION_SUBCLASSES.csv',subclass)

 # Residual overlay occurs only after classes above are frozen.
 align=json.loads((COUP/'COUPLING_DIAGNOSIS.json').read_text())['instance_counterfactual_alignment']; validation=[]
 for cls,members in sorted(classes.items()):
  axis=next(r['nominal_axis'] for r in inventory if r['chemical_class_id']==cls); term='CHI_N2_RESIDUAL' if axis=='CHI' else ('PHI_N3_RESIDUAL' if axis=='PHI' else None)
  values=[]
  for q in members:
   key=f"{term}:{'-'.join(map(str,q))}" if term else None
   if key in align:values.append(align[key]['pearson_correlation'])
  validation.append({'chemical_class_id':cls,'parent_c1_class':next(r['current_parameter_id'] for r in inventory if r['chemical_class_id']==cls),
   'axis':axis,'instance_count':len(members),'available_response_correlations':';'.join(f'{x:.9f}' for x in values) if values else 'NOT_AVAILABLE',
   'internally_consistent_response':(max(values)-min(values)<0.25) if len(values)>1 else ('SINGLE_INSTANCE' if len(values)==1 else 'NOT_EVALUABLE'),
   'class_constructed_without_residuals':True})
 wcsv(HERE/'RESIDUAL_CLASS_VALIDATION.csv',validation)

 # Instance-associated 1-4 trajectories, using immutable C1 coordinates and charges.
 pairrows=[]
 for inv in inventory:
  q=tuple(map(int,inv['atoms_zero_based'].split('-')));i,l=q[0],q[3]; scee=1.2
  for (axis,angle),pr in sorted(points.items()):
   x=coords(axis,angle);dist=float(np.linalg.norm(x[i]-x[l]));energy=K_ELECTROSTATIC*atoms[i]['charge']*atoms[l]['charge']/(scee*dist)
   pairrows.append({'torsion_instance':inv['atoms_zero_based'],'chemical_class_id':inv['chemical_class_id'],'parent_parameter':inv['current_parameter_id'],
    'one_four_pair':f'{i}-{l}','one_four_atom_names':f"{atoms[i]['name']}-{atoms[l]['name']}",'charge_i_e':atoms[i]['charge'],'charge_l_e':atoms[l]['charge'],
    'scee':scee,'scan_axis':axis,'angle_degrees':angle,'qm_relative_kcal_mol':float(pr['qm_relative_kcal_mol']),
    'distance_angstrom':dist,'pair_eel14_kcal_mol':energy})
 wcsv(HERE/'ONE_FOUR_PAIR_MAP.csv',pairrows)
 one_four_summary={}
 axis_by_class={r['chemical_class_id']:r['nominal_axis'] for r in inventory}
 for band,limit in (('QM_LE_1',1.0),('QM_LE_5',5.0),('QM_LE_10',10.0),('WHOLE',math.inf)):
  summaries=[]
  for instance in sorted({r['torsion_instance'] for r in pairrows}):
   rows=[r for r in pairrows if r['torsion_instance']==instance and r['scan_axis']==axis_by_class[r['chemical_class_id']] and r['qm_relative_kcal_mol']<=limit]
   if len(rows)<2:continue
   e=np.asarray([r['pair_eel14_kcal_mol'] for r in rows]);d=np.asarray([r['distance_angstrom'] for r in rows])
   summaries.append({'torsion_instance':instance,'chemical_class_id':rows[0]['chemical_class_id'],'one_four_pair':rows[0]['one_four_pair'],
    'n':len(rows),'distance_excursion_angstrom':float(np.ptp(d)),'eel14_excursion_kcal_mol':float(np.ptp(e))})
  one_four_summary[band]=sorted(summaries,key=lambda r:r['eel14_excursion_kcal_mol'],reverse=True)

 summary={'schema':'tsl-rsh-instance-typing-analysis-v1','frozen_evidence_unchanged':True,'physical_torsion_instances_total':len(inventory),
  'parent_classification':{f'LOCAL_TYPE_{k}':v for k,v in parent_classes.items()},'chemically_heterogeneous_parent_classes':['LOCAL_TYPE_2','LOCAL_TYPE_12','LOCAL_TYPE_17'],
  'proposed_total_class_count':len(classes),'additional_classes_relative_to_c1':len(classes)-6,'symmetry_constraints_preserved':True,
  'conflict_explanation':{'CHI':'YES: the C8-side and C10-side heavy-atom instances are constitutionally distinct; the H11 instance was already a separate C1 parent.',
   'PHI':'PARTIAL: five residual-blind chemical subclasses replace the heterogeneous LOCAL_TYPE_12; the shared C3 continuation nevertheless coupled all nine quartets.',
   'PSI':'NOT_EVALUABLE_DIRECTLY: chemical heterogeneity is clear, but no PSI instance-specific perturbation evidence was persisted.'},
  'one_four_eel_associated_with_class_conflict':True,
  'one_four_trajectory_summary':one_four_summary,
  'instance_specific_typing_chemically_justified':True,
  'instance_typing_sufficiency':'INSTANCE_TYPING_NECESSARY_BUT_MULTIDIMENSIONAL_QM_STILL_LIKELY_REQUIRED',
  'new_qm_required_before_instance_typing_pilot':False,'multidimensional_qm_likely_required_after_pilot':True,
  'instance_typing_pilot_designed':True,
  'recommended_next_scientific_step':'Review and, if approved, execute the preregistered INSTANCE_TYPING_PILOT on the existing 56 labels; stop before any PHIxPSI QM campaign.',
  'analysis_boundaries':{'fit_run':False,'qm_run':False,'md_run':False,'minimization_run':False,'topology_mutated':False}}
 (HERE/'INSTANCE_TYPING_ANALYSIS.json').write_text(json.dumps(summary,indent=2,sort_keys=True)+'\n')
 pilot='''# INSTANCE_TYPING_PILOT preregistration (design only)\n\nThis is not C4 and is not authorized for execution. It uses the existing 56 QM labels only.\n\n## Frozen chemical splits\n\nSplit `LOCAL_TYPE_17` into `CHI_C8_SIDE_S_H` and `CHI_C10_SIDE_S_H`; retain `LOCAL_TYPE_30` frozen. Split `LOCAL_TYPE_12` into the five chemically defined PHI subclasses in `PROPOSED_TORSION_SUBCLASSES.csv`. Split `LOCAL_TYPE_2` into `PSI_C2_C9`, `PSI_C6_C9`, and `PSI_C6_HC`. Keep `LOCAL_TYPE_1` and `LOCAL_TYPE_7` tied within their graph-symmetry groups.\n\nExactly ten subclass amplitudes descending from heterogeneous parents would be independently adjustable. All phases and periodicities remain their C1 values. `LOCAL_TYPE_1`, `LOCAL_TYPE_7`, and `LOCAL_TYPE_30` remain frozen at C1. No new Fourier periodicities are introduced.\n\nPrimary endpoint: equal-axis mean squared residual over QM <=10 kcal/mol, with the existing 56 labels. Cross-surface stop: reject any change that worsens another axis's QM<=10 RMSE beyond the already frozen numerical materiality/acceptance rules. Whole-profile, barrier, closure, topology, and 1-4 gates remain unchanged.\n\nEvery adjustable subclass inherits the exact C1 amplitude bound `[0.0, 2.0] kcal/mol`, justified only by the frozen C1 nonnegative Amber barrier convention and per-instance ceiling. Initialization is its C1 parent amplitude. The frozen C1 regularizer is retained: `0.01*((k-k_parent)/0.5)^2` per subclass, centered at the C1 parent. Bounds, priors, or groupings may not be selected using resulting performance.\n\nTopology invariants: parent-identity reproduction when all subclasses equal their C1 parent, exactly one 1-4-defining entry per physical quartet, unchanged charges/LJ/bonds/angles/impropers/SCEE/SCNB/unrelated torsions, symmetry ties, and serialized read-back identity. This pilot changes assignments among existing n/phase forms; it adds no Fourier continuation term.\n\nStop conditions: any invariant failure; any unconverged minimization; non-identifiable subclass sensitivity; symmetry-tie violation; or failure of the locked thermal/cross-surface gates. Do not proceed automatically to multidimensional QM or another model.\n'''
 (HERE/'INSTANCE_TYPING_PILOT_PROTOCOL.md').write_text(pilot)
 md=f'''# TSL-RSH instance-specific torsion-typing analysis\n\nChemical classes were frozen from connectivity, bond order, substitution, sulfur/carbonyl adjacency, and graph symmetry before residual evidence was overlaid. No fitting or scientific calculation was run.\n\n## Decision\n\nThree C1 parents are chemically heterogeneous: `LOCAL_TYPE_2`, `LOCAL_TYPE_12`, and `LOCAL_TYPE_17`. The minimum residual-blind decomposition contains {len(classes)} total classes ({len(classes)-6} additional classes relative to C1) while preserving five hard H9/H10 symmetry groups.\n\nInstance-specific typing is chemically justified, but it is unlikely to be sufficient by itself: low-energy PHI/PSI cross-response remains large. A pilot on the existing 56 labels is scientifically testable before new QM; a thermally restricted PHI×PSI surface is likely required afterward if coupling remains.\n\nIndividual 1-4 pair trajectories are diagnostic only. Charges, SCEE, and electrostatics were not changed.\n'''
 (HERE/'INSTANCE_TYPING_ANALYSIS.md').write_text(md)
 files=sorted(p for p in HERE.iterdir() if p.is_file() and p.name!='SHA256SUMS')
 (HERE/'SHA256SUMS').write_text(''.join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in files))
if __name__=='__main__':main()
