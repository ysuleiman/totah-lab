"""Committed-source foundation qualification; never reads project build outputs."""
from pathlib import Path
import hashlib, io, json, subprocess, tarfile, sys
repo=Path.cwd(); q=repo/'software/qualification/foundation-clean-source-20261006'/('attempt-'+sys.argv[1] if len(sys.argv)>1 else 'attempt-1'); q.mkdir(exist_ok=False)
rev=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
work=Path('/private/tmp/athena-foundation-'+rev[:9]+'-'+q.name);work.mkdir(exist_ok=False)
src=work/'source';src.mkdir();classes=work/'classes';classes.mkdir()
tracked=subprocess.check_output(['git','ls-tree','-r','--name-only',rev],text=True).splitlines()
paths=[p for p in tracked if p.startswith('software/modules/') and '/src/' in p]
# Contracts, fixtures and golden replay inputs only; no prior compiled outputs.
paths += [p for p in tracked if p.startswith('software/qualification/') and
          (p.endswith('/DESIGN.txt') or p.endswith('/REVIEW_GATE.txt') or p.endswith('/REVIEWED_DOSSIER.json'))]
helpers=['software/qualification/b06-connectivity-coverage-characterization-20261005/ConnectivityCoverageCharacterizationTest.java','software/qualification/p06-centroid-characterization-20261005/CentroidCharacterizationTest.java']
paths+=helpers
paths+=[p for p in tracked if p.startswith('software/qualification/first-real-rule-package-20261006/') and p.endswith(('.json','.txt'))]
paths=list(dict.fromkeys(paths))
archive=subprocess.check_output(['git','archive',rev,'--',*paths])
tarfile.open(fileobj=io.BytesIO(archive)).extractall(src,filter='data')
prior=json.loads((repo/'software/qualification/activation-clean-source-20261006/BUILD.json').read_text())
jars=list(prior['jars']); cp=':'.join(jars)
sourcepath=':'.join(str(p) for p in src.glob('software/modules/*/src/*/java'))
tests=list((src/'software/modules/daedalus/src/test/java/totah/lab/daedalus/system').glob('*.java'))
tests+=list((src/'software/modules/daedalus/src/test/java/totah/lab/athena/system/rules/research').glob('*.java'))
tests+=list((src/'software/modules/mnemosyne/src/test/java').rglob('*.java'))
for name in ['OclCorrectedOccurrenceTest','OclExplicitRadicalStateTest','OclRadicalRepresentationBoundaryTest','OclMappingBoundaryAuditTest']:
 tests+=list(src.glob('software/modules/athena-openchemlib/src/test/java/**/'+name+'.java'))
for name in ['Plane3DCentroidTest','Plane3DTest','PiStackingDetectorTest','PiCationDetectorTest','ChargeGroupSumTest']:
 tests+=list(src.glob('software/modules/*/src/test/java/**/'+name+'.java'))
tests += [src/p for p in helpers]
tests += list(src.glob('software/modules/hermes/src/main/java/totah/lab/hermes/file/pdbqt/**/*.java'))
models=[p for m in ['gaia','euclid'] for p in (src/f'software/modules/{m}/src/main/java').rglob('*.java')]
j='/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/'
cmd=[j+'javac','--release','21','-cp',cp,'-processorpath','/Users/yazan/.m2/repository/org/projectlombok/lombok/1.18.46/lombok-1.18.46.jar','-sourcepath',sourcepath,'-d',str(classes),*map(str,tests+models)]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
meta={'commit':rev,'source':str(src),'classes':str(classes),'jars':{p:sha(Path(p)) for p in jars},'sourcePins':{p:sha(src/p) for p in paths},'testSources':[str(p.relative_to(src)) for p in tests]}
(q/'BUILD.json').write_text(json.dumps(meta,indent=2)+'\n');(q/'compile-command.json').write_text(json.dumps(cmd,indent=2)+'\n')
with (q/'compile.log').open('w') as f:r=subprocess.run(cmd,stdout=f,stderr=subprocess.STDOUT)
print('compile',r.returncode,flush=True)
if r.returncode:raise SystemExit(r.returncode)
resources=[str(p) for p in src.glob('software/modules/*/src/*/resources')]
runtime=':'.join([str(classes),*resources,*jars]);(q/'classpath.txt').write_text(runtime)
cmd=[j+'java','-Xmx512m','-Dbasedir='+str(src/'software/modules/mnemosyne'),'-Dathena.reviewPackage='+str(src/'software/qualification/first-real-rule-package-20261006'),'-jar','/Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar','execute','--class-path',runtime]
for package in ['totah.lab.daedalus.system','totah.lab.athena.system.rules.research','totah.lab.mnemosyne']:
 cmd+=['--select-package',package]
for p in tests:
 if '/src/main/' in str(p) or '/daedalus/' in str(p) or '/mnemosyne/' in str(p) or '/qualification/' in str(p):continue
 text=p.read_text();package=text.split('package ',1)[1].split(';',1)[0];cmd+=['--select-class',package+'.'+p.stem]
cmd+=['--exclude-classname','.*FoundationArchitectureTest','--details','summary','--disable-ansi-colors','--reports-dir',str(q/'junit')]
(q/'test-command.json').write_text(json.dumps(cmd,indent=2)+'\n')
with (q/'tests.log').open('w') as f:r=subprocess.run(cmd,cwd=src,stdout=f,stderr=subprocess.STDOUT)
print('tests',r.returncode,flush=True)
