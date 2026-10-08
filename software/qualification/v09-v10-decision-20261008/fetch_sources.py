"""Acquire immutable primary artifacts for review only; verify Git blob identities."""
from pathlib import Path
import json,hashlib,urllib.request,concurrent.futures
folder=Path(__file__).resolve().parent;old=folder.parent/'foundation-v2-review-20261008';out=folder/'reference';out.mkdir(exist_ok=True)
repos={'reference_data':('rlabduke/reference_data','5ee6875fc29eccc3c9dc7cbe705ff9fd1b505d7d'),'cctbx':('cctbx/cctbx_project','ed314689c2d2945d7fd136d5f66d33ce1688e7a7')}
paths={'reference_data':['LICENSE','README.md','Top8000/README.md','Top8000/Top8000_ramachandran_pct_contour_grids/README']+['Top8000/Top8000_ramachandran_pct_contour_grids/rama8000-'+x+'.data' for x in ['general-noGPIVpreP','gly-sym','cispro','transpro','prepro-noGP','ileval-nopreP']]+['Top8000/Top8000_rotamer_pct_contour_grids/rota8000-'+x+'.data' for x in ['ser','thr','val']],'cctbx':['scitbx/math/linear_interpolation.h','scitbx/math/dihedral.h','cctbx/geometry_restraints/dihedral.h','LICENSE.txt','mmtbx/rotamer/__init__.py','mmtbx/rotamer/n_dim_table.py','mmtbx/rotamer/ramachandran_eval.py','mmtbx/rotamer/ramachandran_eval_deprecated.py','mmtbx/rotamer/rotamer_eval.py','mmtbx/rotamer/sidechain_angles.py','mmtbx/rotamer/sidechain_angles.props','mmtbx/rotamer/rotamer_names.props','mmtbx/validation/ramalyze.py','mmtbx/validation/rotalyze.py','mmtbx/validation/ramachandran/rama_eval.h','mmtbx/validation/ramachandran/rama8000_tables.h','mmtbx/validation/ramachandran/convert_from_text.py','mmtbx/conformation_dependent_library/utils.py']}
oldSources=json.loads((old/'SOURCES.json').read_text());byUrl={s['url']:old/'reference'/s['file'] for s in oldSources if 'sha256' in s}
trees={k:{x['path']:x for x in json.loads((old/'reference'/(k+'-tree.json')).read_text())['tree']} for k in repos}
def get(job):
 k,path=job;repo,commit=repos[k];url=f'https://raw.githubusercontent.com/{repo}/{commit}/{path}';target=out/(k+'--'+path.replace('/','__'))
 if target.exists():b=target.read_bytes();mode='existing immutable review artifact'
 elif url in byUrl:b=byUrl[url].read_bytes();mode='existing pinned local source'
 else:
  with urllib.request.urlopen(url,timeout=60) as r:b=r.read()
  mode='immutable upstream retrieval'
 blob=hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest();assert blob==trees[k][path]['sha'],(path,blob)
 target.write_bytes(b)
 return {'repository':repo,'commit':commit,'path':path,'file':str(target.relative_to(folder)),'url':url,'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest(),'gitBlobSha1':blob,'retrieved':'2026-10-08','mode':mode,'status':'REVIEW_ONLY_NOT_ADMITTED'}
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:results=list(pool.map(get,[(k,p) for k,ps in paths.items() for p in ps]))
(folder/'SOURCES.json').write_text(json.dumps(results,indent=2)+'\n');print(json.dumps({'artifacts':len(results),'bytes':sum(s['bytes'] for s in results),'allGitBlobsVerified':True}))
