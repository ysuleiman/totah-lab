# Milestone 14 execution protocol

All evidence is `SCREENING_ONLY`. The M13 source, resources, scripts and evidence are
preserved in `validation/milestone-14/m13-reference-source.tar.gz`, with a file
manifest alongside it. Existing public entry points retain their M13 behavior.
M14 entry points and receipts are additive and versioned separately.

* Density: immutable orbitals from the existing validated Lowdin solver bind the
  system, ordered basis, overlap receipt and Fock source. Construct the first Ne/2
  columns using the existing doubled-density sum. Check finite values, symmetry,
  provenance, ascending orbital energies and Tr(PS). Construction mode reports
  `GUARANTEED_BY_CONSTRUCTION_EXACT_ARITHMETIC` and `NOT_MEASURED`, never zero.
  Explicit AUDIT mode measures the residual independently.
* Pure LDA requests COULOMB_ONLY. RHF requests COULOMB_AND_EXCHANGE.
* PACKED_REFERENCE retains M13 integrals and contractions. DIRECT_EXACT visits
  deterministic shell quartets, evaluates canonical AO quartets with the existing
  Gaussian/Hermite/Boys mathematics, applies distinct eightfold permutations and
  discards each block. No screening, threshold, RI, fitting or Cholesky is enabled.
  Pair preparation is quadratic; a reusable primitive Coulomb recurrence workspace
  is bounded by the supported angular momentum. Within one shell quartet, auxiliary
  recurrence values share an exact context cache (at most 4096 contexts, each with
  495 degree-at-most-eight entries). Context keys preserve all pair exponent sums
  and product-center bits. Eviction and shell-boundary clearing only recompute exact
  values; no integral is screened or cached across shell blocks. No full ERI tensor
  is retained.
* The grid remains 120 radial by 590 angular points, unpruned, with the same Becke
  partition and atom/radial/angular ordering. Point and XC kernels are shared with
  the dense reference. Block size is frozen at **512 points** before final validation.
  The 128/512/2048-point water trials are in `validation/milestone-14/block-trials.log`.
  Warm median times were 602.5/599.0/599.2 ms, respectively; 512 gives the best
  measured median with one quarter of the 2048-point workspace. This is a local
  implementation choice, not a claim of a universal optimum.
* SCF uses the existing ScfCycles transitions, DIIS, core guess, 128 iteration cap,
  energy threshold 1e-12 and density threshold 1e-10. Timing is excluded from hashes.

Direct storage removal does not reduce the asymptotic exact ERI compute count:
for fixed shell size it remains quartic per density contraction. Runtime and memory
are reported independently; a direct-path slowdown will not be called a speedup.

## Bounded independent evaluation trial (14-2)

Frozen before parallel validation: eight workers, 16 shell blocks per task,
maximum 16 in-flight tasks; grid blocks remain 512 points with at most 16
in-flight blocks. One worker remains the explicit serial baseline. Workers
perform independent ERI evaluation and pointwise AO/density/functional evaluation.
Every J/K, electron-count, XC-energy and XC-matrix accumulation, and every density
hash update occurs on the caller in unchanged submission/point order. There are
no parallel floating-point reductions. Mutable recurrence sessions are private
to workers; prepared primitive pairs are immutable and shared.

Worker integral/AO/kernel nanoseconds are summed worker durations and may overlap;
they are not additive wall-clock components. Total J/K, XC and SCF measurements
remain wall-clock durations. This trial is not an accepted performance result.
The serial compiled implementation is retained in validation/milestone-14/serial-baseline.

Recurrence workspace arrays are recycled within each worker, with the same 4096
buffer bound. `reset()` invalidates all previous numerical entries before a buffer
is reassigned; there is no cross-shell numerical cache. A water allocation probe
recorded approximately 229.8 MB allocated per warmed serial contraction before
reuse and 75.0 MB after reuse, with identical receipts. These are allocation
volumes, not retained heap, and the probe timings are not final benchmark timings.
Grid coordinate/AO buffers are likewise recycled only after ordered consumption;
at most the fixed in-flight block count is retained within one evaluation.

The final candidate interns Gaussian product contexts by exact exponent-sum and
product-center binary64 values during quadratic pair preparation. A bounded
8192-slot primitive-long table (at most 4096 occupied contexts) replaces per-call
object keys. Collision resolution compares the complete key; there is no tolerance
or probabilistic match. The two product IDs identify precisely the same context
as the previous tuple key. Each recurrence buffer supplies its own three-value
coordinate workspace, reassigned with the identical subtraction operations.
Context eviction, shell transitions and fresh-JVM replay are explicitly tested.
The warmed water allocation probe falls to approximately 36.6 MB per contraction
without changing its receipt. No full-quartet cache is introduced.
