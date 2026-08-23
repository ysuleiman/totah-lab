# INVALIDATED — Conservative local QM-atlas feasibility study

> **Do not cite any scientific conclusion in this report.** `ATLAS_FEASIBILITY_RESULT_VALID = false`. Validation-integrity audit required because held-out QM gradient information leaked into fitted curvature in the subsequent atlas implementation and shared preprocessing lacked programmatic provenance guards.

## Scope

This experiment uses exactly the 60 homogeneous GPU-QM TSL-RSH structures. It generates no QM, fits no force field, uses no neural network, and computes no Hessian. All energies and forces are the immutable PBE-D3(BJ)/def2-SVP GPU labels.

The experiment tests a first-order conservative atlas. It is not optimized against the historical 7.5 kcal/mol/Å criterion and is not a production-model selection exercise.

## Invariant coordinates and metric

The 1,962-dimensional coordinate map contains:

- 60 covalent bond lengths in Å;
- 114 bonded-angle cosines;
- 240 proper-dihedral components, stored as 120 ordered `(sin(phi), cos(phi))` pairs;
- 1,548 inverse intramolecular distances `1/r` for atom pairs separated by at least three graph bonds.

Every coordinate is translation and rotation invariant. Coordinate scales are their GPU-60 geometric standard deviations subject to fixed physical floors. Each of the four blocks is additionally divided by the square root of its dimension, so the through-space block cannot dominate merely because it contains more coordinates. No energy or force error was used to tune the metric.

Complete zero-based coordinate definitions, scaling vectors, equations, and finite-difference conventions are preserved in `CONSERVATIVE_LOCAL_QM_ATLAS_RESULT.json`.

## Conservative construction

For structure `i`, the first-order scalar chart is

`L_i(z) = E_i + g_i · (z - z_i)`.

The internal covector is the minimum-norm solution

`g_i = J_i (J_iᵀ J_i)⁺ ∇x E_i`.

Eight nearby charts are blended using normalized Gaussian weights:

`w_i(z) = exp(-||z-z_i||²/(2h²)) / Σj exp(-||z-z_j||²/(2h²))`.

The bandwidth is fixed geometrically as the median sixth-neighbor distance, `h = 1.4292323410`. No label-dependent bandwidth optimization was performed.

The scalar atlas and its exact gradient are

`Ê(z) = Σi w_i L_i`,

`∇Ê = Σi w_i g_i + Σi w_i(L_i-Ê)∇log(w_i)`.

Every predicted force is therefore

`F̂(x) = -Jz(x)ᵀ ∇z Ê`.

No Cartesian force vector is fitted independently. The RMS error when pulling the internal covectors back to their source QM gradients is `0.003325 kcal/mol/Å`, confirming that the invariant coordinate map spans the molecular gradient field to numerical accuracy.

## Pairwise smoothness

Across all 1,770 geometry pairs:

- geometry distance versus absolute QM energy difference: Spearman `0.7110`;
- geometry distance versus QM force difference: Spearman `0.8598`.

Median energy/force differences increase monotonically across distance quintiles:

| Distance quintile | Median ΔE, kcal/mol | Median force difference, kcal/mol/Å |
|---|---:|---:|
| 1 | 8.9166 | 9.2606 |
| 2 | 22.4581 | 12.1174 |
| 3 | 66.6781 | 14.8657 |
| 4 | 89.2299 | 42.6120 |
| 5 | 122.8548 | 56.2367 |

Thus local smoothness clearly exists in the frozen invariant metric.

## Leave-one-out reconstruction

Aggregate LOO errors:

- energy RMS: `65.8299 kcal/mol`;
- global force RMS: `22.3481 kcal/mol/Å`;
- sulfur-local force RMS: `24.8017 kcal/mol/Å`.

The atlas modestly improves over a zero-force predictor (`24.7174` global and `29.2398` sulfur-local), but its energy RMS is worse than the LOO mean-energy comparator (`58.5173`). It therefore does not work as a complete reconstruction over all 60 points.

The support dependence is strong:

- nearest distance versus absolute energy error: Spearman `0.7458`;
- nearest distance versus sulfur-local force error: Spearman `0.6159`;
- chart-force disagreement versus sulfur-local error: Spearman `0.5013`.

Approximate support strata show the transition:

| Support stratum | Energy RMS | Sulfur-local force RMS |
|---|---:|---:|
| nearest 25% | 12.37 | 10.85 |
| 25–50% | 20.91 | 11.46 |
| 50–75% | 23.08 | 16.80 |
| 75–90% | 90.08 | 36.19 |
| sparsest 10% | 168.28 | 54.32 |

The sparsest 25% account for `93.43%` of squared energy error and `79.89%` of squared sulfur-local error. The sparsest 10% alone account for `65.35%` and `47.96%`, respectively.

Using the preregistered LOO 90th-percentile support boundary and 75th-percentile error boundary produces six extrapolation failures and nine nominal interpolation failures. Several nominal interpolation failures sit close to the sparse-support boundary, but at least two occur at moderate support. Consequently local-chart disagreement/curvature remains a secondary interpolation problem even though sparse sampling dominates aggregate error.

## Leave-one-minimum-out transfer

| Held-out minimum | Energy RMS | Global force RMS | Sulfur-local force RMS |
|---|---:|---:|---:|
| MIN01 | 65.9783 | 23.8313 | 29.0699 |
| MIN02 | 68.8871 | 21.9103 | 25.8929 |
| MIN04 | 92.1764 | 22.4669 | 25.7161 |

All three transfers fail as reconstructions. MIN04 has the largest energy extrapolation, while MIN01 has the largest sulfur-local force error.

## Decision

The atlas premise is partly validated: invariant-space QM energy and force differences are locally smooth, close-support predictions are materially better, and both support distance and chart disagreement diagnose errors. The existing 60 points do not provide sufficiently dense support for a complete conservative first-order reconstruction.

The dominant failure is `SAMPLING`, with a secondary representation/metric/first-order-chart component. A Hessian experiment is not justified yet: Hessians would not repair the support holes responsible for most squared error, and the atlas metric/chart gauge should be stabilized using existing data before adding curvature information.

`LOCAL_SMOOTHNESS_EXISTS = true`

`CONSERVATIVE_LOCAL_RECONSTRUCTION_WORKS = false`

`ERROR_CORRELATES_WITH_SUPPORT_DISTANCE = true`

`MIN01_TRANSFER = FAIL (E_RMS=65.9783; GLOBAL_F_RMS=23.8313; LOCAL_F_RMS=29.0699)`

`MIN02_TRANSFER = FAIL (E_RMS=68.8871; GLOBAL_F_RMS=21.9103; LOCAL_F_RMS=25.8929)`

`MIN04_TRANSFER = FAIL (E_RMS=92.1764; GLOBAL_F_RMS=22.4669; LOCAL_F_RMS=25.7161)`

`DOMINANT_FAILURE = SAMPLING`

`HESSIAN_EXPERIMENT_JUSTIFIED = false`
