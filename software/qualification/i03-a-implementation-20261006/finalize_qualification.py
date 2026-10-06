"""Persist completed committed-source checks. Never issues scientific or producer authority."""
from pathlib import Path
import hashlib,json,shutil,subprocess,xml.etree.ElementTree as ET
repo=Path.cwd();q=repo/'software/qualification/i03-a-implementation-20261006'
b=Path('/private/tmp/i03-clean-fae270a04');r=Path('/private/tmp/i03-replay-fae270a04')
assert '0 tests failed' in (b/'tests.log').read_text()
assert '0 tests failed' in (b/'isolation.log').read_text()
replay=json.loads((r/'REPLAY.json').read_text());assert len(replay['independentJvmComparisons'])==13
prior=json.loads((repo/'software/qualification/water-bridge-implementation-20261006/REPLAY.json').read_text())
for name,h in prior['independentJvmComparisons'].items():assert replay['independentJvmComparisons'][name]==h,name
replay['previousElevenReplayHashesUnchanged']=True
replay['qualifiedSP3Producer']=False
replay['I03ScientificActivation']='NOT_EVALUATED'
(q/'REPLAY.json').write_text(json.dumps(replay,indent=2)+'\n')
clean=q/'clean';clean.mkdir(exist_ok=True)
for name in ['BUILD.json','compile-command.json','compile.log','test-command.json','tests.log','isolation-commands.json','isolation.log']:shutil.copy2(b/name,clean/name)
shutil.copytree(b/'junit',clean/'junit',dirs_exist_ok=True)
replays=q/'replay-results';replays.mkdir(exist_ok=True)
for p in r.glob('*.log'):shutil.copy2(p,replays/p.name)
for p in r.glob('ImplicitHProxy*.json'):shutil.copy2(p,replays/p.name)
cases=[]
for p in (b/'junit').glob('TEST-*.xml'):cases.extend(ET.parse(p).getroot().findall('testcase'))
assert len(cases)==2089,len(cases)
assert all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
assert len([c for c in cases if 'ImplicitHProxy' in c.get('classname','')])==59
assert len([c for c in cases if 'WaterBridge' in c.get('classname','')])==98
source=json.loads((b/'BUILD.json').read_text())['commit']
subprocess.run(['python3',str(q/'validate_preservation.py')],check=True)
result={'sourceCommit':source,'scope':'I03-A plumbing and numeric predicate only; no scientific activation','focusedEngineeringTests':59,'cleanCommittedSourceTests':len(cases),'cleanSourceI03Tests':59,'unchangedWaterFamilyTests':98,'isolationTests':3,'independentJvmPairs':13,'previousReplayHashesUnchanged':11,'historicalFilesByteIdentical':65,'preservationPinsIntact':25,'protectedTrackedFilesUnchanged':3508,'currentPolicyScientificRulesQualified':0,'SP3ProducerSelected':False,'productionReceiptsIssued':0,'I03ScientificMatrixClosed':False,'I04':'SCIENTIFIC_REVIEW_REQUIRED; not implemented','externalRowsUnchanged':12,'pushed':False}
(q/'QUALIFICATION.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
