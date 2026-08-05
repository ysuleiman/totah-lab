# PocketMatch Stage 1 benchmark

Query pocket: 32 (METTL7A pocket 32)
Distance tolerance: 0.5 angstroms
Signatures: full-fidelity residue atoms parsed from structure artifacts

## Fixed-case pairwise scores

| direction | matched | query coverage | candidate coverage | symmetric score |
|---|---|---|---|---|
| 32 -> METTL7B pocket 3 (homologous) | 5568 | 0.8645 | 0.5890 | 0.5890 |
| METTL7B pocket 3 (homologous) -> 32 | 5568 | 0.5890 | 0.8645 | 0.5890 |
| 32 -> METTL7B pocket 1 (secondary) | 1011 | 0.1570 | 0.8963 | 0.1570 |
| METTL7B pocket 1 (secondary) -> 32 | 1011 | 0.8963 | 0.1570 | 0.1570 |
| 32 -> AF-Q14112 pocket 313826 (geometry-only FP) | 4392 | 0.6819 | 0.6151 | 0.6151 |
| AF-Q14112 pocket 313826 (geometry-only FP) -> 32 | 4392 | 0.6151 | 0.6819 | 0.6151 |

## Stage 1 rank comparison

| candidate | current descriptor rank | PM symmetric rank | PM query-coverage rank | PM candidate-coverage rank |
|---|---|---|---|---|
| pocket 752299 | 6 | 83088 | 83146 | 452131 |
| METTL7B pocket 1 (secondary) | 38566 | 57804 | 57924 | 256444 |
| pocket 311590 | 4 | 3007 | 5997 | 502106 |
| METTL7B pocket 3 (homologous) | 6840 | 2338 | 902 | 504735 |
| pocket 51779 | 200 | 1 | 2128 | 476424 |
| AF-Q14112 pocket 313826 (geometry-only FP) | 60 | 1453 | 3406 | 503527 |
| pocket 135880 | 9 | 2675 | 2360 | 505130 |
| pocket 762919 | 8 | 3489 | 6333 | 483716 |
| pocket 88332 | 3 | 8250 | 10007 | 458119 |
| pocket 933667 | 13815 | 4 | 2443 | 484419 |
| pocket 415563 | 34389 | 6 | 2502 | 486119 |
| pocket 293300 | 10 | 18511 | 19251 | 285838 |
| pocket 153747 | 4778 | 7 | 2525 | 486250 |
| pocket 631288 | 14871 | 10 | 2594 | 487613 |
| pocket 1024317 | 5 | 7969 | 9777 | 482596 |
| pocket 474576 | 2 | 32600 | 32900 | 400084 |
| pocket 370226 | 7976 | 8 | 2526 | 468538 |
| pocket 942935 | 6552 | 3 | 2271 | 480226 |
| pocket 631794 | 10850 | 2 | 2205 | 478469 |
| pocket 1024179 | 1 | 5502 | 7832 | 419847 |
| pocket 913232 | 17264 | 9 | 2571 | 487334 |
| pocket 15454 | 7 | 20653 | 21283 | 202885 |
| pocket 887410 | 9094 | 5 | 1935 | 485391 |

Current descriptor top 10: 1024179, 474576, 88332, 311590, 1024317, 752299, 15454, 762919, 135880, 293300
PocketMatch symmetric top 10: 51779, 631794, 942935, 933667, 887410, 415563, 153747, 370226, 913232, 631288

## Ranking overlap

| K | current top-K in PM top-K | PM top-K in current top-K | METTL7B pocket 3 in current top-K | METTL7B pocket 3 in PM top-K |
|---|---|---|---|---|
| 100 | 0 | 0 | no | no |
| 500 | 10 | 10 | no | no |
| 1000 | 42 | 42 | no | no |

## Distance-tolerance sweep

| tolerance | pocket 3 rank | pocket 1 rank | pocket 313826 rank |
|---|---|---|---|
| 0.25 | 1495 | 58476 | 1013 |
| 0.5 | 2338 | 57804 | 1453 |
| 1.0 | 2798 | 58691 | 1709 |

## Signature storage

Binary float records over 510469 pockets:

- mean: 3.24 KiB
- median: 2.06 KiB
- p95: 8.74 KiB
- projected total for 510462 pockets: 1.58 GiB
- raw double-precision payload would double the per-record distance bytes; a PostgreSQL float8 array adds row and array overhead on top of that

## Comparison latency

- single-pair comparison: 24.1 microseconds
- measured comparison time during the scan: 5.4 s total, 10.5 microseconds per pocket
- full corpus scan measured: 439.245 s for 510469 pockets (signature build from parsed structure artifacts, serialization, and comparison; 0 structures failed)
- projected query-versus-all at 510462 pockets with precomputed signatures: 12.3 s single-threaded

