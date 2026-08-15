# Nuclear-Force Estimator Decision Review

Formal classification: `FROZEN_BASELINE_REPLAY_MISMATCH`

Because the historical force table retained only 16 significant digits, two
baseline values cannot pass a bitwise replay gate. The numerical differences are
one ULP and scientifically negligible, but the preregistered gate is not waived.
The conclusions below are therefore diagnostic and not a production promotion.

## Accuracy

Approximate three-geometry RMSE values from raw statistics are:

| Estimator | Force RMSE (Ha/bohr) | Maximum error | Locked reference gate |
|---|---:|---:|---|
| Direct HF+Pulay | 0.0334 | 0.0502 | FAIL |
| Bare HF | 0.0683 | 0.0958 | FAIL |
| Correlated finite difference | 0.0232 | 0.0393 | FAIL |
| SWCT | 0.0248 | 0.0419 | FAIL |
| AC-ZV | ~1.01 | 1.3605 | FAIL |
| AC-ZVZB | 0.0217 | 0.0359 | FAIL |

AC-ZVZB has the lowest diagnostic RMSE, but no estimator satisfies the locked
maximum trusted-reference error at all three geometries. AC-ZV preserves Qian's
printed Eq. 11 literally and performs catastrophically; the known printed-sign
inconsistency was not silently repaired.

## Variance

SWCT has the lowest raw variance at every geometry: 0.0810, 0.0411, and 0.0106
Ha2/bohr2. Relative to the direct estimator this is approximately 572x, 989x,
and 2623x lower. This strongly reproduces the literature's variance-reduction
finding, but variance alone cannot pass the force gate.

## Frozen-PES slope versus estimator error

The correlated finite-difference control differs from the trusted force by
0.0393, 0.00856, and 0.00101 Ha/bohr at R=1.0, 1.4, and 3.0. Thus most of the
R=1.0 discrepancy is already present in the frozen PES slope.

SWCT agrees with that independent frozen-PES slope to approximately 0.00259,
0.000987, and 0.000061 Ha/bohr. AC-ZVZB also stays within 0.01 Ha/bohr of it at
all three points. These are strong diagnostic indications that SWCT and AC-ZVZB
extract the frozen PES derivative more consistently than the original direct
estimator. They still fail the separately locked trusted-force gate at R=1.0.

## Nonstationarity

The variational-gradient residual is nonzero at all three geometries. The
unregularized response system is formally determined only at R=1.0, with pivot
ratio `1.33e-10`, barely above the locked cutoff. Its response force is
`-0.4891 Ha/bohr`, which moves the force away from both controls. The R=1.4 and
R=3.0 systems are underdetermined under the locked rule and yield no correction.

Therefore nonstationarity is observed, but it is **not established as the cause
of the prior force failure**, and the response audit does not justify a corrected
force.

## Answers

- Most accurate diagnostically: AC-ZVZB by three-point RMSE.
- Lowest variance: SWCT at every geometry.
- Cheapest standalone literature estimator: AC-ZV, but it is grossly inaccurate.
- Best defensible accuracy/cost direction: SWCT or AC-ZVZB; neither is formally
  qualified by this run.
- Frozen PES: adequate for the prior energy-curve scope, but its compressed
  R=1.0 slope misses the trusted force gate.
- Nonstationarity materially responsible: not established.
- Production recommendation: none. Preserve SWCT as the leading estimator
  candidate, repair evidence serialization in a separately preregistered replay,
  and do not tune the H2 wavefunction inside this study.

ZVZB+SWCT was not evaluated because the locked prerequisite required both
components to qualify independently; neither passed the trusted-reference gate.

