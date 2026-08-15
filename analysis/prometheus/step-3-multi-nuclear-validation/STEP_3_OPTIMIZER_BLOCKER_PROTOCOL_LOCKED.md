# Step 3 optimizer-blocker correction protocol (locked)

## Scope

Step 3 remains open. The frozen H2O execution failure is preserved as negative
numerical evidence and is not interpreted as a molecular-physics failure.

The diagnosed defect is confined to construction of the stochastic-
reconfiguration covariance matrix in `GeneralMolecularMatrixFreeSrOptimizer`.
The implementation populated each `mean[i]` and immediately evaluated row `i`
of `E[O_i O_j] - E[O_i]E[O_j]`. Means with `j > i` were therefore still zero,
creating an order-dependent, nonsymmetric matrix. The resulting 2x2 BLOCK
preconditioner was not the intended damped covariance and produced a negative
preconditioned residual inner product at the first PCG iteration.

## Authorized correction

Only the accumulation finalization order may change:

1. compute every parameter mean;
2. compute every SR gradient component;
3. assemble the complete covariance from the now-complete mean vector.

No molecular state, Hamiltonian, sampling data, damping, block partition,
solver tolerance, optimizer family, force estimator, scientific threshold, or
Step 3 geometry may change.

## Pre-execution gates

- covariance is symmetric to 1e-12 absolute tolerance;
- covariance agrees with an independent dense reference construction;
- every damped 2x2 production block is positive definite for the frozen H2O
  equilibrium fixture;
- the formerly failing first PCG step has positive `r^T M^-1 r`;
- an SR iteration satisfies the unchanged true-residual gate;
- H, He, and H2 regressions and the full Prometheus suite pass.

After these gates pass, the same three locked H2O geometries may be rerun as a
new qualified execution attempt. The original failed evidence must not be
overwritten.

