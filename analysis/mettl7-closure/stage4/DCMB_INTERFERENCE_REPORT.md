# Stage 4: SAM-present DCMB pose families and DCMB×TSL interference

Status: **COMPLETE — PASS**. This stage uses only the eight locked Stage 2 systems and all 36 accepted Stage 3 TSL states. No mutant, docking setting, score interpretation, TSL gate, or interference definition was added or changed.

## Docking and pose-family gate

All 48 preregistered Vina jobs (8 systems × 2 enantiomers × 3 seeds) returned at least eight modes. The fixed SAM remained present as 49 prepared atom records in every receptor and its 27 heavy-atom coordinates match the target-specific Stage 2 SAM coordinates within the locked 0.001 Å precision tolerance.

The campaign produced 427 raw poses. The locked canonical-site rule retained 312 poses and explicitly rejected 115 off-site poses. Complete-linkage direct RMSD clustering at 2.0 Å produced 125 system-and-enantiomer-specific families. Representatives are geometric medoids with seed/mode tie-breaking; Vina scores are stored only as engine outputs. Every accepted family representative is SAM-compatible (minimum SAM distance at least 2.5 Å).

| System | Accepted DCMB families | Accepted family populations | Broad | State-dependent | Escape | Movement-gate families |
|---|---:|---:|---:|---:|---:|---:|
| 7A WT | 11 | 30 | 11 | 0 | 0 | 10 |
| 7A F43L | 19 | 54 | 19 | 0 | 0 | 4 |
| 7A F199G | 18 | 48 | 15 | 2 | 1 | 13 |
| 7A F43L/F199G | 22 | 54 | 22 | 0 | 0 | 4 |
| 7B WT | 19 | 46 | 16 | 0 | 3 | 0 |
| 7B L43F | 15 | 38 | 5 | 7 | 3 | 0 |
| 7B G199F | 11 | 20 | 3 | 0 | 8 | 0 |
| 7B L43F/G199F | 10 | 22 | 3 | 0 | 7 | 0 |

“Broad,” “state-dependent,” and “escape” apply the locked Stage 1 direct-overlap/transfer-corridor definitions. Movement-gate counts are a separate dimension and require at least 0.5 Å³ shared swept-envelope volume; they are evaluated only for the METTL7A limited-response states.

## Accepted computational distinction

**COMPUTATIONAL_HYPOTHESIS:** In the sampled canonical DCMB families, METTL7A is dominated by broad interference with its accepted productive TSL response states: WT, F43L, and the double have no escape family, while F199G alone admits one escape family and two state-dependent families. METTL7B retains clean non-interfering escape families in every background. L43F shifts METTL7B toward state dependence, whereas G199F and the double retain predominantly escape-capable DCMB families despite their contracted TSL ensembles.

This does not turn positions 43 and 199 into a reciprocal switch. Their effects remain background-dependent and distributed: neither reciprocal pair converts the paralog-wide architecture, and no docking score is used as mechanistic or potency evidence.

## Artifacts

- `pose_results.csv`: all raw modes with canonical-site acceptance or explicit off-site rejection.
- `family_results.csv` and `family_contacts.csv`: geometric family medoids, SAM compatibility, accessible volume, centroid/orientation, and residue contacts.
- `interference_state_matrix.csv`: all 600 DCMB-family × accepted-TSL-state measurements.
- `interference_family_matrix.csv`: locked family-level classification.
- `eight_system_interference_matrix.csv`: requested eight-system summary matrix.
- `seed_validation.csv`, `campaign_manifest.json`, and `validate_stage4.py`: reproducibility and fail-fast checks.
- `raw/`: all Vina PDBQT outputs and logs.

Stage 4 stops here. No ML/model work has been started.
