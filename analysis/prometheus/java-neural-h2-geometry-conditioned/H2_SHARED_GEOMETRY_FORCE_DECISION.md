# Shared-Geometry H2/Force Decision

Frozen classification: `GEOMETRY_CONDITIONED_H2_FORCE_FAILED`

## Observed

- The shared PES is quantitatively accurate and all local physics/derivative
  audits pass.
- The optimizer reached the preregistered iteration ceiling without satisfying
  its convergence status gate.
- The nuclear-force comparison fails the locked analytic/finite-difference gate;
  the R=1.0 force also fails the trusted-reference gate.
- Deterministic replay is exact and redundant state evaluations are zero.

## Decision

The geometry-conditioned representation is promising evidence for reusable
molecular states, but this execution does not validate native nuclear forces.
No threshold, iteration count, model term, or sampling setting was changed after
observing results. Gen-1 and Gen-2 remain frozen. No LiH, production force use,
or additional H2 tuning is authorized by this result.

