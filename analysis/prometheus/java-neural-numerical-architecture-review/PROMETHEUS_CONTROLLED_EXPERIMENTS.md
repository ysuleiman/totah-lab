# Small Controlled Experiments

Status: `PREREGISTRATION_REQUIRED_BEFORE_EXECUTION`

No experiment below is authorized by this review. Each must receive a locked
protocol and its own scientific identity before implementation or execution.

## E0 — compressed-region PES derivative fidelity

Hypothesis: the energy-only geometry-conditioned training objective permits a
small energy error but an incorrect local derivative at compressed H2 geometry;
a preregistered derivative-aware objective can improve slope fidelity without
damaging the PES holdout.

- Development: a minimal subset of already accepted energy/force references,
  selected before training. Hold out at least one compressed/intermediate region
  and the dissociation behavior.
- Compare exactly two objectives: current energy-only baseline and one locked
  energy-plus-derivative objective. Freeze all weights, sample streams, seeds,
  iterations, and gates first.
- Measure energies, local slopes, curvature diagnostics, trusted-force errors,
  variance, seed spread, and evaluation cost. Estimator and objective errors are
  reported separately.
- Falsifier: derivative improvement fails holdout, energy/PES gates regress,
  result is seed-fragile, or improvement requires post-hoc weight adjustment.
- Hard stop: one comparison only; no continuing H2 objective tuning.

## E1 — force-estimator mathematics

Hypothesis: the Linteau acceptance-ratio Pulay treatment and/or compact
Hellmann-Feynman estimator reduces tail pathology enough to improve effective
accuracy/cost without unacceptable bias.

- Fixed: frozen H2 state, R=1.0/1.4/3.0, configurations, Hamiltonian, trusted
  references, units, signs, sampling, and existing thresholds.
- Vary: estimator mathematics only, one published intervention at a time.
- Measure: raw/clipped distributions, tail index diagnostics, bias, variance,
  standard error with autocorrelation, RMSE, maximum error, state evaluations,
  and wall time.
- Falsifier: lower wall time/variance without passing accuracy and controlled-bias
  gates. Such a result remains negative evidence.
- Boundary: do not alter the wavefunction or combine estimators until each
  component independently qualifies.

## E2 — matrix-free SR

Hypothesis: a streamed covariance operator plus iterative solve reproduces the
dense SR direction while bounding memory at useful parameter counts.

- Phase A: current 20-parameter H2 replay, same samples and regularization.
  Compare dense and matrix-free `Sv`, solve residual, update direction, predicted
  energy change, and deterministic replay.
- Phase B: generated parameter-log-derivative matrices at P=1,000 and P=10,000
  with known spectrum; no scientific VMC claim. Measure memory, matvec count,
  convergence, and wall time.
- Preconditioners in order: none, diagonal, fixed wavefunction-component blocks.
  Flexible PCG is tested only if a preconditioner truly changes per iteration.
- Falsifier: direction or residual outside preregistered tolerance, loss of
  reproducibility, or total work exceeding dense solve in the intended regime.

## E3 — analytic directional SWCT

Hypothesis: a minimal mixed-direction derivative can replace four displaced
state evaluations without changing the SWCT estimator.

- Build a derivative oracle on tiny analytic states before neural H2.
- Compare analytic versus multiple symmetric finite-difference step sizes for
  every per-sample term, not only the final mean.
- Audit singular/cusp-region samples separately.
- Count graph nodes, retained bytes, state/local-energy evaluations, and time.
- Falsifier: unexplained per-sample residual, sign/unit mismatch, tail change, or
  reliance on a dense third-order tensor.

Cost hypothesis to test: current `5N` state/local-energy evaluations versus `N`
fused primal-plus-directional traversals. Report state-equivalent node work and
retained bytes; do not advertise a 5x speedup from evaluation count alone.

## E4 — deterministic worker-local statistics

Hypothesis: worker-local Welford/pairwise packets increase throughput without
changing scientifically meaningful moments.

- Replay the same ordered stream with 1, 2, and 4 workers.
- Use a fixed partition and fixed binary reduction tree.
- Compare count, mean, variance, covariance, force, and checksum of the reduction
  plan. Declare bitwise or ULP tolerance before execution.
- Report scaling, allocation, and peak resident memory.
- Falsifier: nondeterministic outputs, lost samples, or throughput loss.

## E5 — adaptive stopping by offline replay

Hypothesis: uncertainty-based stopping would save samples at easy geometries
without invalid coverage.

- Do not generate new samples. Reveal frozen traces in fixed batches.
- Lock minimum samples, target SE, autocorrelation/ESS estimator, and maximum
  samples in advance.
- Measure hypothetical stop point and interval coverage against the full-trace
  estimate/trusted reference.
- Falsifier: undercoverage, frequent early false confidence, or no meaningful
  sample saving.

## E6 — correlated neighboring geometries

Hypothesis: paired configurations reduce variance of `E+ - E-` through positive
covariance without weight collapse.

- Compare original pairing with deterministically permuted, decorrelated pairs.
- Report covariance term, variance ratio, reweighting ESS, maximum weight, force
  bias, and cost.
- Falsifier: small/negative covariance benefit, unstable weights, or bias outside
  the force gate.

## E7 — exact scalar evidence serialization

Hypothesis: storing IEEE-754 bits, Java hexadecimal form, and presentation
decimal eliminates formatting-dependent replay failures.

- Use a synthetic corpus covering finite normal/subnormal values, signed zero,
  infinities and NaN policy, plus copies of frozen force scalars.
- Round-trip JSONL and restart/reload; compare raw bits, units, typed field
  identity, and envelope checksum.
- Confirm duplicate scientific identity is unaffected by presentation formatting.
- Historical files are read-only fixtures and are never rewritten.
- Falsifier: any finite value changes bits, representations disagree without
  rejection, or checksum/identity depends on locale or decimal formatting.

## Mandatory evidence envelope

Every completed experiment must retain formulation, equation provenance,
predecessor limitation, assumptions, implementation boundary, complexity,
benchmark method, scientific and performance results, negative outcomes,
regression gates, deterministic replay, artifact checksums, environment, and Git
commit. The progression record is append-only.
