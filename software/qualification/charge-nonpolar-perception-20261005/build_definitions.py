# Emits reviewed-domain literal predicates; Java/B00 performs every chemistry match.
from pathlib import Path
import json,copy,hashlib
root=Path('software/modules/athena/src/main/resources/totah/lab/athena/system/rules')
out=root/'perception-state-v1';out.mkdir(exist_ok=True)
source=root/'groups-foundation-v2/ATHENA.GROUP.AMINE.PRIMARY_CARBON_BOUND.rule.json'
records=[]
for name,query,h,degree,aromatic in [('FORMAL_CHARGE.PLUS_ONE','[*;+1]',None,None,False),('FORMAL_CHARGE.MINUS_ONE','[*;-1]',None,None,False)]+[('NONPOLAR.SATURATED_C_H'+str(h),'[C;X4;H'+str(h)+';+0]',h,4-h,False) for h in range(4)]+[('NONPOLAR.AROMATIC_C_H'+str(h),'[c;H'+str(h)+';+0]',h,3-h,True) for h in range(2)]:
 m=json.loads(source.read_text());d=json.loads(m['parameters']['definition']['value']);rid='ATHENA.PERCEPTION.'+name
 dossier=Path('software/qualification/rule-qualification-blueprint-20261005/dossiers')/('ATHENA.PERCEPTION.'+name.split('.')[0]+'.json')
 refs=[dict(kind='SOURCE',namespace='athena.foundation',id=str(p),version=hashlib.sha256(p.read_bytes()).hexdigest()) for p in [source,dossier]]
 d.update(groupId=rid,patternId=rid+'/pattern',patternVersion='1',negativeCoverageVersion=rid+'/negative/1',query=query,memberQueryIndices=[0],roles={'feature':[0]},sourceReferences=refs,occurrenceExclusions=[])
 d['requiredState']=dict(graphCompleteness=True,formalCharge=True,aromaticity=aromatic,hydrogenElements=[] if h is None else ['C'],hydrogenRoles=[] if h is None else [dict(role='feature',count=h)],roleHeavyDegree={} if h is None else {'feature':degree},roleCharges={'feature':1 if name.endswith('PLUS_ONE') else -1 if name.endswith('MINUS_ONE') else 0},hydrogenConsistencyQueries={str(i):'[*;H'+str(i)+']' for i in range(5)})
 d['supportedDomain']['unsupportedQueries']=[]
 if h is not None:
  d['occurrenceExclusions']=[dict(id='heteroatom_neighbor',patternId=rid+'/heteroatom_neighbor',patternVersion='1',query='[#6]~[!#6;!#1]',anchors={'feature':[0]},hydrogenRoles=[],rationale='Only explicit C/H neighborhoods in this initial subdomain; heteroatom-adjacent carbon is not silently equated.',sourceReferences=refs)]
 limitations=['Source-state feature only; no pKa, partial-charge substitution, favorable contact, hydrophobic free energy or biological inference.', 'Atomic +/-1 features do not assert a net charged group: nitro/zwitterionic internal charges remain separately attributed.', 'Nonpolar candidates here mean only the declared neutral saturated/aromatic C/H neighborhood with authoritative H; other carbon/halogen/sulfur contexts are outside this candidate definition.', 'Zero match is only absence of this exact feature in complete reviewed scope; unknown state remains non-negative.', 'Implementation qualification is not current-policy scientific eligibility.']
 d['limitations']=limitations
 m.update(ruleId=rid,qualification='NOT_EVALUATED',profile='ATHENA_GROUP_CONTEXT_V2',implementationVersion='2',tier='B03',limitations=limitations,measurementsProduced={'state-features':'athena-group-identities/2'},scientificSources=[dict(locator=r['id'],sha256=r['version'],citation='Pinned reviewed blueprint and existing local-context substrate') for r in refs])
 m['negativeCoverage']['version']=d['negativeCoverageVersion'];m['negativeCoverage']['supportedDomain']='Complete explicit component, exact charge/H/context subdomain; no universal nonpolar or net-group-charge claim.'
 m['parameters']['definition']['value']=json.dumps(d,sort_keys=True,separators=(',',':'))
 p=out/(rid+'.rule.json');p.write_text(json.dumps(m,indent=2)+'\n');records.append(dict(rule=rid,manifest=str(p),definition=d))
Path('software/qualification/charge-nonpolar-perception-20261005/DEFINITIONS.json').write_text(json.dumps(records,indent=2)+'\n')
print(len(records),'definitions')
