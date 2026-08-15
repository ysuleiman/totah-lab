# Prometheus Numerical Architecture and Scientific Methods

Status: `REVIEW_ONLY_NO_PRODUCTION_CHANGE`

This is the living architectural index for Prometheus neural variational quantum
mechanics. It records accepted methods, frozen negative evidence, and proposed
experiments. A proposed optimization is not an accepted method until it passes
the promotion contract below.

## Current scientific baseline

- The infinite-well, hydrogen, helium, H2 Generation-1/2, shared-geometry H2,
  and nuclear-force studies remain immutable historical evidence.
- The nuclear-force capability study is frozen as
  `FROZEN_BASELINE_REPLAY_MISMATCH`. The mismatch is one ULP at two geometries
  because the older table serialized 16 significant digits. It was not waived.
- No tested force estimator passed every preregistered accuracy gate. SWCT had
  the lowest variance at every geometry, reducing raw variance by approximately
  572x, 989x, and 2623x relative to the direct estimator. AC-ZVZB had the best
  three-point diagnostic RMSE (about 0.0217 Ha/bohr), but failed the maximum-error
  gate. These are directions for experiments, not production promotions.
- The frozen-PES correlated finite-difference slope accounts for most of the
  compressed-R discrepancy. SWCT tracks that slope closely, but neither fact
  proves agreement with the independent trusted-force reference.

## Accepted mathematical and architectural principles

1. **Scientific identity precedes execution.** Reusable calculations and
   intermediates are keyed by scientifically relevant state, not executor.
2. **One valid numerical node per state.** A dependency graph evaluates each
   unique node once, subject to `MANDATORY_REUSE`, `CACHE_IF_BENEFICIAL`, or
   `RECOMPUTE_IF_CHEAPER`. Minimum total cost, not indiscriminate retention, is
   the objective.
3. **QM state evaluation is fused where the mathematics permits.** One sampled
   configuration should provide amplitude/log-amplitude, spatial derivatives,
   Laplacian, local energy, parameter log-derivatives, force ingredients, and SR
   sufficient statistics. Distinct geometries are not falsely declared shared.
4. **SR is a regularized linear system.** For centered log derivatives
   `O_tilde`, solve `(S + lambda I) delta = -g`, where
   `S = <O_tilde O_tilde^T>`. Explicit dense construction is only a small-model
   reference implementation.
5. **Statistical efficiency precedes micro-optimization.** Lower variance and
   lower bias can remove orders of magnitude more work than vectorizing an
   inferior estimator.
6. **Worker-local sufficient statistics precede shared mutation.** Deterministic
   reduction is a separate phase; bounded memory and replayability are gates.
7. **ForceBalance and downstream consumers never invoke QM.** They consume a
   frozen, checksummed evidence set read-only.

## Literature-supported mathematical directions

### Stochastic reconfiguration / natural gradient

Sorella's formulation treats optimization as motion in the variational metric,
not an optimizer trick. The Prometheus matrix-free form is

`v -> (S + lambda I)v = <O_tilde (O_tilde^T v)> + lambda v`.

This permits iterative solution without storing `S`. It does not remove the need
to qualify regularization, conditioning, stopping, or stochastic error.

### Nuclear forces and variance reduction

- Assaraf-Caffarel ZV/ZVZB changes estimator variance and approximate-state bias;
  it does not license an equation-sign repair after results are observed.
- Qian et al. compare neural-VMC force estimators and motivate forces for
  molecular simulation and force-field evidence. Their work supports treating
  accuracy, individual contributions, variance, and cost as joint outcomes.
- Differential SWCT is a first-class force method, not a coordinate convenience.
  Sorella-Capriotti show how adjoint differentiation and differential SWCT can
  make all force components comparable in cost to energy evaluation. Nakano et
  al. show favorable force-overhead scaling with SWCT when singular terms are
  appropriately regularized.
- Filippi-Umrigar correlated sampling and coordinate transformations exploit the
  covariance of neighboring geometries:
  `Var(E+ - E-) = Var(E+) + Var(E-) - 2 Cov(E+, E-)`.
- Linteau, Moroni, Carleo, and Holzmann (2026) propose an acceptance-ratio
  modification that softens Pulay-estimator power-law variance divergence to a
  logarithmic divergence, controlled-bias regularization, and compact
  variance-reduced Hellmann-Feynman estimators. Their >100-atom metallic-hydrogen
  neural-state demonstration makes this the first force-mathematics experiment
  to preregister for Prometheus. It is not yet an accepted Prometheus estimator.

## Prometheus implementation evidence

### Explicit SR

`GeometryConditionedStochasticReconfigurationOptimizer` stores `double[P][P]`
for both raw and combined covariance and accumulates every pair of parameter log
derivatives. Its dense elimination is a small-system oracle. Approximate costs
are `O(N P^2 + P^3)` time and `O(P^2)` memory. At the present H2 `P=20`, this is
not the measured dominant cost; it becomes structurally unsuitable as P grows.

