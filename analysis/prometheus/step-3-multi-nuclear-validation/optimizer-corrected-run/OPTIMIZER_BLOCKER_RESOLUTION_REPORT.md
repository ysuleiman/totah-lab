# Step 3 optimizer-blocker resolution

## Outcome

The optimizer blocker is resolved. This continuation does **not** establish an
H2O physics failure.

The same three locked H2O geometries completed through BLOCK-preconditioned
matrix-free SR and the general Cartesian-force path. The resulting Step 3
classification remains `STEP_3_MULTI_NUCLEAR_VALIDATION_FAILED`, with
`sampling_variance` as the dominant blocker. Energy/force accuracy numbers are
not interpreted as model bias because their estimated uncertainty is orders of
magnitude above the preregistered gates.

## Root cause

`GeneralMolecularMatrixFreeSrOptimizer.statistics` previously initialized
`mean[i]` and assembled covariance row `i` in the same outer loop. For `j > i`,
`mean[j]` was still zero. This produced an order-dependent nonsymmetric matrix,
so the intended damped covariance block was not SPD and PCG encountered a
negative preconditioned residual product at iteration 1.

The authorized correction computes the complete mean/gradient vectors first
and assembles the covariance in a second pass. No SR physics, damping, block
partition, solver threshold, state, Hamiltonian, sampler, force estimator, or
Step 3 acceptance gate changed.

## Numerical audit

- All 512 frozen EQ state/local-energy evaluations completed before and after
  correction.
- Corrected covariance is symmetric to floating-point equality in the audited
  entries and agrees with the independent dense reconstruction.
- Damped 2x2 block determinants were positive:
  `6.615061681134114e-4`, `6.925124088790878e-3`, and
  `3.268006199018984e-1`.
- Independently reconstructed first-PCG-step `r^T M^-1 r`:
  `20313.691829565156` (positive).
- The formerly failing EQ SR iteration completes.
- All three full Step 3 calculations report SR residuals at or below the
  unchanged `1e-10` gate.

## Corrected locked execution

- Geometries completed: 3/3.
- Executor failures: 0.
- SR convergence gate: PASS.
- `force = -gradient` convention: PASS.
- Immediate identical-request reuse with zero executor calls: PASS.
- Maximum heap growth: 464,806,496 bytes (bounded-memory gate: PASS).
- Full Prometheus suite: 293 tests, 0 failures, 0 errors.

## Remaining Step 3 blocker

- Maximum energy standard error: `42.81244104582549 Ha` versus gate
  `0.005 Ha`.
- Maximum force-component standard error: `40.196229553360524 Ha/bohr`
  versus gate `0.010 Ha/bohr`.

Because uncertainty fails first and overwhelmingly, the large observed energy
and force discrepancies cannot be assigned to H2O physics accuracy in this
experiment. They remain unresolved under the frozen sampling protocol.

No sampling change, tuning, Step 4 work, or new molecule was started.

