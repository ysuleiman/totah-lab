# Frozen H2O Step 3 sampling-variance diagnosis

## Decision

`IMPORTANCE_SAMPLING_WEIGHT_DEGENERACY_AND_TAIL_CONCENTRATION_SUPPORTED`

No sampling correction was applied and the Step 3 gate was not rerun.

## Evidence availability

The accepted Step 3 JSON/JSONL evidence preserves aggregate energy, force,
uncertainty, optimized parameters, sampler-defining identities, and checksums.
It does not preserve per-sample trajectories. Because the sampler is
deterministic, the exact four 128-point streams were regenerated from each
geometry's accepted parameter vector, fixed skips, exponent, and atom order.
Replay hashes are emitted for every stream.

Future accepted VMC calculations should persist bounded per-sample diagnostic
records or an equivalent checksummed sufficient-statistics artifact so this
audit does not depend only on deterministic regeneration.

## Observed

- All 1,536 regenerated evaluations were finite.
- The sampler is deterministic Halton importance sampling, not MCMC. Proposal
  acceptance and burn-in are therefore inapplicable.
- Per-stream Kish ESS is only 1.17--6.92 out of 128 (0.91%--5.41%).
- A single normalized target weight carries 26.2%--92.5% of a stream.
- Raw importance weights span approximately `2.4e-6` to `1.83e7`; effective
  `w|psi|^2` values span approximately `1.45e-16` to `52.66`.
- Weighted local-energy variance is 640--23,221 Ha^2 across streams.
- The largest 1% of weighted variance contributions account for 36.9%--84.5%;
  the largest 5% account for 77.2%--96.6%.
- Block means vary from 7.65 to 213.56 Ha, explaining the enormous frozen
  block standard errors.
- The same failure pattern occurs in EQ, COMPRESSED, and STRETCHED. No single
  geometry is the cause.
- Lag ordering correlations are usually small, except the skip-2017 streams
  (roughly 0.55). These are low-discrepancy sequence-order diagnostics, not MCMC
  autocorrelation or an integrated autocorrelation time.

## Hypothesis classification

| Hypothesis | Classification | Evidence |
|---|---|---|
| Poor proposal scale / acceptance | INAPPLICABLE | No Markov proposal or accept/reject step exists. |
| Insufficient effective sample size | SUPPORTED | ESS fraction is 0.0091--0.0541. |
| Autocorrelation | NOT ESTABLISHED | MCMC autocorrelation is inapplicable; one Halton subsequence has ordering correlation. |
| Weight degeneracy | SUPPORTED, PRIMARY | Maximum normalized weight reaches 0.925. |
| Local-energy heavy tails | SUPPORTED | 5% of points carry up to 96.6% of weighted variance. |
| Bad initialization / burn-in | INAPPLICABLE | Independent deterministic importance points have no chain initialization. |
| H2-setting mismatch | SUPPORTED | The fixed exponent-4 product proposal scales poorly in 30 electronic dimensions. |
| One geometry dominates | NOT SUPPORTED | All three geometries show essentially the same ESS/tail pathology. |
| Genuinely poor wavefunction quality | UNRESOLVED | Large local-energy variance is compatible with poor state quality, but ESS collapse prevents separating state error from proposal mismatch. |

## Root interpretation

The dominant statistical defect is the dimensional product-importance proposal:
small per-electron density mismatch compounds across ten electrons, causing
extreme target-weight concentration. The nominal 128 points are effectively
only about one to seven samples. Local-energy tail contributions then make the
four block estimates unstable.

The data do not justify changing the optimizer or declaring H2O physics wrong.
They also do not yet justify a wavefunction redesign, because proposal mismatch
must be corrected before local-energy variance can cleanly diagnose state
quality.

## Smallest justified next experiment

Preregister one established VMC sampling correction that samples `|psi|^2`
directly (bounded Metropolis/Langevin drift-diffusion with acceptance, burn-in,
ESS, and integrated-autocorrelation diagnostics) on the same frozen H2O state.
Compare it against the frozen importance streams before rerunning Step 3. Do not
change the optimizer, wavefunction, force estimator, or gates in that experiment.
