# DCMB mechanism/SAR validation

## Executive outcome

The explicit-SAM docking model gives a reproducible **paralog separation**, but it
does **not** reproduce the dramatic historical benzylamine SAR strongly enough to
claim a validated DCMB mechanism.

Observed: in the SAM-bound receptors, the best DCMB enantiomer scores −6.164
kcal/mol in METTL7A versus −5.323 kcal/mol in METTL7B. DCMB has no fully
SAM-compatible target-site poses among 12 generated poses per enantiomer in 7A,
but has 2 (R) and 1 (S) in 7B. However, benzylamine and DCMB differ by only 0.124
kcal/mol in the 7A+SAM rank-1 scores, and the 2,4-dichloro positional isomer scores
better than DCMB there (−6.633 versus −6.164 kcal/mol).

Inference: the static model supports a hypothesis that 7B offers more ways for
DCMB to coexist with SAM, while 7A imposes a narrower cofactor-contacting pose.
It does not establish that this geometry causes inhibition, nor explain why DCMB
is dramatically stronger than benzylamine.

## A. Receptor reconstruction validation

Observed: the canonical protein coordinates and rigidly transferred SAM are
unchanged from the accepted validation. Protein fits used 244 Cα pairs, with
0.550 Å (7A) and 0.611 Å (7B) RMSD. The closest protein–SAM heavy-atom distances
are 2.486 and 2.311 Å, respectively; neither complex has a pair below 2.0 Å.
Both retain 25 SAM-contacting protein residues at 4.5 Å.

The inspectable PDB and mmCIF files are under `receptors/`. The 7B pocket is
**FPOCKET pocket 2, 1,690.538 Å³, 197 alpha spheres**. Its copied source file is
named `pocket1_vert.pqr` only because of rerun/indexing; that filename is not used
as the biological pocket number. The 7A 59-sphere artifact is a smaller DCMB
subsite intersecting the homologous SAM superpocket.

Uncertainty: SAM was not minimized or experimentally resolved in these models;
the coordinates are a validated homologous transfer. That uncertainty is retained
rather than hidden by relaxation.

## B. SAR ligand set and activities

Observed: `sar_compounds.csv` contains 12 named parents and 18 prepared variants:
benzylamine; α-methylbenzylamine; all three monochloro positional isomers;
2,3-dichlorobenzylamine; DCMB; four additional dichloro-α-methyl positional
comparators; and SKF-64139. Unspecified α stereocenters are represented as both R
and S. All docking structures use the documented +1 amine state.

The directly verified METTL datum is DCMB IC50 = 1.17 μM for recombinant human
METTL7A; the same study reports no METTL7B inhibition at high tested
concentrations without a numeric IC50. The historical 1973 PNMT paper establishes
the qualitative benzylamine series and identifies 2,3-dichloro substitution as
among the most active, but its numeric table could not be recovered in a form safe
to transcribe. Those cells are explicitly blank—no activities were invented.
SKF-64139 identity is PubChem CID 123920; its retained 0.0031 μM value is for human
PNMT, not METTL7.

