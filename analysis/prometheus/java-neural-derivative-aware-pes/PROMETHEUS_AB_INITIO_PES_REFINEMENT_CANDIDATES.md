# Ab-Initio PES Refinement Candidates — Design Only

Status: `NOT_AUTHORIZED_FOR_EXECUTION`

These candidates address the production question: how can Prometheus discover
where its PES needs more quantum information and repair it variationally without
already knowing the answer? They are separate from the reference-assisted H2
capacity diagnostic.

## 1. Adaptive geometry allocation

Established basis: transferable neural-wavefunction work supports shared
optimization across systems/geometries and reports geometry selection using
prior energy variance (Scherbela et al., DOI
`10.1038/s41467-023-44216-9`).

Prometheus hypothesis: define an acquisition vector without collapsing its
dimensions into an invented master score:

- local-energy variance;
- SR/stationarity residual;
- Monte Carlo and derivative uncertainty;
- neighboring-energy disagreement;
- local curvature;
- geometry coverage/distance.

A preregistered decision rule selects additional sampling, optimization, or a
new geometry while preserving each evidence dimension separately.

- Expected compute impact: move work from easy/smooth regions to difficult
  regions; total reduction is unknown before replay.
- Expected scientific impact: improved local PES fidelity without external
  labels.
- Smallest falsifier: offline replay of an existing multi-geometry training trace
  comparing uniform allocation with a locked variance/curvature rule, followed
  by one untouched geometry holdout.
- Rollback: no work saving, worse holdout, unstable acquisition, or a rule that
  depends on post-result threshold changes.

## 2. Shared plus local residual parameters

Prometheus hypothesis:

`theta(R) = theta_shared + Delta theta(R)`.

`Delta theta` is low-dimensional, smooth across neighboring geometries, and
regularized toward zero. All parameters remain variationally optimized from the
Hamiltonian. This is not established by the cited transferable-wavefunction
paper; it is a new architecture candidate.

- Expected compute impact: more parameters/evaluations than the fully shared
  model, but materially less than independent wavefunctions if residual rank is
  small.
- Expected scientific impact: local flexibility in compressed/high-curvature
  regions while retaining transfer.
- Smallest falsifier: fixed-rank residual at one preregistered weak region and
  one geometry holdout, compared with fully shared and fully independent
  parameter counts/cost.
- Rollback: residual fails to shrink toward zero in easy regions, is
  non-identifiable, creates oscillation, or approaches independent-model cost.

## 3. Internal derivative consistency

Prometheus hypothesis:

`F_SWCT(R) = -[E_theta(R+d)-E_theta(R-d)]/(2d)`

within preregistered statistical/numerical uncertainty. Both sides derive from
the same state, so no external force label is needed.

- Expected compute impact: expensive under current numerical SWCT; potentially
  practical after analytic differential SWCT.
- Expected scientific impact: detects estimator/PES inconsistency and may
  stabilize shared-state derivatives.
- Smallest falsifier: frozen-state consistency audit across existing geometries,
  with no training, followed only later by a separately authorized regularizer.
- Rollback: agreement is automatic/redundant, variance dominates, or consistency
  improves while the independently trusted PES remains wrong.

This condition cannot supply missing physical truth and cannot by itself repair
a globally wrong PES.

## 4. Curvature-driven geometry refinement

Prometheus hypothesis:

`C(R) = |E(R+d)-2E(R)+E(R-d)|/d^2`.

High curvature requests denser geometry support; high variance requests more
sampling; poor stationarity requests more optimization. These actions remain
distinct rather than being merged into one undocumented score.

- Expected compute impact: fewer geometries in smooth regions and targeted work
  in compressed/torsional/barrier regions.
- Expected scientific impact: resolve local slopes/curvature without reference
  forces.
- Smallest falsifier: hide alternating geometries from an existing curve, use a
  locked curvature rule to reacquire them, and compare reconstruction with
  uniform insertion at the same budget.
- Rollback: curvature is dominated by Monte Carlo noise, misses known weak
  regions, or provides no advantage at equal cost.

## Capability and evidence boundary

All four candidates are `AB_INITIO` only if their execution consumes Hamiltonian,
geometry, and Prometheus-generated uncertainty/state evidence exclusively.
Using external energies or forces changes the execution to
`REFERENCE_ASSISTED_DIAGNOSTIC` or `SURROGATE` and must change its evidence
eligibility accordingly.
