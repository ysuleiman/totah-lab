# Focused BA/DCMB/2,4-isomer geometric validation

## Decision

The multi-seed experiment confirms that the docking scores and leading pose
families are reproducible, but DCMB does not uniquely reproduce a METTL7A
inhibitory geometry. DCMB shows a real BA-relative increase in leading-pose TSL
volume occlusion and greater 7B escape behavior. The 2,4 positional isomer,
however, reproduces or exceeds both properties, and BA blocks the 7A catalytic
corridor more frequently. Mechanistic geometry is somewhat informative but is not
selective enough to authorize the top-100 SAM-bound screen.

## Fixed scope and provenance

Observed: only BA, DCMB R/S, and 2,4-dichloro-α-methylbenzylamine R/S were docked.
WT METTL7A+SAM and WT METTL7B+SAM were reused byte-for-byte from the validated SAR
checkpoint. SAM was neither moved nor minimized. All ligands retained the same +1
amine preparation. Vina 1.2.5 used seeds 1, 7, and 42, exhaustiveness 16, up to 12
poses, and the established receptor-specific boxes. This produced 30 jobs and 358
poses; two jobs returned 11 rather than 12 poses.

The five existing productive METTL7A TSL states and six existing productive
METTL7B TSL states were reused without regeneration or transformation. The 7B
states match the docking frame exactly. The locally relaxed 7A states remain in
the same frame with raw 244-Cα RMSDs of 0.000–0.058 Å, reflecting their previously
accepted local response rather than a coordinate-frame change.

The accepted homologous superpocket is the METTL7B **FPOCKET pocket 2,
1,690.538 Å³, 197-alpha-sphere** cloud. It was rigidly transferred to 7A by the
244-Cα homologous fit solely for matched containment measurement. No biological
pocket identity was inferred from the retained `pocket1_vert.pqr` filename.

## 1. Are score differences reproducible?

Yes, numerically—but score does not reproduce the desired ordering.

| Receptor | BA | DCMB, best enantiomer | 2,4 isomer, best enantiomer |
|---|---:|---:|---:|
| 7A + SAM | −6.017 ± 0.010 | −6.168 ± 0.002 | **−6.628 ± 0.010** |
| 7B + SAM | −4.411 ± 0.001 | −5.262 ± 0.008 | **−5.298 ± 0.037** |

Values are mean ± population SD of the best score from each seed, in kcal/mol.
Each 7A variant's rank-1 pose belongs to the same structural family in all three
seeds. This is reproducible failure, not random seed noise: the 2,4 isomer remains
favored over DCMB.

Inference: docking score is unsuitable as the ranking channel for a future broad
screen.

## 2. SAM compatibility and on-site occupancy

Observed: no 7A pose among the three compounds has a hard ligand–SAM pair below
2.0 Å. Rank-1 poses are SAM-contacting across every 7A seed. Across all poses,
fully compatible fractions are similarly low: BA 5.6%, DCMB 4.2%, and 2,4 isomer
5.6%.

In 7B the fully SAM-compatible fractions are BA 19.4%, DCMB 9.9%, and 2,4 isomer
12.7%. Thus DCMB does not uniquely coexist with SAM in 7B. BA has the most SAM-
compatible poses, although that fact alone does not imply productive coexistence.

On-site fractions in 7A are BA 63.9%, DCMB 48.6%, and 2,4 isomer 47.2%. In 7B
they are 97.2%, 73.2%, and 70.4%, respectively. All determinations use the matched
197-sphere homologous superpocket and remain separate from docking score.

## 3. Does DCMB occupy a more consistent 7A inhibitory geometry?

Partly, relative to BA, but not relative to the 2,4 isomer.

All three DCMB rank-1 poses reproduce one family for each enantiomer across seeds.
All rank-1 BA and 2,4-isomer poses are also recurrent across seeds. Against all
five productive 7A TSL states, every rank-1 pose of every compound has a direct
pair below 2.0 Å.

