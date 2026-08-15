# Prometheus Current State and Completion Plan

Audit date: 2026-08-15  
Repository branch: `codex/prometheus-neural-qm`  
Audited commit: `f397f1a44`  
Scope: repository, tests, persisted evidence, frozen reports, and current runtime wiring. No scientific calculation was launched.

## EXECUTIVE STATUS

Prometheus is **a credible research platform with several qualified numerical components, but it is not a production molecular-QM engine**.

What exists today:

- a Prometheus-owned Java neural/VMC core that solved and validated the infinite well, hydrogen, and interacting-electron helium benchmark problems;
- deterministic sampling, bounded H2 streaming, first/second derivatives, and H2-specific higher directional derivatives;
- an H2-specific analytic differential SWCT kernel that is equivalent to the numerical reference and about 1.7–2.1 times faster;
- a validated matrix-free covariance action and a narrowly qualified BLOCK-preconditioned matrix-free SR solve;
- a strong scientific identity, provenance, durable evidence registry, reuse planner, failure memory, and immutable QM-target boundary for externally generated evidence;
- authoritative readers for historical PySCF/geomeTRIC/Amber artifacts.

What does not exist today:

- a concrete Java `QuantumBackend` in the main runtime;
- an arbitrary-nucleus, arbitrary-electron molecular wavefunction/Hamiltonian;
- general 3D vector nuclear forces;
- a molecular system larger or less symmetric than H2 that passes an energy-and-force gate;
- a qualified end-to-end `geometry -> E_QM + F_QM -> reusable evidence` Java runtime;
- a completed 36-point force dataset or a ForceBalance fitting implementation.

The shortest honest status is:

`COMPONENTS_QUALIFIED_RUNTIME_NOT_ASSEMBLED_GENERAL_MOLECULAR_QM_NOT_READY`

The older external-QM campaign is a separate lineage. Its pilot was scientifically qualified, but its runner invokes Python and the campaign is incomplete. Under the current zero-Python direction it is obsolete as a normal execution route, although its accepted evidence and raw artifacts remain valid and reusable.

## WHAT IS QUALIFIED

“Qualified” below is always scope-limited; it does not imply general molecular production readiness.

| Capability | Qualified? | Evidence | Scope | Limitation |
|---|---:|---|---|---|
| Infinite-well benchmark | Yes | `JAVA_NEURAL_QUANTUM_END_TO_END_VALIDATED` | 1D one-particle exact-reference regression | Benchmark oracle only |
| Hydrogen | Yes | `HYDROGEN_COULOMB_GATE_PASSED` | One electron, one Coulomb center, 3D derivatives/cusp | Not molecular |
| Helium | Yes, benchmark gate | `HELIUM_INTERACTING_ELECTRON_GATE_PASSED` | Two correlated electrons; cusps, symmetry, 6D derivatives | About 0.00685 Eh energy error; not chemical accuracy |
| H2 molecular PES | No | `H2_MULTI_GEOMETRY_GATE_FAILED`; Gen-2 also failed | Smooth deterministic nine-point curve | Compressed-region and global accuracy gates failed |
| Geometry-conditioned H2 | No | `GEOMETRY_CONDITIONED_H2_FORCE_FAILED` | Shared scalar bond coordinate | PES/force accuracy and optimizer convergence failed |
| Nuclear-force estimator capability | No global qualification | `FROZEN_BASELINE_REPLAY_MISMATCH` | H2 estimator diagnostics | Locked prerequisite failed; AC-ZVZB formally unevaluated |
| Analytic differential SWCT | Yes | `ANALYTIC_DIFFERENTIAL_SWCT_EQUIVALENT_AND_FASTER` | H2 scalar bond force; fixed state/sampler | Not arbitrary nuclei or vector forces |
| Matrix-free covariance action | Yes | E4 operator vectors passed; synthetic scaling passed | SR action and storage behavior | Not wired into production optimizer |
| Historical global matrix-free SR | No; frozen negative | `MATRIX_FREE_SR_NOT_EQUIVALENT` | E4 formal result | Must never be rewritten |
| BLOCK-preconditioned matrix-free SR | Yes, narrowly | `BLOCK_PRECONDITIONED_MATRIX_FREE_SR_QUALIFIED` | Frozen 20-parameter H2 control, fixed blocks | Production PCG lacks true-residual acceptance path; block layout is H2-specific |
| Deterministic replay | Partially | Bitwise H2/SWCT/SR replay | Those frozen paths | Not a universal runtime invariant |
| Bounded-memory streaming | Yes as mechanism | Streaming tests, SWCT audit, matrix-free scaling | H2 sample traversal | No general molecular sampler/source interface |
| Arbitrary-nucleus 3D force support | No | No implementation/evidence | None | Major missing capability |
| General molecular force support | No | No implementation/evidence | None | Major missing capability |
| Larger-than-H2 molecular validation | No | No evidence | None | Major scientific gate |
| Production-QM qualification | No | No end-to-end runtime or molecular gate | None | Final readiness gate absent |
| Scientific identity/reuse | Yes for registered external evidence | lifecycle/restart/dedup/checksum/planning tests and pilot | Accepted primary evidence | Not integrated with Java neural computations |

