# Controlled Experiment E4 - Matrix-Free Stochastic Reconfiguration

Status: `LOCKED_BEFORE_IMPLEMENTATION_AND_EXECUTION`

## Frozen predecessor

`ANALYTIC_DIFFERENTIAL_SWCT_EQUIVALENT_AND_FASTER` remains frozen. This unit
does not alter SWCT, H2 targets, state parameters, sampling, or training. H2 is
only a 20-parameter numerical-control system.

## Mathematical system

For logarithmic derivatives `O_s` and normalized sample weights `p_s`, define

`mu = sum_s p_s O_s`, `Otilde_s = O_s-mu`,

`S v = sum_s p_s Otilde_s (Otilde_s^T v)`.

Solve `(S + lambda I) delta = -g`, with the frozen control's
`lambda=1e-3`. The explicit control constructs S and uses its existing pivoted
dense elimination. The candidate streams deterministic samples for every
operator application and never retains an `N x P` derivative matrix or a
`P x P` covariance.

The RHS, mean derivatives, energy, and covariance control are generated from
the same frozen state bundle on one initial pass. Matrix-free Krylov operator
passes recompute only the state-derived O-vector required by the operator;
local energy is not recomputed or accumulated during those passes. Every pass
and state traversal is counted.

## Frozen control

- Shared 20-parameter geometry-conditioned H2 state and exact frozen parameter
  vector from the accepted historical control.
- Radii `[0.8,1.0,1.2,1.4,1.6,2.0,3.0,4.0,6.0]` bohr, equally weighted.
- 2,500 deterministic two-center Halton configurations per radius, exponent
  1.15, skip 43, batches <=512.
- Identical g, damping, state, atom/parameter order, and configuration stream.
- Fixed test vectors: unit basis e0, alternating normalized vector, and a
  deterministic SHA-derived dense vector.

## PCG

Use conventional preconditioned conjugate gradients because all preconditioners
are fixed during one solve. Relative residual tolerance `1e-12`, absolute floor
`1e-14`, maximum 200 iterations. Breakdown/non-positive curvature is a solver
failure. Flexible PCG is not used; adaptive preconditioners are outside scope.

## Preconditioners

Preregistered panel only:

1. `NONE` - identity.
2. `DIAGONAL` - exact streamed diagonal of S plus lambda.
3. `BLOCK_BY_WAVEFUNCTION_COMPONENT` - five fixed four-parameter blocks matching
   the encoder outputs: localization response `[0,4)`, neural base `[4,8)`, and
   hidden amplitudes `[8,12)`, `[12,16)`, `[16,20)`. Each small regularized block
   is inverted by pivoted dense elimination. No cross-block term is stored.

Low-rank and Kronecker approximations are not used: the present state offers no
preregistered factorization whose approximation error is independently bounded.

## Equivalence gates

- For every fixed vector, matrix-free versus explicit `S v`: maximum component
  error `<=1e-11`, RMS `<=2e-12`.
- PCG update versus explicit update: maximum error `<=1e-9`, RMS `<=2e-10`.
- Predicted regularized quadratic decrease absolute difference `<=1e-10`.
- Final relative residual `<=1e-12` and absolute residual finite.
- Primary/replay update, residual, iterations, and operator count match bitwise.
- No state, parameter, sample, sign, or unit mismatch.

Any arithmetic/operator mismatch is `MATRIX_FREE_SR_NOT_EQUIVALENT`; invalid
operator behavior, nonfinite state, hidden dense allocation, or identity/order
failure is `MATRIX_FREE_SR_CORRECTNESS_DEFECT`. Krylov breakdown or failure to
converge is `MATRIX_FREE_SR_SOLVER_INSTABILITY`.

## Performance and structured-preconditioner gates

Record iterations, operator applications, streamed sample passes, state
traversals, local-energy evaluations, wall time, process CPU time, allocated
bytes when available, and peak observed heap.

Structured preconditioning is beneficial only if it reduces either iterations
or total wall time relative to NONE without changing the update tolerance.
Matrix-free control wall time must remain below 10x explicit construction plus
solve time. Matrix-free covariance storage must be `O(P+B*P+sum block^2)`, not
`O(P^2)`; instrumentation must show no dense `P x P` candidate allocation.

## Locked scaling experiment

Synthetic positive-definite SR controls use `P=[64,256,1024]`, `N=4096`, fixed
SHA-derived deterministic feature streams, damping `1e-3`, and the same three
preconditioners with contiguous blocks of size 16. The feature operator is
generated and consumed by batches of 128; no `N x P` storage.

At P=64 and 256, also construct the explicit covariance and measure allocation,
wall time, and agreement. At P=1024, measure explicit covariance allocation and
one matrix action but do not perform cubic dense elimination; record that scope
explicitly. Matrix-free must converge at all sizes. A scalable result requires:

- measured candidate covariance storage grows linearly in P apart from fixed
  block storage;
- explicit covariance bytes follow measured P-squared growth;
- P=1024 matrix-free peak heap remains <1 GiB;
- no update/residual equivalence failure at P=64/256;
- no wall time >10 minutes for any scaling row.

Synthetic scaling establishes numerical/storage behavior only, not scientific
H2 performance.

## Evidence

All accepted scalars use raw IEEE-754 bits, `Double.toHexString`, and decimal.
Writes are synchronous. Preserve solver traces, exact configuration, software
environment, checksums, and negative results.

## Classification

- `MATRIX_FREE_SR_EQUIVALENT_AND_SCALABLE`
- `MATRIX_FREE_SR_EQUIVALENT_NOT_BENEFICIAL`
- `MATRIX_FREE_SR_NOT_EQUIVALENT`
- `MATRIX_FREE_SR_SOLVER_INSTABILITY`
- `MATRIX_FREE_SR_CORRECTNESS_DEFECT`

## Hard stop

No H2 optimization, target change, flexible PCG, adaptive block search,
matrix-free force retuning, LiH, or arbitrary-nucleus implementation. Freeze the
result and move next to the separately authorized vector-force capability.
