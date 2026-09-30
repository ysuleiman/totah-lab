# M14.1 frozen experiment protocol

SCREENING_ONLY. M14 source, tests, reports and completed timings are frozen and
will not be modified or rerun. Only additive M14.1 files are introduced.

The disk file is the persistent secondary store. The primary working set is a
bounded contiguous buffer, not an LFU/LRU collection of individual integrals.
No full tensor is retained in Java heap. Exact quartic disk storage remains.

- Identity: ordered basis/centers/primitive exponents and contraction coefficients,
  actual primitive and contraction normalization values, Cartesian convention,
  existing integral protocol, and versioned binary/cache format. Nuclear charges,
  occupations and densities are excluded only from the ERI cache identity.
- Format: canonical pair/pair triangle, raw IEEE binary64, big endian, zero-based
  byte offset 8*triangle(pair-high)+8*pair-low. Payload pages: 65,536 doubles
  (524,288 bytes). No screening or approximation. Generation workers: eight;
  bounded ordered queue: at most sixteen pages. No cross-iteration ERI generation.
- Persistence: per-identity process lock, private temporary directory, forced files,
  read-back SHA-256 verification, atomic directory publication; no non-atomic
  fallback. Invalid existing entries fail closed and are not silently repaired.
  A separate small sequential SHA-256 page index detects corruption during reads.
- Reader experiment: FileChannel block reads versus read-only mmap on the exact
  same validated cache and contraction workload. Mmap is experimental until the
  comparison is measured. It must not be confused with bounded physical RAM:
  mapped pages and OS file caching are accounted for separately from Java heap.
- Five alternated reader trials; report first observed scan separately and medians
  of trials 2–4. OS caches are not forcibly flushed. A reopened file is not labeled
  a verified cold OS cache. Only the selected reader runs the five full benchmarks.
- Numerical gate: existing 1e-8 reference tolerance, no SCF threshold/cap change.
  One fixed canonical packed traversal and existing eightfold reconstruction;
  no per-integral I/O. J/K accumulation order is deterministic but differs from
  M14 shell traversal, so compare numerical results within the frozen tolerance.
- CP: prove a bijection of complete basis functions, then explicitly order all
  full-basis components as AB. Cache identity must then match exactly. Each role
  computes its own S/Hcore/nuclear energy/occupation/density/SCF state. Own-basis
  subsets have independent caches; subset extraction is deferred.
- Performance acceptance, chosen before new results: all numerical/integrity/
  replay gates pass, all runs fit -Xmx512m, and summed first-use runtime for the
  five requested benchmarks is at least 20% lower than frozen M14 DIRECT_EXACT.
  Include generation, writing and checksum verification. Publish every per-case
  regression and RSS measurement; this is not a claim of uniform speedup.
- Reader selection: choose the lower warm median total J/K time on DMS unless the
  difference is below 5%, in which case prefer explicit bounded FileChannel reads.
  Retain mmap only as test instrumentation unless it demonstrates a measured win.
  Do not add an application page cache without evidence of repeated page accesses.

Full benchmarks: DMS, trimethylsulfonium and chlorobenzene RHF; ammonium-benzene and
chlorobenzene-water CP. Use frozen packed results as oracle and M14 DIRECT_EXACT
measurements as performance baselines. Emit progress after each SCF iteration and
CP role. No new PBE, physics, approximation or M14 reruns.