## Interpretation

Data source note: an initial run built signatures from
`docking.pocket_atom`, but that table stores only pocket-lining
contact atoms (~1.2 atoms/residue; only 4 of 38 residues of METTL7A
pocket 32 carry a CA), producing degenerate signatures. All results
above use full-fidelity residue atoms parsed from the structure
artifacts (22,753 structures, 0 parse failures). The query signature
covers 38/38 residues (114 points, 6,441 distances).

### Fixed METTL7 cases

- **Homologous pair (32 vs 7B pocket 3):** symmetric 0.589, query
  coverage 0.865. The shared homologous subsite is clearly detected.
  Pocket 3 is larger than the query; 86% of the query's distance
  distribution is reproduced in it.
- **Secondary 7B pocket 1:** symmetric score is only 0.157, but
  candidate coverage is 0.896 — pocket 1 is small and almost entirely
  contained in the query distribution. This is exactly the
  merged-pocket asymmetry the task brief warned about: the symmetric
  PMScore alone would discard a biologically interesting containment.
  **Directional coverage is required.**
- **Geometry-only false positive (Q14112 pocket 313826):** symmetric
  0.615, *above* the homologous pair's 0.589. PocketMatch at tau 0.5
  does **not** demote this false positive below the true homolog.
  Reported as measured, per the brief.

### Stage 1 ranks (query: METTL7A pocket 32)

- METTL7B pocket 3: current descriptor rank **6840** -> PM symmetric
  rank **2338** -> PM query-coverage rank **902**. PocketMatch lifts
  the homologous pocket ~3x (symmetric) and ~7.6x (query coverage),
  the latter putting it inside the top 1000.
- Q14112 false positive: current rank **60** -> PM symmetric rank
  **1453**. PocketMatch demotes it ~24x — but it still sits above the
  homologous pocket (1453 vs 2338 on symmetric). The FP:homolog rank
  ratio shrinks from ~114x (current) to ~1.6x (PM).
- Tolerance sweep (0.25/0.50/1.00) does not change the FP-above-homolog
  ordering; no tolerance tuning forces the expected order.
- Top-100 overlap between the two methods is 0 (10/500 at K=500,
  42/1000 at K=1000): PocketMatch retrieves a substantially different
  candidate set. The PM top-10 pockets are not biologically validated
  here.
- Candidate-coverage ranking is not discriminative on this corpus:
  small pockets' distance lists match almost fully within 0.5 A, so
  candidate coverage saturates near 1.0 with id tie-breaking (fixed
  cases rank 200k-500k). Query coverage is the informative directional
  score for this query.

### Storage and latency

- Float-binary records: mean 3.24 KiB, median 2.06 KiB, p95 8.74 KiB;
  1.58 GiB for the full 510,462-pocket corpus (raw doubles would be
  ~2x; a PostgreSQL float8-array column adds row/array overhead on
  top). A binary side artifact is the right first persistence form; a
  relational design is not yet justified.
- Single-pair comparison: ~24 microseconds. Query-versus-all with
  precomputed signatures: ~12.3 s single-threaded (~1.5-2 s at 8
  threads), ~0.3 s if signatures are memory-resident as primitive
  arrays. Acceptable for an offline/batch channel; would need an
  index or prefilter for interactive full-corpus scans.
- Signature build is the expensive step when structures must be
  parsed: 439 s for the whole corpus (8 workers, parse-dominated).

## Recommendation

**Keep as benchmark only; do not enable the retrieval channel by
default.**

Against the primary decision criterion:

1. *Retrieves homologous pockets the current descriptor misses:*
   partially yes. METTL7B pocket 3 improves from rank 6840 to 902
   (query-coverage ranking) — from invisible to inside top 1000.
2. *Demotes geometry-only false positives:* partially. The Q14112
   false positive drops from rank 60 to 1453, but it still outranks
   the true homolog on the symmetric score (0.615 vs 0.589). The
   chemistry-group distance distribution at 0.5 A tolerance does not
   separate them on this case.

Directional scoring is confirmed necessary for merged-pocket cases
(7B pocket 1: symmetric 0.157 hides 0.896 containment).

Suggested next steps before any production consideration: evaluate on
a broader panel of validated positive/negative pocket pairs (a single
query is not enough); try query-coverage-led or hybrid
(max of coverage-weighted) ranking; consider per-category weighting
to increase chemistry specificity against large chemistry-rich
decoys.

## Reproduction

```
TOTAH_LAB_ROOT=/Users/yazan/totah-lab mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none \
  --pocket.search.pocket-match.benchmark-enabled=true"
```

Artifacts: signature store
`workspace/output/pocketmatch/pocket-match-signatures.bin` (1.6 GiB,
consumed by the disabled-by-default experimental channel
`pocket.search.pocket-match.*`).
