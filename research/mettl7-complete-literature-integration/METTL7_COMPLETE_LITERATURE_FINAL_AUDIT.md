# METTL7 complete-literature final audit

Audit date: 2026-09-09

## Frozen corpus accounting

- Primary papers: 13
- Unpublished lab evidence records: 1
- Claim records: 34
- VERIFIED: 20
- PARTIAL: 6
- UNVERIFIED: 7
- CONTRADICTED: 1
- `METTL7_COMPLETE_LITERATURE_CORPUS = FROZEN`

Expression-only disease papers were excluded unless they added biochemical, substrate, mutational, structural, pharmacological, or otherwise mechanistic evidence.

## Completed freeze gates

1. The 2024 HDAC-thiol study was fully extracted. Purified N-GST-METTL7A and N-GST-METTL7B (0.06 mg/mL; DMPG; KPi pH 7.0; SAM 500 uM; estimated reduced romidepsin approximately 500 uM; 2 h at 37 C) each produced monomethylated romidepsin by LC-MS. Vehicle/no-SAM and boiled-enzyme controls were negative or trace. No Km, kcat, Ki, or IC50 was reported for this reaction.
2. The final 2024 conserved-TMT1A study was extracted separately from its preprint. Five full-length C-terminal-FLAG orthologs in stable HEK293 cells generated extracellular dimethylated romidepsin after 10 uM romidepsin for 24 h and conferred cellular resistance. This is direct cellular product evidence, not purified-enzyme kinetics.
3. Every thiol/thiol-like chemical inherited in the unified substrate matrix was dispositioned in `METTL7_THIOL_COMPOUND_CONDITION_AUDIT.csv`. Cysteine and glutathione remain condition-specific negatives, never universal nonsubstrates. The glutathione/GST-fusion sequestration caveat is explicit.
4. TSL kinetic competence is established for both paralogs. Russell 2023 direct-product Km values are 39.41 uM (A) and 32.50 uM (B); Maldonato 2021 reports 48.9 +/- 5.5 uM for B using a different byproduct assay. Netarsudil selectivity therefore cannot be explained simply by absent TSL activity in A.
5. The final Shentu supplement is formally documented unavailable. Publisher metadata exposed two disabled attachments, including `final_all_supplemental_materials_07282026_1.pdf`; preprint/final preparation equivalence is not assumed.
6. Chen's netarsudil claim is narrowed to operational activation. The separate SPR reliability claim records the 9.53 mM fitted KD and its approximately 95-fold extrapolation beyond the 100 uM maximum tested concentration.

## Corrections and retained boundaries

- ST7464AA1/ST7612AA1 was downgraded from claimed methylation/resistance to `NOT_TESTED / PROVENANCE_UNRESOLVED`.
- Reduced romidepsin was upgraded to `DIRECT_POSITIVE` for both purified A and B.
- KD5170, largazole, OKI-005, and NCH-51 were not upgraded from cellular pharmacology to purified substrates.
- `TKT` and `ACSL3` remain cellular m6A association + MeRIP-dependent + genetically inferred; neither is a direct purified substrate.
- FILIP1L remains strong targeted cellular/genetic evidence, not purified sequence-defined turnover.
- `CLM-018`, `CLM-019`, and `CLM-020` retain their negative/unverified outcomes.
- The Rheem 0.25 mg/mL value describes a spheroplast preparation, not purified METTL7B. DCMB's listed 25 uM role and any co-incubation remain unresolved.

## Exact-location audit

Thirty-two of 34 claim rows contain a figure, table, methods/result-section locator, or an exact local evidence artifact. Two rows are explicitly `EXACT_LOCATION = UNRESOLVED` (CLM-014 and the negative-search claim CLM-020). Exact-location completion is therefore **94.1% (32/34)**. No location was invented to close a gap.

## Freeze meaning

The freeze records a saturated and internally reconciled evidence baseline. It does not convert PARTIAL or UNVERIFIED claims into facts. Recovery of the Shentu final supplement, raw Rheem assay records, or a new primary mechanistic paper requires a versioned reopening.

## Primary sources

1. [Maldonato et al. 2023, matched TMT1A/TMT1B study](https://pmc.ncbi.nlm.nih.gov/articles/PMC10353073/)
2. [Maldonato et al. 2021, METTL7B alkyl-thiol methyltransferase](https://pmc.ncbi.nlm.nih.gov/articles/PMC7921093/)
3. [Robey et al. 2024, thiol-HDAC inhibitor resistance](https://pmc.ncbi.nlm.nih.gov/articles/PMC11223745/)
4. [Hegedus et al. 2024, conserved TMT1A activity](https://pmc.ncbi.nlm.nih.gov/articles/PMC11056289/)
5. [Chen et al. 2026, METTL7B vascular study](https://pubmed.ncbi.nlm.nih.gov/42639676/)
6. [Shentu et al. 2026, final publication record](https://pubmed.ncbi.nlm.nih.gov/42578282/)
