"""Compare evaluated Athena angles against the actual pinned upstream evaluators."""
from pathlib import Path
import json,hashlib,subprocess,importlib.util,struct
root=Path(__file__).resolve().parent;decision=root.parent/'v09-v10-decision-20261008';oracle=root.parent/'v09-v10-public-contract-20261008/oracle'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
exe=Path('/private/tmp/athena-v09-v10-oracle');runtime=json.loads((oracle/'RUNTIME.json').read_text())
assert sha(exe)==runtime['executableSha256']
for s in json.loads((decision/'SOURCES.json').read_text()):assert sha(decision/s['file'])==s['sha256']
spec=importlib.util.spec_from_file_location('pinned_ndim',decision/'reference/cctbx--mmtbx__rotamer__n_dim_table.py');ndim=importlib.util.module_from_spec(spec);spec.loader.exec_module(ndim)
reports=json.loads((root/'independent-replay-2/replay-1.json').read_text());rows=[]
classes=['GENERAL','GLYCINE','CIS_PROLINE','TRANS_PROLINE','PRE_PROLINE','ILE_VAL']
for name,p in reports.items():
 angles={t['name']:t['value'] for t in p['geometry']['torsions']}
 if name.startswith('rama'):
  c=classes.index(p['classSelection']['ramaClass']);line=subprocess.check_output([str(exe)],input=f"R {c} {angles['PHI']!r} {angles['PSI']!r}\n",text=True).split();q=float(line[0]);cat={0:'OUTLIER',1:'ALLOWED',2:'FAVORED'}[int(line[1])]
 else:
  residue=p['classSelection']['canonicalIdentity'].lower()
  with (decision/f'reference/reference_data--Top8000__Top8000_rotamer_pct_contour_grids__rota8000-{residue}.data').open() as f:t=ndim.NDimTable.createFromText(f)
  q=t.valueAt([angles['CHI1']]);cat='FAVORED' if q>=.02 else 'ALLOWED' if q>=.003 else 'OUTLIER'
 bits=struct.pack('>d',q).hex();assert bits==p['referenceScore']['binary64Hex'],(name,bits,p['referenceScore']);assert cat==p['category']
 rows.append({'case':name,'sameBinary64Angles':angles,'upstreamScoreBits':bits,'category':cat})
(root/'SAME_ANGLE_ORACLE.json').write_text(json.dumps({'status':'PASS','geometryClaim':'Athena DIHEDRAL unchanged; exact upstream score/category at same binary64 angles only','cases':rows},indent=2)+'\n');print('PASS',len(rows))
