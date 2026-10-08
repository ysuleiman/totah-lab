from pathlib import Path
import json,hashlib,shlex
p=Path('software/qualification/foundation-v2-closure-20261008/v07-v08');ref=p/'reference';sha=lambda b:hashlib.sha256(b).hexdigest()
tree=json.loads(Path('software/qualification/foundation-v2-review-20261008/reference/monomers-tree.json').read_text()); lookup={x['path']:x['sha'] for x in tree['tree']}
pins=json.loads((p/'SOURCE_PINS.json').read_text())
for row in pins['files']:
 b=Path(row['path']).read_bytes();name=row['url'].split(pins['commit']+'/')[1]; h=hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest();assert h==lookup[name];row['gitBlobSha1']=h
(p/'SOURCE_PINS.json').write_text(json.dumps(pins,indent=2)+'\n')
def block(txt,key):return txt.split('data_'+key+'\n',1)[1].split('\ndata_',1)[0]
a=(ref/'links_and_mods.cif').read_text();b=(ref/'list__mon_lib_list.cif').read_text();checks={}
for k in ['link_TRANS','mod_DEL-OXT','mod_DEL-HN1']:
 x=block(a,k);y=block(b,k);checks[k]={'tokenIdentical':shlex.split(x)==shlex.split(y),'sha256':sha(x.encode())};assert checks[k]['tokenIdentical']
(p/'SOURCE_CHECKS.json').write_text(json.dumps({'status':'PASS','allDownloadsMatchPinnedTree':True,'duplicateBlockComparison':checks,'athenaTestsExecuted':0,'scientificQualification':False},indent=2)+'\n')
def loops(txt):
 lines=txt.splitlines(); out=[];i=0
 while i<len(lines):
  if lines[i].strip()!='loop_':i+=1;continue
  i+=1;head=[]
  while i<len(lines) and lines[i].startswith('_'):head.append(lines[i]);i+=1
  rows=[]
  while i<len(lines) and lines[i].strip() and not lines[i].startswith(('loop_','data_','_')):
   vals=shlex.split(lines[i]);assert len(vals)==len(head);rows.append(dict(zip(head,vals)));i+=1
  out.extend(rows)
 return out
allrows=[]
for name,fn in [('SER','s__SER.cif'),('THR','t__THR.cif'),('VAL','v__VAL.cif')]:
 rs=loops((ref/fn).read_text());atoms={r['_chem_comp_atom.atom_id']:r['_chem_comp_atom.type_symbol'] for r in rs if '_chem_comp_atom.atom_id' in r}
 for r in rs:
  typ='bond' if '_chem_comp_bond.comp_id' in r else 'angle' if '_chem_comp_angle.comp_id' in r else None
  if not typ:continue
  prefix='_chem_comp_'+typ+'.';ids=[r[prefix+'atom_id_'+str(i)] for i in range(1,3 if typ=='bond' else 4)]
  if any(atoms[a]=='H' or a=='OXT' for a in ids):continue
  key='value_dist' if typ=='bond' else 'value_angle';target=r[prefix+key];esd=r[prefix+key+'_esd'];origin=fn
  if typ=='bond' and set(ids)=={'N','CA'}:target,esd,origin='1.453','0.010','DEL-HN1'
  if typ=='bond' and set(ids)=={'CA','C'}:target,esd,origin='1.526','0.010','DEL-OXT'
  if typ=='bond' and set(ids)=={'C','O'}:target,esd,origin='1.229','0.012','DEL-OXT'
  if typ=='angle' and ids==['CA','C','O']:target,esd,origin='120.614','1.50','DEL-OXT'
  allrows.append({'residue':name,'kind':typ,'orderedAtoms':ids,'targetDecimal':target,'esdDecimal':esd,'origin':origin})
(p/'PROPOSED_HEAVY_ATOM_TABLE.json').write_text(json.dumps({'status':'PROPOSED_NOT_APPROVED_OR_QUALIFIED','scope':'central intramonomer heavy-atom bond/angle rows after exact DEL-HN1 and DEL-OXT reference modifications; no source graph mutation','rows':allrows},indent=2)+'\n')
print('proposed rows',len(allrows))