| 7A rank-1 comparison | BA | DCMB R | DCMB S | 2,4 R | 2,4 S |
|---|---:|---:|---:|---:|---:|
| Direct-conflict fraction | 100% | 100% | 100% | 100% | 100% |
| Mean core overlap (Å³) | 11.42 | 13.48 | 15.79 | 14.96 | 14.01 |
| Mean shared envelope (Å³) | 72.30 | 85.66 | 94.65 | 92.22 | 86.61 |
| Corridor-blocked fraction | **80.0%** | 20.0% | 20.0% | 6.7% | 33.3% |

Observed: DCMB's leading families occlude more TSL volume than BA, but the 2,4
isomer has comparable occlusion. BA, not DCMB, most consistently occupies the
finite TSL-S→SAM-methyl catalytic corridor.

Across all alternate poses rather than rank 1 alone, direct TSL-conflict fractions
are BA 58.3%, DCMB 44.4%, and 2,4 isomer 37.5%. Therefore DCMB is not the most
consistent substrate-conflicting ligand under alternate-pose sampling.

## 4. Does DCMB interfere with productive TSL more consistently?

No. It interferes more volumetrically than BA in the recurrent leading family,
but less frequently across the full pose ensemble. The 2,4 isomer reproduces the
leading-pose volume phenotype, and BA more frequently blocks the catalytic
corridor. These independent channels do not identify DCMB as uniquely inhibitory.

## 5. Does DCMB show stronger 7B redirection or escape?

DCMB shows a BA-relative increase, but the 2,4 isomer is stronger:

| All-pose 7B behavior | BA | DCMB | 2,4 isomer |
|---|---:|---:|---:|
| Wider/escape-subpocket poses | 11.1% | 18.3% | **22.5%** |
| Substrate-facing poses | 86.1% | 54.9% | **47.9%** |
| Direct TSL-conflict pairings | 86.1% | 54.9% | **47.9%** |
| Corridor-blocked pairings | 15.7% | **5.9%** | 8.5% |

Inference: dichloro-α-methyl substitution promotes 7B redirection relative to BA,
but positional chlorine placement is not discriminated in DCMB's favor. This may
describe a chemotype-level escape property, not the DCMB-specific METTL7A
phenotype.

## 6. Docking score versus mechanistic geometry

Mechanistic geometry is more informative than score because it exposes recurrent
TSL occlusion and 7B redirection that score cannot describe. It is not yet a useful
DCMB-specific discriminator: BA and especially the 2,4 isomer reproduce major
channels, sometimes more strongly. The evidence dimensions are therefore retained
separately; no master score was constructed.

## 7. Is the model ready for top-100 screening?

No. A broad screen would rank compounds using a model that reproducibly favors the
wrong positional isomer and does not uniquely recover DCMB's known METTL7A
phenotype. A defensible next gate would require METTL7A activity data for BA and
the 2,4 isomer, or an orthogonal dynamic/energetic discriminator validated against
those measurements. No top-100 jobs were launched.

## Machine-readable deliverables

- `per_seed_pose_metrics.csv`: score, seed, rank, SAM geometry, containment,
  centroid/orientation, and directional subpocket for all 358 poses.
- `tsl_interference_metrics.csv`: 1,968 pose/TSL comparisons with direct, volume,
  envelope, and catalytic-corridor channels.
- `pose_families.csv` and `pose_family_behavior.csv`: unconstrained RMSD family
  assignments, seed recurrence, and substrate-conflict/escape behavior.
- `cross_seed_reproducibility.csv`: rank-1 family and geometry per seed.
- `matched_compound_summary.csv` and `matched_parent_summary.csv`: matched 7A/7B
  variant-level and racemate-aware parent summaries.
- `tsl_frame_validation.csv`, `docking_manifest.json`, and
  `analysis_manifest.json`: provenance and operational definitions.

**PARTIAL**
