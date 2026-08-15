# Controlled Experiment E5 — Matrix-Free SR Solver Convergence

Status: `LOCKED_BEFORE_IMPLEMENTATION_AND_EXECUTION`

## Frozen predecessor and scientific system

The complete E4 result, including classification
`MATRIX_FREE_SR_NOT_EQUIVALENT`, remains immutable in
`analysis/prometheus/java-neural-matrix-free-sr/results/`.

This experiment reuses without alteration the E4 20-parameter H2 control,
sample stream, covariance operator, right-hand side, parameter order,
`lambda=1e-3`, dense reference update, and acceptance thresholds. It changes
solver-side arithmetic only. It performs no H2 optimization or training.

## Question

Is the E4 NONE/DIAGONAL failure a genuine Krylov limitation at the locked
accuracy, or a last-digit numerical-convergence issue caused by recursive
residual drift, reduction error, or loss of conjugacy? If only the fixed
wavefunction-component block preconditioner passes robustly, structured block
preconditioning is required under this accuracy standard.

## Frozen tolerances

- relative true residual `||b-Ax||2/||b||2 <= 1e-12`;
- finite absolute true residual (solver absolute floor remains `1e-14`);
- maximum update difference from dense reference `<=1e-9`;
- RMS update difference `<=2e-10`;
- predicted regularized quadratic decrease difference `<=1e-10`;
- maximum 200 update iterations;
- bitwise deterministic replay for solution, reported residual, iteration
  count, and operator count.

The true residual is always recomputed independently as `b-Ax` before an
acceptance decision. A recursively updated residual may never establish pass.

## Predeclared diagnostics

For NONE, DIAGONAL, and the frozen five-block wavefunction-component
preconditioner, record:

1. recursive residual and independently recomputed true residual each
   iteration;
2. residual gap `||r_recursive-(b-Ax)||2`;
3. compensated and ordinary dot-product/norm values;
4. pairwise normalized A-conjugacy loss among stored search directions;
5. Ritz/explicit spectral conditioning of the unchanged 20x20 damped system;
6. convergence/stagnation history and operator passes;
7. deterministic reduction order and replay.

Condition numbers are diagnostic only and cannot change damping or gates.

## Predeclared solver panel

The panel is evaluated in this order for each frozen preconditioner:

1. `BASELINE_RECURSIVE_PCG`: the E4 implementation, with an added final
   independently recomputed true residual. This must reproduce E4.
2. `PCG_TRUE_RESIDUAL`: conventional fixed-preconditioner PCG, but recompute
   `r=b-Ax` from the operator after every update and use that true residual for
   stopping and the next preconditioned direction.
3. `PCG_TRUE_RESIDUAL_COMPENSATED`: variant 2 with deterministic Neumaier
   compensated dot products and norms for solver-side scalar Krylov
   reductions only. The streamed covariance operator and its accumulation
   arithmetic remain byte-for-byte the E4 implementation.

No flexible/adaptive preconditioner is introduced because every tested
preconditioner is fixed. MINRES is reserved as a diagnostic only if the
explicit spectrum or curvature audit contradicts SPD; it must not replace CG
for an operator confirmed SPD. No damping, covariance, or target change is
permitted.

## Hypothesis attribution

- Variant 2 passes where variant 1 fails and the residual gap explains the
  difference: `RECURSIVE_RESIDUAL_DRIFT`.
- Variant 3 newly passes or materially reduces the true residual: 
  `FLOATING_POINT_REDUCTION_LIMIT`.
- Search directions lose normalized A-conjugacy above `1e-10` and the passing
  variants do not correct it: `KRYLOV_CONJUGACY_LOSS`.
- No solver-side variant passes for NONE/DIAGONAL while BLOCK passes:
  `STRUCTURED_PRECONDITIONING_REQUIRED`.
- Curvature is non-positive or the explicit damped matrix is not SPD:
  `SOLVER_OR_OPERATOR_CORRECTNESS_DEFECT`.

Multiple supported attributions may be recorded; no causal label is inferred
from iteration count alone.

## Decision

Exactly one primary classification is emitted:

- `BLOCK_PRECONDITIONED_MATRIX_FREE_SR_QUALIFIED`: BLOCK passes true-residual,
  update, replay, and scaling gates; NONE/DIAGONAL need not pass.
- `GENERAL_MATRIX_FREE_SR_SOLVER_CONVERGENCE_QUALIFIED`: all three frozen
  preconditioners pass with one predeclared solver-side variant.
- `MATRIX_FREE_SR_TIGHT_TOLERANCE_LIMIT_CONFIRMED`: BLOCK remains the only
  passing method but cannot satisfy all qualification/replay gates.
- `MATRIX_FREE_SR_SOLVER_CORRECTNESS_DEFECT`: SPD, residual, sign, identity,
  or deterministic-replay correctness fails.

The narrower BLOCK qualification requires the existing E4 operator
equivalence and scaling gates to remain satisfied. No claim is made that
DIAGONAL is defective merely because it is insufficient at this tolerance.

## Evidence and hard stop

Preserve complete traces, exact scalars (decimal, hexadecimal, raw bits),
software/commit provenance, checksums, and the immutable E4 linkage. Writes
are synchronous. Stop after this solver-convergence decision. Do not alter the
H2 model, covariance operator, damping, target, acceptance threshold, or start
training, LiH, or larger-system work.
