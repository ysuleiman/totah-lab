# Mixed source-atom / group-centroid measurements (B05 prerequisite)

The [approved exact definition](../../../../software/qualification/b05-mixed-geometry-contract-20261005/DESIGN.txt)
is the authority for this operation. [Qualification](../../../../software/qualification/b05-mixed-geometry-v3-20261005/CHECKPOINT.txt)
pins code, fixtures, approval and replay. It adds no CH–π classifier.

`athena.geometry/3`, profile `ATHENA_CONTINUOUS_GEOMETRY_V3`, adds `POINT_PAIR_GROUP`
to the extensible plan `/1`; result schema remains `/1`. An ordered pair of distinct
source atom references P,Q and a complete explicit group determine the five quantities
listed in the contract. G is a derived equal-weight centroid, never an atom or a graph
mutation. P and Q have no chemistry assigned by this geometry operation.

## Mathematical support and choices

Reuse Gaia's existing centroid, vector angle and covariance-plane routines. Distances
are Euclidean; angle(P−Q,G−Q) is at Q. The normal-derived angle uses the absolute scalar
product with Q−G, clamped to [0,1] before arccos, giving [0,90] degrees independent of
normal sign. Zero-length/undefined vectors remain unavailable. Existing Gaia numerical
zero guards are retained; there is no new empirical interaction cutoff.

ADOPT explicit atom/group/state provenance and existing numerical machinery. MODIFY the
plan vocabulary to describe mixed source/derived geometry without a pseudoatom. REJECT
inferring C–H connectivity, hydrogen placement, aromaticity, favorable contact or energy
from this geometry. UNSUPPORTED remains the result for this operation under V1/V2.
Those versions retain their original recognized-selection validation; a caller must not
send an inconsistent request scope and expect an unsupported report instead of rejection.

Centroid, distances and tuple angle are evaluated independently of plane fitting. A
failed/nonunique plane makes only its angle unavailable, with incomplete operation
coverage. P=Q or Q=G can make the tuple angle unavailable; Q=G also makes the normal
angle unavailable even when the plane itself is unique. Other values cannot upgrade
that status. Unqualified frames retain numbers inconclusively. Missing requested scope
and exhausted node budget never yield a convenient subset centroid. No certificate
capability or scientific negative is inferred by the raw collector.

## Existing scientific motivation, not a threshold authority

The pinned [CH–π dossier](../../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.INT.CH_PI.GEOMETRY.json)
and its inherited dossier/source map describe the C/H-centroid and directional
measurements missing from older contact summaries. They also explicitly require
empirical/H-placement work before favorable-interaction classification. This operation
implements the raw mathematics only; it does not claim those datasets were acquired
or calibrated. [P06 support](CENTROID_PLANE.md) explains centroid/plane independence.
No literature acquisition or interpretation was repeated for this implementation.

Synthetic analytic fixtures cover face/tangential directions, explicit-H coordinate
variation, tuple reversal, rigid rotation/translation, ring-point permutation, normal
sign invariance, collinear/coincident/nonunique planes, zero vectors, incomplete scope,
budget/frame failure, tampering and unknown/versioned operations. Independent JVM,
V1/V2, legacy solver and scientific-consumer replays are preserved. Broader B05 chemical
composition, methyl-environment channels and interaction calibration remain separate.

## Bounded chemical attribution acceptance

[The subsequent integration checkpoint](../../../../software/qualification/b05-chemical-attribution-20261005/CHECKPOINT.txt)
connects existing METHYL and RING6 reports to V3 through stable source atom mapping.
Both methyl occurrences and all six explicit bonded hydrogens survive; implicit H
counts never produce coordinates. Complete source identity reports survive exchange
alongside their references in the plan. This is explicit fixture composition, not an
automatic adapter or a favorable-interaction assertion. Candidate ring evaluation stays
NOT_EVALUATED; raw positive identity coverage does not forge a qualification receipt.
