from pathlib import Path
import json,subprocess,sys,hashlib
repo=Path.cwd();q=repo/'software/qualification/foundation-batched-closure-20261008/implementation';base=Path('/private/tmp/a07-clean-20261008-attempt3');b=json.loads((base/'BUILD.json').read_text());classes=Path('/private/tmp/batch-c-working-classes');classes.mkdir(exist_ok=True)
def remap(cmd):return [x.replace(b['source'],str(repo)).replace(b['classes'],str(classes)) for x in cmd]
c=remap(json.loads((base/'compile-command.json').read_text()))
for folder in ['software/modules/athena/src/main/java/totah/lab/athena/system/rules','software/modules/daedalus/src/test/java/totah/lab/athena/system/rules','software/modules/daedalus/src/test/java/totah/lab/daedalus/system']:
 for p in sorted((repo/folder).glob('*.java')):
  if str(p) not in c:c.append(str(p))
(q/'compile-command.json').write_text(json.dumps(c,indent=2)+'\n')
tracked_source=[Path(x) for x in c if x.endswith('.java') and Path(x).is_file()]+list((repo/'software/modules/athena/src/main/resources/totah/lab/athena/system/rules').rglob('*.json'))
pins={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in tracked_source}
(q/'focused-source-pins.json').write_text(json.dumps(pins,indent=2)+'\n')
with (q/'compile.log').open('w') as f:r=subprocess.run(c,stdout=f,stderr=subprocess.STDOUT)
print('compile',r.returncode,flush=True)
if r.returncode:raise SystemExit(r.returncode)
c=remap(json.loads((base/'test-command.json').read_text()));c=c[:c.index('--class-path')+2]
selected=sys.argv[1:]
if not selected:raise SystemExit(0)
for name in selected:c+=['--select-class',name]
c+=['--details','summary','--reports-dir',str(q/'focused-junit')]
(q/'focused-command.json').write_text(json.dumps(c,indent=2)+'\n')
with (q/'focused.log').open('w') as f:r=subprocess.run(c,stdout=f,stderr=subprocess.STDOUT)
changed=[p for p,h in pins.items() if hashlib.sha256(Path(p).read_bytes()).hexdigest()!=h]
assert not changed,changed
print('focused',r.returncode,'stable source pins',len(pins),flush=True);raise SystemExit(r.returncode)
