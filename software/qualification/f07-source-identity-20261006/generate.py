"""Compose the approved F07 definitions from the existing group /2 and methyl predicate."""
import copy, hashlib, json
from pathlib import Path
root=Path('software/modules/athena/src/main/resources/totah/lab/athena/system/rules')
template=json.loads((root/'groups-adopted-v1/ATHENA.GROUP.ISOCYANATE.CARBON_BOUND.rule.json').read_text())
methylpath=root/'groups-b01/ATHENA.GROUP.METHYL.rule.json'
methyl=json.loads(json.loads(methylpath.read_text())['parameters']['definition']['value'])
review=Path('software/qualification/f07-source-identity-review-20261006/REVIEW.txt')
refs=[{'kind':'SOURCE','namespace':'athena.foundation','id':str(p),'version':hashlib.sha256(p.read_bytes()).hexdigest()} for p in [review,methylpath,Path('software/qualification/f07-source-identity-20261006/SCOPE_AMENDMENT.txt')]]
base=json.loads(template['parameters']['definition']['value'])
base['sourceReferences']+=refs
base['limitations']=['Exact approved F07 source identity only; no normalization, universal chemistry, reactivity, donor/acceptor, pKa, E/Z equivalence or biological claim.', 'Complete authoritative source H/charge/mapping required. Unsupported representations remain non-negative.']
base['supportedDomain']['unsupportedQueries']=[]
base['requiredState']['hydrogenElements']=['C','N','O']
base['requiredState']['aromaticity']=True
specs=[
 ('IMINE.N_METHYL.CARBON_BOUND',methyl['query'].replace('[*]','[N;!a;+0;R0]')+'=[C;!a;+0]',[1,2],{'methylCarbon':[0],'attachment':[1],'nitrogen':[1],'imineCarbon':[2]}, {'methylCarbon':3,'nitrogen':0},{'methylCarbon':1,'nitrogen':2}),
 ('IMINE.METHYLENE.CARBON_BOUND','[#6;+0]-[N;!a;+0;R0]=[C;!a;+0;H2]',[1,2],{'carbonAttachment':[0],'nitrogen':[1],'imineCarbon':[2]}, {'nitrogen':0,'imineCarbon':2},{'nitrogen':2,'imineCarbon':1}),
 ('OXIME.HYDROXY.CARBON_BOUND','[C;!a;+0]=[N;!a;+0]-[O;+0;H1]',[0,1,2],{'imineCarbon':[0],'nitrogen':[1],'oxygen':[2]}, {'nitrogen':0,'oxygen':1},{'nitrogen':2,'oxygen':1}),
 ('NITROSO.CARBON_BOUND','[#6;+0]-[N;!a;+0]=[O;+0]',[1,2],{'carbonAttachment':[0],'nitrogen':[1],'oxygen':[2]}, {'nitrogen':0,'oxygen':0},{'nitrogen':2,'oxygen':1})]
for suffix,c_ring,n_ring in [('ACYCLIC','R0','R0'),('EXOCYCLIC','R','R0'),('ENDOCYCLIC','R','R')]:
 specs.append(('IMINE.N_CARBON_BOUND.'+suffix,'[#6;+0]-[N;!a;+0;'+n_ring+']=[C;!a;+0;'+c_ring+']',[1,2],{'carbonAttachment':[0],'nitrogen':[1],'imineCarbon':[2]}, {'nitrogen':0},{'nitrogen':2}))
for suffix,c_ring in [('ACYCLIC_C','R0'),('CYCLIC_C','R')]:
 specs.append(('IMINE.N_H.'+suffix,'[N;!a;+0;H1]=[C;!a;+0;'+c_ring+']',[0,1],{'nitrogen':[0],'imineCarbon':[1]}, {'nitrogen':1},{'nitrogen':1}))
out=root/'groups-f07-v1';out.mkdir(exist_ok=True)
for name,query,members,roles,hs,degrees in specs:
 d=copy.deepcopy(base); rid='ATHENA.GROUP.'+name
 d.update(groupId=rid,patternId=rid+'/pattern',negativeCoverageVersion=rid+'/negative/1',query=query,memberQueryIndices=members,roles=roles)
 d['requiredState']['hydrogenRoles']=[{'role':r,'count':h} for r,h in hs.items()]
 d['requiredState']['roleHeavyDegree']=degrees
 # Preserve the actual approved methyl predicate and source-H/degree constraints in composition.
 if name.startswith('IMINE.N_METHYL'):
  for h in methyl['requiredState']['hydrogenRoles']:assert h in d['requiredState']['hydrogenRoles']
  for r,v in methyl['requiredState']['roleHeavyDegree'].items():assert degrees[r]==v
 core='[C]~[N]' if not name.startswith('NITROSO') else '[#6]-[N]~[O]'
 unsupported=[(core.replace('[N]', '[N;'+charge+']'),'Charged core nitrogen outside reviewed neutral-state domain') for charge in ['+1','-1']]
 if name.startswith('OXIME') or name.startswith('NITROSO'):
  unsupported += [('[N]~[O;'+charge+']','Charged oxygen/resonance alternative outside reviewed neutral-state domain') for charge in ['+1','-1']]
 if name.startswith('IMINE.') or name.startswith('OXIME.'):
  unsupported += [('[C;'+charge+']~[N]','Charged carbon/resonance alternative outside neutral C=N domain') for charge in ['+1','-1']]
  unsupported += [('[N]=[C]-[!#6;!#1]','Imine carbon heteroatom substitution outside carbon-bound domain'),('[N]=[C]=[*]','Additional imine-carbon multiple bond outside reviewed exact bond-order domain')]
 d['supportedDomain']['unsupportedQueries']=[{'query':q,'reason':r} for q,r in unsupported]
 m=copy.deepcopy(template);m.update(ruleId=rid,limitations=d['limitations']);m['negativeCoverage']['version']=d['negativeCoverageVersion']
 m['parameters']['definition']['value']=json.dumps(d,sort_keys=True,separators=(',',':'))
 m['scientificSources'] += [{'locator':str(review),'sha256':hashlib.sha256(review.read_bytes()).hexdigest(),'citation':'User-approved exact F07 definition, 2026-10-06; attributed approval retained in qualification checkpoint.'}]
 (out/(rid+'.rule.json')).write_text(json.dumps(m,indent=2)+'\n')
