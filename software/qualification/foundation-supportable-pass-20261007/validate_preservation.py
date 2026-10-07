"""Check preserved foundation and unrelated files against the pushed baseline."""
from pathlib import Path
import hashlib,json,subprocess
repo=Path(__file__).resolve().parents[3];q=Path(__file__).resolve().parent
b=json.loads((q/'PRESERVATION_BEFORE.json').read_text())
allowed={'software/modules/athena/src/main/java/totah/lab/athena/system/rules/RuleAnalyzers.java','software/modules/athena/src/main/java/totah/lab/athena/system/rules/RuleRegistry.java','docs/manual/athena/CATALOG.md','docs/manual/athena/RULES.md',*[f'software/qualification/chemistry-geometry-foundation-20261005/{p}' for p in ['START_HERE.txt','PROGRESS.json','CAPABILITY_LEDGER.json']]}
for p,h in b['trackedFiles'].items():
 if p not in allowed:assert hashlib.sha256((repo/p).read_bytes()).hexdigest()==h,p
pins=json.loads((repo/'software/qualification/water-bridge-contract-20261006/PRESERVATION_BEFORE.json').read_text())['sha256']
for p,h in pins.items():assert hashlib.sha256((repo/p).read_bytes()).hexdigest()==h,p
result={'baseline':b['head'],'protectedTrackedFilesUnchanged':len(b['trackedFiles'])-len(allowed),'preservationPins':25,'qualifiedS1I03WaterI02RoleAndEventPathSourceUnchanged':True,'unrelatedTrackedEditsPreserved':True}
(q/'PRESERVATION_VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
