# C3 low-energy Fourier attribution result

The sealed C3 panel tested only CHI n=2 and PHI n=3 corrections with all six C1 coefficients frozen. All topology, phase/sign, zero-extension, serialization, and 1-4 invariants passed. No QM or MD was run.

All optimizers converged, but none of the candidate-specific low-energy hypotheses was supported. C3A moved CHI n=2 to the lower preregistered LOO bound and worsened CHI <=10 RMSE from 0.783062 to 0.800630 kcal/mol. C3B moved PHI n=3 to its lower bound and worsened observed PHI <=10 RMSE from 0.636015 to 4.166386. C3C improved CHI <=10 to 0.746500 but worsened PHI <=10 to 4.127841 and PSI <=10 to 3.557801.

The result demonstrates that a residual harmonic in the scanned collective angle is not automatically equivalent to assigning the same Amber Fourier amplitude across every mapped physical torsion instance. The analytic Amber sign/phase oracle passed; the failure is scientific transfer under relaxed multi-instance evaluation, not a 1-4 or sign implementation defect.

`LOW_ENERGY_HYPOTHESIS_SUPPORTED = false`

`SELECTED_C3_MODEL = NONE`

The unchanged publication gates are disclosed per candidate and fail. No C4 or other experiment is authorized or executed here.
