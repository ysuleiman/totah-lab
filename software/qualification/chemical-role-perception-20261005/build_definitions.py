# Declarative role candidates over qualified group machinery; no new chemistry engine.
import json,copy,hashlib
from pathlib import Path
base=Path('software/modules/athena/src/main/resources/totah/lab/athena/system/rules')
out=base/'perception-foundation-v1';out.mkdir(exist_ok=True)
records=[]
def source_manifest(group):
 for folder in ['groups-foundation-v2','groups-foundation-v1','groups-b01']:
  p=base/folder/('ATHENA.GROUP.'+group+'.rule.json')
  if p.exists():return p,json.loads(p.read_text())
 raise ValueError(group)
def add(kind,name,group,role,change=None):
 src,m=source_manifest(group);m=copy.deepcopy(m);d=json.loads(m['parameters']['definition']['value'])
 d['schema']='athena-group-definition/2';d.setdefault('occurrenceExclusions',[])
 rid='ATHENA.PERCEPTION.'+kind+'.'+name
 d.update(groupId=rid,patternId=rid+'/pattern',patternVersion='1',negativeCoverageVersion=rid+'/negative/1')
 if change:change(d)
 member=d['roles'][role];d['memberQueryIndices']=member;d['roles'][kind.lower()]=member
 dossier=Path('software/qualification/rule-qualification-blueprint-20261005/dossiers')/('ATHENA.PERCEPTION.'+kind+'.json')
 sources=[{'kind':'SOURCE','namespace':'athena.foundation','id':str(p),'version':hashlib.sha256(p.read_bytes()).hexdigest()} for p in [src,dossier]]
 d['sourceReferences']=sources
 limitations=['Bounded state-specific '+kind.lower()+' role candidate only; no interaction, strength, protonation prediction, favorable chemistry or biological inference.',
 'ABSENT_FALSE is restricted to this exact class; it is never absence of all donors/acceptors in the system.',
 'Authoritative implicit H can establish role identity but does not supply an oriented D-H coordinate; geometry remains separately unevaluated.',
 'Production activation requires current-policy research eligibility; synthetic implementation qualification does not fabricate review.']
 d['limitations']=limitations;m.update(ruleId=rid,qualification='NOT_EVALUATED',implementationVersion='2',profile='ATHENA_GROUP_CONTEXT_V2',tier='B02',limitations=limitations)
 m['measurementsProduced']={'role-identities':'athena-group-identities/2'}
 m['negativeCoverage']['version']=d['negativeCoverageVersion'];m['negativeCoverage']['supportedDomain']='Explicit complete component; class-scoped exact definition and source-state requirements, never universal donor/acceptor absence.'
 m['scientificSources']=[{'locator':s['id'],'sha256':s['version'],'citation':'Pinned reviewed blueprint scope and qualified source identity; no external-engine parity claim.'} for s in sources]
 m['parameters']['definition']['value']=json.dumps(d,sort_keys=True,separators=(',',':'))
 p=out/(rid+'.rule.json');p.write_text(json.dumps(m,indent=2)+'\n');records.append({'rule':rid,'manifest':str(p),'definition':d})
for n,label in [(1,'PRIMARY'),(2,'SECONDARY')]:add('DONOR','AMINE_'+label,'AMINE.'+label+'_CARBON_BOUND','nitrogen')
for label in ['PRIMARY','SECONDARY','TERTIARY']:add('DONOR','AMMONIUM_'+label,'AMMONIUM.'+label+'_CARBON_BOUND','nitrogen')
for h in [2,1]:add('DONOR','AMIDE_NH'+str(h),'AMIDE.NH'+str(h),'nitrogen')
for group in ['ALCOHOL','PHENOL']:add('DONOR',group,group,'hydroxylO')
for label in ['PRIMARY','SECONDARY','TERTIARY']:add('ACCEPTOR','AMINE_'+label,'AMINE.'+label+'_CARBON_BOUND','nitrogen')
def carbonyl_o(d):
 d['requiredState'].update(hydrogenElements=['O'],hydrogenRoles=[{'role':'carbonylOxygen','count':0}],roleHeavyDegree={'carbonylOxygen':1},hydrogenConsistencyQueries={str(n):'[*;H'+str(n)+']' for n in range(5)})
add('ACCEPTOR','CARBONYL_O','CARBONYL','carbonylOxygen',carbonyl_o)
def ether(d):
 refs=d['sourceReferences'];d['occurrenceExclusions'].append({'id':'acyl_oxygen','patternId':'ATHENA.PERCEPTION.ACCEPTOR.ETHER_O/acyl','patternVersion':'1','query':'[O]-[C](=[O])','anchors':{'etherO':[0]},'hydrogenRoles':[],'rationale':'Keep ether and acyl-oxygen role domains separate.','sourceReferences':refs})
add('ACCEPTOR','ETHER_O','ETHER','etherO',ether)
def aromatic_n(h,charge):
 def change(d):
  d['query']=('[n;H1;+1]' if charge else '[n;X'+str(2+h)+';H'+str(h)+';+0]')
  d['roles']={'nitrogen':[0]};d['occurrenceExclusions']=[]
  d['requiredState'].update(aromaticity=True,hydrogenElements=['N'],hydrogenRoles=[{'role':'nitrogen','count':h}],roleHeavyDegree={'nitrogen':2})
 return change
add('DONOR','PYRROLIC_NH','AMINE.PRIMARY_CARBON_BOUND','nitrogen',aromatic_n(1,0))
add('DONOR','PYRIDINIUM_NH','AMINE.PRIMARY_CARBON_BOUND','nitrogen',aromatic_n(1,1))
add('ACCEPTOR','PYRIDINE_LIKE_N','AMINE.PRIMARY_CARBON_BOUND','nitrogen',aromatic_n(0,0))
Path('software/qualification/chemical-role-perception-20261005/DEFINITIONS.json').write_text(json.dumps(records,indent=2)+'\n')
print(len(records),'role candidates emitted')
