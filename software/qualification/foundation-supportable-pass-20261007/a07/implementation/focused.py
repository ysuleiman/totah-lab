from pathlib import Path
import json,subprocess
repo=Path.cwd();q=repo/'software/qualification/foundation-supportable-pass-20261007/a07/implementation';base=Path('/private/tmp/i04-option-b-clean-20261008');b=json.loads((base/'BUILD.json').read_text());classes=Path('/private/tmp/a07-working-classes');classes.mkdir(exist_ok=True)
c=json.loads((q/'compile-command.json').read_text())
for folder in ['software/modules/athena/src/main/java/totah/lab/athena/system/rules','software/modules/daedalus/src/test/java/totah/lab/athena/system/rules','software/modules/daedalus/src/test/java/totah/lab/daedalus/system']:
 for p in (repo/folder).glob('AdvisoryAlert*.java'):
  if str(p) not in c:c.append(str(p))
(q/'compile-command.json').write_text(json.dumps(c,indent=2)+'\n')
with (q/'compile.log').open('w') as f:r=subprocess.run(c,stdout=f,stderr=subprocess.STDOUT)
print('compile',r.returncode,flush=True)
if r.returncode:raise SystemExit(r.returncode)
c=json.loads((base/'test-command.json').read_text());c=c[:c.index('--class-path')+2];c=[x.replace(b['source'],str(repo)).replace(b['classes'],str(classes)) for x in c];c+=['--select-class','totah.lab.athena.system.rules.AdvisoryAlertNativeAcceptanceTest','--select-class','totah.lab.daedalus.system.AdvisoryAlertAcceptanceTest','--select-class','totah.lab.daedalus.system.AdvisoryAlertCurrentPipelineTest','--details','summary','--reports-dir',str(q/'focused-junit')]
(q/'focused-command.json').write_text(json.dumps(c,indent=2)+'\n')
with (q/'focused.log').open('w') as f:r=subprocess.run(c,stdout=f,stderr=subprocess.STDOUT)
print('focused',r.returncode)
