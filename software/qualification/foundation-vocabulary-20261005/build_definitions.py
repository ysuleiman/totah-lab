# Emits reviewed declarative candidates; executes no chemical perception or calculations.
import json,hashlib,copy
from pathlib import Path
root=Path('software/modules/athena/src/main/resources/totah/lab/athena/system/rules/groups-foundation-v2');root.mkdir(exist_ok=True)
base=json.loads(Path('software/modules/athena/src/main/resources/totah/lab/athena/system/rules/groups-foundation-v1/ATHENA.GROUP.AMINE.PRIMARY_ALIPHATIC.rule.json').read_text())
inventory=Path('software/qualification/rule-qualification-blueprint-20261005/REFERENCE_CAPABILITY_INVENTORY.json')
records=[]
def add(name,query,members,roles,hroles=None,degree=None,exclusions=None,aromatic=False,source='ATHENA.GROUP.AMINE',limits=None,unsupported=None):
 m=copy.deepcopy(base);d=json.loads(m['parameters']['definition']['value']);rid='ATHENA.GROUP.'+name
 m.update(ruleId=rid,profile='ATHENA_GROUP_CONTEXT_V2',implementationVersion='2')
 m['measurementsProduced']['group-identities']='athena-group-identities/2'
 d.update(schema='athena-group-definition/2',groupId=rid,patternId=rid+'/pattern',query=query,memberQueryIndices=members,roles=roles,occurrenceExclusions=[])
 d['negativeCoverageVersion']=rid+'/negative/1';m['negativeCoverage']['version']=d['negativeCoverageVersion']
 d['requiredState']={'graphCompleteness':True,'formalCharge':True,'aromaticity':aromatic,'hydrogenElements':sorted(set(x[2] for x in hroles or [])),
 'hydrogenRoles':[{'role':r,'count':h} for r,h,e in hroles or []], 'roleHeavyDegree':degree or {},
 'hydrogenConsistencyQueries':{str(n):'[*;H'+str(n)+']' for n in range(5)}}
 d['supportedDomain']['unsupportedQueries']=[{'query':q,'reason':r} for q,r in unsupported or []]
 src=Path('software/qualification/rule-qualification-blueprint-20261005/dossiers')/(source+'.json') if source else inventory
 refs=[{'kind':'SOURCE','namespace':'athena.foundation','id':str(src),'version':hashlib.sha256(src.read_bytes()).hexdigest()}]
 d['sourceReferences']=refs;m['scientificSources']=[{'locator':str(src),'sha256':refs[0]['version'],'citation':'Existing pinned blueprint/reference inventory; exact literal/state fixtures qualify this bounded identity, not external-engine parity.'}]
 for eid,q,anchors in exclusions or []:
  d['occurrenceExclusions'].append({'id':eid,'patternId':rid+'/'+eid,'patternVersion':'1','query':q,'anchors':anchors,'hydrogenRoles':[],
  'rationale':'Exclude this exact anchored constitutional context without suppressing unrelated occurrences.','sourceReferences':refs})
 d['limitations']=m['limitations']=base['limitations']+(limits or [])
 m['parameters']['definition']['value']=json.dumps(d,sort_keys=True,separators=(',',':'))
 (root/(rid+'.rule.json')).write_text(json.dumps(m,indent=2)+'\n');records.append({'rule':rid,'query':query,'members':members,'roles':roles,'definition':d,'manifest':str(root/(rid+'.rule.json'))})
ex=[('acyl_or_amidino','[N]-[C](=[O,N,S])',{'nitrogen':[0]}),('sulfonyl','[N]-[S](=[O])',{'nitrogen':[0]})]
for n,label in [(1,'PRIMARY'),(2,'SECONDARY'),(3,'TERTIARY')]:
 q='[#6]-[N;!a;X3;H'+str(3-n)+';+0]'+('(-[#6])' if n>=2 else '')+('(-[#6])' if n>=3 else '')
 add('AMINE.'+label+'_CARBON_BOUND',q,[1],{'nitrogen':[1],'carbonAttachments':[0]+list(range(2,n+1))},[('nitrogen',3-n,'N')],{'nitrogen':n},ex,limits=['Carbon-bound neutral nonaromatic N only; acyl/amidino/thioacyl/sulfonyl contexts excluded. Enamines/anilines are constitutional amines here, without acceptor/pKa inference.'])
for n,label in [(1,'PRIMARY'),(2,'SECONDARY'),(3,'TERTIARY'),(4,'QUATERNARY')]:
 q='[#6]-[N;!a;X4;H'+str(4-n)+';+1]'+''.join('(-[#6])' for _ in range(n-1))
 add('AMMONIUM.'+label+'_CARBON_BOUND',q,[1],{'nitrogen':[1],'carbonAttachments':[0]+list(range(2,n+1))},[('nitrogen',4-n,'N')],{'nitrogen':n},ex,limits=['Exact source +1 N state only; no prediction of protonation or ionizability.'])
