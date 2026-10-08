import sys
sys.dont_write_bytecode = True
"""Review oracle only: acquire unchanged pinned cctbx header dependency closure."""
from pathlib import Path
import re,json,hashlib,urllib.request,concurrent.futures
root=Path(__file__).resolve().parent
repo=root.parents[3]
treefile=root.parent.parent/'foundation-v2-review-20261008/reference/cctbx-tree.json'
tree={i['path']:i for i in json.loads(treefile.read_text())['tree']}
commit='ed314689c2d2945d7fd136d5f66d33ce1688e7a7'
base=root/'upstream';base.mkdir(exist_ok=True)
pending={'mmtbx/validation/ramachandran/rama_eval.h','scitbx/math/dihedral.h'} | {p for p in tree if p.startswith('scitbx/source_generators/') and p.endswith('.py')};done={}
for generated in base.rglob('*.h'):
 for inc in re.findall(r'^\s*#\s*include\s*[<"]([^>"]+)',generated.read_text(),re.M):
  if inc in tree:pending.add(inc)
def fetch(p):
 dest=base/p;u=f'https://raw.githubusercontent.com/cctbx/cctbx_project/{commit}/{p}'
 if dest.exists():b=dest.read_bytes()
 else:
  with urllib.request.urlopen(u,timeout=60) as r:b=r.read()
 blob=hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest();assert blob==tree[p]['sha'],p
 dest.parent.mkdir(exist_ok=True,parents=True);dest.write_bytes(b)
 return p,b,{'path':p,'url':u,'sha256':hashlib.sha256(b).hexdigest(),'gitBlobSha1':blob,'bytes':len(b)}
while pending:
 with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool: rows=list(pool.map(fetch,sorted(pending)))
 pending=set()
 for p,b,m in rows:
  done[p]=m
  for delim,inc in re.findall(r'^\s*#\s*include\s*([<\"])([^>\"]+)',b.decode(),re.M):
   choices=[inc,str(Path(p).parent/inc)] if delim=='<' else [str(Path(p).parent/inc),inc]
   for c in choices:
    if c in tree:
     if c not in done:pending.add(c)
     break
 pending-=done.keys()
 print('headers',len(done),'remaining',len(pending),flush=True)
(root/'UPSTREAM_HEADERS.json').write_text(json.dumps({'commit':commit,'files':list(done.values())},indent=2)+'\n')
