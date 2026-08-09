# Focused BA/DCMB/2,4-isomer/CONH geometric validation

## Decision

Adding the chemically distinct historical inhibitor CONH reveals **convergence of
the leading poses**, but not inhibitor-specific discrimination. Recurrent rank-1
DCMB and CONH poses in 7A contact SAM and conflict with every productive TSL
state; CONH produces even larger mean TSL-envelope overlap. However, benzylamine
and the 2,4 positional control also conflict with every TSL state in their leading
families. Across all alternate poses, productive-TSL interference is not enriched
among the two inhibitor-labeled compounds. CONH also has more 7B escape poses than
DCMB, showing that redirection is shared rather than DCMB-specific.

Mechanistic geometry remains more descriptive than docking score, but it cannot
yet identify the experimentally anchored DCMB phenotype reliably enough to
authorize a top-100 screen.

## Evidence labels

- **DCMB / LY-78335:** experimentally established recombinant human METTL7A
  inhibitor (IC50 1.17 μM) with reported lack of METTL7B inhibition in the modern
  study. [Russell et al. 2023](https://pmc.ncbi.nlm.nih.gov/articles/PMC10353073/)
- **CONH / UK-1187A:** historical TMT/PNMT inhibitor comparator. No direct
  METTL7A-versus-METTL7B experiment was located; no METTL7 selectivity is assigned.
  Historical PNMT use is supported by [Liang et al. 1982](https://doi.org/10.1016/S0022-3565(25)33343-4),
  and historical TMT inhibition is reported in the small-molecule
  methyltransferase literature.
- **Benzylamine and 2,4 positional analog:** historical benzylamine/PNMT SAR
  comparators; no direct METTL7 activity is assigned.

No missing activity value was imputed and no PNMT measurement was relabeled as a
METTL7 result.

## CONH chemical identity and preparation

Observed source identity: 2-cyclooctyl-2-hydroxyethylamine, PubChem CID 1551,
canonical connectivity `C1CCCC(CCC1)C(CN)O`, InChIKey
`NUOYMOJXFODLFN-UHFFFAOYSA-N`. [PubChem record](https://pubchem.ncbi.nlm.nih.gov/compound/1551)

The source record does not specify the alcohol-center stereochemistry. Both R and
S variants were therefore prepared explicitly. The primary amine is protonated
(+1), the hydroxyl is neutral, and both variants use the same ETKDGv3/MMFF94s and
Hephaestus PDBQT pipeline as the controls. `conh_compounds.csv` and
`conh_preparation_manifest.json` retain hashes and assumptions.

## Fixed experimental scope

Only BA, DCMB R/S, 2,4-isomer R/S, and CONH R/S were docked. WT METTL7A+SAM and
WT METTL7B+SAM were reused byte-for-byte; SAM was not moved or minimized. Vina
1.2.5 used seeds 1, 7, and 42, exhaustiveness 16, up to 12 poses, established
receptor-specific boxes, and identical preparation rules. The expanded campaign
contains 42 jobs and 502 poses.

The five accepted productive 7A TSL states and six accepted productive 7B TSL
states were reused without regeneration. They produced 2,760 pose/TSL comparisons.
The 7B structures match the receptor frame exactly. The accepted locally relaxed
7A structures remain in the direct receptor frame with 244-Cα RMSDs of
0.000–0.058 Å. No coordinate transformation was applied to TSL or ligand poses.

Containment uses the corrected METTL7B **FPOCKET pocket 2, 1,690.538 Å³,
197-alpha-sphere** homologous superpocket, rigidly transferred to 7A by the 244-Cα
homology fit. The retained `pocket1_vert.pqr` filename remains an indexing artifact,
not the biological pocket number.

## 1. Score reproducibility

| Receptor | BA | DCMB | 2,4 isomer | CONH |
|---|---:|---:|---:|---:|
| 7A + SAM | −6.017 ± 0.010 | −6.168 ± 0.002 | **−6.628 ± 0.010** | −6.523 ± 0.035 |
| 7B + SAM | −4.411 ± 0.001 | −5.262 ± 0.008 | −5.298 ± 0.037 | **−5.453 ± 0.004** |

Values are mean ± population SD of the best enantiomer/score per seed in kcal/mol.
Scores are highly reproducible, but their ordering has no validated activity
meaning: the unmeasured 2,4 control remains favored over DCMB in 7A and CONH is
favored in 7B despite lacking a direct METTL7 selectivity measurement.

Inference: score remains unsuitable as a top-100 ranking channel.

## 2. Leading-pose convergence in METTL7A

Every rank-1 pose of BA, DCMB, the 2,4 isomer, and CONH has at least one <2.0 Å
pair with every productive 7A TSL state. DCMB and CONH therefore converge on a
TSL-conflicting leading geometry, but the controls do too.

| 7A rank-1 evidence | BA | DCMB R | DCMB S | 2,4 R | 2,4 S | CONH R | CONH S |
|---|---:|---:|---:|---:|---:|---:|---:|
| Direct-conflict fraction | 100% | 100% | 100% | 100% | 100% | 100% | 100% |
| Mean core overlap (Å³) | 11.42 | 13.48 | 15.79 | 14.96 | 14.01 | **18.04** | **17.71** |
| Shared envelope (Å³) | 72.30 | 85.66 | 94.65 | 92.22 | 86.61 | **104.10** | **104.10** |
| Corridor-blocked fraction | **80.0%** | 20.0% | 20.0% | 6.7% | 33.3% | 26.7% | 0.0% |

Observed: CONH produces the largest leading-family TSL-volume occlusion despite
its different scaffold. This supports a physically possible general
TMT-inhibitor-compatible geometry. It does not establish an inhibitor-associated
signature because both non-METTL-validated controls reproduce direct conflict,
and BA blocks the finite catalytic corridor most often.

Pose recurrence is also not unique. CONH S rank 1 belongs to one family across all
seeds; CONH R uses the same leading family for seeds 1 and 42 but a different
substrate-facing family in seed 7. All 7A DCMB, BA, and 2,4 leading poses are
likewise recurrent across seeds.

## 3. Full-ensemble productive-TSL interference

| 7A all-pose evidence | BA | DCMB | 2,4 isomer | CONH |
|---|---:|---:|---:|---:|
| Direct TSL-conflict pairings | **58.3%** | 44.4% | 37.5% | **27.8%** |
| Mean core overlap (Å³) | 6.27 | 6.41 | 5.61 | 4.66 |
| Mean shared envelope (Å³) | 40.13 | 40.41 | 34.20 | 28.29 |
| Corridor-blocked pairings | 11.1% | 8.9% | 6.7% | 8.9% |

Observed: inhibitor-labeled DCMB and CONH are not enriched for interference when
all retained poses are considered. CONH has the strongest leading-pose overlap but
the lowest all-pose conflict frequency, indicating a bimodal or orientation-
dependent geometry rather than a uniformly substrate-occluding mechanism.

Inference: DCMB and CONH may access a common 7A TSL-conflicting family, but the
static ensemble does not show that this family is inhibitor-specific or dominant.

## 4. SAM compatibility

No 7A rank-1 pose has a hard ligand–SAM pair below 2.0 Å; all are SAM-contacting.
Across all 7A poses, fully compatible fractions are BA 5.6%, DCMB 4.2%, 2,4
isomer 5.6%, and CONH 13.9%. CONH therefore has more cofactor-compatible
alternatives than DCMB rather than reproducing a uniquely constrained DCMB state.

In 7B, fully compatible fractions are BA 19.4%, DCMB 9.9%, 2,4 isomer 12.7%, and
CONH 9.7%. CONH R's rank-1 family is fully SAM-compatible in all three seeds;
CONH S is compatible in two of three. DCMB rank-1 compatibility is less consistent.

## 5. 7A-versus-7B redirection and escape

| All-pose behavior | BA | DCMB | 2,4 isomer | CONH |
|---|---:|---:|---:|---:|
| 7A wider/escape poses | 5.6% | 4.2% | 9.7% | **26.4%** |
| 7B wider/escape poses | 11.1% | 18.3% | 22.5% | **37.5%** |
| 7B substrate-facing poses | 86.1% | 54.9% | 47.9% | **47.2%** |
| 7B direct TSL-conflict pairings | 86.1% | 54.9% | 47.9% | **47.2%** |

CONH has the strongest 7B escape/redirection signal, but also substantial 7A
escape. Thus 7B escape is shared with CONH and is not DCMB-scaffold specific.
DCMB's 7B redirection remains greater than BA's but weaker than both the 2,4
control and CONH.

## 6. Protein contact fingerprints

The recurrent 7A CONH leading poses contact the same central wall used by DCMB,
including Phe43, Leu145, His175, Trp195, Phe199, Cys202, Trp231, and Val234;
CONH additionally commonly contacts Asp200/Gly201. In 7B, CONH shifts to the
Leu39/Met40/Leu43, Ile198/Gly199, Cys202, Leu232, and Val234 wall. This is genuine
contact-level convergence and paralog redirection, but the complete fingerprints
show substantial overlap with the 2,4 comparator as well. Every pose's residue
set is retained in `per_seed_pose_metrics.csv` rather than collapsed into a score.

## Requested answers

1. **Does DCMB retain a distinctive structural phenotype?** Only partly. Its
   recurrent 7A leading family is TSL-conflicting and it redirects in 7B, but those
   properties are not unique and are sometimes stronger for controls.
2. **Does CONH reproduce that phenotype?** It reproduces and exceeds leading-pose
   TSL-volume occlusion, while showing a more bimodal full ensemble and more escape.
   This is partial functional convergence, not proof of one inhibitory mechanism.
3. **Is productive-TSL interference enriched among inhibitors?** No. BA has the
   highest all-pose direct-conflict frequency, while CONH has the lowest. Rank-1
   direct conflict is universal across all four parents.
4. **Is 7B escape DCMB-specific?** No. It is shared and strongest for CONH; the
   2,4 positional comparator also exceeds DCMB.
5. **Do the results strengthen mechanistic ranking readiness?** They strengthen
   the physical plausibility of a cross-scaffold TSL-occluding family, but weaken
   its specificity as a ranking rule. The model remains unready for top-100 use.

## Deliverables and readiness gate

The expanded machine-readable outputs contain 502 poses, 2,760 TSL comparisons,
233 unconstrained RMSD families, 42 seed reproducibility records, parent- and
variant-level summaries, SAM and superpocket geometry, directional occupancy,
catalytic-corridor metrics, and protein-contact fingerprints. Provenance and
thresholds are in `docking_manifest.json`, `conh_preparation_manifest.json`, and
`analysis_manifest.json`.

No top-100 docking was started. A future readiness gate still requires direct
METTL7 measurements for CONH, BA, and the 2,4 isomer or a validated orthogonal
dynamic/energetic discriminator.

**PARTIAL**
