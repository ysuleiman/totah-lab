# H2 Generation-2 Final Report

Frozen classification: `H2_GENERATION2_MULTI_GEOMETRY_GATE_FAILED`

Generation-2 is a one-shot validation. Generation-1 remains independently frozen as `H2_MULTI_GEOMETRY_GATE_FAILED`; no threshold or ansatz term was changed.

## Scientific outcome

- Curve RMSE: `0.004521178 Ha` — PASS
- Maximum absolute error: `0.005846818 Ha` — PASS
- Equilibrium bond length: `1.423355543 bohr` — PASS
- Well-depth error: `0.004789861 Ha` — PASS
- Maximum local-energy variance: `0.033884453 Ha^2` — PASS
- Maximum multi-seed spread: `0.000683427 Ha` — PASS
- Deterministic replay: PASS
- Cusp, exchange symmetry, 6D gradient, and 6D Laplacian audits: PASS at all nine geometries
- Convergence: `1/9` geometries — FAIL

The complete gate fails only because eight geometries reached the preregistered 120-iteration ceiling. Their final energies, variances, integration diagnostics, and physics audits remain valid frozen negative evidence. No post-result optimizer adjustment is authorized.

## Performance

- Objective/statistics-pass work reduction: `81.48%`
- Warm-curve wall-clock speedup: `4.552x`
- Continuation saving relative to Gen-2 cold controls: `-19.41%`

SR and corrected sampling substantially improved physics quality and reduced work. Continuation was not beneficial under this Gen-2 SR protocol; it cost more objective passes than the specified cold controls. Acceleration is not promoted as a passed molecular method because the complete convergence gate failed.

## Decision

`NO_FUNDAMENTAL_CORRECTNESS_DEFECT_OBSERVED`

Generation-2 is closed. Localized residual or stopping behavior does not authorize continued H2 tuning. Advance to the next separately approved capability.
