# Netarsudil activation–inhibition reconciliation

## Bounded conclusion

The two observations are not measurements of the same endpoint. Chen et al. directly measured netarsudil binding to surface-immobilized METTL7B by SPR and measured dose-dependent induction of **METTL7B mRNA** in Ang II-stimulated VSMCs (EC50 0.1 µM, 24 h, n=5), followed by protein-axis and contractility phenotypes. The paper calls this an agonist/activator experiment, but it does not report netarsudil in a purified METTL7B catalytic assay.

Dr. Rheem's current result directly measures inhibition of METTL7B-dependent TSL methylation in a SAM-containing spheroplast endpoint assay (IC50 approximately 40 µM; 45 min), with no detectable METTL7A inhibition in the matched assay.

Accordingly, the evidence supports both context-specific statements without establishing a biochemical contradiction:

- `CHEN_CELLULAR_EXPRESSION_PHENOTYPE = ACTIVATION-LABELED`
- `RHEEM_TSL_METHYLATION_ENDPOINT = INHIBITION`
- `NETARSUDIL_DIRECTLY_ACTIVATES_METTL7B_CATALYSIS = NOT_TESTED_IN_CHEN`
- `MECHANISTIC_EXPLANATION_FOR_CONTEXT_DIFFERENCE = UNRESOLVED`

Neither the Chen SPR binding result nor its docking score establishes catalytic activation. Conversely, the Rheem TSL assay does not test whether netarsudil induces METTL7B expression in VSMCs.

## Exact Chen netarsudil evidence

1. **In-silico screen:** AlphaFold METTL7B (Q6UX53) screened against FDA/clinical compound libraries; netarsudil docking score −11.494. This is candidate selection, not activity evidence (main text p. 15-16; Fig S8A-B).
2. **SPR binding:** purified METTL7B immobilized on a CM5 sensor at 5000-10000 RU; netarsudil 0.1-100 µM in twofold dilutions; Biacore 8K, 10 C; 120-s association and 120-s dissociation; 1.05x PBS-P plus 5% DMSO; multicycle kinetics; duplicate runs; 1:1 Langmuir equilibrium fitting (Fig 8E; supplement Methods pp. 22-23). Fig 8E prints **KD = 9.53E-03 M (9.53 mM)**. This fitted KD is approximately 95-fold above the highest tested analyte concentration (100 µM), so it is a far extrapolation and should not be described as high-affinity binding.
3. **Operational activation endpoint:** Ang II-stimulated VSMCs treated for 24 h; qRT-PCR measured METTL7B mRNA induction across concentrations plotted from 10^-10 to 10^-4 M, yielding EC50 0.1 µM (n=5; Fig 8F).
4. **Downstream cellular endpoints:** 24-h immunoblots of METTL7B, HNRNPH1 and FILIP1L (Fig 8G); 36-h collagen-gel contraction, n=5 (Fig 8H and Fig S8C).
5. **In-vivo dependency:** 20 mg/kg/day intraperitoneal netarsudil from day 7 of Ang II infusion; benefit was lost in VSMC-specific Mettl7b knockout mice (Fig 8I-K).

Netarsudil was **not reported** in the purified poly(A)-RNA assay. It was **not reported** with TSL, captopril, another thiol substrate, or a direct methyl-product readout. The purified RNA experiment and the netarsudil experiments are separate experimental branches.

## Chen purified RNA assay, kept separate

The cell-free assay used recombinant human METTL7B residues 24-244 expressed in baculovirus-infected Hi-5 cells, isolated by Ni-IMAC after urea extraction and stepwise refolding. Recombinant protein was incubated with polyadenylated RNA and SAM for 2 h at 37 C with a no-protein control. RNA was hydrolyzed and analyzed by LC-MS/MS as an m6A/rA ratio using m6A standards (Fig 2E-F; supplement Methods pp. 7-8). Enzyme, RNA and SAM concentrations and the RNA's sequence/source were not reported. No netarsudil arm was described.

## FILIP1L evidence chain

The chain combines MeRIP-seq, eCLIP and RNA-seq candidate convergence; cellular m6A enrichment at the FILIP1L/Filip1l 3' UTR; HNRNPH1 recruitment; precursor/mature RNA measurements and decay assays; and mutation of a predicted `GGACT` motif to `AAGTC` in cellular and rescue constructs (main Fig 5 and Fig 7; supplement Fig S5-S7). This is strong targeted cellular/genetic evidence, but it is not a purified, sequence-defined FILIP1L methyl-transfer assay.

The paper's METTL7B catalytic-motif mutants—G80R/G82R (Mut1), D98A (Mut2), and the combined G80R/G82R/D98A (Mut3)—were tested by global m6A dot blot in transfected HEK293T cells (Fig 2I-J). They were not used in the netarsudil experiment.

## UNTESTED hypotheses

The following are hypotheses only and are not promoted to conclusions:

- `UNTESTED_CONSTRUCT_OR_PREPARATION_DEPENDENCE`
- `UNTESTED_LIPID_DEPENDENCE`
- `UNTESTED_SUBSTRATE_DEPENDENT_PHARMACOLOGY`
- `UNTESTED_ASSAY_FORMAT_DEPENDENCE`
- `UNTESTED_CONCENTRATION_DEPENDENT_OR_BIPHASIC_BEHAVIOR`
- `UNTESTED_INDIRECT_ASSAY_ARTIFACT`

## Required resolving experiment

A matched experiment would use the same METTL7B preparation and buffer to measure direct product formation across the same netarsudil concentration series with TSL and the RNA substrate, with vehicle, no-enzyme, no-SAM, heat-inactivated enzyme, and recovery/interference controls. This is a proposed discriminator, not evidence about the current mechanism.
