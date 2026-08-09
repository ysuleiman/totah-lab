# Experimental training-feasibility audit

Date: 2026-08-09  
Authoritative inputs: experimental binding sites, target correspondence method
version 2, and experimental site grammar derived from accepted correspondences.

## Decision

The current evidence does **not** support leakage-safe supervised ML. The
recommended decision is **B: expand the experimental dataset first**. Until
then, the defensible use of the current evidence is **C: a provenance-aware
retrieval/rule-based or exploratory positive-unlabelled formulation**, with no
claim of independently validated predictive performance.

The limiting factor is not the number of residue rows. It is the number and
independence of experimentally observed physical-site relationships.

## Counting rules

- Only cofactor and organic-ligand canonical experimental binding sites were
  considered.
- Only version-2 `ACCEPTED` target correspondences were considered.
- No unoccupied fpocket cavity was labelled negative.
- No low-confidence target correspondence was labelled negative.
- A physical-site observation required a human target and mapped UniProt
  positions for direct-contact residues.
- Repeated observations on the same target were grouped when their mapped
  direct-contact residue sets formed a connected component at residue Jaccard
  >= 0.50. Group-count sensitivity is reported below.
- A positive site relationship required at least two aligned direct-contact
  residues and aligned-contact Jaccard >= 0.25.
- A hard-negative candidate required two experimentally occupied sites on an
  accepted homologous target pair, at least three mapped direct-contact
  residues on each side, and zero aligned direct-contact overlap.
- Hard-negative counts are an audit upper bound. They still require scientific
  review before use as labels, especially where both sites bind the same CCD.

## Evidence inventory and distributions

| Quantity | Count |
|---|---:|
| Canonical site observations | 697 |
| Cofactor observations | 275 |
| Organic-ligand observations | 422 |
| Unique CCD IDs | 218 |
| Strong localizations | 684 |
| Weak localizations | 13 |
| Sites with mapped target/direct-contact positions | 394 |
| Sites with a target but no mapped direct-contact position | 284 |
| Sites with no human-target association | 19 |

The direct-contact residue count has min/Q1/median/mean/Q3/max of
0/6/12/11.55/16/25. The near-shell distribution is
1/11/17/16.85/23/36. Contributing fpocket cavities per canonical site are
1/2/3/3.01/4/9.

The ligand distribution is strongly concentrated at the top: SAH 141, SAM
111, SFG 41, FMT 24, X9L 16, and MTA 12. SAH, SAM and SFG alone account for
293/697 (42.0%) observations despite the presence of 218 CCD IDs.

## Physical-site redundancy

At direct-residue Jaccard >= 0.50, the 394 mappable observations collapse to
108 target-local physical-site groups. Group size has min/median/mean/max of
1/1/3.65/31. Fifty-two groups contain repeats, accounting for 338/394 (85.8%)
of all mappable observations.

The number of groups is sensitive to the grouping threshold, but the
conclusion is not:

| Direct-residue Jaccard threshold | Physical-site groups |
|---:|---:|
| 0.30 | 87 |
| 0.50 | 108 |
| 0.70 | 141 |
| 1.00 (exact sets) | 234 |

Exact residue-set equality therefore overcounts variations caused by construct,
resolution, alternate ligand pose, or incomplete contacts.

## Pair labels after physical-site collapse

There are 156 accepted version-2 protein pairs. Forty-five have no defensible
site-pair label under the rules above. The remaining 111 protein pairs yield:

| Label | Unique physical-site relationships | Raw crystallographic pair expansion |
|---|---:|---:|
| Positive | 19 | 1,380 |
| Hard-negative candidate | 281 | 2,885 |
| Total | 300 | 4,265 |

Thus 98.6% of the apparent positive crystallographic pairings are repeated
evidence of the same 19 physical-site relationships. Across both labels, 93.0%
of the 4,265 apparent pairings disappear after grouping.

Positive-count sensitivity also shows the small-data problem: 130 candidate
site pairs share at least one aligned direct residue, 53 share at least two,
and only 19 pass both the two-residue and Jaccard criteria. Weakening the rule
would mostly convert ambiguous partial overlap into labels.

Of the 19 positives, 11 share a CCD ID and 8 involve different CCD IDs. Of the
281 hard-negative candidates, 175 involve different CCD IDs and 106 share a
CCD ID. The same-CCD negative candidates are scientifically interesting but
must not be accepted automatically as negatives.

## Families and strict grouped splits

The accepted-correspondence graph contains 56 targets with an accepted edge.
It forms only two connected empirical families, of sizes 54 and 2. Label
distribution by family is:

| Family | Positives | Hard-negative candidates |
|---|---:|---:|
| 54-target component | 19 | 279 |
| 2-target component | 0 | 2 |

Grouping by shared physical-site relationships produces the same effective
split structure: one component with 83 site groups containing all 19 positives
and 279 negatives, and one component with 3 site groups containing only 2
negatives.

Consequently:

- training on the large family leaves a test set with zero positives;
- holding out the large family leaves a training set with zero positives;
- an 80/10/10 family-grouped split is impossible;
- a site-grouped split that prevents the same relationship appearing on both
  sides is also impossible to evaluate meaningfully.

The 52,499 site-grammar residue rows are feature/evidence rows, not independent
examples. Treating residues, structures, or ligand occurrences as independent
samples would create severe biological leakage.

## Recommendation and stop/go criteria

Do not train a supervised classifier from this cohort now. Expanding model
complexity would measure memorization of one dominant methyltransferase family
and repeated SAM/SAH/SFG crystallography, not generalization.

For a future supervised go decision, collect enough independent experimental
families and physical-site relationship groups to place positive and hard
negative evidence in every family-held-out partition. A practical minimum audit
target is at least 5 independent families with positives, with no family
dominating the labels; preferably substantially more before using a flexible
model. The present data have 1 such family.

Useful work now is:

1. repair or complete residue mapping for the 284 targeted sites lacking mapped
   direct-contact positions;
2. expand experimental structures toward underrepresented methyltransferase
   families and non-SAM/SAH ligand classes;
3. manually adjudicate the 106 same-CCD hard-negative candidates;
4. use AlphaFold/fpocket structures only for retrieval or prospective
   application, not as labelled negatives;
5. retain the current evidence as a deterministic benchmark, case-based
   retrieval corpus, and source for prospective hypotheses.

No dataset, embeddings, synthetic negatives, model, or training artifacts were
created by this audit.
