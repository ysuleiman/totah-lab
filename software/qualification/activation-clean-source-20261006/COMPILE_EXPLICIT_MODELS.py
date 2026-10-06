from pathlib import Path
import json,subprocess
q=Path('software/qualification/activation-clean-source-20261006');d=json.loads((q/'BUILD.json').read_text());cmd=d['command'];src=Path(d['export']);old=Path(d['classes']);new=old.parent/'classes-2';new.mkdir();cmd[cmd.index('-d')+1]=str(new)
cmd[cmd.index('-processorpath')+1]='/Users/yazan/.m2/repository/org/projectlombok/lombok/1.18.46/lombok-1.18.46.jar'
cmd += [str(p) for m in ['gaia','euclid'] for p in (src/f'software/modules/{m}/src/main/java').rglob('*.java')]
# Explicit source submission ensures Lombok processes authoritative model classes.
(q/'compile-2-command.json').write_text(json.dumps(cmd,indent=2)+'\n')
with (q/'compile-2.log').open('w') as f:r=subprocess.run(cmd,stdout=f,stderr=subprocess.STDOUT)
print('compile',r.returncode)