## HISTORICAL FORMAL CLASSIFICATION VS COMPONENT INTERPRETATION

Frozen classifications remain authoritative for their experiments:

- E4 remains `MATRIX_FREE_SR_NOT_EQUIVALENT`.
- H2 Generation 1 remains `H2_MULTI_GEOMETRY_GATE_FAILED`.
- H2 Generation 2 remains `H2_GENERATION2_MULTI_GEOMETRY_GATE_FAILED`.
- Geometry-conditioned H2 remains `GEOMETRY_CONDITIONED_H2_FORCE_FAILED`.
- The force-estimator study remains `FROZEN_BASELINE_REPLAY_MISMATCH`.
- Derivative-aware training remains `DERIVATIVE_AWARE_OBJECTIVE_CORRECTNESS_DEFECT`.
- Exact finite-objective training remains `EXACT_FINITE_OBJECTIVE_DIFFERENTIATION_NOT_SUPPORTED`.

Those global decisions do not erase component evidence:

- E4 validated streamed `S*v`; E5 later qualified the BLOCK-preconditioned solver while leaving E4 untouched.
- The analytic differential SWCT implementation passed equivalence and performance gates even though the underlying H2 wavefunction/force curve is not scientifically qualified.
- Exact derivative machinery passed its independent finite-difference preflight even though the derivative-supervised objective was scientifically unsuccessful.
- H2 continuation reduced work and wall time but did not repair the H2 scientific model.

## WHAT IS GOOD BUT LIMITED

### GOOD / RETAIN

- Immutable state/evaluation value objects and the small Java neural core.
- `SecondOrderJet` first/second spatial differentiation.
- `DirectionalSecondOrderJet` and the fused directional H2 evaluator as the prototype for higher mixed derivatives.
- Deterministic samplers and bounded H2 batches.
- The state-scoped numerical computation DAG and its mandatory/cache/recompute
  policies as an architectural candidate; retain it, but do not call it active
  until real VMC/SR/force kernels use it.
- Streaming local-energy evaluation.
- Analytic differential SWCT, retaining numerical SWCT as its oracle.
- `StreamingCovarianceOperator`.
- The qualified fixed wavefunction-component BLOCK preconditioner and true-residual grading demonstrated by E5.
- Dense SR as the small-system correctness oracle.
- Canonical/scientific identity, atom mapping, provenance, accepted/rejected evidence states, and comparability rules.
- `GeneratedEvidenceRegistry`, durable artifact checks, immediate reuse, failure memory, and `FrozenQmTargetDataset`'s read-only boundary.
- Authoritative artifact readers; trusted historical calculations should be recovered, not repeated.

### KEEP ONLY AS REFERENCE OR DIAGNOSTIC

- Dense SR: small-system oracle, not scalable production solver.
- Numerical five-traversal SWCT: reference for analytic SWCT.
- NONE preconditioning: floating-point-sensitive diagnostic.
- Brute-force parameter-response audit: underdetermined and too expensive.
- Direct HF/Pulay, correlated finite difference, AC-ZV, and AC-ZVZB implementations: research comparators until a clean estimator qualification succeeds.
- Exact finite-objective differentiator: derivative oracle only.
- H2 continuation/warm-start: performance evidence only until a scientifically passing model uses it.
- Infinite well, H, and He states: permanent regression benchmarks, not generic molecular states.
- All one-shot validation mains: evidence reproduction/reference tools, not production API entry points.

## WHAT FAILED

### DO NOT USE FOR PRODUCTION

- Both H2 wavefunction generations as production molecular states.
- The geometry-conditioned H2 state as a production PES or force source.
- The derivative-aware objective implementation.
- The exact finite-objective training path.
- DIAGONAL matrix-free preconditioning under the locked accuracy standard.
- The current baseline PCG's recursive residual as an acceptance criterion; production acceptance must independently recompute `b-Ax`.
- AC-ZVZB as a claimed qualified estimator—the formal experiment never reached its evaluation gate.
- The parameter-response correction as a force correction.

