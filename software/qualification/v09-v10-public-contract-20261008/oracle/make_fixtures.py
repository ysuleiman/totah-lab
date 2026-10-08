import sys
sys.dont_write_bytecode = True
"""Independent review fixtures: unchanged upstream C++ and Python evaluator code.
No Athena scoring implementation is used. Synthetic angles/sites are numerical
probes, not observed structures or source-chemistry authorities.
"""
from pathlib import Path
import json,hashlib,subprocess,math,struct,gzip,io,importlib.util,ast,types,sys,platform
root=Path(__file__).resolve().parent; decision=root.parent.parent/'v09-v10-decision-20261008';ref=decision/'reference'
for p in json.loads((decision/'SOURCES.json').read_text()):
 assert hashlib.sha256((decision/p['file']).read_bytes()).hexdigest()==p['sha256']
for p in json.loads((root/'UPSTREAM_HEADERS.json').read_text())['files']:
 assert hashlib.sha256((root/'upstream'/p['path']).read_bytes()).hexdigest()==p['sha256']
spec=importlib.util.spec_from_file_location('pinned_ndim',ref/'cctbx--mmtbx__rotamer__n_dim_table.py');ndim=importlib.util.module_from_spec(spec);spec.loader.exec_module(ndim)
# Compile exact upstream function AST, not a locally rewritten category function.
src=(ref/'cctbx--mmtbx__validation__rotalyze.py').read_text();tree=ast.parse(src)
method=next(m for c in tree.body if isinstance(c,ast.ClassDef) and c.name=='rotalyze' for m in c.body if isinstance(m,ast.FunctionDef) and m.name=='evaluateScore')
constants={n.targets[0].id:ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and len(n.targets)==1 and isinstance(n.targets[0],ast.Name) and n.targets[0].id in ['ALLOWED_THRESHOLD','OUTLIER_THRESHOLD']};assert constants=={'ALLOWED_THRESHOLD':0.02,'OUTLIER_THRESHOLD':0.003}
ns={'ALLOWED_THRESHOLD':constants['ALLOWED_THRESHOLD']};exec(compile(ast.Module(body=[method],type_ignores=[]),str(ref/'cctbx--mmtbx__validation__rotalyze.py'),'exec'),ns)
obj=types.SimpleNamespace(n_favored=0,n_allowed=0,n_outliers=0,n_favored_by_model={'':0},n_allowed_by_model={'':0},n_outliers_by_model={'':0},outlier_threshold=0.003)
def cat(q):return ns['evaluateScore'](obj,q).upper()
rows=[];requests=[]
def request(row,command):rows.append(row);requests.append(command)
for c in range(6):
 for phi in range(-179,180,2):
  for psi in range(-179,180,2):request({'kind':'rama','class':c,'phi':phi,'psi':psi,'probe':'all_centers'},f'R {c} {phi} {psi}')
 # fractional, midpoint and periodic boundary probes; no pseudo-random fixture dependency.
 for phi,psi in [(-60,-40),(-60.25,-40.75),(0,0),(179.25,-179.75),(-180,180),(180,-180),(-180,-180),(180,180),(math.nextafter(180,0),0),(math.nextafter(-180,0),0),(179,179),(-179,-179),(-90.125,90.375)]:
  request({'kind':'rama','class':c,'phi':phi,'psi':psi,'probe':'interpolation_seam'},f'R {c} {phi!r} {psi!r}')
 for threshold in [0.02,0.0005 if c==0 else 0.002 if c==2 else 0.001]:
  for q in [math.nextafter(threshold,0),threshold,math.nextafter(threshold,math.inf)]:request({'kind':'rama_category','class':c,'q':q,'qHex':q.hex()},f'C {c} {q!r}')
