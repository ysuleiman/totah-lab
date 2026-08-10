# Experimental representation learning and sealed METTL7 benchmark

Status: **COMPLETE — EXPERIMENTAL REPRESENTATION SEALED — EXTERNAL QUERY RUN ONCE**.

No Vina score, DCMB, TSL, METTL7 structure, mutation, or frozen Stage 0–4 outcome entered fitting, normalization, model selection, or hyperparameter selection. There is no family-held-out generalization claim.

## Stage 6.1 materialization

The offline corpus contains 697 unchanged graph IDs and exactly reproduces the Stage 6 grouping-assignment hash. It contains 11,741 canonical site residue nodes, 10,942 sequence edges, 22,775 spatial edges, 17,511 deposited ligand/cofactor atoms, 18,770 CCD covalent bonds, and 129,501 unchanged alpha spheres. Spatial edges use heavy atoms at 4.5 Å and preserve minimum, Cα, centroid, and every establishing atom-pair distance. Cavity adjacency uses signed sphere surface gap ≤1.0 Å. Both rules are stored in every graph and the manifest.

The compressed graph hash is `4031b46c02ea073b9b440602492695adfb87c1c759cfe222cfccca9ad5e6843c`; independent repeated generation reproduced it exactly. The loader verifies all 219 files (graph archive plus 218 CCD definitions) before returning data and never accesses the database.

## Experimental retrieval ablations

All retrieval metrics exclude another observation from the same PDB and use the identical 297 queries having an independently repeated physical-site group.

| Representation | Method | R@1 | R@5 | R@10 | MRR | Median rank |
|---|---|---:|---:|---:|---:|---:|
| Geometry only | deterministic PCA | 0.498 | 0.741 | 0.822 | 0.611 | 2 |
| Geometry only | small autoencoder | 0.337 | 0.636 | 0.731 | 0.473 | 3 |
| Gaia ResidueGraph | deterministic PCA | **0.822** | **0.953** | **0.976** | **0.885** | **1** |
| Gaia ResidueGraph | small autoencoder | 0.795 | 0.943 | 0.953 | 0.862 | 1 |
| Residue + ligand/cofactor | deterministic PCA | 0.795 | 0.939 | 0.973 | 0.860 | 1 |
| Residue + ligand/cofactor | small autoencoder | 0.747 | 0.919 | 0.960 | 0.823 | 1 |
| Residue + ligand/cofactor + cavity | deterministic PCA | 0.741 | 0.896 | 0.939 | 0.817 | 1 |
| Residue + ligand/cofactor + cavity | small autoencoder | 0.710 | 0.865 | 0.902 | 0.779 | 1 |

The simplest sufficient representation is therefore the non-neural Gaia ResidueGraph PCA. Additional chemical/cavity dimensions did not improve repeat-site retrieval, and no learned autoencoder beat PCA. Selection was frozen from these experimental results before METTL access.

## Deterministic baselines

| Baseline | R@1 | R@5 | R@10 | MRR | Median rank |
|---|---:|---:|---:|---:|---:|
| PocketMatch symmetric | 0.838 | 0.946 | 0.970 | 0.889 | 1 |
| PocketMatch query coverage | 0.232 | 0.488 | 0.677 | 0.357 | 6 |
| Production geometric channels | 0.226 | 0.458 | 0.552 | 0.328 | 7 |
| Aligned raw-pocket geometry | 0.273 | 0.468 | 0.542 | 0.366 | 8 |

PocketMatch symmetric slightly exceeds the selected PCA at R@1/MRR; PCA slightly exceeds it at R@5/R@10. The small model has not established superiority over deterministic structural retrieval.

## Masked tasks and controls

- Contact-role reconstruction: observation accuracy 0.701; balanced accuracy 0.533. The gap indicates class imbalance and limited minority-role learning.
- Masked residue identity from non-identity node channels: accuracy 0.525; balanced accuracy 0.499 versus majority accuracy 0.110. This is diagnostic within-family reconstruction, not independent generalization.
- Training examples were inverse-weighted by physical-site group. Missing groups and unevaluated labels were excluded, never made negative.
- Target ID, CCD ID, cofactor identity, PDB ID, and physical-group ID were excluded from learned input features. Same-PDB retrieval matches were removed.
- Physical-group hash partitions were used for masked-task evaluation. Because all positive relationships remain in one dominant family, these metrics can still contain homologous-family signal and cannot establish family transfer.

Learning curves are preserved in `results/learning-curves.json`. The autoencoders converged smoothly but plateaued below their deterministic PCA counterparts.

## Sealed one-time METTL7 queries

The selected artifact is `residue_graph-sealed.npz`, SHA-256 `2e0871c3abe6292b1897ce7655014d2a63c6a441a61f95500783776a801086b9`. METTL7A WT and METTL7B WT were encoded once after sealing. Their embedding distance is **2.015**. Standardized raw-layer separation is 1.587 from geometry channels and 2.085 from residue-graph channels; the selected representation contains no ligand/cavity layer, so those layers cannot contribute to its learned A/B separation.

Nearest-neighbor lists and all requested per-neighbor distances/similarities are in `results/sealed-mettl-benchmark.json`. The closest 7A site is PDB 4MIK/JIL at distance 4.034; the closest 7B site is PDB 9FKM/A1IC5 at 5.272. These are retrieval observations, not functional assignments.

Mutant movements relative to their own WT backgrounds:

| Probe | Movement | Fraction of WT A/B separation |
|---|---:|---:|
| 7A F43L | 0.420 | 0.208 |
| 7A F199G | 0.751 | 0.373 |
| 7A double | 0.994 | 0.493 |
| 7B L43F | 0.326 | 0.162 |
| 7B G199F | 0.345 | 0.171 |
| 7B double | 0.577 | 0.286 |

**COMPUTATIONAL OBSERVATION:** all six mutations perturb their own background but none moves by the full WT A/B separation. Effects differ by position and background, consistent with modulation of distributed architecture rather than a reciprocal switch. This is a frozen-model sensitivity result, not a new experimental label.

## Failure analysis

- PocketMatch symmetric remains at least as strong as the selected learned representation; model complexity is not justified yet.
- Ligand and cavity additions hurt repeat-site retrieval, plausibly from high heterogeneity and limited independent groups; this does not show those layers are biologically irrelevant.
- Contact balanced accuracy is weak despite reasonable observation accuracy.
- The residue classifier emitted an optimizer convergence warning; its result is diagnostic only.
- The one-time cavity/free-space per-neighbor scalar cosine is identically 1.0 and therefore uninformative. It is retained as a failed diagnostic and was not rerun or tuned after observing METTL results.
- Query construction and evaluation remain computational and cannot validate selectivity, inhibition, or potency.

No ligand generation, selectivity prediction, or supervised ligand-site compatibility was performed.
