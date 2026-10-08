import sys
sys.dont_write_bytecode = True
from pathlib import Path
import json,hashlib,subprocess,sys,platform,importlib.metadata
import scitbx_array_family_flex_ext
root=Path(__file__).resolve().parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
deps=Path('/private/tmp/athena-v09-v10-oracle.d').read_text().replace('\\\n',' ').split()[1:]
boost=[Path(p) for p in deps if p.startswith('/opt/homebrew/include/boost/')]
obj={'status':'ORACLE_BUILD_RUNTIME_NOT_PRODUCTION_DEPENDENCY','python':sys.version,'platform':platform.platform(),'compiler':subprocess.check_output(['c++','--version'],text=True),'compilerFlags':['-std=c++11','-O0','-ffp-contract=off'],'packages':{n:importlib.metadata.version(n) for n in ['cctbx-base','six','numpy']},'wheels':[{'file':p.name,'sha256':sha(p),'bytes':p.stat().st_size} for p in sorted(Path('/private/tmp/athena-v09-v10-oracle-wheels').glob('*.whl'))],'nativeFlexModule':{'file':str(scitbx_array_family_flex_ext.__file__),'sha256':sha(Path(scitbx_array_family_flex_ext.__file__))},'boostHeaders':[{'path':str(p),'sha256':sha(p)} for p in sorted(set(boost))],'boostVersionHeader':{'path':'/opt/homebrew/include/boost/version.hpp','sha256':sha(Path('/opt/homebrew/include/boost/version.hpp'))},'driverSha256':sha(root/'driver.cpp'),'executableSha256':sha(Path('/private/tmp/athena-v09-v10-oracle')),'java':subprocess.run(['/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java','-version'],capture_output=True,text=True,check=True).stderr,'note':'Upstream evaluator headers/Python sources and generators are pinned independently of runtime wheel version. No wheel evaluator or wheel empirical grid is used. System Boost headers are hash-pinned build dependencies, not scientific authorities; they are not redistributed here.'}
(root/'RUNTIME.json').write_text(json.dumps(obj,indent=2)+'\n');print('runtime recorded; boost headers',len(boost))
