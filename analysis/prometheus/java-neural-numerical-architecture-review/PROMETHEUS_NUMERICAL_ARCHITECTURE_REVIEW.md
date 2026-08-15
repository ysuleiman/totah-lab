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
8. **Capability class is explicit.** `AB_INITIO` consumes only Hamiltonian and
   geometry; `REFERENCE_ASSISTED_DIAGNOSTIC` may use external labels to test
   capacity but cannot produce production QM evidence; `SURROGATE` learns
   external QM labels and is never represented as ab initio.

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

### PES derivative fidelity

The dominant compressed-region force miss is already present in the derivative
of the frozen geometry-conditioned PES. At R=1.0 bohr, correlated finite
difference differs from the trusted force by about `0.0393 Ha/bohr`, while SWCT
differs from that same frozen-PES slope by only about `0.00259 Ha/bohr`.
Therefore an estimator-only improvement cannot be presumed to repair the trusted
force error.

The current shared-geometry SR objective is an equal-weight mean of sampled
energies over the training radii. It contains no force label, derivative-match
term, curvature term, or explicit geometry-local smoothness term. Smoothness and
force behavior are checked after optimization. This is a scientifically clean
energy-first baseline, but it does not constrain a low-energy-error model to
have the correct local slope between training geometries.

A future, separately preregistered model-generation study may compare the
energy-only objective with a derivative-aware/Sobolev objective such as

`L = L_energy + w_F sum_k ||dE_theta/dR(R_k) - F_k^reference||^2`,

or a geometry-local consistency term that matches independently accepted energy
differences/slopes. The derivative targets, weights, development geometries, and
holdout geometries must be frozen before training. This is not authorization to
retune H2: one controlled objective comparison should establish capacity and
transfer, then close. Smoothness penalties without independent physical labels
are lower priority because they can make a wrong PES smoothly wrong.

The selected Controlled Experiment 1 is narrower still: a one-shot
`REFERENCE_ASSISTED_DIAGNOSTIC` using dimensionless existing error scales and
force-derived symmetric local-energy constraints. Success establishes
representational capacity only. It does not authorize force-supervised
production Prometheus.

Controlled Experiment 1 terminated before training as
`DERIVATIVE_AWARE_OBJECTIVE_CORRECTNESS_DEFECT`. The proposed covariance RHS
failed the locked exact finite-objective gradient audit (maximum component
mismatch `17.040986779063722` versus `3e-5`). No parameters changed and no
holdouts opened. This is a failure of the locked diagnostic realization, not
evidence that the wavefunction cannot represent the derivative. A future design
must distinguish exact differentiation of a finite objective from statistical
qualification of an expectation-level VMC gradient estimator.

The separately locked exact-finite-objective follow-up implemented mixed
parameter/spatial forward AD, including `d(nabla^2 psi)/dtheta`. It passed the
independent pre-iteration finite-difference gate (maximum error
`6.120904494366641e-8`, RMS `3.063106549414219e-8`) and was computationally
feasible. Its one controlled training/holdout execution nevertheless closed as
`EXACT_FINITE_OBJECTIVE_DIFFERENTIATION_NOT_SUPPORTED`: R=1.0 validation-slope
error worsened to `0.0591988761 Ha/bohr`, energy RMSE rose to
`0.0184894380 Ha`, and fixed-perturbation spread was `0.155275954 Ha`.
R=1.4/R=3.0 holdouts passed. Thus exact finite differentiation is now a verified
numerical capability, but this reference-assisted finite-objective training
intervention is not an accepted PES-construction method. The statistical
expectation-objective route remains a separate future experiment, not a
fallback used in this unit.

### Self-supervised production PES refinement hypotheses

The production direction must remain variational and ab initio. Four
Prometheus-specific hypotheses are recorded for later preregistration:

1. allocate geometry work adaptively from local-energy variance, SR residual,
   Monte Carlo/derivative uncertainty, neighboring-energy disagreement, and
   curvature;