Frozen implementations and evidence must remain available for regression and provenance; “do not use” does not mean delete.

## WHAT IS OBSOLETE

- `ForceCampaignRunner`, `LockedPyscfEnergyGradientExecutor`, and `LockedPyscfForceTargetExecutor` as normal execution paths under the current **zero-Python** architecture. They remain useful only for reading/reconciling historical evidence until an explicit migration decision.
- The campaign progress file is not live authority: it reports four completed/reused targets and three `RUNNING` targets last updated on 2026-08-14, but no frozen 36-target dataset exists. Those `RUNNING` values are stale execution state, not reusable evidence.
- Standard ff-style external strategy classes, including `ForceBalanceStrategy`, are integration skeletons rather than executable optimizers.

## ACTUAL RUNTIME PATH

There is no single current production path. There are three disconnected paths.

### A. Java neural/VMC research path actually executed by validation mains

```text
validation main
  -> constructs H2/He/H benchmark-specific neural state directly
  -> constructs benchmark-specific deterministic point set/batches
  -> state.evaluateWithDerivatives() or evaluateWithGeometryDerivatives()
  -> Hamiltonian potential + (-1/2 Laplacian / psi)
  -> dense benchmark-specific SR optimizer (where training is used)
  -> benchmark energy/variance
  -> H2-specific force estimator when requested
  -> bespoke CSV/JSON/report files
```

Representative real H2 classes:

```text
HydrogenMoleculeGeneration2Validation
  -> HydrogenMoleculeCorrelatedState
  -> HydrogenMoleculeImportanceBatches
  -> StochasticReconfigurationOptimizer      [dense SR]
  -> HydrogenMoleculeStreamingRayleighEvaluator
  -> bespoke checkpoint/result writers
```

The qualified matrix-free and analytic SWCT components are not the default optimizer/force path. `MatrixFreeSrExperiment` and `AnalyticDifferentialSwctExperiment` invoke them from validation code only.

### B. Java quantum execution architecture

```text
QuantumExecutionRequest
  -> QuantumBackendSelector
  -> QuantumExecutionService
  -> QuantumBackend
  -> QuantumResult
```

This is only an architectural contract. Main code contains **zero concrete `QuantumBackend` implementations** and no runtime wiring to the neural/VMC engine or evidence lifecycle.

### C. External-QM evidence path

```text
CalculationSpecification + EvidenceIdentity
  -> GeneratedEvidenceLifecycle.executeOrReuse()
  -> GeneratedEvidenceRegistry lookup
  -> if absent: legacy PySCF executor (Python process)
  -> validate raw result and force=-gradient
  -> fsync artifacts
  -> atomic JSONL registry replacement and in-memory refresh
  -> future request returns REUSE_EXISTING
  -> FrozenQmTargetDataset read-only boundary
```

This path has strong persistence/reuse behavior, but its executor violates the current zero-Python architecture and it does not use the Java neural solver.

## REUSE / RECOMPUTATION AUDIT

### Correct reuse behavior

- `GeneratedEvidenceLifecycle` checks accepted primary evidence before execution.
- `GeneratedEvidenceRegistry` verifies payload and artifact checksums on reload.
- Duplicate scientific identity with differing content is rejected.
- Accepted primary evidence becomes reusable after synchronous durable registration.
- Failed attempts are recorded and auxiliary finite-difference evidence is not promoted to primary.
- `PlanningEvidenceLoader` combines canonical evidence with accepted generated primary evidence.
- `FrozenQmTargetDataset` exposes accepted references without executor authority.

### Remaining recomputation risks

1. **Java neural calculations bypass the registry.** Benchmark runners can recompute identical state/energy/force work because their identity is not resolved through `GeneratedEvidenceLifecycle`.
2. **`QuantumExecutionService` bypasses reuse.** It selects and executes a backend directly; no registry lookup, durable registration, or failure memory surrounds it.
3. **No reusable neural checkpoint service exists.** Continuation and checkpoints are bespoke runner logic and cannot be safely shared across studies.
4. **Force estimators independently reevaluate the same states.** The estimator panel does not consume one shared immutable state-evaluation stream. AC-ZVZB explicitly performs a second state pass for contributions.
5. **Dense, matrix-free, and force validation runners duplicate moment accumulation and pivoted linear solves.** Some duplication is oracle separation, but ownership is unclear and production code could select the wrong copy.
6. **Matrix-free operator passes recompute O-vectors.** This is required for bounded memory today, but no retention policy is connected to the operator despite the separate numerical computation-graph abstractions.
7. **Historical experiment replay can recompute expensive controls.** One-shot guards are inconsistent: some runners reject existing outputs, some load CSV checkpoints, and some overwrite via `Files.writeString`.
8. **Stale progress is possible after interruption.** The 36-point progress JSON still says three targets are `RUNNING`; registry evidence, not progress state, must remain authoritative.
9. **Canonical and generated stores are merged into a copied bundle.** New registrations update the registry immediately, but an already-created combined planning bundle/index is not live and must be reloaded.
10. **The numerical computation DAG is disconnected.** Its reuse policies pass
    unit tests, but current neural/VMC/SR/force evaluations do not execute
    through it, so it prevents no real recomputation yet.

