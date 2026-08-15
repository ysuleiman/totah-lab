# Step 0 — Java Neural Quantum Runtime Protocol

Status: `LOCKED_BEFORE_IMPLEMENTATION`

## Scope

Step 0 assembles existing Prometheus Java neural/VMC capabilities. It introduces
no new Hamiltonian, wavefunction physics, molecule, force estimator, or
scientific benchmark. Only the existing hydrogen, helium, and H2 regression
identities are executable. General molecules and general vector forces remain
for later separately authorized steps.

## Authoritative path

`QuantumExecutionRequest -> evidence identity -> GeneratedEvidenceRegistry ->
QuantumExecutionService -> JavaNeuralQuantumBackend -> validation -> exact-bit
artifact -> synchronous GeneratedEvidenceRegistry registration -> return`.

An accepted exact identity exits as `REUSE_EXISTING` before backend selection or
state evaluation. A failure is registered and blocks automatic repetition.

## Frozen kernel policy

- optimizer: `BLOCK_PRECONDITIONED_MATRIX_FREE_SR`;
- dense SR: `REFERENCE_ORACLE`, inaccessible from normal backend options;
- force estimator where applicable: `ANALYTIC_DIFFERENTIAL_SWCT`;
- numerical SWCT: `REFERENCE_ORACLE`, inaccessible from normal backend options;
- NONE/DIAGONAL and AC/direct/parameter-response/failed objective paths are not
  backend options.

The BLOCK matrix-free optimizer uses the existing streamed covariance action,
fixed wavefunction-component block partitions, damping, bounded replayable
sample traversal, and an independently recomputed true residual. Dense SR may
be exercised only by tests as an oracle.

## Regression-only system identities

- `prometheus-regression-hydrogen`: one H nucleus at the origin, energy only;
- `prometheus-regression-helium`: one He nucleus at the origin, energy only;
- `prometheus-regression-h2`: two H nuclei centered on and ordered along z,
  energy and the already-qualified scalar-bond analytic SWCT force mapped to
  the two axial Cartesian components.

The backend rejects every other identity, geometry, unit, state, solver mode,
observable, protocol, and constraint. This restriction is intentional and is
not a general molecular API.

`SINGLE_POINT` evaluates the frozen accepted regression parameter vector.
`OPTIMIZATION` uses BLOCK matrix-free SR. `FORCE_EVALUATION` is supported only
for H2 and uses analytic differential SWCT. Optimizer output parameters are
preserved in the exact result artifact; `QuantumResult` carries the requested
energy/gradient/force values.

## Serialization and durability

Every generated scalar and Cartesian component is written with:

- `Double.toString` human-readable decimal;
- `Double.toHexString` exact hexadecimal floating form;
- raw IEEE-754 bits as 16 lowercase hexadecimal digits;
- explicit units.

The artifact also stores scientific identity, evidence identity, atom-map hash,
atom order, geometry checksum, backend/kernel identities, and provenance. The
file is created once and forced to disk before registry registration. Registry
artifact checksums are verified and the registry is synchronously forced before
success returns.

## Acceptance

All checklist items in the sequential Step 0 authorization must pass. In
particular, a counting-backend test must prove that an immediate identical
request performs zero backend executions, and selection tests must prove that
dense SR, numerical SWCT, DIAGONAL/NONE, and diagnostic estimators cannot be
requested through the normal backend.

Existing infinite-well, H, He, and H2 tests remain unchanged and must pass with
the full Prometheus suite.

## Hard stop

After implementation, tests, documentation, evidence checksums, and commit,
classify exactly `STEP_0_RUNTIME_ASSEMBLY_COMPLETE` or report a blocker. Do not
start general molecular representation, arbitrary-nucleus forces, LiH, target
generation, or fitting.
