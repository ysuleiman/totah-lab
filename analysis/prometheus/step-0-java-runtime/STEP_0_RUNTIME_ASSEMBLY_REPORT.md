# Prometheus Step 0 Java Runtime Assembly

## Classification

`STEP_0_RUNTIME_ASSEMBLY_COMPLETE`

This report covers Step 0 only. No Step 1 capability was started.

## Executable path

The assembled path is:

`QuantumExecutionRequest` → scientific/evidence identity → persistent registry lookup → `REUSE_EXISTING` or capability-selected Java backend → bounded deterministic neural-VMC evaluation → validation → synchronous JSONL registration → immediate in-memory reuse refresh.

The concrete backend is `JavaNeuralQuantumBackend`. It is stateless, thread-safe, Java-only, and accepts only the three frozen regression molecule identities and exact Step 0 protocol. The executor has no authority to alter the immutable `CalculationSpecification`.

## Accepted numerical architecture

- H and He use their frozen validated neural parameter vectors and deterministic bounded quadrature/importance sets.
- H2 energy uses the frozen geometry-conditioned state and bounded deterministic importance batches.
- H2 optimization selects only `BLOCK_PRECONDITIONED_MATRIX_FREE_SR`, using the streamed covariance operator, fixed five-block preconditioner, and independently recomputed true residual.
- H2 nuclear force selects only `ANALYTIC_DIFFERENTIAL_SWCT` and emits an exact negative-gradient pair in canonical atom order.
- Dense SR and numerical SWCT remain reference/diagnostic implementations and are not selectable backend policies.
- Failed diagnostic branches are not exposed through the concrete backend.

## Persistence and exact representation

Successful execution writes an authoritative JSON artifact with `CREATE_NEW`, forces the file to stable storage, validates its SHA-256, registers accepted evidence through the atomic/fsync JSONL registry, and confirms that the active index can immediately return it. A second identical request therefore returns `REUSE_EXISTING` without backend invocation. A restart reconstructs the same behavior from JSONL.

Every stored floating-point result includes:

- round-trip decimal representation;
- Java hexadecimal floating-point representation;
- raw IEEE-754 bits;
- explicit units;
- canonical atom order;
- request scientific identity, evidence identity inputs, geometry checksum, atom-map hash, artifact checksum, and execution provenance.

Failures are registered with their scientific identity and explicit reason before being rethrown.

## Mathematical and architectural provenance

The runtime composes previously frozen Prometheus-owned implementations rather than adding new physics:

- neural forward/first/second derivatives and Laplacians: Prometheus Java autodifferentiation graph;
- variational energy: Rayleigh/VMC estimators already qualified by infinite-well, H, He, and H2 evidence;
- optimization: stochastic reconfiguration as a streamed covariance linear system, solved by fixed-preconditioner conjugate gradients with independently recomputed true residual;
- force: analytic differential space-warp coordinate transformation (SWCT), preserving the previously documented Sorella–Capriotti formulation and derivative audit.

The previous disconnected state—validated scientific kernels without a production backend/runtime lifecycle—was insufficient because callers could not obtain a validated, persisted, immediately reusable `QuantumEvidence` through the execution boundary.

## Complexity and reuse

The matrix-free SR operator retains bounded batch state and block matrices rather than a dense global covariance. Each operator application streams the deterministic sample source. Mandatory per-state neural results are evaluated once in each valid traversal; large sample tensors are not retained indiscriminately. Registration is synchronous, so every successful CPU expenditure permanently expands the verified evidence base.

## Validation

Targeted tests cover:

- true-residual PCG on an independently checkable SPD system;
- concrete H2 energy/force execution;
- exact `force = -gradient` sign and unit convention;
- analytic SWCT selection and numerical-SWCT exclusion;
- BLOCK matrix-free SR selection and dense-solver exclusion;
- exact-bit artifact fields and atom ordering;
- successful result registration, immediate reuse, no second executor call, and restart/reload reuse.

The pre-existing hydrogen, helium, H2, derivative, force, persistence, identity, and architecture regressions remain in the full Maven suite.

## Negative evidence retained

No historical failure or diagnostic result was deleted or rewritten. Dense/reference solvers, numerical SWCT, failed derivative-aware objective work, compressed-H2 limitations, and solver-convergence evidence remain preserved in their original artifacts. This assembly only prevents those nonaccepted branches from being selected as production runtime policy.

## Decision

Step 0 is complete: Prometheus now has one executable Java-only scientific runtime path with immutable requests, qualified solver selection, exact artifacts, validation, durable synchronous registration, and deterministic reuse. Work stops here as required by the sequential plan.
