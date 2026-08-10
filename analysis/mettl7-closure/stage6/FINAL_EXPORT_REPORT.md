# Deterministic ML graph adapter export

Status: **EXPORT COMPLETE — VALIDATED — NO TRAINING**.

## Architecture

The export introduces no independent molecular graph semantics. Each example is an immutable adapter envelope whose protein channel is a Gaia `ResidueGraphView`; Athena supplies experimental annotations; Hephaestus/Gaia supply deposited ligand/cofactor atoms and canonical topology; contributing cavities reference Gaia `AlphaSphereSet` geometry. The audit found no missing general protein-graph capability requiring a Gaia API change.

The export is deliberately reconstruction-friendly rather than a framework-specific tensor dump. It records canonical source identity, domain adapter and query parameters, experimental annotations, leakage metadata, raw cavity geometry, missingness, and ablation membership. A downstream loader must call the named domain APIs; it may not reinterpret edge semantics.

## Final feature matrix

| Layer | Node/raw channels | Edges | Availability | Role |
|---|---|---|---|---|
| Geometry-only | Source residue coordinates; minimum/CA/centroid distances | Gaia distance-query pairs | Per-measurement optionality | Ablation input |
| Residue graph | Gaia residue identity, chemistry/status, source coordinates | Gaia sequence edges with provenance; spatial distances; atom proximities | Canonical graph query status | Primary protein input |
| Ligand/cofactor | Deposited atoms, element, formal charge, aromaticity; explicit SAM/SAH/SFG identity | Hephaestus/Gaia covalent topology; Athena direct/shell contacts | Donor/acceptor is explicitly unavailable until canonicalized | Added ablation layer |
| Cavity | Every fpocket sphere center and radius, pocket membership | Rebuildable sphere surface-gap edges at ≤1.0 Å | Present for all 697 graphs | Added ablation layer |
| Experimental grammar | Site membership, mapped UniProt position, mapping outcome, direct/shell role, correspondence and variability | Annotation joins only | Null plus reason when unevaluated | Masked targets/context |
| Grouping | Physical-site and leakage-component IDs | None | Present for 394 mapped sites; explicit missingness otherwise | Sampling/split metadata only |

Detailed classification of every geometry/chemistry channel is in `feature-classification.csv`. fpocket scores/druggability, Vina outputs, METTL7 computational classes, fabricated donor/acceptor states, and numeric-zero missingness are excluded.

## Exported inventory

| Artifact/count | Value |
|---|---:|
| Source atlas assemblies | 416 |
| Structures contributing canonical sites | 341 |
| Experimental site graph envelopes | 697 |
| Site-residue annotation rows | 20,906 |
| Sites with mapped physical group | 394 |
| Physical-site groups | 108 |
| Sites with raw cavity geometry | 697 |
| Raw alpha-sphere references | 129,501 |

Checksums:

- `graph-envelopes.jsonl`: `88178c47ff7a2926f444a356af22136b86ad97f92d08efdc707b54990c93bc78`
- `alpha-spheres.csv`: `69d174a18686027f2046680080c488da02e6c0d4ddbd90f4f45e72cb7550ebc0`
- `site-residue-annotations.csv`: `e1f750f066d80d154847b494ff433d2986991eb882b0308f2feea9a52046c80c`

## Ablations and baselines

All four variants use the same 697 envelopes and grouping metadata:

1. geometry-only;
2. Gaia residue graph;
3. residue graph + ligand/cofactor;
4. residue graph + ligand/cofactor + raw cavity geometry.

Retrieval will be compared against PocketMatch symmetric rank, PocketMatch query-coverage rank, production global geometric retrieval, and aligned raw pocket geometry. Evaluation is by physical-site group using group recall@K, mean reciprocal rank, and median first-relevant rank. These remain diagnostic grouped retrieval comparisons, not a claim of family-held-out supervised generalization.

## Validation

- Gaia module: 134 tests passed, including all 10 `ResidueGraph` tests.
- Export: 697 unique, lexicographically ordered graph IDs.
- Grouping: accepted audit counts reproduced exactly (394 mapped sites, 108 groups at direct-position Jaccard ≥0.50).
- Cavity: every graph has contributing raw spheres; all 129,501 radii are positive.
- Domain boundary: every envelope names Gaia protein/cavity and Hephaestus ligand adapters.
- Checksums match the manifest; rerunning the export produced identical hashes.
- No graph tensor, fitted vocabulary/normalizer, embedding, model weight, or training run exists.
