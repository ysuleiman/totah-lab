"""Two fresh JVMs consume deterministic source fixtures; retain exact result bytes."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'software/qualification/foundation-v2-workflow-20261008/harness'))
import foundation as f
base=Path(__file__).resolve().parent
build=f.read(Path(sys.argv[1]))
out=Path(sys.argv[2]).resolve();out.mkdir(exist_ok=False)
for n in [1,2]:
    cmd=f.engine.test_base(ROOT,build['classes'])
    cmd.insert(1,'-Dathena.residueReplayOutput='+str(out/f'replay-{n}.json'))
    cmd+=['--select-method','totah.lab.daedalus.system.ResidueValidationAcceptanceTest#independentReplayCorpus','--details','summary','--disable-ansi-colors']
    r=f.engine.timed(cmd,out/f'jvm-{n}.log',ROOT);f.write(out/f'jvm-{n}.json',r)
    assert r['exitCode']==0
assert (out/'replay-1.json').read_bytes()==(out/'replay-2.json').read_bytes()
f.write(out/'RESULT.json',{'independentJvms':2,'byteIdentical':True,'sha256':f.digest(out/'replay-1.json'),'sourceCacheKey':build['cacheKey']})
print((out/'RESULT.json').read_text())
