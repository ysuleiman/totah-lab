# Exact Finite-Objective Differentiation Final Report

Classification: `EXACT_FINITE_OBJECTIVE_DIFFERENTIATION_NOT_SUPPORTED`

- Exact-gradient correctness: **PASS** (preflight)
- Primary R=1.0 derivative gate: **FAIL**; force error `0.059198876 Ha/bohr`
- Absolute energy/physics gates: **FAIL**
- Relative non-degradation gates: **FAIL**
- One-shot R=1.4/R=3.0 holdouts: **PASS**
- Fixed-perturbation reproducibility: **FAIL**; spread `0.155275954 Ha`
- Energy RMSE: `0.018489438 Ha`; maximum error `0.035153951 Ha`

The exact finite objective is differentiable correctly and materially improves the compressed-region slope. The final classification is limited by the frozen gates shown above. The rejected covariance-gradient experiment remains unchanged; no stochastic-objective route was invoked.
