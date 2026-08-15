# Direct-|Psi|2 molecular MCMC qualification

## Decision

`RANDOM_WALK_METROPOLIS_QUALIFICATION_FAILED`

The frozen H2O EQ random-walk Metropolis oracle did not pass the preregistered
statistical-health gates. Execution therefore stopped before MALA qualification
and before any Step 3 energy/force reassessment. No upstream H2O state,
optimizer, force estimator, geometry, threshold, or scientific parameter was
changed.

## What passed

- The Java implementation samples directly from `|Psi|2`; retained samples
  carry unit weight and are not multiplied by `Psi^2` again in diagnostics, SR,
  or analytic differential-SWCT evaluation.
- Electron-by-electron random-walk Metropolis uses the symmetric proposal ratio.
- Electron-by-electron MALA includes the asymmetric forward/reverse Gaussian
  Hastings correction. MALA was validated against the random-walk oracle on the
  analytic hydrogen fixture, but was not opened on frozen H2O after the oracle
  failed.
- Analytic hydrogen `1s |Psi|2` radial behavior passed: random-walk and MALA
  reproduce the 1.5-bohr radial mean within the locked tolerances.
- Deterministic seeded replay passed in permanent JUnit tests.
- Frozen H2O random-walk acceptance was 0.4551, within `[0.30,0.75]`.
- Between-walker Rhat was 1.0586; retained-state sticking was zero.
- Tail-concentration gates passed: top 1% and 5% variance contributions were
  0.1691 and 0.4436.
- Peak heap was 361,592,856 bytes, below 512 MiB.

## Blocking observation

The integrated autocorrelation estimate was 7.8007. The 1,024 retained states
therefore represented an autocorrelation-adjusted ESS of 131.27, or 0.1282 of
the nominal sample count. The locked minimum was 0.20.

This is no longer importance-weight collapse: all retained samples have equal
weight. It is ordinary Markov-chain autocorrelation under the locked oracle
transition schedule. The result does not establish a MALA failure, because the
protocol correctly prohibited opening MALA after the oracle failed.

The very large local-energy mean and variance are recorded but are not used as
H2O physics evidence in this stopped run. Until a direct sampler passes the
statistical gate, they remain confounded by insufficient effective sampling and
the frozen trial state.

## Architecture changes accepted by tests

- `DirectWavefunctionSampleSource` explicitly distinguishes direct `|Psi|2`
  statistical samples from quadrature/importance samples.
- `WavefunctionMcmcSampleSet` provides bounded multi-walker random-walk and MALA
  transitions, warmup-only deterministic scale adaptation, frozen measurement
  scale, unit-weight replay, and immutable diagnostics.
- SR and analytic differential-SWCT consume direct samples without a second
  `Psi^2` factor. A permanent regression test protects this semantic boundary.
- The old finite-difference SWCT oracle now rejects direct samples explicitly
  rather than silently using an invalid displaced-state weight; proper
  likelihood reweighting would require a separately qualified implementation.

## Not established

- MALA health on H2O.
- H2O energy or force accuracy under direct sampling.
- A wavefunction-capacity defect.
- That increasing sample count alone is sufficient.

No gate was relaxed and no post-result tuning was performed.
