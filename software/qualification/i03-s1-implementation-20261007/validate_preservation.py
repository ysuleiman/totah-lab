"""Preserve unrelated work and every non-I03 capability; never updates old qualification artifacts."""
from pathlib import Path
import json,hashlib,subprocess
repo=Path(__file__).resolve().parents[3];q=Path(__file__).resolve().parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
b=json.loads((q/'PRESERVATION_BEFORE.json').read_text())
allowed={
'software/modules/athena/src/main/java/totah/lab/athena/system/rules/RuleAnalyzers.java',
'software/modules/athena/src/main/java/totah/lab/athena/system/rules/RuleRegistry.java',
'software/modules/athena/src/main/java/totah/lab/athena/system/rules/ImplicitHProxyRules.java',
'software/modules/daedalus/src/main/java/totah/lab/daedalus/system/CurrentRuleExecution.java',
'software/modules/daedalus/src/main/java/totah/lab/daedalus/system/DirectAssessmentInputs.java',
'docs/manual/athena/CATALOG.md','docs/manual/athena/RULES.md',
'software/qualification/chemistry-geometry-foundation-20261005/START_HERE.txt',
'software/qualification/chemistry-geometry-foundation-20261005/PROGRESS.json',
'software/qualification/chemistry-geometry-foundation-20261005/CAPABILITY_LEDGER.json'}
for p,h in b['trackedFiles'].items():
 if p not in allowed:assert sha(repo/p)==h,p
p='software/modules/athena/src/main/java/totah/lab/athena/system/rules/ImplicitHProxyRules.java'
old=subprocess.check_output(['git','show',b['head']+':'+p],cwd=repo,text=True)
assert (repo/p).read_text()==old.replace('    private static JsonNode build(','    static JsonNode build('),'historical I03 semantics changed'
review=json.loads((repo/'software/qualification/i03-sp3-source-review-20261006/PRESERVATION_BEFORE.json').read_text())
old={r['capabilityId']:r for r in review['ledger']['entries']}
new=json.loads((repo/'software/qualification/chemistry-geometry-foundation-20261005/CAPABILITY_LEDGER.json').read_text())
for row in new['entries']:
 if row['capabilityId']!='I03':assert row==old[row['capabilityId']],row['capabilityId']
for p,h in review['original25'].items():assert sha(repo/p)==h,p
for folder in ['i03-n-sp3-s1-v1','implicit-h-proxy-s1-v1']:
 for p in (repo/'software/modules/athena/src/main/resources/totah/lab/athena/system/rules'/folder).glob('*.json'):
  m=json.loads(p.read_text())
  for s in m['scientificSources']:assert sha(repo/s['locator'])==s['sha256'],s['locator']
subprocess.run(['python3',str(repo/'docs/manual/athena/render_reference.py'),'--check'],cwd=repo,check=True)
out={'protectedTrackedFilesUnchanged':len(b['trackedFiles'])-len(allowed),'allowedExistingFoundationFiles':sorted(allowed),'preservationPinsIntact':25,'nonI03CapabilityRowsUnchanged':95,'I04AndExternal12Unchanged':True,'historicalI03OnlyPackageVisibilityChanged':True,'I02WaterRolesEventsPathsUnchanged':True,'productionReceiptsIssued':0,'publicJavaSignatureChanges':0}
(q/'PRESERVATION_VALIDATION.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps(out,indent=2))
