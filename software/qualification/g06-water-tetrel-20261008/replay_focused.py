"""Two fresh JVMs for this G06 water leaf only; not a batch/release certificate."""
from pathlib import Path
import importlib.util,json,concurrent.futures,hashlib
root=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('foundation',root/'software/qualification/foundation-v1-rc-20261008/harness/foundation.py');f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
folder=Path(__file__).resolve().parent
build=f.read(folder/'focused-qualified/BUILD.json');base=f.test_base(root,build['classes']);java=base[0];cp=base[base.index('--class-path')+1]
out=folder/'focused-replay';out.mkdir(exist_ok=False)
def run(i):
 d=out/str(i);d.mkdir();temp=d/'tmp';temp.mkdir();target=d/'result.json'
 command=[java,'-Xmx512m','-Djava.io.tmpdir='+str(temp),'-cp',cp,'totah.lab.daedalus.system.SamWaterTetrelAcceptanceTest',str(d/'work'),str(target)]
 result=f.timed(command,d/'replay.log',root);assert result['exitCode']==0,result
 result.update(sha256=f.digest(target),bytes=target.stat().st_size);return result
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool: results=list(pool.map(run,[1,2]))
assert results[0]['sha256']==results[1]['sha256'],results
assert (out/'1/result.json').read_bytes()==(out/'2/result.json').read_bytes()
f.write(out/'RESULT.json',{'independentJvmPairs':1,'identicalBytes':True,'cases':['admitted selected water','missing water authority','excluded water charge','undefined angle with retained distance'],'results':results})
print(json.dumps({'identicalBytes':True,'sha256':results[0]['sha256'],'bytes':results[0]['bytes']}))
