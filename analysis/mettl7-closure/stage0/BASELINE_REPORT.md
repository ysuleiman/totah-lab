# METTL7A/METTL7B canonical baseline — freeze candidate

Status: **FINAL_WITH_DECLARED_LIMITATIONS**. The baseline is frozen for Stage 1. No new docking was run.

## Canonical structural inputs

METTL7A is UniProt **Q9H8H3** (TMT1A); METTL7B is **Q6UX53** (TMT1B). The canonical receptor coordinates are AlphaFold DB v6 models with 244 residues in chain A. These are predictions, not experimental structures.

The canonical SAM-containing structures are the validated rigid-fit products under `analysis/dcmb/sam_state/validated/`. Their SAM placements originate from BioHub `esmfold2-fast-2026-05` ligand-conditioned predictions. Both fits use all 244 C-alpha pairs, have fitted RMSD below 0.62 Å, contain 27 SAM heavy atoms, and have no receptor–SAM heavy-atom pairs below 1.5 Å. They remain **PREDICTION** evidence.

The biological pocket reference is the homologous **197-sphere SAM superpocket**. An fpocket ordinal is never sufficient to identify it. For METTL7B it is fpocket pocket 1 / database pocket id 3 in the imported run and pocket 2 in the checked-in rerun. METTL7A fpocket pocket 1 is a smaller 59-sphere DCMB-facing subsite, not the whole superpocket.

The previously transient METTL7A realization of the 197-sphere cloud is now materialized at `METTL7A_homologous_197_sphere_SAM_superpocket.pqr`. It is a deterministic 244-C-alpha Kabsch transfer, not an independently detected pocket.

## Evidence boundary

- **EXPERIMENTAL:** Russell et al. report DCMB/LY-78335 IC50 1.17 µM against purified recombinant N-GST-METTL7A using TSL, and no inhibition of purified N-GST-METTL7B in the matched comparison. TSL methylation was measured through TMSL formation. Primary source: DOI `10.1124/dmd.123.001268`, PMID `37137720`, PMCID `PMC10353073`.
- **STRUCTURAL OBSERVATION:** atom coordinates, fitted RMSDs, pocket containment, residue correspondence, contacts, overlaps, and displacements measured directly from named artifacts.
- **COMPUTATIONAL HYPOTHESIS:** productive TSL states, DCMB–TSL interference, mutation effects, and the proposed release/accommodation mechanism.
- **PREDICTION:** AlphaFold/ESMFold structures, docking poses/families, and docking-engine outputs. Vina values are not potency estimates.

## Retained baseline

- Five restrained-response WT METTL7A SAM/TSL pre-transfer states are retained. The post-transfer SAH/TMSL structures are diagnostic only.
- Six fixed-receptor WT METTL7B SAM/TSL pre-transfer states are retained.
- Only DCMB families generated with SAM physically present and assigned to the canonical catalytic site may enter the final mechanistic comparison.
- The normalized inventory contains 214 SAM-bound poses: 161 accepted on-site and 53 explicitly rejected outside the canonical site. Those accepted poses form 66 families across the four previously evaluated receptor identities.
- The residue correspondences used by the final matrix include F43/L43, F199/G199, W195/W195, and E232/L232.
- Existing fixed-backbone mutant structures are inputs to Stage 2 validation, not automatically canonical prepared receptors. METTL7B L43F specifically requires documented local L229 accommodation.

## Supported working hypotheses

Current coordinates support—but do not experimentally establish—the following:

1. Every retained WT METTL7A SAM-bound DCMB family conflicts with every retained productive TSL state.
2. METTL7B retains three directional DCMB families that spatially escape all retained TSL states, although most METTL7B families still interfere.
3. Positions 43 and 199 contribute in a background-dependent distributed pocket field; existing fixed-backbone results do not support a simple symmetric switch or resolvable synergistic epistasis.
4. W195/L232 describe destination-side accommodation and backbone-conditioned geometry more convincingly than an independently sufficient selectivity determinant.

These are geometric/modeling conclusions. They do not establish binding affinity, inhibition type, residence time, biological causality, or the complete biochemical mechanism.

## Deprecated evidence

- All METTL7B results mixing the old AlphaFold v2 receptor frame with v6 pocket spheres are invalid.
- Pocket ordinals used as cross-run biological identifiers are invalid; run-specific mappings must accompany the 197-sphere site identity.
- Protein-only DCMB poses cannot support the canonical SAM-bound mechanistic comparison.
- Failed static WT METTL7A TSL candidates cannot support DCMB–TSL interference conclusions.
- The forced, unrepacked METTL7B L43F rotamer is not a final receptor state.

## Declared limitations carried forward

The baseline is frozen with three transparent limitations:

1. Original host/container lockfiles were not retained for every historical calculation. Stage 1 must lock the environment used for the final matrix.
2. Final METTL7B L43F and L43F/G199F conformers will be selected during Stage 2 under the frozen L229-aware rule in `l43f_selection_rule.json`; no DCMB or TSL result may influence that selection.
3. METTL7A post-transfer states remain diagnostic-only and are not productive-state evidence.

Normalized evidence tables are `productive_tsl_inventory.csv`, `dcmb_family_inventory.csv`, and `dcmb_pose_inventory.csv`. The materialization/runtime records are `superpocket_transfer.json` and `runtime_provenance.json`.

The complete machine-readable record is `canonical-baseline.json`; canonical file hashes are in `SHA256SUMS`.
