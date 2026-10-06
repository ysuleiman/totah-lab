from pathlib import Path
import json,subprocess
q=Path('software/qualification/activation-clean-source-20261006').resolve();d=json.loads((q/'BUILD.json').read_text());src=Path(d['export']);classes=src.parent/'classes-3';j='/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java';junit='/Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar'
resources=[str(p) for p in src.glob('software/modules/*/src/main/resources')]+[str(src/'software/modules/mnemosyne/src/test/resources')]
cp=':'.join([str(classes),*resources,*d['jars']]);(q/'clean-classpath.txt').write_text(cp)
select=['--select-class','totah.lab.athena.system.rules.research.'+c] if False else []
for c in ['ResearchGateAcceptanceTest','ResearchSeparationCharacterizationTest','ResearchV2AcceptanceTest']:select+=['--select-class','totah.lab.athena.system.rules.research.'+c]
for c in ['ResearchV2PipelineAcceptanceTest','ResearchGatePipelineAcceptanceTest','DirectAssessmentExecutionAcceptanceTest','SystemQualificationAcceptanceTest','RuleRegistryAcceptanceTest','VdwContactI16AcceptanceTest']:select+=['--select-class','totah.lab.daedalus.system.'+c]
select+=['--select-package','totah.lab.mnemosyne','--exclude-classname','.*FoundationArchitectureTest']
cmd=[j,'-Xmx512m','-Dbasedir='+str(src/'software/modules/mnemosyne'),'-jar',junit,'execute','--class-path',cp,*select,'--details','summary','--disable-ansi-colors','--reports-dir',str(q/'junit-2')];(q/'test-command.json').write_text(json.dumps(cmd,indent=2)+'\n')
with (q/'tests-2.log').open('w') as f:r=subprocess.run(cmd,cwd=src,stdout=f,stderr=subprocess.STDOUT)
print('tests',r.returncode)
