# Controlled Experiment E3 - Analytic Differential SWCT

Status: `LOCKED_BEFORE_IMPLEMENTATION_AND_EXECUTION`

## Frozen evidence

The classifications `DERIVATIVE_AWARE_OBJECTIVE_CORRECTNESS_DEFECT` and
`EXACT_FINITE_OBJECTIVE_DIFFERENTIATION_NOT_SUPPORTED` remain immutable. This
experiment changes no state, force mathematics, sampling, weight, Jacobian,
threshold, or H2 parameter. It tests computational equivalence only.

## Compared implementations

- Reference: frozen `HydrogenMoleculeSpaceWarpForceEstimator`, central step
  `1e-3 bohr`, five state/local-energy traversals per accepted configuration.
- Candidate: one fused primal-plus-directional state graph per configuration,
  using the derivation in `ANALYTIC_DIFFERENTIAL_SWCT_DERIVATION.md`.

Frozen radii: R=1.0, 1.4, and 3.0 bohr. Frozen shared 20-parameter H2 state,
two-center Halton samples, exponent 1.15, skip 1009, batch maximum 512.
Qualification uses 2,000 identical configurations per radius. Performance uses
72,000 identical configurations per radius only after qualification passes.

## Pre-aggregate derivative oracle

Before the 72,000-point benchmark, compare analytic and numerical values on
every qualification configuration:

- total local-energy directional derivative;
- log-amplitude plus half-Jacobian directional derivative;
- base force contribution;
- bare local-energy derivative.

Locked tolerances:

- absolute log-response difference `<=2e-5` for every valid point;
- absolute local-energy derivative and bare-derivative difference `<=5e-4`
  for at least 99.9% and maximum `<=5e-3`;
- absolute base-force difference `<=5e-4` for at least 99.9% and maximum
  `<=5e-3`;
- no non-finite mismatch or change in accepted configuration count.

Failure is `ANALYTIC_DIFFERENTIAL_SWCT_NOT_EQUIVALENT`, unless primitive AD,
sign, unit, ordering, or replay audits fail, which is
`ANALYTIC_DIFFERENTIAL_SWCT_CORRECTNESS_DEFECT`.

## Aggregate equivalence gates

On each radius independently:

- mean force absolute difference `<=5e-5 Ha/bohr`;
- variance difference `<=max(5e-5 Ha^2/bohr^2, 0.5% of numerical variance)`;
- standard-error difference `<=0.5%` relative, or exact agreement if zero;
- nuclear antisymmetry `F_A + F_B <=1e-12 Ha/bohr` by the centered-coordinate
  construction;
- transverse force components are structurally zero and paired reflection
  audits must be `<=1e-12 Ha/bohr`;
- primary/replay results and raw IEEE-754 evidence fields match bitwise.

No trusted-force improvement is required and cannot rescue inequivalence.

## Performance gate

Scientific equivalence is evaluated first. Then report traversals,
local-energy evaluations, directional-AD graph passes, wall time, process CPU
time when available, peak observed heap, and allocation volume when available.

`FASTER` requires:

- analytic state/local-energy traversals `<=N` versus numerical `5N`; and
- median analytic wall time across three exact replays at least 20% lower than
  median numerical wall time on the same 72,000-point stream at every radius;
- peak heap no more than 1.5x numerical peak and always below 1 GiB.

Traversal reduction without wall-time improvement classifies equivalent but
not faster.

## Serialization

Every accepted scalar stores field name, units, raw 64-bit IEEE-754 hexadecimal
bits, `Double.toHexString`, and a human-readable decimal. Integrity uses the raw
bits. Writes are synchronous and all artifacts are checksummed.

## Classifications

- `ANALYTIC_DIFFERENTIAL_SWCT_EQUIVALENT_AND_FASTER`
- `ANALYTIC_DIFFERENTIAL_SWCT_EQUIVALENT_NOT_FASTER`
- `ANALYTIC_DIFFERENTIAL_SWCT_NOT_EQUIVALENT`
- `ANALYTIC_DIFFERENTIAL_SWCT_CORRECTNESS_DEFECT`
- `ANALYTIC_DIFFERENTIAL_SWCT_NOT_FEASIBLE`

## Hard stop

No derivative-aware loss, H2 retuning, estimator change, dense third-order
tensor, matrix-free SR, structured preconditioner, LiH, larger system, or next
experiment is authorized here. Freeze the result and stop.
