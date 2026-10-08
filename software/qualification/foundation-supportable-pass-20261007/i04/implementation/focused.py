from pathlib import Path
import json,subprocess
repo=Path.cwd();q=repo/'software/qualification/foundation-supportable-pass-20261007/i04/implementation';base=Path('/private/tmp/g03-clean-c76db2b8b');classes=Path('/private/tmp/i04-working-classes');classes.mkdir(exist_ok=True)
build=json.loads((base/'BUILD.json').read_text());command=json.loads((base/'compile-command.json').read_text());command=[s.replace(build['source'],str(repo)).replace(build['classes'],str(classes)) for s in command]
for parent in ['software/modules/athena/src/main/java/totah/lab/athena/system/rules','software/modules/daedalus/src/test/java/totah/lab/daedalus/system','software/modules/daedalus/src/test/java/totah/lab/athena/system/rules']:
 for p in (repo/parent).glob('GlycineHCarbonyl*.java'):
  if str(p) not in command:command.append(str(p))
(q/'compile-command.json').write_text(json.dumps(command,indent=2)+'\n')
with (q/'compile.log').open('w') as f:r=subprocess.run(command,stdout=f,stderr=subprocess.STDOUT)
print('compile',r.returncode,flush=True)
if r.returncode:raise SystemExit(r.returncode)
old=json.loads((base/'test-command.json').read_text());command=old[:old.index("--class-path")+2];command=[s.replace(build['source'],str(repo)).replace(build['classes'],str(classes)) for s in command]
for name in ['GlycineHCarbonylAcceptanceTest','GlycineHCarbonylCurrentPipelineTest']:command.extend(['--select-class','totah.lab.daedalus.system.'+name])
command.extend(['--select-class','totah.lab.athena.system.rules.GlycineHCarbonylPredicateTest']);command.extend(['--details','summary','--reports-dir',str(q/'focused-junit')]);(q/'focused-command.json').write_text(json.dumps(command,indent=2)+'\n')
with (q/'focused.log').open('w') as f:r=subprocess.run(command,stdout=f,stderr=subprocess.STDOUT)
print('focused',r.returncode,flush=True)
