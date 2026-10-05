# Independent centroid and plane geometry (P06)

The authoritative bounded contract is the [approved review proposal](../../../../software/qualification/p06-centroid-characterization-20261005/REVIEW_GATE.txt).
The [V2 checkpoint](../../../../software/qualification/p06-centroid-v2-20261005/CHECKPOINT.txt)
links implementation, fixtures, replay, attributed approval and source hashes.
This is arithmetic/geometry support, not an empirical interaction definition.

## Definition and supporting reasoning

For a complete nonempty requested list of finite coordinates, the equal-weight
arithmetic centroid is `(sum x / n, sum y / n, sum z / n)`. Its existence does not
require three points, noncollinearity or a unique best-fit plane. The existing Gaia
implementation already used this arithmetic internally before covariance fitting.
`Plane3D.centroidOf(List<Point3D>)` exposes that exact computation without fitting a
plane, mutating points, weighting by mass or changing summation order.
`Point3D` rejects nonfinite coordinates; nonfinite accumulated results are rejected.
Floating-point summation is not claimed bitwise invariant under arbitrary reordering.
No new numerical algorithm, threshold or external scientific dataset was introduced.

The [original continuous geometry contract](../../../../software/qualification/continuous-geometry-contract-20261005/DESIGN.txt)
(lines defining PLANE and quantity-specific coverage) remains the source for units,
plane diagnostics and the pinned relative eigengap guard. The existing covariance
solver, eigenvalues and diagnostics remain unchanged. That numerical uniqueness guard
is not an empirical planarity or favorable-interaction threshold.

The [preserved V1 witness](../../../../software/qualification/p06-centroid-characterization-20261005/run/observed-v1.json)
shows centroid suppression for `(0,0,0), (2,0,0), (4,0,0)` when fitting fails. The
independent arithmetic oracle is `(2,0,0)`. This motivated the correction; the old
artifact and V1 interpretation are not rewritten.

## Versioned operational semantics

Explicit opt-in: implementation `athena.geometry` version `2`, profile
`ATHENA_CONTINUOUS_GEOMETRY_V2`. Plans/results still use the existing `/1` payloads.
V1 retains the old behavior. Only V2 PLANE computes the centroid before attempting
plane fit; other operations retain their definitions. The exact request scope, atom
IDs, coordinate frame/state digest, source references, plan pin and implementation
identity remain in the report. No consumer is automatically migrated.

On a qualified frame with a degenerate plane, `centroidAngstrom` may be
`SUPPORTED_PRESENT`, while `measurement` and `normalUniquenessStatus` are
`UNKNOWN_INCONCLUSIVE` and `completeEnumeration=false`. No normal, residual or offset
is fabricated. An unqualified frame retains raw values as inconclusive, as in V1.
Centroid availability never grants PLANE qualification; the raw collector declares
no certificate capability. The evaluator rechecks the complete attributed payload.

Missing requested atoms, empty selection and invalid state fail closed; node-budget
failure emits no centroid. It is forbidden to drop missing points and report a
centroid for a subset. Absence/false is not a meaningful inference from these
continuous measurements. Plane degeneracy says nothing about chemical ring identity.

## Decisions and limitations

- ADOPT: existing equal-weight Gaia arithmetic, complete explicit scope and immutable
  evidence transport; reuse existing plane solver independently.
- MODIFY: V2 retains a defined centroid even when the plane solver fails.
- REJECT: centroid availability as plane success, invented normal/planarity,
  coordinate repair, partial-scope centroids, and implicit legacy migration.
- UNSUPPORTED: favorable interaction, aromaticity from coordinates, mass weighting,
  energy, biological interpretation or numerical stability beyond existing arithmetic.

Synthetic witnesses cover null/empty/nonfinite boundary and overflow, singleton,
coincident/collinear/ordinary geometry, ordering, translation, complete selection,
budget/frame coverage and exchange/evaluator replay. Ordinary V1/V2 quantities are
compared directly. Historical plane solver hexadecimal replay and V1 report bytes
remain identical. Qualification is bounded implementation qualification; it does not
invent a current-policy Research Gate review receipt or validity period.
