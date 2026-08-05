# METTL7A pocket 32 ↔ METTL7B pocket 3 — validated interpretation

Date: validation via analysis/mettl7-correspondence/mettl7_validation.py
(see mettl7a_p32_mettl7b_p3_sequence_mapping.csv and
mettl7a_p32_mettl7b_p3_alignment_comparison.csv)

## Interpretation

METTL7A pocket 32 represents a large homologous subsite relative to
METTL7B pocket 3, but it omits aligned residues corresponding to the
METTL7B catalytic-cysteine region (CYS148, HIS175, CYS202, CYS203).

Those aligned residues occur outside pocket 32's residue set and are
associated with METTL7A pocket 19 (METTL7A's fpocket pocket #1).

This is evidence that fpocket segmented the homologous functional
region differently between METTL7A (as a larger merged pocket, #14)
and METTL7B (as a compact pocket). Pockets 19 and 32 must not be
merged automatically; the segmentation difference is a retrieval
concern, not evidence of missing function.

## Alignment finding (production-relevant)

For this pair, the unguided PCA+ICP registration aligns the wrong
subregion (0/27 sequence-consistent correspondences), while a
sequence-correspondence-seeded Kabsch recovers the correct frame
(31/31, chemistry 0.193 -> 0.823) with equivalent geometry
(0.263 vs 0.265). The low production chemistry score was an alignment
artifact, not biological divergence.
