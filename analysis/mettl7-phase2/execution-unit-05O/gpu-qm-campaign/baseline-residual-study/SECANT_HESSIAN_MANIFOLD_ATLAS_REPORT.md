# INVALIDATED — Secant-Hessian manifold atlas study

> **Do not cite any scientific conclusion in this report.** `ATLAS_FEASIBILITY_RESULT_VALID = false`. The original implementation leaked held-out gradients into fitted curvature. The patched rerun is also withdrawn until a separate adversarial provenance audit passes.

## Scope and pool audit

No QM was generated. No neural model or force field was fitted, and no analytic Hessian was calculated. Curvature comes only from symmetric least-squares estimates of existing QM-gradient secants between the 60 frozen GPU anchors.

The repository geometry inventory contains 783 rows, but it is not a homogeneous 783-geometry TSL-RSH manifold:

- 726 rows are 56-atom `C22H30O3S` structures;
- those represent 700 unique geometries after checksum deduplication;
- 57 rows have different atom counts/compositions or unspecified composition;
- all 60 GPU anchors occur in the compatible pool.

The 57 incompatible rows cannot be assigned a distance in the fixed 56-atom TSL-RSH internal-coordinate manifold. They are preserved in the audit and are not silently coerced into it.

## Manifold and curvature construction

The frozen invariant coordinate families remain covalent bonds, bonded-angle cosines, periodic dihedral sine/cosine pairs, and inverse intramolecular distances. Geometry-only means and scales are recomputed over all 700 unique compatible pool geometries. A symmetric 12-nearest-neighbor graph is connected.

At every labeled anchor:

- a six-dimensional tangent space is obtained from the 24 nearest pool geometries;
- the 12 nearest labeled anchors supply gradient secants;
- a symmetric `6×6` curvature matrix is estimated from `Δg ≈ H Δz`;
- no second derivatives are evaluated;
- the second-order scalar chart is differentiated analytically to obtain forces.

The complete symmetric Hessian eigenvalues, secant residuals, neighbor identities, predictions, and force arrays are preserved in the JSON artifacts.

## Reconstruction results

| Validation | Atlas | Energy RMS | Global force RMS | Sulfur-local RMS |
|---|---|---:|---:|---:|
| LOO | Manifold first order | 58.5172 | 21.4846 | 24.2752 |
| LOO | Manifold second order | 49.1108 | 20.6908 | 23.3951 |
| Leave-minimum-out | Manifold first order | 71.3734 | 21.8675 | 26.6856 |
| Leave-minimum-out | Manifold second order | 50.7044 | 20.7993 | 24.5990 |

Relative to manifold first order, secant curvature improves:

- LOO energy RMS by `16.07%`;
- LOO global-force RMS by `3.69%`;
- LOO sulfur-local RMS by `3.63%`;
- leave-minimum-out energy RMS by `28.96%`;
- leave-minimum-out global-force RMS by `4.89%`;
- leave-minimum-out sulfur-local RMS by `7.82%`.

The reconstruction still does not work globally: errors remain large and the frozen P90-support/P75-error interpolation-failure definition gives `10` failures for both manifold first- and second-order charts. Secant curvature changes which points fail but does not reduce the count.

## Minimum transfer

Second-order leave-one-minimum-out metrics:

| Held-out minimum | Energy RMS | Global force RMS | Sulfur-local RMS |
|---|---:|---:|---:|
| MIN01 | 40.0775 | 17.9525 | 20.9633 |
| MIN02 | 46.6422 | 20.7400 | 23.4200 |
| MIN04 | 62.6985 | 23.3536 | 28.7641 |

The geometry-only manifold metric modestly improves first-order transfer relative to the frozen Euclidean atlas (`26.6856` versus `26.9371` sulfur-local RMS). The leakage-free manifold-plus-curvature construction improves it further to `24.5990`, so the manifold metric improves transfer, but none of the minimum transfers is an acceptable reconstruction.

## Support-error relationship and pool coverage

For second-order LOO:

- graph support distance versus sulfur-local force error: Spearman `0.8034`;
- local chart disagreement versus sulfur-local force error: Spearman `0.7612`.

Isotonic regression of the 60 LOO sulfur-local errors against graph support distance places the empirical `7.5 kcal/mol/Å` crossing at:

`support distance = 0.9019732231`.

This is a diagnostic coverage threshold, not a production success gate for the atlas.

Among the 700 unique compatible geometries:

- median current support distance: `0.6374948062`;
- 90th percentile: `1.2976992479`;
- 95th percentile: `1.5135665132`;
- maximum: `2.9853248793`.

A deterministic farthest-first graph-cover estimate requires 25 additional labels to bring all compatible pool nodes to `≤0.9019732231`. This is an estimated k-center cover, not proof of the combinatorial optimum. The exact ordered IDs, source paths, geometry checksums, current distances, and selection flags are in `GPU783_MANIFOLD_SUPPORT_COVERAGE.csv`. No selected geometry was submitted for QM.

## Decision

Secant curvature improves average reconstruction and cross-minimum transfer, but it does not reduce interpolation failures and remains far from a complete reconstruction. The strong, reproducible support-error relationship and finite 25-point coverage estimate keep the atlas premise viable as a sampling-driven research direction.

`SECANT_HESSIAN_RECONSTRUCTION_WORKS = false`

`INTERPOLATION_FAILURES_REDUCED = 0 (10 -> 10)`

`MANIFOLD_METRIC_IMPROVES_TRANSFER = true`

`MAX_CURRENT_SUPPORT_DISTANCE = 2.9853248793452725` for the compatible manifold; undefined for 57 incompatible inventory rows

`SUPPORT_DISTANCE_FOR_7_5_FORCE_ERROR = 0.901973223087143`

`ESTIMATED_ADDITIONAL_QM_POINTS = 25`

`NEXT_QM_GEOMETRY_IDS = see GPU783_MANIFOLD_SUPPORT_COVERAGE.csv`

`ATLAS_PREMISE_STILL_VIABLE = true`
