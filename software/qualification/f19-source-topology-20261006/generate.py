"""Render source topology definitions using the unchanged Athena group /2 interpreter."""
from pathlib import Path
import copy,hashlib,json
root=Path('software/modules/athena/src/main/resources/totah/lab/athena/system/rules')
template=json.loads((root/'groups-f07-v1/ATHENA.GROUP.NITROSO.CARBON_BOUND.rule.json').read_text())
sources=[Path('software/qualification/scientific-rule-knowledge-audit-20261004/reference/rdkit--Data__FunctionalGroups.txt'),Path('software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.ALKYL.json'),Path('software/qualification/foundation-closure-execution-20261006/REQUEST.txt')]
refs=[{'kind':'SOURCE','namespace':'athena.foundation','id':str(p),'version':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sources]
methyl=json.loads(json.loads((root/'groups-b01/ATHENA.GROUP.METHYL.rule.json').read_text())['parameters']['definition']['value'])
q=methyl['query'].split('-')[0]
specs=[
 ('CARBON_BRANCH_POINT.THREE_CARBON_NEIGHBORS','[C;!a;+0;H1](-[#6])(-[#6])-[#6]',[0],{'center':[0],'neighbor1':[1],'neighbor2':[2],'neighbor3':[3]}, {'center':1},{'center':3}),
 ('CARBON_BRANCH_POINT.FOUR_CARBON_NEIGHBORS','[C;!a;+0;H0](-[#6])(-[#6])(-[#6])-[#6]',[0],{'center':[0],'neighbor1':[1],'neighbor2':[2],'neighbor3':[3],'neighbor4':[4]}, {'center':0},{'center':4}),
 ('TERT_BUTYL.SOURCE_CONNECTIVITY','[*]-[C;!a;+0](-'+q+')(-'+q+')-'+q,[1,2,3,4],{'attachment':[0],'center':[1],'methyl1':[2],'methyl2':[3],'methyl3':[4]}, {'center':0,'methyl1':3,'methyl2':3,'methyl3':3},{'center':4,'methyl1':1,'methyl2':1,'methyl3':1}),
 ('CARBON_SINGLE_BOND_TRIANGLE','[C;!a;+0]1-[C;!a;+0]-[C;!a;+0]-1',[0,1,2],{'carbon1':[0],'carbon2':[1],'carbon3':[2]}, {},{})]
out=root/'groups-f19-v1';out.mkdir(exist_ok=True)
for name,query,members,roles,hs,degrees in specs:
 m=copy.deepcopy(template);d=json.loads(m['parameters']['definition']['value']);rid='ATHENA.GROUP.'+name
 d.update(groupId=rid,patternId=rid+'/pattern',query=query,memberQueryIndices=members,roles=roles,sourceReferences=refs,negativeCoverageVersion=rid+'/negative/1')
 d['requiredState'].update(hydrogenElements=['C'],hydrogenRoles=[{'role':r,'count':h} for r,h in hs.items()],roleHeavyDegree=degrees)
 d['supportedDomain']['unsupportedQueries']=[]
 d['limitations']=['Exact supplied neutral nonaromatic carbon topology; member/context boundaries are explicit. No maximal fragment, hydrophobicity, strain, reactivity, interaction or potency inference.', 'Triangle means three distinct carbon atoms joined by three SINGLE edges, including substituted/fused/spiro contexts. No ring-size or ring-system preference is inferred.','Complete authoritative C hydrogen, charge, topology, mapping and aromaticity state required. Source graph is never normalized.']
 if name=='CARBON_SINGLE_BOND_TRIANGLE':
  d['requiredState']['hydrogenElements']=[]
  d['limitations'][-1]='Complete source charge, topology, mapping and aromaticity required. H is preserved as supplied/unknown; no hydrogen-count or valence-validity proposition is made by a triangle.'
 m.update(ruleId=rid,limitations=d['limitations'],profile='ATHENA_GROUP_SOURCE_H_V4',implementationVersion='4');m['negativeCoverage']['version']=d['negativeCoverageVersion'];m['parameters']['definition']['value']=json.dumps(d,sort_keys=True,separators=(',',':'))
 m['scientificSources']=[{'locator':r['id'],'sha256':r['version'],'citation':'Pinned source topology precedent / reviewed graph-identity architecture / explicit closure authorization; not an interaction criterion.'} for r in refs]
 (out/(rid+'.rule.json')).write_text(json.dumps(m,indent=2)+'\n')