Required production invariant: the future Java neural backend must be invoked only through an execution lifecycle that performs scientific-identity lookup, validates/registers results synchronously, and updates the active index before returning success.

## SERIALIZATION AUDIT

- Generated registry JSON is deterministic, checksummed, atomically replaced, forced to disk, and reloaded with verification. Jackson's emitted doubles are round-trip capable.
- Newer SWCT/SR evidence also records raw IEEE-754 bits and hexadecimal floats.
- Many historical neural validation files serialize with `%.16g`. Those decimals are scientifically readable but are not guaranteed bit-round-trip representations. They remain immutable historical evidence and must not seed an exact replay solely from presentation decimals.
- Checkpoint CSVs generally use `%.16g` and have inconsistent checksum/one-shot semantics.
- The desired exact-bit scientific envelope is documented but not uniformly implemented across result types.

## CODE-HEALTH RISKS

1. **Validation/production coupling:** qualified kernels live beside large experiment mains; runtime authority is determined by who directly constructs a class, not by a controlled capability registry.
2. **No concrete backend:** `QuantumBackend` architecture is disconnected from every neural state, sampler, optimizer, force estimator, and evidence registry.
3. **Duplicate solvers:** pivoted elimination and covariance construction appear independently in `StochasticReconfigurationOptimizer`, geometry-conditioned SR, derivative-aware diagnostics, exact-objective code, parameter response, fixed preconditioners, and matrix-free experiments.
4. **Superseded defaults:** dense SR remains the real optimizer; qualified matrix-free BLOCK SR is validation-only. Numerical and analytic SWCT coexist without a production selector.
5. **Weak scientific typing:** many methods accept primitive `double` radii, damping, tolerances, charge-like values, and raw arrays. Units/order are often carried by convention rather than types.
6. **Large classes:** `LegacyPhase2ArchiveIngester` (1316 lines), authoritative audit/report readers (roughly 300–500 lines), and several validation runners combine parsing, computation, decisions, and rendering.
7. **Hidden mutable accumulation:** numerous private mutable moment/accumulator classes are single-thread assumptions. They are not safe for parallel reductions without explicit worker-local ownership.
8. **Deterministic but naive reductions:** most sums are ordered plain-double accumulation. E5 demonstrated that a `1e-12` decision can depend on compensated reductions/preconditioning.
9. **Allocation pressure:** derivative and matrix-free paths allocate arrays per state/operator observation; H2 sizes hide the scaling cost.
10. **Checkpoint inconsistency:** no shared scientific identity/checksum/lifecycle model for optimization checkpoints.
11. **Incomplete immutability:** records often defensively copy lists, but internal numerical records and arrays are not uniformly deep-copied; package-private experiment controls expose arrays internally.
12. **CLI obsolescence:** old PySCF runners remain executable despite the zero-Python direction; nothing centrally prevents accidental selection.
13. **No normal-runtime gate:** failed H2 and force classes are public and directly constructible. Their frozen status is documentary, not enforced by an API boundary.
14. **No general antisymmetry/fermion layer:** current correlated states hard-code small singlet forms.

No refactoring was performed in this audit.

## REMAINING CRITICAL PATH

The user's expected milestones are directionally correct, but one prerequisite must come first: assemble qualified components behind the scientific-identity execution lifecycle.

### 0. Assemble and gate the runtime

- Implement one concrete Prometheus-owned Java neural `QuantumBackend`.
- Wrap it with execute-or-reuse, synchronous evidence registration, artifact checksums, failure memory, and immutable results.
- Make failed/diagnostic states and estimators impossible to select from the normal runtime.
- Integrate BLOCK-preconditioned matrix-free SR with independent true-residual grading; retain dense SR as a small-system oracle.
- Select analytic differential SWCT while retaining numerical SWCT only as a regression oracle.

