# Derivative-Aware PES Diagnostic Final Report

Classification: `DERIVATIVE_AWARE_OBJECTIVE_CORRECTNESS_DEFECT`

Capability class: `REFERENCE_ASSISTED_DIAGNOSTIC` (not production-ab-initio eligible).

The locked pre-training correctness gate failed: the VMC covariance RHS is an expectation-level energy derivative estimator, but it is not the exact derivative of the finite deterministic quadrature loss used by the reference-assisted diagnostic. Maximum component mismatch was `17.0409868`, versus the locked `3e-5` gate; RMS mismatch was `7.14744008`.

Training was not executed, parameters were not changed, derivative holdouts were not opened, and no energy/force/PES claim is made for a candidate model. The negative result is preserved. A new experiment would require separately preregistered mathematics that either differentiates the finite objective exactly or uses a statistically valid gradient-equivalence gate; this run cannot be repaired post hoc.
