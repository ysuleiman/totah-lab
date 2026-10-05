"""Render documentation only; never mutate scientific manifests or checkpoints."""
import hashlib, json, pathlib, sys
root=pathlib.Path(__file__).resolve().parents[3]
out=pathlib.Path(__file__).parent
ledger=root/'software/qualification/chemistry-geometry-foundation-20261005/CAPABILITY_LEDGER.json'
resources=root/'software/modules/athena/src/main/resources/totah/lab/athena/system/rules'
def link(p):
    return '../../../'+p.relative_to(root).as_posix()
def pin(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()
entries=json.loads(ledger.read_text())['entries']
assert len({e['capabilityId'] for e in entries})==len(entries)
a=['# Master capability catalog','',f'Generated from [accepted ledger]({link(ledger)}), SHA256 `{pin(ledger)}`.', '', 'Disposition is copied without upgrading partial capability coverage.', '', '| ID | Capability | Priority | Disposition | Evidence |','|---|---|---|---|---|']
for e in entries:
    refs=[]
    for s in e.get('progressArtifacts',[]):
        p=root/s
        assert p.is_file(),s
        refs.append(f'[{p.parent.name}]({link(p)})')
    a.append('| '+' | '.join([e['capabilityId'],e['capability'].replace('|','/'),e['priority'],e['currentDisposition'],', '.join(refs)])+' |')
b=['# Scientific rule records','','Generated links and fields; manifests and dossiers remain authoritative.', '', 'Shared perception/group/role support: [Mobley 2018](supporting-material/MOBLEY_2018.md).', '', 'Current-policy gate status must be established by a valid receipt, not the historical qualification field. Supporting-material completeness is not inferred from a citation or from this rendering.','']
for p in sorted(resources.rglob('*.rule.json')):
    d=json.loads(p.read_text())
    b += [f"## {d['ruleId']} — {d.get('version')} ({p.parent.name})",'',f'[Authoritative manifest]({link(p)}) · SHA256 `{pin(p)}`','']
    for k in ['family','profile','implementationId','implementationVersion','qualification','requiredCapabilities','requiredChemistry','requiredGeometry','measurementsProduced','classificationStates','negativeCoverage','limitations','scientificSources','referenceArtifacts']:
        if k in d:b += [f'**{k}**', '', '```json',json.dumps(d[k],indent=2,ensure_ascii=False),'```','']
    definition=d.get('parameters',{}).get('definition',{}).get('value')
    if definition:
        b += ['**Exact declarative definition (rendered, not independently maintained)**','','```json',json.dumps(json.loads(definition),indent=2,ensure_ascii=False),'```','']
for name,lines in [('CATALOG.md',a),('RULES.md',b)]:
    content='\n'.join(lines).rstrip()+'\n';p=out/name
    if '--check' in sys.argv:
        assert p.read_text()==content,f'Stale reference: {name}'
    else:p.write_text(content)
print(f'{len(entries)} capability entries; manifest references rendered and verified')
