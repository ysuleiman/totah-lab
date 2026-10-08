"""Generate release bookkeeping from completed immutable qualification evidence."""
from pathlib import Path
import collections, hashlib, json, shutil, sys

q = Path(__file__).resolve().parent
repo = q.parents[2]
run = Path(sys.argv[1]).resolve()
read = lambda p: json.loads(p.read_text())
write = lambda p, v: p.write_text(json.dumps(v, indent=2) + '\n')
result = read(run / 'QUALIFICATION.json')
assert result['regressionTests'] == 2876 and result['failures'] == result['skips'] == 0
assert result['independentJvmPairs'] == 29 and result['previousReplayHashesUnchanged'] == 28
assert result['historicalComparisons'] == 65 and result['preservationPins'] == 25
assert result['cleanExport'] and not result['compileCacheUsed']
assert 'Ran 16 tests' in (run / 'harness-tests.log').read_text()
ledger_path = Path('software/qualification/chemistry-geometry-foundation-20261005/CAPABILITY_LEDGER.json')
ledger = read(run / 'source' / ledger_path)
counts = dict(collections.Counter(row['currentDisposition'] for row in ledger['entries']))
assert counts == result['counts'] and sum(counts.values()) == 96
destination = q / 'release'
destination.mkdir(exist_ok=False)
# Preserve logs and exact result data, excluding compiled classes and duplicate source exports.
for path in run.rglob('*'):
    relative = path.relative_to(run)
    if not path.is_file() or relative.parts[0] in {'source', 'classes', 'mnemosyne-test'}:
        continue
    # Replay result bytes are the comparison authority. Retain the real consumer's
    # complete inspectable catalog once; other synthetic temporary catalogs add
    # no replay evidence and need not be duplicated in the repository.
    if 'catalog' in relative.parts and not (relative.parts[0] == 'replay' and
            relative.parts[1] == 'FoundationV1ConsumerAcceptanceTest1'):
        continue
    if path.suffix not in {'.json', '.xml', '.log'}:
        continue
    target = destination / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, target)
write(q / 'FOUNDATION_V1_RELEASE_LEDGER.json', {
    'sourceCommit': result['sourceCommit'], 'counts': counts,
    'full96Complete': False, 'newCapabilities': 0,
    'affectedCapabilityScope': 'Engineering consumer and harness only; scientific row domains unchanged',
    'sourceLedgerSha256': hashlib.sha256((run / 'source' / ledger_path).read_bytes()).hexdigest(),
    'entries': ledger['entries'],
    'blockerDecisions': read(q / 'RELEASE_SCOPE_DECISIONS.json')['rows'],
    'focusedAcceptanceEvidence': {
        name: read(q / 'profiling' / name / 'RESULT.json')
        for name in ['tier1', 'affected-family-development-run']
    },
    'qualification': result,
})
baseline = read(q / 'BASELINE_TIMINGS.json')['suiteSeconds']
regression = result['stages']['regressionSeconds']
consumer = read(run / 'replay/FoundationV1ConsumerAcceptanceTest1/catalog/consumer.json')
consumer_lines = ['FOUNDATION V1 — REAL-SOURCE CONSUMER', '', consumer['scope'], '',
                  'Supplied-source graph query occurrences (not governed pose chemistry):']
for occurrence in consumer['sourceQueryOccurrences']:
    consumer_lines.append(str(occurrence['definition']) + ': ' + occurrence['occurrenceState'])
consumer_lines += ['', 'Selected original-pose geometry:',
                   json.dumps(consumer['rawGeometry']['operations'], indent=2), '',
                   'Source chemistry to pose: ' + json.dumps(consumer['sourceChemistryToPose']),
                   'Direct SDF import: ' + json.dumps(consumer['unsupportedSourceImport']),
                   'Scientific evaluation: ' + consumer['scientificEvaluation']['status'],
                   'Production scientific receipts issued: ' + str(consumer['productionScientificReceiptsIssued']),
                   '', 'Exact source digests:', json.dumps(consumer['sourceSha256'], indent=2), '',
                   'All source/preparation attribution and catalog evidence are preserved in',
                   'release/replay/FoundationV1ConsumerAcceptanceTest1/catalog/.',
                   'No mechanistic, binding, potency, energy or biological conclusion is made.']
(q / 'CONSUMER_RESULT.txt').write_text('\n'.join(consumer_lines) + '\n')
text = f'''FOUNDATION_V1_FINAL_CHECKPOINT — 2026-10-08

Foundation v1 release scope is qualified and frozen at source {result['sourceCommit']}.
{counts['BOUNDED_SUPPORTED_DOMAIN_QUALIFIED']} bounded-qualified capability domains; {counts['EXPLICIT_ARCHITECTURAL_DISPOSITION']} architectural dispositions;
{counts['SCIENTIFIC_REVIEW_REQUIRED']} scientific blockers deferred to v2; {counts['REQUIRES_EXTERNAL_REFERENCE_DATA']} external-reference-data requirements.
Full 96-capability scientific completion is FALSE. All bounded-domain limitations remain.
No production scientific authority or receipt was created. No new chemistry was added.

Fresh committed-source qualification: {result['regressionTests']} regression tests,
3 isolation tests, 16 harness tests; zero failures/skips. All 2872 prior test identities
are preserved. 29 independent-JVM replay pairs agree; all 28 prior hashes unchanged.
65 historical file comparisons and 25 preservation pins pass. 14 unrelated tracked
edits retain their exact pre-release bytes. Clean export used no compiled cache.

Measured regression wall time: {regression:.3f} seconds ({regression/60:.2f} minutes).
Complete release workflow: {result['totalSeconds']:.3f} seconds ({result['totalSeconds']/60:.2f} minutes).
Historical Batch C JUnit duration: {baseline:.3f} seconds. These are not controlled
same-load benchmarks: the old run contains a 946.486-second unexplained outlier.
Do not attribute that outlier's disappearance to optimization.
Focused I11: 62 tests / 13.206 seconds. Affected family: 82 tests / 224.333 seconds.
Development uses conservative dependency selection and byte-verified compiled cache.
Release preserves every check and parallelizes only separately isolated reviewed JVMs.
Pinned production serialization and scientific setup were not modified.

Real-source consumer: frozen METTL7B/netarsudil pose and supplied source SMILES.
Explicit source-query occurrences, raw selected distance, UNKNOWN correspondence,
UNSUPPORTED SDF import and NOT_EVALUATED current authority remain separate.
Evidence is saved under release/replay/FoundationV1ConsumerAcceptanceTest1/.
This engineering demo changes no METTL7 mechanistic hypothesis and grants no authority.

Machine-readable authority: FOUNDATION_V1_RELEASE_LEDGER.json and release/QUALIFICATION.json.
Scope decisions: RELEASE_SCOPE_DECISIONS.json. Timing details: PROFILE_REPORT.txt.
Development/release commands: WORKFLOW.txt. Historical instructions below the current
START_HERE resume block are history. Stop capability expansion; return to separately
authorized METTL7 scientific work after publishing the release tag.
'''
(q / 'FOUNDATION_V1_FINAL_CHECKPOINT.txt').write_text(text)
write(q / 'RELEASE_ARTIFACT_HASHES.json', {
    str(p.relative_to(q)): hashlib.sha256(p.read_bytes()).hexdigest()
    for p in sorted(destination.rglob('*')) if p.is_file()
})
print(text)
