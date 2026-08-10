# Experimental corpus and ML representation — pre-training review

Status: **DESIGN COMPLETE; TRAINING NOT STARTED**.

## Frozen benchmark boundary

Stages 0–4 are frozen by `benchmark-lock.json` and `BENCHMARK_SHA256SUMS`. The accepted statement is recorded at the **COMPUTATIONAL_HYPOTHESIS** evidence level exactly as follows:

> METTL7A shows accommodation-dependent productive TSL geometry and predominantly broad DCMB interference, whereas METTL7B retains static productive TSL configurations and DCMB escape families in every tested background. Positions 43 and 199 modulate these landscapes but do not constitute a reciprocal selectivity switch.

No Stage 0–4 result is a training label. Vina scores are excluded from both features and objectives.

## Corpus schema and audited examples

The export unit is one experimentally observed ligand occurrence at one canonical experimental site. Each example links PDB biological assembly and artifact provenance, human UniProt mapping, chain/entity identifiers, canonical and physical-site group identifiers, ligand/component occurrence, explicit cofactor identity, residue nodes, ligand/cofactor atom nodes, typed edges, availability masks, and field-level derivation provenance. The exact field contract is in `corpus-schema.json`.

Accepted audit snapshot:

| Quantity | Count |
|---|---:|
| Experimental human methyltransferase structures in atlas | 416 |
| Canonical experimental site observations / candidate graphs | 697 |
| Cofactor-site observations | 275 |
| Organic-ligand-site observations | 422 |
| Unique CCD components | 218 |
| Strong / weak localizations | 684 / 13 |
| Sites with mapped target and direct-contact positions | 394 |
| Targeted sites lacking mapped direct-contact positions | 284 |
| Sites without human-target association | 19 |
| Physical-site groups at residue Jaccard ≥0.50 | 108 |
| Accepted target correspondences | 156 |
| Site-grammar residue evidence rows | 52,499 |
| Positive physical-site relationships | 19 |
| Hard-negative candidates pending adjudication | 281 |

The 697 graphs are usable for provenance-aware self-supervision with missing-feature masks; they are not 697 independent supervised labels. The 394 mapped observations collapse to only 108 physical-site groups. All 19 positive relationships occur in one large experimental-family component.

## Graph representation

The representation is a typed heterogeneous graph:

- Residue nodes retain amino-acid identity, chemistry class, mapped position, coordinates, direct/shell roles, structural variability, and only actually available accessibility/conservation features.
- Ligand atoms and cofactor atoms are separate node types with element/type, formal charge, aromaticity, donor/acceptor state, coordinates, and covalent topology.
- Edges independently encode sequence adjacency, residue spatial contact, ligand covalent bonds, and residue–ligand/cofactor direct or near-shell evidence. Distances remain raw edge features; contact types are categorical evidence, not a combined score.
- SAM, SAH, and SFG remain chemically distinct explicit subgraphs. Cofactor identity is not collapsed into a generic flag.
- Cartesian coordinates are retained for audit. Learning inputs use invariant distances and, if an equivariant architecture is selected later, relative vectors; no global-frame orientation is learned accidentally.
- Every unavailable field is represented by `value=null`, `evaluated=false`, and a reason. Absence of evidence is never encoded as zero or as a negative label.

## Leakage control and split strategy

The leakage unit is the **physical-site relationship**, not the crystallographic row. Before any split:

1. Collapse repeat structures for a target when mapped direct-contact sets form a connected component at Jaccard ≥0.50; assign one immutable `physical_site_group_id`.
2. Join physical-site groups connected by accepted target correspondence or a labelled site relationship into a `leakage_component_id`.
3. Keep every PDB, assembly, alternate ligand occurrence, conformer, same/analogous CCD, and homologous target from that component in exactly one partition.
4. Fit normalization, vocabularies, masking frequencies, and any CCD-derived statistics on training only.
5. Keep METTL7A, METTL7B, all eight computational systems, DCMB, and TSL outside every fitting and hyperparameter-selection partition.

A conventional 80/10/10 supervised split is **not valid now**: the accepted-correspondence graph has two components of 54 and 2 targets; the large component contains all 19 positives. Therefore no train/validation/test division can simultaneously prevent family leakage and place positives in each partition.

The only approved current split artifacts are:

- `retrieval_corpus`: all 697 experimental graphs, with repeat-aware group IDs, for deterministic indexing and masked self-supervision;
- `grouped_resampling_folds`: diagnostic physical-site-group folds inside the dominant family, explicitly labelled non-independent and unsuitable for claims of generalization;
- `supervised_test`: `NOT_AVAILABLE_INSUFFICIENT_INDEPENDENT_FAMILIES`;
- `mettl7_benchmark`: frozen, external, prediction-only evaluation set; never a training/validation split.

Supervised go criterion: at least five independent experimental families containing positive relationships, with positives and scientifically adjudicated negatives supportable in every family-held-out partition and no single family dominating labels.

## Proposed objectives

Recommended now, in order:

1. **Masked experimental contact reconstruction:** mask residue–ligand/cofactor direct/shell edge roles and predict the observed categorical role using only experimental graphs. Loss is computed only where the role was evaluated.
2. **Masked residue/chemistry reconstruction:** mask residue identity or chemistry class at experimentally localized site nodes. Group-balanced sampling prevents repeated structures from dominating.
3. **Within-corpus retrieval/contrastive representation learning:** positives are repeat observations of the same physical-site group or accepted experimental homologous relationships; batches and evaluation are physical-group aware. This is representation learning, not a leakage-safe supervised generalization claim.
4. **Experimental ligand-site compatibility ranking, deferred:** enable only after negative adjudication and independent-family expansion. No unoccupied pocket, mapping failure, same-CCD discordance, or AlphaFold cavity is an automatic negative.

Vina-score regression/classification and prediction of the frozen METTL7 interference classes are prohibited objectives.

## Exact METTL7A/METTL7B evaluation role

METTL7A/7B form a fully external, downstream benchmark. After the encoder, feature preprocessing, hyperparameters, and retrieval rules are frozen using experimental data alone:

1. Encode canonical SAM-present 7A WT and 7B WT sites as unlabelled queries; mutants may be encoded only for benchmark sensitivity analysis.
2. Retrieve experimental site neighbors and report raw, separate evidence: residue chemistry, cofactor-contact geometry, contact-role patterns, cavity graph topology, and embedding/retrieval distance.
3. Test whether the representation distinguishes the two WT sites and whether mutant movements are background-dependent, without fitting a decision boundary to the eight systems.
4. Compare the representation’s structural separation with the frozen Stage 3/4 matrix only after predictions are sealed. Report agreement or disagreement; do not back-propagate, tune thresholds, or relabel experimental examples from this result.

The benchmark question is: **does an experimentally learned site representation recover a structural distinction consistent with accommodation-dependent/broad-interference 7A versus static-feasible/escape-capable 7B?** It is not a potency, inhibition, or experimentally validated selectivity test.

## Review stop

No graph tensors, trainable embeddings, fitted normalizers, model weights, or training runs have been created. Review is required before implementing the deterministic graph exporter or starting any representation learning.
