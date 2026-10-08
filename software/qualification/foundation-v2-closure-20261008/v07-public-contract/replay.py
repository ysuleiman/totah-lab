"""Two independent JVMs; compare complete governed selected-residue V07 reports."""
from pathlib import Path
import sys,json
ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'software/qualification/foundation-v2-workflow-20261008/harness'))
import foundation as f
build=f.read(Path(sys.argv[1]));out=Path(sys.argv[2]).resolve();out.mkdir(exist_ok=False)
for n in [1,2]:
 cmd=f.engine.test_base(ROOT,build['classes']);cmd.insert(1,'-Dv07.replay='+str(out/f'replay-{n}'));cmd+=['--select-class','totah.lab.daedalus.system.RestraintAcceptanceTest','--details','summary','--disable-ansi-colors','--reports-dir',str(out/f'junit-{n}')]
 result=f.engine.timed(cmd,out/f'jvm-{n}.log',ROOT);f.write(out/f'jvm-{n}.json',result);assert result['exitCode']==0
hashes={}
for name in ['SER','THR','VAL']:
 a=out/f'replay-1-{name}.json';b=out/f'replay-2-{name}.json';assert a.read_bytes()==b.read_bytes();hashes[name]=f.digest(a)
f.write(out/'RESULT.json',{'status':'PASS','independentJvms':2,'fullReportByteIdentical':True,'sha256':hashes,'compiledSourceKey':build['cacheKey']});print((out/'RESULT.json').read_text())
