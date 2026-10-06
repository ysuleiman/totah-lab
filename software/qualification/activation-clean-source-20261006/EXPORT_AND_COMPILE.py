from pathlib import Path
import subprocess,tarfile,io,json,hashlib
repo=Path.cwd();q=repo/'software/qualification/activation-clean-source-20261006';q.mkdir(exist_ok=True);rev=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip();out=Path('/private/tmp/athena-r02-'+rev[:9]);out.mkdir(exist_ok=False);src=out/'source';src.mkdir();classes=out/'classes';classes.mkdir()
tracked=subprocess.check_output(['git','ls-tree','-r','--name-only',rev,'software/modules'],text=True).splitlines()
paths=[p for p in tracked if ('/src/main/java/' in p or '/src/test/java/' in p or '/src/main/resources/totah/lab/athena/system/rules/' in p or '/mnemosyne/src/test/resources/' in p) and not '/validation/' in p]
# use an explicit archive path list, only committed files
archive=subprocess.check_output(['git','archive',rev,'--',*paths]);tarfile.open(fileobj=io.BytesIO(archive)).extractall(src,filter='data')
old=(repo/'software/qualification/research-separation-v2-20261006/final-run/classpath.txt').read_text().split(':');jars=list(dict.fromkeys(p for p in old if p.endswith('.jar') and '/totah/' not in p and '/totah-lab/' not in p));jars += [str(p) for p in Path('/Users/yazan/.m2/repository/org/projectlombok/lombok').glob('*/lombok-*.jar')][-1:];jars=list(dict.fromkeys(jars));cp=':'.join(jars)
sourcepath=':'.join(str(p) for p in src.glob('software/modules/*/src/*/java'))
tests=[]
for p in (src/'software/modules/mnemosyne/src/test/java').rglob('*.java'):tests.append(str(p))
for p in (src/'software/modules/daedalus/src/test/java/totah/lab/athena/system/rules/research').glob('*.java'):tests.append(str(p))
for n in ['ResearchV2PipelineAcceptanceTest','ResearchGatePipelineAcceptanceTest','DirectAssessmentExecutionAcceptanceTest','SystemQualificationAcceptanceTest','RuleRegistryAcceptanceTest','VdwContactI16AcceptanceTest']:
 tests.append(str(src/'software/modules/daedalus/src/test/java/totah/lab/daedalus/system'/ (n+'.java')))
j='/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/'
cmd=[j+'javac','--release','21','-cp',cp,'-processorpath',':'.join(p for p in jars if 'lombok' in p),'-sourcepath',sourcepath,'-d',str(classes),*tests]
meta={'commit':rev,'export':str(src),'classes':str(classes),'jars':{p:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in jars},'exportedFiles':len(paths),'command':cmd};(q/'BUILD.json').write_text(json.dumps(meta,indent=2)+'\n');(q/'location.txt').write_text(str(out)+'\n')
with (q/'compile.log').open('w') as f:r=subprocess.run(cmd,stdout=f,stderr=subprocess.STDOUT)
print('compile',r.returncode,'export',out)