Sources: [Fuller et al. 1973 SAR](https://doi.org/10.1021/jm00260a002),
[Russell et al. 2023 METTL7A/B](https://pmc.ncbi.nlm.nih.gov/articles/PMC10353073/),
[SKF-64139 identity](https://pubchem.ncbi.nlm.nih.gov/compound/123920), and
[SKF-64139 PNMT context](https://pmc.ncbi.nlm.nih.gov/articles/PMC7558223/).

## C. Apo docking

Observed parent-best rank-1 scores (kcal/mol):

| Compound | 7A apo | 7B apo |
|---|---:|---:|
| benzylamine | −5.873 | −4.713 |
| α-methylbenzylamine | −6.148 | −5.150 |
| 2-chlorobenzylamine | −6.045 | −5.031 |
| 2,3-dichlorobenzylamine | −6.279 | −5.282 |
| DCMB | −6.966 | −5.497 |
| SKF-64139 | −7.202 | −5.990 |

Inference: hydrophobic growth is rewarded in apo 7A, and the BA→DCMB separation
is 1.093 kcal/mol. This is a docking-score trend, not an activity prediction.

## D. SAM-bound docking

Observed parent-best rank-1 scores (kcal/mol):

| Compound | 7A + SAM | 7B + SAM |
|---|---:|---:|
| benzylamine | −6.040 | −4.427 |
| α-methylbenzylamine | −6.229 | −4.817 |
| 2-chlorobenzylamine | −6.009 | −4.850 |
| 2,3-dichlorobenzylamine | −6.436 | −5.076 |
| DCMB | −6.164 | −5.323 |
| 2,4-dichloro-α-methylbenzylamine | −6.633 | −5.285 |
| SKF-64139 | −6.336 | −5.480 |

Observed: adding SAM penalizes the 7A DCMB rank-1 score by 0.608 kcal/mol (R) or
0.802 kcal/mol (S), while benzylamine improves by 0.167 kcal/mol. In 7B, SAM
penalizes DCMB only 0.272 (R) or 0.103 (S) kcal/mol.

## E. SAM–ligand geometry

Observed: all four rank-1 7A/7B DCMB poses are `SAM_CONTACTING`, not hard-clashing.
Minimum DCMB–SAM heavy-atom distances are 3.340/3.445 Å in 7A (R/S) and
3.982/3.994 Å in 7B. No rank-1 DCMB pose has a <2 Å overlap. In 7A, all target-site
DCMB alternatives contact SAM and none is fully compatible; 7B retains three
fully compatible alternatives across the two enantiomers.

The 7A DCMB rank-1 chlorine environments include Phe43, His175, His237, Leu145,
Tyr47, and Val234. In 7B they shift toward Leu43/Gly199/Leu232/Trp195 and related
wall residues. The complete per-pose centroids, axes, contact fingerprints,
candidate hydrogen bonds, burial, mouth projection, and documented overlap proxy
are in `pose_metrics.csv`.

Inference: SAM constrains DCMB more sharply in 7A; 7B redirects it toward the
43/199/232 wall and retains alternative coexistence geometries. The absence of a
hard clash argues against a simple steric-ejection mechanism.

## F. Experimental SAR versus computational trend

Observed: apo 7A orders BA < α-methyl < 2,3-dichloro < DCMB by Vina score, but the
explicit-SAM 7A model collapses BA versus DCMB to 0.124 kcal/mol and incorrectly
prefers at least one positional isomer. The available literature values are also
insufficient for a defensible numeric rank correlation because most exact 1973
table values remain unverified.

Verdict: **the current explicit-SAM docking model fails strict SAR validation**.
It cannot presently answer “why is DCMB dramatically stronger than benzylamine?”
with a quantitatively discriminating structural trend.

## G. METTL7A/METTL7B comparison

Observed: DCMB rank-1 scores favor 7A by 0.84–0.94 kcal/mol in the SAM state, yet
7B permits fully SAM-compatible alternate DCMB poses whereas 7A does not. Rank-1
apo→SAM centroid shifts are 1.56/1.11 Å in 7A and 11.19/6.85 Å in 7B (R/S), showing
large 7B redirection rather than a single conserved binding mode.

Inference: the best-supported selectivity feature is not “7B is simply larger.”
It is a combination of weaker 7B docking and greater 7B orientational escape/
coexistence around Gly199/Leu43/Leu232, versus a tighter Phe43/Phe199 7A wall.

## H. Strongest supported mechanism

DCMB can occupy the accepted catalytic pocket of both paralogs. In 7A it is more
favorably docked but forced into SAM-contacting wall-packed solutions, while 7B
has weaker rank-1 binding and more alternative SAM-compatible orientations. This
is consistent with, but does not prove, selective 7A inhibition through a tighter
ternary-pocket perturbation rather than direct SAM steric displacement.

## I. Alternative explanations

The phenotype may depend on protein dynamics, membrane context, slow conformational
selection, protonation microstates, water networks, substrate-dependent ternary
states, or kinetic mechanism. Vina scores omit these. PNMT SAR may also be an
imperfect surrogate for METTL7A SAR; only DCMB has a verified METTL7A activity in
the retained table.

## J. Unresolved uncertainties and next gate

No ligand strain energy was reported because no validated strain decomposition is
available. Hydrogen bonds are distance candidates without angular validation.
The overlap volume is a clearly labeled pairwise sphere-intersection proxy, not an
exact union volume. One deterministic Vina seed with up to 12 poses was used; the
campaign is broad enough for alternate orientations but not uncertainty-calibrated.

Before any top-100 screen, the useful next experiment is experimental recovery of
the full historic activity table and a focused ensemble/redocking sensitivity test
for BA, DCMB, and the 2,4 positional isomer. The present result is a checkpoint,
not a validated predictive model.
