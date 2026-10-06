from pathlib import Path
import subprocess,json,shutil
q=Path('software/qualification/activation-clean-source-20261006').resolve();d=json.loads((q/'BUILD.json').read_text());src=Path(d['export']);base=src/'software/modules/mnemosyne';p='software/modules/mnemosyne/pom.xml';(src/p).write_bytes(subprocess.check_output(['git','show',d['commit']+':'+p]));classes=base/'target/classes';classes.mkdir(parents=True);testout=src.parent/'mnemosyne-test';testout.mkdir();j='/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/';junit='/Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar';cp=':'.join(p for p in d['jars'] if 'jackson' in p)
commands=[[j+'javac','--release','21','-cp',cp,'-d',str(classes),*[str(p) for p in (base/'src/main/java').rglob('*.java')]],[j+'javac','--release','21','-cp',str(classes)+':'+cp+':'+junit,'-d',str(testout),str(base/'src/test/java/totah/lab/mnemosyne/FoundationArchitectureTest.java')],[j+'java','-Dbasedir='+str(base),'-jar',junit,'execute','--class-path',str(classes)+':'+str(testout)+':'+cp,'--select-class','totah.lab.mnemosyne.FoundationArchitectureTest','--details','summary','--disable-ansi-colors','--reports-dir',str(q/'isolation-junit')]]
(q/'isolation-commands.json').write_text(json.dumps(commands,indent=2)+'\n')
with (q/'isolation.log').open('w') as f:
 for c in commands:subprocess.run(c,check=True,stdout=f,stderr=subprocess.STDOUT)
print('isolation passed')
