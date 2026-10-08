import sys
sys.dont_write_bytecode = True
"""Characterize unchanged Athena vs pinned scitbx; no tolerance or pass claim."""
from pathlib import Path
import json,subprocess,hashlib
root=Path(__file__).resolve().parent
rows=json.loads((root/'FIXTURE_MANIFEST.json').read_text())['coordinateCases']
sites='\n'.join(' '.join(repr(float(x)) for p in r['sites'] for x in p) for r in rows)+'\n'
out=subprocess.run(['/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java','-cp','/private/tmp/athena-v09-v10-existing-geometry','ExistingGeometryProbe'],input=sites,text=True,capture_output=True,check=True).stdout.splitlines()
assert len(out)==len(rows)
for r,s in zip(rows,out):
 r['athenaAngle']=None if s=='UNDEFINED' else float(s)
 r['athenaAngleHex']=None if s=='UNDEFINED' else float(s).hex()
 r['sameResult']=r['angleHex']==r['athenaAngleHex']
 r['absoluteDelta']=abs(r['angle']-r['athenaAngle']) if r['angle'] is not None and r['athenaAngle'] is not None else None
repo=root.parents[3]
pins={str(p.relative_to(repo)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (repo/'software/modules/gaia/src/main/java/totah/lab/gaia/geometry').glob('*.java') if p.name in ['Dihedral.java','Point3D.java','Vector3D.java','Tuple3D.java']}
(root/'GEOMETRY_COMPARISON.json').write_text(json.dumps({'status':'CHARACTERIZATION_NOT_NUMERIC_EQUIVALENCE_QUALIFICATION','sourcePins':pins,'cases':rows},indent=2)+'\n')
print(json.dumps([{'probe':r['probe'],'cctbx':r['angle'],'athena':r['athenaAngle'],'same':r['sameResult']} for r in rows],indent=2))
