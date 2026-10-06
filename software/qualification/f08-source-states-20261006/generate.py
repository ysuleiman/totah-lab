"""Exact supplied N–N state identities; no normalization or chemical-role inference."""
from pathlib import Path
import copy,json,hashlib
root=Path('software/modules/athena/src/main/resources/totah/lab/athena/system/rules')
template=json.loads(next((root/'groups-f19-v1').glob('*.json')).read_text());base=json.loads(template['parameters']['definition']['value'])
source=Path('software/qualification/scientific-rule-knowledge-audit-20261004/reference/rdkit--Data__FunctionalGroups.txt');hier=source.with_name('rdkit--Data__Functional_Group_Hierarchy.txt')
refs=[{'kind':'SOURCE','namespace':'athena.foundation','id':str(p),'version':hashlib.sha256(p.read_bytes()).hexdigest()} for p in [source,hier,Path('software/qualification/foundation-closure-execution-20261006/REQUEST.txt')]]
spec=[]
for family,order,maxh in [('HYDRAZINE','-',2),('DIAZENE','=',1)]:
 for h1 in range(maxh+1):
  for h2 in range(h1,maxh+1):
   spec.append((family+'.NEUTRAL.H'+str(h1)+'_H'+str(h2),f'[N;!a;+0;H{h1}]{order}[N;!a;+0;H{h2}]',[0,1],{'nitrogen1':[0],'nitrogen2':[1]}, {'nitrogen1':h1,'nitrogen2':h2},{'nitrogen1':maxh+1-h1,'nitrogen2':maxh+1-h2}))
spec += [
 ('DIAZO.C_DOUBLE_N_POS_DOUBLE_N_NEG','[C;!a;+0]=[N;+1]=[N;-1]',[0,1,2],{'carbon':[0],'centralNitrogen':[1],'terminalNitrogen':[2]}, {'centralNitrogen':0,'terminalNitrogen':0},{'centralNitrogen':2,'terminalNitrogen':1}),
 ('DIAZO.C_NEG_SINGLE_N_POS_TRIPLE_N','[C;!a;-1]-[N;+1]#[N;+0]',[0,1,2],{'carbon':[0],'centralNitrogen':[1],'terminalNitrogen':[2]}, {'centralNitrogen':0,'terminalNitrogen':0},{'centralNitrogen':2,'terminalNitrogen':1}),
 ('DIAZONIUM.CARBON_BOUND','[#6;+0]-[N;+1]#[N;+0]',[1,2],{'attachment':[0],'centralNitrogen':[1],'terminalNitrogen':[2]}, {'centralNitrogen':0,'terminalNitrogen':0},{'centralNitrogen':2,'terminalNitrogen':1}),
 ('AZIDE.CARBON_BOUND.DOUBLE_DOUBLE','[#6]-[N;+0]=[N;+1]=[N;-1]',[1,2,3],{'attachment':[0],'attachedNitrogen':[1],'centralNitrogen':[2],'terminalNitrogen':[3]}, {'attachedNitrogen':0,'centralNitrogen':0,'terminalNitrogen':0},{'attachedNitrogen':2,'centralNitrogen':2,'terminalNitrogen':1}),
 ('AZIDE.CARBON_BOUND.SINGLE_TRIPLE','[#6]-[N;-1]-[N;+1]#[N;+0]',[1,2,3],{'attachment':[0],'attachedNitrogen':[1],'centralNitrogen':[2],'terminalNitrogen':[3]}, {'attachedNitrogen':0,'centralNitrogen':0,'terminalNitrogen':0},{'attachedNitrogen':2,'centralNitrogen':2,'terminalNitrogen':1})]
out=root/'groups-f08-v1';out.mkdir(exist_ok=True)
for name,query,members,roles,hs,degrees in spec:
 m=copy.deepcopy(template);d=copy.deepcopy(base);rid='ATHENA.GROUP.'+name
 d.update(groupId=rid,patternId=rid+'/pattern',query=query,memberQueryIndices=members,roles=roles,sourceReferences=refs,negativeCoverageVersion=rid+'/negative/1')
 d['requiredState'].update(hydrogenElements=['C','N'],hydrogenRoles=[{'role':r,'count':h} for r,h in hs.items()],roleHeavyDegree=degrees)
 d['occurrenceExclusions']=[]
 if name.startswith('HYDRAZINE') or name.startswith('DIAZENE'):
  # Other source bonds on each N must be single; the selected core is anchored.
  for first,second in [('nitrogen1','nitrogen2'),('nitrogen2','nitrogen1')]:
   for symbol,label in [('=','double'),('#','triple')]:
    core='-' if name.startswith('HYDRAZINE') else '='
    d['occurrenceExclusions'].append({'id':first+'-extra-'+label,'patternId':rid+'/'+first+'-extra-'+label,'patternVersion':'1','query':'[N]('+core+'[N])'+symbol+'[*]','anchors':{first:[0],second:[1]},'hydrogenRoles':[],'rationale':'Selected core plus a distinct extra multiple bond is outside this source N valence/bond-order identity.','sourceReferences':refs})
 d['limitations']=['Exact supplied N-N bond/charge/H state; no reactivity, donor/acceptor, toxicity, tautomer/resonance equivalence or biological claim.','Neutral hydrazine/diazene endpoints are nonaromatic; cyclic connectivity and substituent identity are not arbitrarily excluded. Aromatic N-N bonds require a separate identity rather than this nonaromatic name.','Diazo, diazonium and azide source charge/bond depictions are independent identities, never normalized or merged. Source graph validity is separately assessed.']
 m.update(ruleId=rid,limitations=d['limitations']);m['negativeCoverage']['version']=d['negativeCoverageVersion'];m['parameters']['definition']['value']=json.dumps(d,sort_keys=True,separators=(',',':'))
 m['scientificSources']=[{'locator':r['id'],'sha256':r['version'],'citation':'Source motif precedent; labels corrected by explicit bond/charge/state, not copied as scientific truth. No functional interpretation.'} for r in refs]
 (out/(rid+'.rule.json')).write_text(json.dumps(m,indent=2)+'\n')
print(len(spec),'N-N state definitions')
