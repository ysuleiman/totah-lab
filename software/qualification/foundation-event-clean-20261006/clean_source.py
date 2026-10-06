"""Requalify the committed foundation using its existing source/jar/test closure, empty outputs."""
from pathlib import Path
import hashlib, io, json, subprocess, tarfile, sys
repo=Path.cwd(); revision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
output=Path(sys.argv[1]).resolve();output.mkdir(exist_ok=False)
source=output/'source';source.mkdir();classes=output/'classes';classes.mkdir()
prior=json.loads((repo/'software/qualification/foundation-clean-source-20261006/attempt-3/BUILD.json').read_text())
tracked=subprocess.check_output(['git','ls-tree','-r','--name-only',revision],text=True).splitlines()
paths=set(prior['sourcePins'])
paths.update(p for p in tracked if p.startswith('software/modules/') and '/src/' in p)
for prefix in ['i02-directional-candidate-review-20261006','f18-terminal-alkyne-contract-20261006','f05-acyl-sulfonyl-chloride-review-20261006','f14-isocyanate-review-20261006','event-coverage-20261006']:
 paths.update(p for p in tracked if p.startswith('software/qualification/'+prefix+'/') and p.endswith(('.json','.txt')))
archive=subprocess.check_output(['git','archive',revision,'--',*sorted(paths)])
tarfile.open(fileobj=io.BytesIO(archive)).extractall(source,filter='data')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for p,digest in prior['jars'].items():assert sha(Path(p))==digest,p
oldroot=prior['source'];oldclasses=prior['classes']
def remap(command):return [arg.replace(oldroot,str(source)).replace(oldclasses,str(classes)) for arg in command]
command=remap(json.loads((repo/'software/qualification/foundation-clean-source-20261006/attempt-3/compile-command.json').read_text()))
for folder in ['totah/lab/daedalus/system','totah/lab/athena/system/rules/research']:
 for p in sorted((source/'software/modules/daedalus/src/test/java'/folder).glob('*.java')):
  if str(p) not in command:command.append(str(p))
(output/'compile-command.json').write_text(json.dumps(command,indent=2)+'\n')
(output/'BUILD.json').write_text(json.dumps({'commit':revision,'source':str(source),'classes':str(classes),'jars':prior['jars'],'sourcePins':{p:sha(source/p) for p in sorted(paths)}},indent=2)+'\n')
with (output/'compile.log').open('w') as f:r=subprocess.run(command,stdout=f,stderr=subprocess.STDOUT)
print('compile',r.returncode,flush=True)
if r.returncode:raise SystemExit(r.returncode)
command=remap(json.loads((repo/'software/qualification/foundation-clean-source-20261006/attempt-3/test-command.json').read_text()))
i=command.index('--reports-dir');command[i+1]=str(output/'junit')
(output/'test-command.json').write_text(json.dumps(command,indent=2)+'\n')
with (output/'tests.log').open('w') as f:r=subprocess.run(command,cwd=source,stdout=f,stderr=subprocess.STDOUT)
print('tests',r.returncode,flush=True)
if r.returncode:raise SystemExit(r.returncode)
# Reuse the independent Mnemosyne architecture check; never expose Athena classes to it.
base=source/'software/modules/mnemosyne';(base/'pom.xml').write_bytes(subprocess.check_output(['git','show',revision+':software/modules/mnemosyne/pom.xml']))
(base/'target/classes').mkdir(parents=True);(output/'mnemosyne-test').mkdir()
isolation=json.loads((repo/'software/qualification/foundation-clean-source-20261006/attempt-3/isolation-commands.json').read_text())
oldwork=str(Path(prior['source']).parent)
isolation=[[arg.replace(oldwork,str(output)).replace(str(repo/'software/qualification/foundation-clean-source-20261006/attempt-3'),str(output)) for arg in command] for command in isolation]
(output/'isolation-commands.json').write_text(json.dumps(isolation,indent=2)+'\n')
with (output/'isolation.log').open('w') as f:
 for command in isolation:subprocess.run(command,cwd=source,check=True,stdout=f,stderr=subprocess.STDOUT)
print('isolation PASS',flush=True)
