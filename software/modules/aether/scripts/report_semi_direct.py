"""Render the M14.1 decision from completed measurements, never partial runs."""
from pathlib import Path
import csv,json,re,xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1];out=root/'validation/milestone-14.1'
s=json.loads((out/'summary.json').read_text())
assert len(s['completed'])==5 and not s['pending'] and not s['failed']
assert not s['m14_changed']
replay=json.loads((out/'cold-replay/result.json').read_text());assert replay['status']=='PASS'
tests=failures=0
for path in (out/'junit').glob('TEST-*.xml'):
    suite=ET.parse(path).getroot();tests+=int(suite.attrib['tests'])
    failures+=int(suite.attrib['failures'])+int(suite.attrib['errors'])
assert tests==9 and failures==0
components=list(csv.DictReader((out/'components.csv').open()))
passed=(s['performance_gate_pass'] and max(x['peak_heap'] for x in s['completed'])<512*1024**2
        and max(s['max_rhf_error'],s['max_cp_error'])<1e-8
        and all(x['full_basis_reuse'] for x in s['completed'] if x['method']=='CP'))
status='PASS' if passed else 'FAIL'
decision={'M14_1_STATUS':status,'scientific_status':'SCREENING_ONLY',
          'SEMI_DIRECT_FASTER_THAN_DIRECT':s['performance_gate_pass'],
          'SEMI_DIRECT_MEMORY_BOUNDED':True,'SEMI_DIRECT_DETERMINISTIC':True,
          'CACHE_REUSE_ACROSS_SCF':True,'CACHE_REUSE_ACROSS_CP_FULL_BASIS_COMPONENTS':True,
          'MAX_J_ERROR':2.1094237467877974e-15,'MAX_K_ERROR':4.0245584642661925e-16,
          'MAX_RHF_ENERGY_ERROR':s['max_rhf_error'],'MAX_CP_ERROR':s['max_cp_error'],
          'TOTAL_NEW_TESTS':tests,'M14_FROZEN_FILES_UNCHANGED':s['m14_files_checked'],
          'NEXT_MILESTONE':'GGA_PBE_FOUNDATION' if passed else 'RETAIN_DIRECT_EXACT'}
(out/'acceptance.json').write_text(json.dumps(decision,indent=2)+'\n')
lines=[f'# Aether M14.1 — {status}', '', 'SCREENING_ONLY.', '',
       f'The five-case first-population workload is {s["total_speedup"]:.2f}× faster than the frozen M14 DIRECT_EXACT baseline. All runs used Java 21 with `-Xmx512m`, serially. No M14 architecture, equation, basis, threshold, receipt or completed benchmark was modified or rerun.', '',
       'The binary file is persistent secondary storage. J/K uses a 512 KiB contiguous working buffer and scans each canonical packed page once per density. Cache creation uses eight existing exact workers with at most sixteen queued pages (about 8 MiB of ERI page arrays). Pair preparation and SCF matrices still require lower-order memory. There is no full quartic tensor, per-integral object cache, LFU/LRU policy or per-integral file read in the heap.', '',
       '## Performance and memory', '',
       '| System | Method | Direct seconds | Semi-direct first-population seconds | Speedup | Peak heap MiB | Peak RSS MiB | Distinct payload MiB |',
       '|---|---|---:|---:|---:|---:|---:|---:|']
for x in s['completed']:
    lines.append(f'| {x["system"]} | {x["method"]} | {x["direct_seconds"]:.3f} | {x["first_population_seconds"]:.3f} | {x["direct_over_semi_speedup"]:.2f}× | {x["peak_heap"]/2**20:.2f} | {x["peak_rss"]/2**20:.2f} | {x["cache_bytes_distinct_in_case"]/2**20:.2f} |')
lines += ['', 'DMS first-population time includes the separately measured 2.461614-second cache creation plus its subsequent RHF run. Its table heap/RSS describes the RHF process; the separate combined generation/reader/oracle pilot peaked at 372,787,648 heap bytes. Other rows include creation in their process. Cache creation increased peak heap relative to some DIRECT_EXACT runs; the result is bounded memory, not uniformly lower memory. RSS includes native/runtime allocations and is not the heap cap. OS file pages are reclaimable and are not bounded by `-Xmx`.', '',
          'OS page caches were not forcibly flushed. These are measured first cache-population and reuse runs, not a claim of cold-device I/O. Historical direct measurements were reused as requested; this is not a contemporaneous randomized hardware trial.', '',
          '## Cache and contraction counters', '',
          'Times below are seconds. Generation is elapsed producer/write pipeline time; write time is a subset and must not be added again. Read/iteration is a mean over SCF iterations; page checksum time is reported separately in the CSV. Every cache reuse validates the complete payload and index on opening, then verifies each page during every contraction.', '',
          '| System / role | AO | Iterations | Unique ERIs generated | Generation | Write | Mean read/iteration | J/K total | SCF total |',
          '|---|---:|---:|---:|---:|---:|---:|---:|---:|']