2. decompose `theta(R)=theta_shared+Delta theta(R)`, regularizing a smooth local
   residual toward zero to retain shared efficiency with local flexibility;
3. require internal agreement between differential-SWCT and central PES
   derivatives without supplying an external force target;
4. refine the geometry mesh where
   `|E(R+d)-2E(R)+E(R-d)|/d^2`, variance, or stationarity indicates insufficient
   resolution.

The first and fourth are consistent with published transferable-wavefunction
work that samples geometries nonuniformly, including by prior energy variance.
The residual decomposition and combined acquisition policy are Prometheus
hypotheses, not literature-established algorithms. Internal derivative
consistency can expose inconsistency but cannot repair a globally wrong PES by
itself.

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

For `N` accepted configurations, the current path performs `5N` state and `5N`
local-energy evaluations: center, warped `R+delta`, warped `R-delta`, fixed
coordinate `R+delta`, and fixed coordinate `R-delta`. It stores bounded scalar
moments, so memory is `O(B * stateSize + K)` for batch size `B<=512` and `K`
statistics; it does not retain `N` configurations.

Differential SWCT requires the total nuclear-coordinate derivative of local
energy, including the derivative of the electronic Laplacian, plus the warped
log-amplitude/Jacobian derivative. In derivative notation this contains a mixed
quantity of the form `d/dR [sum_i d2 Psi / dx_i2]`. A naive general third-order
tensor would have prohibitive `O(D^3)` storage. The appropriate candidate is a
nuclear-directional mixed derivative/Jacobian-vector product propagated through
the existing graph, retaining only the electronic Hessian diagonal and its
directional nuclear derivative: approximately `O(D)` derivative payload per
node for one nuclear direction.

The best-case analytic path is one primal state traversal plus directional
derivative propagation per configuration, rather than five independent state
traversals. It is not correctly described as exactly 5x faster: derivative
arithmetic and graph retention make the analytic traversal more expensive than
one primal traversal. The experiment must report node evaluations, retained
bytes, state-equivalent work, and wall time. Stability risks include cusp/singular
regions, cancellation in local-energy derivatives, incorrect Jacobian terms,
and silent sign/unit errors. Per-sample comparison against multiple symmetric
finite-difference steps is required before aggregate comparison.

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

### Evidence serialization

The replay stop was archival, not physical. Future accepted scalar evidence must
store three synchronized representations:

- the 64-bit IEEE-754 payload, encoded as an unsigned hexadecimal bit string;
- `Double.toHexString(value)`, which round-trips exactly;
- a human-readable decimal, which is presentation only.

Scientific identity and integrity checks use the exact bits plus typed units and
field identity, never the presentation decimal. Arrays store exact bits per
element with shape, atom/order mapping, units, and endianness in the checksummed
envelope. Readers must verify agreement among bit, hexadecimal, and decimal
representations and reject corruption. This design applies prospectively;
historical `%.16g` evidence remains immutable and retains its documented replay
limitation.

### Parameter response and low-level tuning

The brute-force parameter-response audit consumed exactly 3,096,000 state
evaluations per geometry, was underdetermined at R=1.4 and 3.0 bohr, and produced
a pathological correction at R=1.0. It remains diagnostic only. No optimization
of this path is justified unless future evidence establishes that a response
term is scientifically required.

Bounded streaming already works. Off-heap storage, memory mapping, vectorization,
and low-level loop tuning remain deferred until a qualified scientific kernel is
profiled and one of those mechanisms is shown to dominate.

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

No production rewrite is authorized. The first controlled scientific experiment
should test PES derivative fidelity using a frozen derivative-aware objective and
an untouched geometry holdout; it must be a single closed capability study, not
another open-ended H2 tuning cycle. In parallel only at the design level, specify
the analytic directional-SWCT derivative oracle. After those correctness issues,
the order is matrix-free/structured SR, fused evaluation, exact evidence
serialization, deterministic worker-local statistics, adaptive stopping, then
profile-driven low-level tuning.

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