This is engineering integration, not a new physics experiment, but it is required before later scientific results can be called production evidence.

### 1. General arbitrary-nucleus/electron molecular representation

- General Coulomb Hamiltonian with typed nuclei, electrons, spin, charge, geometry, and units.
- General antisymmetric/fermionic neural state, not an H2-specific singlet ansatz.
- Deterministic bounded sampler and shared state-evaluation bundle for energy, parameter derivatives, and nuclear derivatives.

This missing representation—not solver speed—is the largest capability gap.

### 2. General 3D vector nuclear forces

- Generalize the qualified analytic directional/SWCT machinery from one H2 bond coordinate to all `3*N_nuclei` directions.
- Validate vector sign, units, translation/rotation constraints, permutation symmetry, and finite-difference agreement.
- Reuse one state evaluation for all compatible observables/directions where mathematically possible.

### 3. Multi-nuclear, larger-than-H2 validation

- Use the smallest system that tests heteronuclear/multinuclear behavior and nontrivial vector forces; LiH remains a reasonable candidate, but protocol selection is a future locked decision.
- Require trusted energy and Cartesian-force references across more than one geometry, not a single equilibrium point.
- Preserve H/He/H2 regressions without reopening their frozen model-development cycles.

### 4. Production-QM qualification

- Run an end-to-end `geometry -> E + Cartesian F -> validated registered reusable evidence` gate.
- Validate multiple geometries, independent references, deterministic/restart behavior, exact atom order, units, force=-gradient conventions, bounded memory, and failure recovery.
- Only this milestone can promote the Java backend to production molecular-QM status.

### 5. Actual target evidence generation

- Resolve every requested target against canonical and generated evidence first.
- Generate only missing identities.
- Freeze a checksummed energy+force dataset. Existing accepted TSL minima, Hessians, RESP, and probes remain reusable; Hessians must not be recomputed.

### 6. Force-field fitting and held-out validation

- Implement or integrate a separately qualified optimizer consuming `FrozenQmTargetDataset` read-only.
- ForceBalance must never execute QM.
- Predeclare model form, development/holdout split, regularization, parameter bounds, and acceptance gates.
- Freeze the candidate before one-shot holdout evaluation.

### Practical warning

The Java neural engine is several scientific gates away from producing trustworthy TSL forces. If near-term TSL force-field evidence is the priority, the shortest practical route is to ingest externally generated authoritative QM artifacts through the already-qualified readers/registry—without allowing Prometheus to invoke Python—then freeze the dataset and qualify the fitting path. That route does not make the Java neural engine production-ready; it separates near-term evidence acquisition from the longer neural-QM program.

## RESEARCH BACKLOG

Documented, not on the current critical path and not executed:

- projected Hessian/Hessian-vector products;
- PySCF `gen_hop` analytic HVP (historical/external reference only under zero-Python direction);
- Hutchinson/SLQ stochastic curvature;
- PHL;
- derivative-conditioned networks;
- adaptive geometry allocation;
- curvature-guided QM;
- delta-learning/multifidelity;
- local residual wavefunctions.

Existing TSL minima already possess full accepted Hessians. They must be reused. Directional HVP is a forward-looking large-system/selected-direction capability, not justification to recompute those Hessians.

## RECOMMENDED NEXT IMPLEMENTATION

The next implementation should be **runtime assembly and authority control**, not another wavefunction experiment:

1. create a concrete Java neural backend adapter around the currently accepted state/evaluation interfaces;
2. place `GeneratedEvidenceLifecycle`/registry reuse ahead of backend execution;
3. expose only qualified BLOCK matrix-free SR and analytic SWCT capability selections, with dense/numerical oracles inaccessible from normal production selection;
4. emit exact-bit, unit-explicit, checksummed energy/force evidence;
5. demonstrate the assembled path on existing H/He/H2 regression inputs without claiming new molecular qualification.

After that integration passes, preregister the arbitrary-nucleus/vector-force capability. Do not start LiH before the general representation and vector-force interface exist.

## Audit provenance

- Source inventory: `software/modules/prometheus/src/main/java` and `src/test/java`.
- Frozen evidence: `analysis/prometheus/java-neural-*`.
- External evidence registry: `analysis/prometheus/generated-evidence-registry/generated-evidence.jsonl`.
- Incomplete campaign state: `analysis/prometheus/force-campaign-36`.
- Latest full Prometheus test run before this audit: 269 tests, 0 failures, 0 errors.
- Machine-readable component decisions: `PROMETHEUS_CAPABILITY_INVENTORY.csv`.