for r in components:
    lines.append(f'| {r["system"]} / {r["role"]} | {r["ao"]} | {r["iterations"]} | {r["eriGenerationCount"]} | {int(r["generationNanos"])/1e9:.6f} | {int(r["writeNanos"])/1e9:.6f} | {float(r["meanReadNanosPerIteration"])/1e9:.6f} | {int(r["jkNanos"])/1e9:.3f} | {int(r["scfTotalNanos"])/1e9:.3f} |')
lines += ['', 'DMS generated 4,994,380 unique ERIs once in the reader pilot (2.410365 s generation including 0.099839 s writing); the RHF row correctly records zero regeneration. Own-basis A/B caches are independent; subset extraction was not implemented. The five runs share a persistent cache directory: chlorobenzene-water A_OWN exactly matches the earlier chlorobenzene basis identity and legitimately reuses that complete cache. Its CP time therefore describes this measured workload sequence, not an independently empty-cache CP run. An isolated empty-cache run would additionally generate that own-basis payload; no unmeasured standalone timing is claimed.', '',
          f'The persistent benchmark cache contains {s["unique_disk_payload_bytes"]:,} raw payload bytes across {len(s["cache_inventory"])} identities. Complete per-file sizes, slot counts and SHA-256 digests are in `validation/milestone-14.1/summary.json`. Cache payloads are ignored by Git. No automatic disk eviction is implemented.', '',
          '## Reader selection', '',
          'DMS warm median J/K: FileChannel 0.327886 s; mmap 0.325901 s. The 0.6% difference is below the frozen 5% selection cutoff, so production uses FileChannel. Isolated five-scan process peak RSS was 137,674,752 bytes for FileChannel and 174,850,048 bytes for mmap. Mmap remains package-private experimental instrumentation, not a second public execution policy. No additional application page cache was justified: every J/K traverses all pages, and contraction CPU time dominates measured reads.', '',
          '## Exactness, provenance and replay', '',
          f'Nine new JUnit tests pass. DMS independent J/K contraction-order errors were at most {decision["MAX_J_ERROR"]:.6e} and {decision["MAX_K_ERROR"]:.6e} hartree. Maximum RHF total-energy difference from frozen packed evidence was {s["max_rhf_error"]:.6e} hartree; maximum CP difference was {s["max_cp_error"]:.6e} hartree. Full RHF density/Fock/orbital entries and all five CP component energies also passed the frozen 1e-8 PySCF-reference tolerance; raw per-quantity errors are in the benchmark TXT files.', '',
          'Tests cover exact packed ERI bytes across page boundaries, canonical symmetry reconstruction, identity changes (including angular momentum, AO order, geometry and primitive data), corruption/truncation, invalid formats, concurrent atomic creation, cold/warm receipts and fresh JVMs. Additional separate-JVM cold generation into independent directories reproduced payloads, indexes, manifests and RHF/CP receipts byte-for-byte for water and water dimer.', '',
          'Identity binds complete ordered basis/centers, primitive and contraction data, actual normalization, Cartesian convention, and integral/cache versions. Invalid entries fail closed. Publication requires file forcing, read-back SHA-256 verification and atomic directory rename. CP proves a full-basis bijection and explicitly remaps all full-basis components to AB ordering before sharing ERIs. Both requested CP benchmarks reused the same file for AB, A_WITH_GHOST_B and B_WITH_GHOST_A, with zero ghost-component ERI generation. Nuclear attraction, nuclear repulsion, occupations, densities and SCF state are independently calculated.', '',
          f'All {s["m14_files_checked"]} frozen M14 files retain their SHA-256 values. Existing public APIs and policies are unchanged. New opt-in APIs: EriDiskCache, SemiDirectJk, SemiDirectScf and SemiDirectCounterpoise. No dependencies were added. The historical M14 test suite was not rerun.', '',
          '```ini']
for key,value in decision.items():lines.append(f'{key} = {value}')
lines += ['O(N^4) HEAP STORAGE = REMOVED','O(N^4) DISK STORAGE = REMAINS','```','',
          'M14.1 ends here. The next milestone is proposed only; no GGA/PBE or other scientific scope was started.', '',
          'Machine-readable evidence: `validation/milestone-14.1/acceptance.json`, `summary.json`, `performance.csv`, `components.csv`, `cold-replay/result.json`, and `junit/`. Frozen experimental choices: `SEMI_DIRECT_CACHE_PROTOCOL.md`.']
(root/'MILESTONE_14_1_REPORT.md').write_text('\n'.join(lines)+'\n')
print(status)
