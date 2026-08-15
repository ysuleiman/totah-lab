# Step 3 H2O sampling-variance diagnosis protocol (locked)

## Question

Why is the unchanged general 10-electron VMC sampling path too noisy for the
locked H2O Step 3 gate?

## Boundaries

- Same EQ, COMPRESSED, and STRETCHED H2O geometries.
- Same accepted optimizer-corrected Step 3 parameter vectors.
- Same deterministic 128-point evaluation streams with skips 1009, 2017,
  3019, and 4027, exponent 4.0, and batch size 64.
- No optimizer, wavefunction, Hamiltonian, force estimator, sampler setting,
  gate, molecule, or architecture change.
- No Step 3 acceptance rerun in this unit.

## Evidence-first audit

The registered exact-result JSON, Step 3 CSV, and JSONL registry are inspected
first. If per-sample trajectories were not persisted, that absence is recorded.
The deterministic samples may then be regenerated exactly from their frozen
scientific inputs for diagnosis; regenerated observations are diagnostic and do
not replace the accepted evidence.

## Frozen diagnostics

For every geometry and evaluation stream report:

- finite/nonfinite count;
- raw importance-weight range;
- effective target weight `w * |psi|^2` range and normalized maximum share;
- Kish effective sample size and ESS fraction;
- weighted local-energy mean and variance;
- local-energy minimum, maximum, and weighted quantiles;
- fraction of weighted variance contributed by the largest 1% and 5% sample
  residuals;
- lag-1 and lag-2 correlations as ordering diagnostics only;
- block-to-block mean dispersion.

## Interpretation classes

Each hypothesized cause is classified as `SUPPORTED`, `NOT_SUPPORTED`,
`INAPPLICABLE`, or `UNRESOLVED`:

- poor proposal scale / acceptance;
- insufficient effective sample size;
- autocorrelation;
- weight degeneracy;
- local-energy heavy tails;
- initialization / burn-in;
- H2-derived setting mismatch;
- geometry domination;
- poor wavefunction quality.

Because `GeneralMolecularImportanceBatches` is deterministic Halton importance
sampling rather than a Markov-chain proposal sampler, acceptance rate and
burn-in may be classified inapplicable. No correction is selected until the
frozen diagnostic output is reviewed.
