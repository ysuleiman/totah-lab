# Derivative-Aware PES Objective Decision Review

Final classification: `DERIVATIVE_AWARE_OBJECTIVE_CORRECTNESS_DEFECT`

The experiment stopped at its mandatory pre-training correctness gate. There is
no model B and therefore no scientifically valid energy-before/after,
slope-before/after, force-before/after, variance-before/after, equilibrium,
well-depth, dissociation, or holdout comparison to report.

The useful result is architectural: ordinary VMC covariance gradients cannot be
presented as exact derivatives of a finite deterministic supervised diagnostic
loss under the locked `3e-5` gate. The mismatch is not marginal; its maximum is
`17.040986779063722`.

No threshold was relaxed. No alternate objective, lambda, displacement, seed,
or optimizer was attempted. Analytic differential SWCT, matrix-free SR,
structured preconditioning, LiH, and larger systems remain untouched.

Recommendation: return to architecture review. Do not advance analytic
differential SWCT automatically because Controlled Experiment 1 did not produce
a supported derivative-aware PES capability. First decide whether exact
finite-objective differentiation or statistically qualified stochastic-gradient
training is the appropriate mathematical target.
