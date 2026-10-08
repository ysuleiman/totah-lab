"""Review-only execution of exact pinned upstream functions, not Athena qualification."""
from pathlib import Path
import ast,json,types,importlib.util,math,hashlib,sys
root=Path(__file__).resolve().parent;old=root.parents[1]/'foundation-v2-review-20261008/reference'
p=old/'cctbx--mmtbx__validation__cbetadev.py';assert hashlib.sha256(p.read_bytes()).hexdigest()=='c6661721fa398358f5a9a98a112105149f40643a801ea17b0c158e464229bd19'
spec=importlib.util.spec_from_file_location('pinned_matrix',root/'reference/scitbx__matrix____init__.py');matrix=importlib.util.module_from_spec(spec);spec.loader.exec_module(matrix)
names={'construct_fourth','idealized_calpha_angles','calculate_ideal_and_deviation'}
tree=ast.parse(p.read_text());selected=ast.Module(body=[n for n in tree.body if isinstance(n,(ast.FunctionDef,ast.ClassDef)) and n.name in names],type_ignores=[])
ns={'col':matrix.col,'dihedral_angle':matrix.dihedral_angle,'rotate_point_around_axis':matrix.rotate_point_around_axis};exec(compile(selected,str(p),'exec'),ns)
rows=[]
for identity in ['SER','THR','VAL']:
 for case,points in [('ordinary',[(0.,0.,0.),(1.,0.,0.),(1.,1.,0.),(1.,0.,1.)]),('collinear',[(0.,0.,0.),(1.,0.,0.),(2.,0.,0.),(1.,0.,1.)]),('tiny-normal',[(0.,0.,0.),(1.,0.,0.),(2.,1e-8,0.),(1.,0.,1.)]),('zero-axis',[(1.,0.,0.),(1.,0.,0.),(2.,0.,0.),(1.,0.,1.)])]:
  atoms={k:types.SimpleNamespace(xyz=v) for k,v in zip([' N  ',' CA ',' C  ',' CB '],points)}
  r={'identity':identity,'case':case,'points':points,'parameters':ns['idealized_calpha_angles'](identity)}
  try:
   q=ns['calculate_ideal_and_deviation'](atoms,identity);r.update(ideal=q.ideal,deviation=q.deviation,deviationHex=None if q.deviation is None else q.deviation.hex(),outlier=None if q.deviation is None else q.deviation>=.25)
  except Exception as e:r['exception']=type(e).__name__;r['message']=str(e)
  rows.append(r)
for identity in ['SER','THR','VAL']:
 base=next(r for r in rows if r['identity']==identity and r['case']=='ordinary')
 for label,offset in [('at-reference',0.),('below-cutoff',math.nextafter(.25,-math.inf)),('at-cutoff',.25),('above-cutoff',math.nextafter(.25,math.inf))]:
  points=[tuple(x) for x in base['points']];ideal=base['ideal'];points[3]=(ideal[0]+offset,ideal[1],ideal[2]);atoms={k:types.SimpleNamespace(xyz=v) for k,v in zip([' N  ',' CA ',' C  ',' CB '],points)};q=ns['calculate_ideal_and_deviation'](atoms,identity)
  rows.append({'identity':identity,'case':label,'points':points,'requestedOffset':offset,'actualDeviation':q.deviation,'deviationHex':q.deviation.hex(),'outlier':q.deviation>=.25,'note':'Category uses actual measured distance, never the requested construction offset.'})
result={'status' :'UPSTREAM_REVIEW_PROBES_NOT_ATHENA_QUALIFICATION','sourceSha256':hashlib.sha256(p.read_bytes()).hexdigest(),'matrixSha256':hashlib.sha256((root/'reference/scitbx__matrix____init__.py').read_bytes()).hexdigest(),'runtime':sys.version,'cases':rows,'thresholdProbes':[{'deviation':v,'hex':v.hex(),'upstreamPredicate':v>=.25} for v in [math.nextafter(.25,-math.inf),.25,math.nextafter(.25,math.inf)]]}
(root/'UPSTREAM_PROBES.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
