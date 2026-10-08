"""v2 preservation overlay: retain all v1 pins plus the exact approved G06 extraction."""
from pathlib import Path
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[4]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def verify(root=ROOT):
 q=root/'software/qualification/foundation-supportable-pass-20261007'
 b=json.loads((q/'PRESERVATION_BEFORE.json').read_text())
 allowed={'software/modules/athena/src/main/java/totah/lab/athena/system/rules/RuleAnalyzers.java','software/modules/athena/src/main/java/totah/lab/athena/system/rules/RuleRegistry.java','docs/manual/athena/CATALOG.md','docs/manual/athena/RULES.md',*[f'software/qualification/chemistry-geometry-foundation-20261005/{p}' for p in ['START_HERE.txt','PROGRESS.json','CAPABILITY_LEDGER.json']]}
 allowed.update({'software/modules/athena/src/main/java/totah/lab/athena/design/backend/SubstructureMatcher.java','software/modules/athena-openchemlib/src/main/java/totah/lab/athena/design/backend/ocl/OclMolecularBackend.java','software/modules/athena-openchemlib/src/main/java/totah/lab/athena/design/backend/ocl/OclOccurrenceMatcher.java'})
 water='software/modules/athena/src/main/java/totah/lab/athena/system/rules/WaterIdentity.java'
 approved=json.loads((root/'software/qualification/g06-water-tetrel-20261008/QUALIFICATION.json').read_text())['sourcePins'][water]
 assert approved=='1b78c7d55e07422f87757d923db711160a380dc9c9e2e020632a649b938c4fe6'
 assert sha(root/water)==approved
 original=subprocess.check_output(['git','show',b['head']+':'+water],cwd=root).decode()
 signature='    static Result assess(SystemStateView s,WaterBridgeInputs inputs,AtomReference oxygen)throws Exception {\n'
 replacement=signature+'        return assess(s,inputs.chemistry,oxygen);\n    }\n    static Result assess(SystemStateView s,HbondCandidateSources chemistry,AtomReference oxygen)throws Exception {\n'
 assert original.count(signature)==1
 expected=original.replace(signature,replacement).replace('var report=inputs.chemistry.report(','var report=chemistry.report(').replace('var anchors=inputs.chemistry.anchors(','var anchors=chemistry.anchors(')
 assert expected==(root/water).read_text(), 'Approved extraction changed beyond delegation/parameter access'
 for p,h in b['trackedFiles'].items():
  if p not in allowed and p!=water:assert sha(root/p)==h,p
 pins=json.loads((root/'software/qualification/water-bridge-contract-20261006/PRESERVATION_BEFORE.json').read_text())['sha256'];assert len(pins)==25
 for p,h in pins.items():assert sha(root/p)==h,p
 unrelated=json.loads((root/'software/qualification/v09-v10-implementation-20261008/PRESERVATION_BEFORE.json').read_text());assert len(unrelated)==14
 for p,h in unrelated.items():assert sha(root/p)==h,p
 return {'status':'PASS','baseline':b['head'],'protectedTrackedFilesUnchanged':len(set(b['trackedFiles'])-allowed-{water}),'preservationPins':25,'unrelatedTrackedEditsPreserved':14,'approvedG06Exception':{'path':water,'sha256':approved,'qualifiedCommit':'705a2d78efc441a1b79eb661577cfe063899b465','exactDelegationOnlyTransformationVerified':True},'noNewException':True}
if __name__=='__main__':print(json.dumps(verify(),indent=2))