### Force evaluation

The current SWCT implementation evaluates center, warped plus/minus, and
fixed-coordinate plus/minus states: five distinct state/local-energy evaluations
per accepted configuration. This is scientifically honest and counted. The
reason is explicit: the current Java graph supplies second electronic
derivatives but not the mixed derivative of the Laplacian with nuclear geometry.
An analytic directional/mixed derivative may reduce evaluations, but only after
finite-difference equivalence and force-statistics gates pass.

The frozen audit also records: correlated finite difference uses two paired
evaluations; AC-ZV uses one state evaluation; AC-ZVZB uses one state/local-energy
evaluation; and the parameter-response audit used exactly 3,096,000 evaluations
per geometry. The direct baseline's separate replay is visible rather than
hidden as reuse.

### Derivative representation

`SecondOrderJet` retains a gradient and Hessian diagonal, not a dense Hessian.
Its per-operation storage and arithmetic scale linearly with coordinate
dimension, which is appropriate for Laplacians. It cannot currently express the
mixed nuclear derivative needed to replace SWCT's central difference. Extending
it should target directional mixed derivatives, not a general third-order
tensor.

### Streaming and reduction

Sampling is already bounded at 512 configurations and does not retain electron
clouds. Accumulation is currently sequential and mutable. The next concurrency
experiment should use worker-local mergeable packets and a fixed reduction tree,
not hot atomics.

## Force Field X lessons, not copied algorithms

Source reviewed at upstream commit
`3ff9accb0a0feea0fd913bb9cba0a3080fb9a435`.

- `MultiDoubleArray` trades `O(workers * array-size)` memory for contention-free
  worker-local writes, followed by explicit reduction.
- `AtomicDoubleArray3D` makes accumulator strategy selectable and keeps reduction
  explicit.
- `PCGSolver` separates the matrix action, preconditioner, convergence loop, and
  parallel regions; it supports conventional and flexible preconditioned-CG
  updates. Prometheus should derive SR preconditioners from wavefunction blocks,
  not copy AMOEBA's physical preconditioner.
- `RealSpaceEnergyRegion`, `ReciprocalSpace`, and
  `InducedDipoleFieldRegion` decompose work by physical responsibility and expose
  execution/reduction boundaries.
- `ForceFieldEnergy` coordinates energy and gradient through one typed evaluation
  path and accounts for physical components. Prometheus should similarly request
  all needed observables before launching the sample kernel.

FFX supplies architecture patterns only. VMC literature supplies the equations;
Prometheus owns its Java implementation and validation.

## Promotion contract for every change

Implementation of the documentation machinery is deferred until the active
scientific problem is resolved, as directed, but the following evidence is a
mandatory promotion gate:

- mathematical formulation/derivation and equation-level literature provenance;
- why the predecessor was insufficient; assumptions and approximations;
- immutable implementation boundaries and dependency graph;
- time and memory complexity before/after;
- benchmark method, scientific accuracy, variance/statistical efficiency, and
  wall time before/after;
- rejected alternatives and negative results, preserved rather than rewritten;
- validation gates, regression tests, deterministic replay information;
- artifact checksums, software versions, and Git commit;
- append-only progression: problem -> hypothesis -> experiment -> result ->
  architectural decision -> validation -> performance impact.

Faster execution alone can never promote a method. Scientific preservation or
improvement must be demonstrated independently.

## Current decision

No production rewrite is authorized. Run the smallest force-estimator
mathematics experiment first. Only after a force formulation qualifies should
Prometheus benchmark matrix-free SR, fused evaluation, deterministic parallel
statistics, adaptive stopping, and low-level vectorization in that order.

## Primary sources

- Sorella, *Phys. Rev. B* 71, 241103(R) (2005),
  DOI `10.1103/PhysRevB.71.241103`.
- Assaraf and Caffarel, *J. Chem. Phys.* 119, 10536 (2003),
  DOI `10.1063/1.1621615`.
- Filippi and Umrigar, *Phys. Rev. B* 61, R16291 (2000),
  DOI `10.1103/PhysRevB.61.R16291`.
- Sorella and Capriotti, *J. Chem. Phys.* 133, 234111 (2010),
  DOI `10.1063/1.3516208`.
- Nakano, Raghav, and Sorella, *J. Chem. Phys.* 156, 034101 (2022),
  DOI `10.1063/5.0076302`.
- Qian, Fu, Ren, and Chen, *J. Chem. Phys.* 157, 164104 (2022),
  DOI `10.1063/5.0112344`.
- Linteau, Moroni, Carleo, and Holzmann, *Variance reduction for forces and
  pressure in variational Monte Carlo* (submitted 15 March 2026),
  arXiv `2603.14521`.
- Force Field X, upstream commit
  `3ff9accb0a0feea0fd913bb9cba0a3080fb9a435`.