coords=[('zero',[[1,0,0],[0,0,0],[0,1,0],[1,1,0]]),('plus90',[[1,0,0],[0,0,0],[0,1,0],[0,1,-1]]),('minus90',[[1,0,0],[0,0,0],[0,1,0],[0,1,1]]),('trans',[[1,0,0],[0,0,0],[0,1,0],[-1,1,0]]),('oblique',[[1.2,0.3,-0.1],[0,0,0],[0.2,1.1,0.4],[-0.7,1.4,1.8]]),('collinear',[[0,0,0],[0,1,0],[0,2,0],[1,2,0]]),('coincident',[[1,0,0],[0,0,0],[0,0,0],[0,1,0]]),('small_perpendicular',[[1e-11,0,0],[0,0,0],[0,1,0],[0,1,1]]),('near_zero',[[1,0,0],[0,0,0],[0,1,0],[1,1,1e-9]])]
for name,xyz in coords:request({'kind':'dihedral','probe':name,'sites':xyz},'D '+' '.join(repr(float(x)) for p in xyz for x in p))
out=subprocess.run(['/private/tmp/athena-v09-v10-oracle'],input='\n'.join(requests)+'\n',text=True,capture_output=True,check=True).stdout.splitlines();assert len(out)==len(rows)
for row,s in zip(rows,out):
 if row['kind']=='rama':q,c=s.split();row.update(q=float(q),qHex=float(q).hex(),category=['OUTLIER','ALLOWED','FAVORED'][int(c)])
 elif row['kind']=='rama_category':row['category']=['OUTLIER','ALLOWED','FAVORED'][int(s)]
 else:row.update(angle=None if s=='UNDEFINED' else float(s),angleHex=None if s=='UNDEFINED' else float(s).hex())
for residue in ['ser','thr','val']:
 with open(ref/f'reference_data--Top8000__Top8000_rotamer_pct_contour_grids__rota8000-{residue}.data') as f:table=ndim.NDimTable.createFromText(f)
 angles=sorted(set([i+0.5 for i in range(-180,180)]+[float(i) for i in range(-180,181)]+[-179.75,-0.25,0.25,179.75,360.0,math.nextafter(-180,0),math.nextafter(180,0)]))
 for a in angles:
  q=table.valueAt([a]);rows.append({'kind':'rotamer','residue':residue.upper(),'chi':a,'q':q,'qHex':q.hex(),'category':cat(q),'probe':'outside_signed_domain_characterization' if a==360 else 'center_midpoint_seam'})
 for t in [.003,.02]:
  for q in [math.nextafter(t,0),t,math.nextafter(t,math.inf)]:rows.append({'kind':'rotamer_category','residue':residue.upper(),'q':q,'qHex':q.hex(),'category':cat(q)})
raw=b''.join((json.dumps(r,sort_keys=True,separators=(',',':'),allow_nan=False)+'\n').encode() for r in rows)
buf=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buf,mtime=0) as f:f.write(raw)
(root/'UPSTREAM_FIXTURES.jsonl.gz').write_bytes(buf.getvalue())
summary={'status':'UPSTREAM_ORACLE_ONLY_NOT_ATHENA_QUALIFICATION','records':len(rows),'counts':{k:sum(r['kind']==k for r in rows) for k in sorted({r['kind'] for r in rows})},'uncompressedSha256':hashlib.sha256(raw).hexdigest(),'gzipSha256':hashlib.sha256(buf.getvalue()).hexdigest(),'python':sys.version,'platform':platform.platform(),'rotamerFunctionLines':[method.lineno,method.end_lineno],'cctbxEvaluatorCommit':'ed314689c2d2945d7fd136d5f66d33ce1688e7a7','dataCommit':'5ee6875fc29eccc3c9dc7cbe705ff9fd1b505d7d','driver':'unchanged pinned C++ rama_eval.h and scitbx/math/dihedral.h; pinned n_dim_table.py with real native flex.float from recorded runtime; exact upstream evaluateScore AST','noMockedArrays':True,'coordinateCases': [r for r in rows if r['kind']=='dihedral']}
(root/'FIXTURE_MANIFEST.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps({k:v for k,v in summary.items() if k in ['records','counts','gzipSha256']}))
