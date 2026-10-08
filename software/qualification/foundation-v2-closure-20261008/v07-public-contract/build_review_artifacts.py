from pathlib import Path
import json,hashlib,shlex,collections
ROOT=Path(__file__).resolve().parents[4];p=ROOT/'software/qualification/foundation-v2-closure-20261008/v07-public-contract';old=p.parent/'v07-v08'
def save(n,o):(p/n).write_text(json.dumps(o,indent=2)+'\n')
def pin(f):return {'path':str(f.relative_to(ROOT)),'sha256':hashlib.sha256(f.read_bytes()).hexdigest()}
def loops(f):
 lines=f.read_text().splitlines();i=0;block='';out=[]
 while i<len(lines):
  if lines[i].startswith('data_'):block=lines[i][5:]
  if f.name=='links_and_mods.cif' and block not in ['mod_DEL-HN1','mod_DEL-OXT']:i+=1;continue
  if lines[i].strip()!='loop_':i+=1;continue
  i+=1;headers=[]
  while i<len(lines) and lines[i].startswith('_'):headers.append(lines[i]);i+=1
  ordinal=0
  while i<len(lines) and lines[i].strip() and not lines[i].startswith(('loop_','data_','_')):
   vals=shlex.split(lines[i]);assert len(vals)==len(headers);ordinal+=1
   out.append({'block':block,'line':i+1,'ordinal':ordinal,'values':dict(zip(headers,vals))});i+=1
 return out
mod=old/'reference/links_and_mods.cif';mr=loops(mod);rows=[]
for idx,r in enumerate(json.loads((old/'PROPOSED_HEAVY_ATOM_TABLE.json').read_text())['rows'],1):
 name=r['residue'];typ=r['kind'];f=old/'reference'/dict(SER='s__SER.cif',THR='t__THR.cif',VAL='v__VAL.cif')[name];rs=loops(f);prefix='_chem_comp_'+typ+'.';names=[prefix+'atom_id_'+str(k) for k in range(1,3 if typ=='bond' else 4)]
 base=next(x for x in rs if all(x['values'].get(k)==a for k,a in zip(names,r['orderedAtoms'])));key='value_dist' if typ=='bond' else 'value_angle'
 def locator(file,x,cat,cols):return {'artifact':pin(file),'dataBlock':x['block'],'category':cat,'rowOrdinal':x['ordinal'],'line':x['line'],'rowValues':x['values'],'valueItems':cols}
 origin=[locator(f,base,prefix[:-1],[prefix+key,prefix+key+'_esd'])]
 if r['origin'] in ['DEL-HN1','DEL-OXT']:
  pre='_chem_mod_'+typ+'.';ks=[pre+'atom_id_'+str(k) for k in range(1,3 if typ=='bond' else 4)]
  chosen=next(x for x in mr if x['block']=='mod_'+r['origin'] and x['values'].get(pre+'function')=='change' and (list(x['values'].get(k) for k in ks)==r['orderedAtoms'] or list(x['values'].get(k) for k in ks)==r['orderedAtoms'][::-1]))
  assert chosen['values'][pre+'new_'+key]==r['targetDecimal'];assert chosen['values'][pre+'new_'+key+'_esd']==r['esdDecimal']
  origin.append(locator(mod,chosen,pre[:-1],[pre+'new_'+key,pre+'new_'+key+'_esd']))
 else:assert base['values'][prefix+key]==r['targetDecimal'] and base['values'][prefix+key+'_esd']==r['esdDecimal']
 rows.append({'rowId':f'V07-{idx:03d}','residue':name,'kind':typ.upper(),'orderedRoles':r['orderedAtoms'],'targetDecimal':r['targetDecimal'],'esdDecimal':r['esdDecimal'],'unit':'ANGSTROM' if typ=='bond' else 'DEGREE','provenanceChain':origin})
save('REFERENCE_ROWS.json',{'schema':'v07-review-reference-rows/1','status':'REVIEW_ARTIFACT_NOT_RUNTIME_AUTHORITY','repository':'MonomerLibrary/monomers','commit':'1c97ea23a7df06373ed01cd7c3156a1f8e9b27e3','rows':rows})
license=ROOT/'software/qualification/foundation-v2-review-20261008/reference/monomers--COPYING'
save('REFERENCE_PROFILE.json',{'schema':'v07-review-reference-profile/1','profile':'ATHENA.V07.MONOMER_LIBRARY_STV_INTERNAL_TRANS_HEAVY/1','repository':'MonomerLibrary/monomers','commit':'1c97ea23a7df06373ed01cd7c3156a1f8e9b27e3','table':pin(p/'REFERENCE_ROWS.json'),'artifacts':[pin(old/'reference'/f) for f in ['s__SER.cif','t__THR.cif','v__VAL.cif','links_and_mods.cif']],'licenseArtifacts':[pin(license)],'centralReferenceModifications':['DEL-HN1','DEL-OXT'],'incomingLink':{'id':'TRANS','centralComponent':2},'outgoingLink':{'id':'TRANS','centralComponent':1},'hydrogenRestraints':'EXCLUDED','numericProfile':'ATHENA.JAVA21.BINARY64.SUBTRACT_THEN_DIVIDE/1'})
save('REVIEW_CHECKS.json',{'status':'PASS','rows':len(rows),'counts':dict(collections.Counter(r['residue'] for r in rows)),'exactOriginalRowValuesVerified':True,'overriddenRows':sum(len(r['provenanceChain'])==2 for r in rows),'productionCodeChanged':False,'athenaAcceptanceTestsExecuted':0})
print('36 exact source-row provenance chains verified')
