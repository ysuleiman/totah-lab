"""Fresh committed-source check of three added tests; verify full-suite source closure is unchanged."""
from pathlib import Path
import hashlib, io, json, subprocess, sys, tarfile
repo=Path.cwd();prior_dir=Path(sys.argv[1]).resolve();out=Path(sys.argv[2]).resolve();out.mkdir(exist_ok=False)
prior=json.loads((prior_dir/'BUILD.json').read_text());revision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
extra='software/modules/daedalus/src/test/java/totah/lab/daedalus/system/WaterBridgeMatrixClosureTest.java'
paths=set(prior['sourcePins'])|{extra};source=out/'source';source.mkdir();classes=out/'classes';classes.mkdir()
archive=subprocess.check_output(['git','archive',revision,'--',*sorted(paths)]);tarfile.open(fileobj=io.BytesIO(archive)).extractall(source,filter='data')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for p,h in prior['sourcePins'].items():assert sha(source/p)==h,p
for p,h in prior['jars'].items():assert sha(Path(p))==h,p
new_tracked=subprocess.check_output(['git','ls-tree','-r','--name-only',revision,'--','software/modules'],text=True).splitlines()
assert {p for p in new_tracked if '/src/' in p}-set(prior['sourcePins'])=={extra}
def remap(command):return [s.replace(prior['source'],str(source)).replace(prior['classes'],str(classes)) for s in command]
command=remap(json.loads((prior_dir/'compile-command.json').read_text()));command.append(str(source/extra))
(out/'compile-command.json').write_text(json.dumps(command,indent=2)+'\n')
(out/'BUILD.json').write_text(json.dumps({'commit':revision,'source':str(source),'classes':str(classes),'jars':prior['jars'],'sourcePins':{p:sha(source/p) for p in sorted(paths)},'fullSuiteCommit':prior['commit'],'unchangedFullSuiteSourceClosure':True,'onlyAdditionalSource':extra},indent=2)+'\n')
with (out/'compile.log').open('w') as f:subprocess.run(command,check=True,stdout=f,stderr=subprocess.STDOUT)
command=remap(json.loads((prior_dir/'test-command.json').read_text()));cp=command[command.index('--class-path')+1]
command=[command[0],'-Xmx512m','-jar',command[command.index('-jar')+1],'execute','--class-path',cp,'--select-class','totah.lab.daedalus.system.WaterBridgeMatrixClosureTest','--reports-dir',str(out/'junit'),'--details','summary']
(out/'test-command.json').write_text(json.dumps(command,indent=2)+'\n')
with (out/'tests.log').open('w') as f:subprocess.run(command,cwd=source,check=True,stdout=f,stderr=subprocess.STDOUT)
print((out/'tests.log').read_text(),flush=True)
