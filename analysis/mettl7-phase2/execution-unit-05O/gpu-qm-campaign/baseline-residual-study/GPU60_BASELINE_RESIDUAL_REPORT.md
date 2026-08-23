# GPU-60 baseline-residual study

## Scope and integrity

This study uses exactly the 60 frozen A100 PBE-D3(BJ)/def2-SVP labels. It generated no QM, altered no labels, and trained no production model. The 45-development/15-validation split was frozen before any baseline residual was calculated. The sealed validation points were not used by the diagnostic locality regressions; those probes use the 45 development points only.

The split CSV SHA-256 is `f19ca00e57d4c4851e789126cbd57faec82344d108c99fcdf6df5d12f6b3225a`. There are exactly five sealed-validation points from each of MIN01, MIN02, and MIN04.

Energy residuals below remove one independent mean offset per source minimum. This is only an energy-reference convention; no force-field or model parameter was fitted.

## All-60 baseline diagnostics

| Baseline | Energy RMS | Global force RMS | Sulfur-local force RMS | Sulfur-atom vector RMS |
|---|---:|---:|---:|---:|
| GAFF2 | 14.0952 | 14.9437 | 21.4724 | 29.9412 |
| Frozen Delta V2 2B + GAFF2 | 13.8393 | 14.8440 | 21.4540 | 28.2809 |
| MACE-OFF24 medium, zero-shot | 6.0677 | 9.0425 | 10.1465 | 21.4472 |

Energies are kcal/mol; forces are kcal/mol/Å. Complete RMS/MAE/maximum values, all Cartesian residual arrays, per-atom RMS values, and x/y/z component summaries are preserved in the JSON artifacts.

The historical best Amber extension is **not evaluable on GPU-60 without refitting**. Its old 45-point training-projection result (`9.6799378314` kcal/mol/Å sulfur-local RMS) survives, but its fitted 26-coefficient vector was not persisted. Reconstructing those coefficients would violate the no-refit instruction, so the historical number is not presented as a GPU-60 result.

## Transfer and perturbation structure

Sulfur-local RMS by source minimum:

| Baseline | MIN01 | MIN02 | MIN04 |
|---|---:|---:|---:|
| GAFF2 | 13.8999 | 15.5091 | 30.8133 |
| Delta V2 2B | 13.5634 | 15.1813 | 31.0868 |
| MACE zero-shot | 9.2242 | 9.2520 | 11.7547 |

Sulfur-local RMS by perturbation family:

| Baseline | Force cloud | Optimization path | Other existing | Torsional/constrained |
|---|---:|---:|---:|---:|
| GAFF2 | 15.5400 | 10.4927 | 9.1233 | 28.9044 |
| Delta V2 2B | 15.1603 | 11.4274 | 8.7572 | 29.1265 |
| MACE zero-shot | 9.3688 | 9.1927 | 9.0837 | 11.2770 |

GAFF2 sulfur-local error correlates most strongly with within-minimum energy/strain rank (Spearman `0.7782`). Individual local-coordinate correlations are moderate rather than dominant: the largest are `ANGLE_9_10_S = 0.4511` and `ANGLE_S_10_37 = -0.4323`. Source-minimum membership explains `19.89%` of variance in per-geometry GAFF2 sulfur-local residual magnitude. MIN04 and torsional/constrained structures are the clear transfer limitations.

## Locality probes

The locality probes predict only the per-geometry sulfur-local residual magnitude. They are diagnostic regressions, not force models. Fixed ridge and median-distance RBF probes were cross-validated on development only.

For GAFF2, five-fold RBF R² rises from `-0.991` for the two sulfur radial distances to `0.720` with the bonded-neighbor environment, `0.798` for the frozen sulfur-local shell, `0.826` through three bonds, and `0.842` through five bonds. Whole-molecule pair distances do not improve it (`0.804`). However, every shell has negative leave-one-source-minimum-out R² (best approximately `-0.127`).

This is **MIXED locality**: the error magnitude is smooth and locally explainable within the sampled distribution, but the mapping does not transfer across source minima without conformation/minimum context. The evidence does not support a one-coordinate repair or a source-agnostic compact local correction.

## High-force and high-strain tails

Ranking points by QM sulfur-local force magnitude:

| Set | GAFF2 sulfur-local RMS | Fraction of total squared local error |
|---|---:|---:|
| Lower 80% | 21.8368 | 82.74% |
| Top 20% | 19.9486 | 17.26% |
| Top 10% | 21.8032 | 10.31% |

Thus the high-QM-force tail does **not** dominate aggregate RMS. Eleven of its top 12 points are deliberate force-cloud perturbations and one is torsional/constrained. Their S-C, S-H, and C-S-H values remain covalently plausible, but whether they belong to the intended MD operating distribution is `UNKNOWN` because no MD-domain distribution was frozen.

Strain is different: the high-strain third has GAFF2 sulfur-local RMS `33.0701`, versus `9.8454` low and `13.8792` medium, and contributes `79.07%` of total squared sulfur-local error. The classical failure is therefore primarily a high-strain/cross-minimum failure, not an artifact of only the six or twelve largest QM-force structures.

## Model-class evidence

`FLEXIBLE_ML_POTENTIAL_REQUIRED` is the supported class-level conclusion:

- the preserved V2 2B correction changes sulfur-local RMS by only `0.086%` relative to GAFF2;
- zero-shot MACE reduces it by `52.75%`, showing that a flexible learned representation captures substantial missing structure;
- compact local descriptors explain within-distribution magnitude but fail leave-minimum-out transfer;
- MIN04 and high-strain errors show that a purely local, source-agnostic parametric repair is not supported.

This does not select a final implementation and does not authorize training. The present 60 labels are sufficient for this model-class diagnosis, so additional QM is not required now.

## Final state

`GPU_60_SPLIT_FROZEN = true`

`BASELINE_60_EVALUATION_COMPLETE = false` (historical best-Amber coefficient artifact is missing; every preserved, scientifically evaluable comparator is complete)

`GAFF2_SULFUR_LOCAL_RMS = 21.4724341962683 kcal/mol/A`

`BEST_AMBER_SULFUR_LOCAL_RMS = NOT_EVALUABLE_ON_GPU60`

`MACE_ZERO_SHOT_SULFUR_LOCAL_RMS = 10.146534039991106 kcal/mol/A`

`RESIDUAL_LOCALITY = MIXED`

`HIGH_FORCE_TAIL_DOMINANT = false`

`HIGH_FORCE_TAIL_MD_RELEVANT = UNKNOWN`

`MODEL_CLASS_EVIDENCE = FLEXIBLE_ML_POTENTIAL_REQUIRED`

`MORE_QM_REQUIRED_NOW = false`
