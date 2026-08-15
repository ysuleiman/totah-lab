# Java Implementation Plan

1. Introduce immutable, molecule-general `NuclearForceRequest`,
   `NuclearForceResult`, and capability-based `NuclearForceEstimator` types.
2. Adapt the existing H2 direct estimator behind that boundary without changing
   its numerical path; baseline replay is the acceptance test.
3. Add a streaming paired-configuration accumulator for correlated finite
   differences. A pair is evaluated and reduced together; displaced states are
   never promoted to independent training evidence.
4. Add a stationarity/parameter-response auditor. It reports the frozen
   variational gradient, response-equation condition/rank, and the response-force
   contribution without changing any parameter.
5. Implement space-warp coordinates and their Jacobian as a standalone pure-Java
   transformation with analytic and finite-difference tests before connecting it
   to any force estimator.
6. Implement the published SWCT estimator with fixed literature definitions and
   reuse the existing Java derivative graph in the Sorella-Capriotti style rather
   than separately differentiating each nuclear component.
7. Add AC-ZV and AC-ZVZB only after equation-level provenance is captured in code comments and
   tests. If the no-fitted-auxiliary-function version is not well-defined, mark it
   `NOT_EVALUABLE_WITHOUT_NEW_DEVELOPMENT` rather than inventing one.
8. Combine ZVZB+SWCT only after both components independently qualify.
9. Execute the frozen three-geometry comparison once and synchronously persist
   raw sufficient statistics, results, environment, identities, and checksums.

Mandatory reuse remains in force: state amplitude, derivatives, local energy,
geometry derivative, and coordinate distances are evaluated once per valid
configuration state and shared by every requested component. Large intermediates
remain policy-managed rather than indiscriminately retained.
