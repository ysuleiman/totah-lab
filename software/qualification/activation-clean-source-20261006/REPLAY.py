from pathlib import Path
import subprocess,json
q=Path('software/qualification/activation-clean-source-20261006').resolve();d=json.loads((q/'BUILD.json').read_text());src=Path(d['export']);cp=(q/'clean-classpath.txt').read_text();j='/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java'
for n in ['one','two']:
 subprocess.run([j,'-Xmx512m','-cp',cp,'totah.lab.daedalus.system.ResearchV2PipelineAcceptanceTest',str(q/n),str(q/(n+'.json'))],cwd=src,check=True)
assert (q/'one.json').read_bytes()==(q/'two.json').read_bytes()
subprocess.run(['diff','-qr',str(q/'one'),str(q/'two')],check=True,stdout=(q/'replay.diff').open('w'))
for name,cls,golden in [('legacy-current','ResearchGatePipelineAcceptanceTest','research-gate-20261005/final-run/gate-one.json'),('legacy-direct','DirectAssessmentExecutionAcceptanceTest','direct-assessment-current-20261005/final-run/direct-one.json')]:
 subprocess.run([j,'-Xmx512m','-cp',cp,'totah.lab.daedalus.system.'+cls,str(q/name),str(q/(name+'.json'))],cwd=src,check=True)
 raw=subprocess.check_output(['git','show',d['commit']+':software/qualification/'+golden]);assert (q/(name+'.json')).read_bytes()==raw
(q/'REPLAY.txt').write_text('PASS: fresh committed-source independent JVM catalogs identical; /1 current/direct golden summaries identical. No workspace classes loaded.\n')
print('replay passed')
