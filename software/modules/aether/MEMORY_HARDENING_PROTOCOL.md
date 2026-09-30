# Aether Milestone 13 memory gate

All evidence is **SCREENING_ONLY**. This gate changes canonical hashing and test instrumentation only. Integral equations, contractions, packed ordering, basis data, quadrature, RHF/KS equations, SCF/DIIS policy and convergence thresholds remain fixed.

## Canonical compatibility

The ERI digest consumes UTF-8 text in exactly this order, including the final LF:

```text
aether-ERI-hartree-packed-v1\n
AO_COUNT\n
UNIQUE_ERI_SLOTS\n
Double.toHexString(canonical_zero_or_value)\n
...remaining packed values in ascending slot order...\n
```

Zero canonicalization, hexadecimal formatting, domains and separators are unchanged. The small compatibility tests independently materialize the historical bytes and call the JDK SHA-256 implementation directly. They cover all 18 Milestone 13 molecular/dimer systems in STO-3G and every def2-SVP system with at most 55 AOs (12 systems), for 30 system/basis comparisons. The 55-AO cutoff is fixed before evaluation to bound the materialized historical oracle under a 512 MiB heap. A test-only JCA provider observes the bytes actually delivered by production code to SHA-256, compares them byte-for-byte with the independently materialized oracle, and delegates hashing to the original JDK provider. It is removed after each case and is absent from production and memory workers. Larger systems use frozen receipt checks and streamed-only memory probes.

Grid/AO/density evidence retains its historical two-level digest: each group of at most 1,024 canonical numbers is hashed, then the domain line, ordered block-digest lines and final `count=N` are hashed. There is **no LF after the count**. Only the current block is materialized; the outer list of block digests is now streamed. Empty input and exact/partial block boundaries have independent compatibility tests.

`ContentHash.Accumulator` uses a fixed 4,096-character buffer and the Java 21 UTF-8 encoder buffer. It preserves surrogate pairs across append boundaries and historical replacement of malformed UTF-16. It rejects reuse after finishing. Matrix, vector and DIIS-matrix value hashes use this same infrastructure. The existing string overload no longer creates a complete UTF-8 byte-array copy.

Remaining materialized builders contain basis/center metadata, one bounded numeric block, or the SCF trajectory metadata required by existing receipt APIs. No numerical matrix/tensor is serialized through `toString()`. The DIIS trajectory is materialized once and reused. Public scientific receipt formats are unchanged.

## Measurement interpretation

One fresh Java 21 worker runs at a time during the gate. The existing jobs are allowed to finish before compiling the repair or starting regression/profiling workers. Each actual-molecule component probe constructs the validated def2-SVP tensor, rehashes that same tensor through the production method, and optionally constructs the unchanged 120×590 molecular grid and AO values.

Heap sampling is every 5 ms. Reported sampled peaks are observations, not a claim to capture every transient allocation. Hash measurements distinguish the peak increase during hashing from retained heap change after explicit collection. Formatting creates short-lived scalar strings; cumulative allocation is not retained payload duplication. External process peak RSS is collected where available and remains distinct from Java heap.

The gate runner reads per-command `wait4` resource usage on macOS. Its RSS high-water mark is an OS process/descendant observation, not simultaneous aggregate RSS. The original `/usr/bin/time -l` wrapper was removed after a denied `kern.clockrate` query made it return exit 1 despite all 21 compatibility tests passing. That wrapper failure and its logs are preserved under `validation/milestone-13/memory/time-wrapper-failure`; it was not a scientific failure.

Full-run peaks include the validation harness, reference-file checks and receipt output. The actual-molecule component probes isolate tensor construction, receipt hashing and grid/AO construction from that full validation workload.

For bounded-overhead comparison, a documented fixed young-generation size may be used consistently across the component probes. This controls accumulation of disposable scalar strings; it neither changes the hash algorithm nor forces collection inside the production hashing loop. Full scientific reduced-heap validation uses its recorded JVM configuration independently.

Before measurement, the gate fixes a 32 MiB young generation for component probes, a maximum 40 MiB transient hashing delta and a maximum 1 MiB retained hashing delta after collection. These operational memory bounds do not affect scientific tolerances. The observed component heap must also contain at least its known ERI and grid/AO raw arrays. A failed bound requires investigation, not automatic heap escalation.

`SCF_MATRIX_BYTES` from completed evidence counts distinct reachable primitive double arrays using identity, excluding array/object headers and boxed scalar metadata. Grid/AO arrays and grid density are separate categories. It measures retained evidence, not the peak sum of all temporary eigensolver and DIIS workspaces. These limitations must accompany the component table.

Counterpoise complex/ghost contexts use the full dimer AO count. Their tensors are constructed sequentially by the existing implementation. A complex-tensor component probe is labeled as such; it is not represented as a full five-calculation CP completion. Full CP success is established by the independent component-energy validation test.

## Storage projection

For `N` AOs, `M=N(N+1)/2` unordered AO pairs are stored as a lower triangle of pairs. The exact count is `M(M+1)/2` binary64 slots, or `4*M*(M+1)` raw bytes. Array headers and all other scientific state are additional. See [packed-eri-scaling.csv](validation/milestone-13/packed-eri-scaling.csv).

There is no screening, direct SCF, density fitting, Cholesky representation, disk storage or approximate threshold in this gate. LDA additionally stores `8*grid_points*N` AO-value bytes and four grid-coordinate/weight arrays; therefore packed ERIs need not dominate LDA memory at the tested sizes.

## Startup diagnostics

The original two C/N/O replay failures recorded child exit code 1, without retained stderr. Their original cause must remain unclassified unless additional evidence is recovered. An isolated rerun passed. The gate retains new child stderr under `target/child-jvm-diagnostics`, bounds each small child heap to 256 MiB, and runs the regression serially after large jobs exit. A passing rerun alone does not establish the cause of the earlier failures.

Completion is controlled by `validation/milestone-13/memory-gate.json`; an unmeasured or failed gate cannot promote Milestone 13 to PASS.