for h in [2,1,0]:
 add('AMIDE.NH'+str(h),'[C;!a;X3;+0](=[O;X1;+0])-[N;!a;X3;H'+str(h)+';+0]',[0,1,2],{'carbonylCarbon':[0],'oxygen':[1],'nitrogen':[2]},[('nitrogen',h,'N')],{'nitrogen':3-h},source='ATHENA.GROUP.AMIDE',limits=['N-H-count subclass of literal neutral amide C(=O)-N, retaining urea/carbamate and carbonyl overlap.'])
add('ALDEHYDE.CARBON_SUBSTITUTED','[#6]-[C;!a;X3;H1;+0]=[O;X1;+0]',[1,2],{'carbonylCarbon':[1],'oxygen':[2],'carbonAttachment':[0]},[('carbonylCarbon',1,'C')],source='ATHENA.GROUP.CARBONYL')
add('ALDEHYDE.FORMALDEHYDE','[C;!a;X3;H2;+0]=[O;X1;+0]',[0,1],{'carbonylCarbon':[0],'oxygen':[1]},[('carbonylCarbon',2,'C')],source='ATHENA.GROUP.CARBONYL')
add('KETONE','[#6]-[C;!a;X3;+0](=[O;X1;+0])-[#6]',[1,2],{'carbonylCarbon':[1],'oxygen':[2],'carbonAttachments':[0,3]},source='ATHENA.GROUP.CARBONYL')
add('NITRILE','[#6]-[C;!a;X2;+0]#[N;X1;+0]',[1,2],{'carbon':[1],'nitrogen':[2],'attachment':[0]},source=None)
add('NITRO.CHARGE_SEPARATED','[#6]-[N;!a;X3;+1](=[O;X1;+0])-[O;X1;-1]',[1,2,3],{'nitrogen':[1],'doubleO':[2],'anionicO':[3],'attachment':[0]},source=None,limits=['One literal charge-separated resonance drawing; alternative O assignment is not overwritten or normalized.'])
add('ALKENE','[C;!a;+0]=[C;!a;+0]',[0,1],{'carbons':[0,1]},source=None,limits=['Exact neutral nonaromatic C=C bond identity; unconstrained E/Z is preserved, not assigned.'])
add('ALKYNE','[C;!a;+0]#[C;!a;+0]',[0,1],{'carbons':[0,1]},source=None)
add('SULFONE','[#6]-[S;!a;X4;+0](=[O;X1;+0])(=[O;X1;+0])-[#6]',[1,2,3],{'sulfur':[1],'oxygens':[2,3],'carbonAttachments':[0,4]},[('sulfur',0,'S')],{'sulfur':4},source=None,limits=['Literal neutral S(=O)2 representation only; charge-separated sulfur remains outside the reviewed domain.'])
add('SULFONAMIDE','[#6]-[S;!a;X4;+0](=[O;X1;+0])(=[O;X1;+0])-[N;!a;X3;+0]',[1,2,3,4],{'sulfur':[1],'oxygens':[2,3],'nitrogen':[4],'carbonAttachment':[0]},[('sulfur',0,'S')],{'sulfur':4},source=None,limits=['Literal neutral S(=O)2-N constitution, no donor/acceptor claim.'])
add('SULFONIUM.CARBON_BOUND','[#6]-[S;!a;X3;+1](-[#6])-[#6]',[1],{'sulfur':[1],'carbonAttachments':[0,2,3]},[('sulfur',0,'S')],{'sulfur':3},source='ATHENA.SAM.SULFONIUM_PI',limits=['Constitutional sulfonium only; does not assert SAM identity or sulfonium-pi interaction.'])
for n in [5,6]:
 q='[#6,#7,#8,#16]~1'+('~[#6,#7,#8,#16]'*(n-1))+'1'
 roles={'ringAtoms':list(range(n)),**{'vertex'+str(i):[i] for i in range(n)}}
 nonarom=[('nonaromatic_vertex_'+str(i),'[*;!a]',{'vertex'+str(i):[0]}) for i in range(n)]
 limits=['Exact 5/6-vertex simple cycle whose every atom is aromatic under pinned OCL; no unique ring-basis, delocalized-bond, pi-energy or planarity claim. Overlapping fused cycles coexist.']
 add('AROMATIC.RING'+str(n),q,list(range(n)),roles,exclusions=nonarom,aromatic=True,source='ATHENA.GROUP.AROMATIC',limits=limits)
 exq='[#6]~1'+('~[#6]'*(n-1))+'1'
 add('HETEROAROMATIC.RING'+str(n),q,list(range(n)),roles,exclusions=nonarom+[('all_carbon_ring',exq,{'ringAtoms':list(range(n))})],aromatic=True,source='ATHENA.GROUP.HETEROAROMATIC',limits=limits+['At least one non-carbon cycle vertex; no pyridine/pyrrole role or tautomer inference.'])
Path('software/qualification/foundation-vocabulary-20261005/DEFINITIONS.json').write_text(json.dumps(records,indent=2)+'\n')
print(len(records),'definitions emitted')
