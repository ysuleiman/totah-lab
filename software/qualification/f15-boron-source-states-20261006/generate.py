"""Boron source connectivity/charge attribution; no normalization or ionization model."""
from pathlib import Path
import copy,json,hashlib
root=Path('software/modules/athena/src/main/resources/totah/lab/athena/system/rules')
m0=json.loads(next((root/'groups-f08-v1').glob('*.json')).read_text());d0=json.loads(m0['parameters']['definition']['value'])
source=Path('software/qualification/scientific-rule-knowledge-audit-20261004/reference/rdkit--Data__Functional_Group_Hierarchy.txt')
refs=[{'kind':'SOURCE','namespace':'athena.foundation','id':str(p),'version':hashlib.sha256(p.read_bytes()).hexdigest()} for p in [source,Path('software/qualification/foundation-closure-execution-20261006/REQUEST.txt')]]
spec=[]
for name,h1,h2 in [('BORONIC_ACID.SOURCE_STATE',1,1),('BORONIC_MONOESTER.SOURCE_STATE',0,1),('BORONIC_DIESTER.SOURCE_STATE',0,0)]:
 spec.append((name,f'[#6]-[B;+0](-[O;+0;H{h1}])-[O;+0;H{h2}]',[1,2,3],{'attachment':[0],'boron':[1],'oxygen1':[2],'oxygen2':[3]},{'boron':0,'oxygen1':h1,'oxygen2':h2},{'boron':3,'oxygen1':2-h1,'oxygen2':2-h2}))
spec.append(('BORON.FOUR_SINGLE_BONDS.FORMAL_MINUS_ONE','[B;-1](-[*])(-[*])(-[*])-[*]',[0],{'boron':[0],'neighbor1':[1],'neighbor2':[2],'neighbor3':[3],'neighbor4':[4]},{'boron':0},{'boron':4}))
out=root/'groups-f15-v1';out.mkdir(exist_ok=True)
for name,query,members,roles,hs,degrees in spec:
 m=copy.deepcopy(m0);d=copy.deepcopy(d0);rid='ATHENA.GROUP.'+name
 d.update(groupId=rid,patternId=rid+'/pattern',query=query,memberQueryIndices=members,roles=roles,sourceReferences=refs,negativeCoverageVersion=rid+'/negative/1',occurrenceExclusions=[])
 d['requiredState'].update(hydrogenElements=['B','O','C'],hydrogenRoles=[{'role':r,'count':h} for r,h in hs.items()],roleHeavyDegree=degrees)
 d['supportedDomain']['elements'].append('B')
 if name.startswith('BORONIC'):
  for role in ['oxygen1','oxygen2']:
   if hs[role]==0:
    # Anchored B-O with one distinct other neighbor. Only a SINGLE carbon attachment is ester scope.
    for q,suffix in [('[B]-[O]-[!#6]','noncarbon'),('[B]-[O]=[*]','double'),('[B]-[O]#[*]','triple')]:
     d['occurrenceExclusions'].append({'id':role+'-'+suffix,'patternId':rid+'/'+role+'-'+suffix,'patternVersion':'1','query':q,'anchors':{'boron':[0],role:[1]},'hydrogenRoles':[],'rationale':'Ester oxygen other heavy attachment must be carbon through a supplied SINGLE bond; shared/cyclic attachment is allowed.','sourceReferences':refs})
 d['limitations']=['Source connectivity/charge/H attribution only; no pKa, boron Lewis acidity, reactivity, stability, physiological population or binding claim.','B(OH)2, B(OH)(OR), and B(OR)2 remain distinct neutral three-coordinate states. Cyclic/shared-carbon attachments are allowed. No RDKit reaction/parent stripping is performed.','Four-SINGLE-bond B(-1) is a separate exact supplied topology/charge descriptor, not automatic acid/ester identity, boronate speciation or universal charged-boron classifier. Other bond/charge/coordination states are retained outside this definition.']
 m.update(ruleId=rid,limitations=d['limitations']);m['negativeCoverage']['version']=d['negativeCoverageVersion'];m['parameters']['definition']['value']=json.dumps(d,sort_keys=True,separators=(',',':'))
 m['scientificSources']=[{'locator':r['id'],'sha256':r['version'],'citation':'B-C/O/O motif precedent, refined into exact supplied H/charge/connectivity states; no reaction semantics.'} for r in refs]
 (out/(rid+'.rule.json')).write_text(json.dumps(m,indent=2)+'\n')
