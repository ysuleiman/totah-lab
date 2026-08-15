# Random-walk oracle decorrelation follow-up — locked

The frozen `RANDOM_WALK_METROPOLIS_QUALIFICATION_FAILED` result and all prior
artifacts remain unchanged.

## Single intervention

Increase the spacing between retained H2O EQ random-walk configurations from 2
complete electron-by-electron sweeps to 8. Nothing else changes:

- same frozen H2O EQ geometry and wavefunction parameters;
- same electron-by-electron random-walk Metropolis kernel;
- 8 walkers, 200 warmup sweeps, 128 retained configurations per walker;
- initial scale 0.20 bohr, warmup target acceptance 0.50, adaptation interval
  20 sweeps, seed 20260815;
- adaptation remains warmup-only and the measurement scale remains frozen;
- same statistical estimators and gates.

This intervention tests only whether ordinary retained-sample autocorrelation,
rather than sampler correctness, caused the prior normalized ESS failure.

## Unchanged gates

- measurement acceptance `[0.30,0.75]`;
- autocorrelation-adjusted normalized ESS `>=0.20`;
- maximum retained-state sticking fraction `<=0.25`;
- between-walker Rhat `<=1.20`;
- top 1% and top 5% local-energy variance fractions `<=0.50` and `<=0.80`;
- every local energy finite; peak heap below 512 MiB; deterministic replay.

## Decision

- If the oracle still fails: `RANDOM_WALK_DECORRELATION_INSUFFICIENT`; stop.
- If it passes: `RANDOM_WALK_ORACLE_QUALIFIED`; resume the already locked MALA
  H2O qualification without changing its configuration.
- If MALA then fails: `MALA_QUALIFICATION_FAILED`; stop.
- If MALA passes: run the already authorized one-shot frozen Step 3 assessment.

No H2O state, optimizer, force estimator, scientific threshold, or MALA setting
may change in this follow-up.
