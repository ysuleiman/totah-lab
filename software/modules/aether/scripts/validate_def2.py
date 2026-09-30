#!/usr/bin/env python3
"""Reproduce the M13 gates in order; no thresholds, basis data or SCF policy are altered."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

ROOT=Path(__file__).resolve().parents[1]
PHASES={
    'integrals':'AetherDef2IntegralTest,AetherDef2AngularTest',
    'energies':'AetherDef2EnergyTest',
    'counterpoise':'AetherDef2CounterpoiseTest',
    'grid':'AetherDef2GridTest',
    'replay':'AetherDef2ReplayTest',
    'profiles':'AetherDef2ProfileTest',
    'regressions':'Aether*Test,!AetherDef2*Test',
}
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--java-home',default=os.environ.get('JAVA_HOME'))
p.add_argument('--offline',action='store_true')
p.add_argument('--heap',default='4g')
p.add_argument('--phase',action='append',choices=PHASES)
p.add_argument('--reference-python',help='Optional PySCF 2.10.0 interpreter for fresh independent references')
a=p.parse_args();env=dict(os.environ)
if a.java_home:env['JAVA_HOME']=a.java_home
java=str(Path(a.java_home)/'bin/java') if a.java_home else 'java'
version=subprocess.run([java,'-version'],capture_output=True,text=True,check=True).stderr
if 'version "21.' not in version:raise SystemExit('Java 21 is required')
out=ROOT/'validation/milestone-13';out.mkdir(parents=True,exist_ok=True)
if a.reference_python:
    for script in ['generate_def2_basis.py','generate_def2_boys_reference.py','generate_def2_integral_reference.py','generate_def2_energy_reference.py','generate_def2_counterpoise_reference.py','generate_def2_grid_reference.py','generate_def2_reference_manifest.py']:
        with (out/(script+'.log')).open('w') as log:
            subprocess.run([a.reference_python,'-B',str(ROOT/'scripts'/script)],stdout=log,stderr=subprocess.STDOUT,check=True,env=env)
results={}
for phase in a.phase or PHASES:
    command=['mvn']+(['-o'] if a.offline else [])+['-f',str(ROOT.parent/'pom.xml'),'-pl','aether','-am','test',
        '-Dtest='+PHASES[phase],'-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx'+a.heap,'-DreuseForks=false']
    started=time.monotonic()
    with (out/(phase+'.log')).open('w') as log:
        run=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,env=env)
    results[phase]={'exit_code':run.returncode,'elapsed_seconds':time.monotonic()-started,'command':command}
    (out/'validation-run.json').write_text(json.dumps({'status':'SCREENING_ONLY','java':version,'phases':results},indent=2)+'\n')
    if run.returncode:raise SystemExit(f'{phase} failed; see {out / (phase+".log")}')
for source in (ROOT/'target/def2-validation').glob('*'):
    if source.is_file():shutil.copy2(source,out/source.name)
manifest={str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for folder in ['src/main','src/test','scripts'] for f in (ROOT/folder).rglob('*') if f.is_file() and '__pycache__' not in f.parts}
(out/'source-and-reference-hashes.json').write_text(json.dumps(manifest,indent=2,sort_keys=True)+'\n')
