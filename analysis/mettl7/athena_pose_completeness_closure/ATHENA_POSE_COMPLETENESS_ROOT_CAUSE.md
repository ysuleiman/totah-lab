# Athena/METTL7 pose-completeness closure

## Root cause

The two originally reported runs do not contain nine coordinate poses. Their Vina logs list nine scored modes, but their immutable `poses.pdbqt` files contain eight `MODEL`/`ENDMDL` blocks. Mode 9 lies outside the frozen 3 kcal/mol energy range and was not emitted as coordinates. The runner nevertheless wrote `parsedPoseCount=9` from the parsed stdout score table. Thus the count divergence occurs between score-table parsing and Vina coordinate emission, not inside `PdbqtReader`, conversion, profiling, aggregation, or CSV writing.

For both `B0__MCV1N_OUTER_ISOPROPYL__s1` and `B0__MCV1N_OUTER_ISOPROPYL__s7`, the pre-repair trace is: receipt expected 9; stdout scores 9; PDBQT models 8; parsed 8; converted 8; profiled 8; emitted 8. No ninth molecular structure exists to profile, and none was fabricated.

## Java fixes

`Mettl7V2DockingCampaignRunner` now derives the receipt count from the emitted PDBQT through Hermes `PdbqtReader`. `Mettl7IncrementalPosePostProcessor` now enforces receipt expected = parsed = profiled = emitted for each run, removes partial rows on failure, marks the run invalid, and keeps it in `remainingRuns`.

Four stale neighbor-validation receipts were corrected from 9 to their authoritative emitted-model count of 8. Their originals remain under `original_receipts/`. Postprocessing was rerun from the existing PDBQT files: 18/18 valid runs, 158 emitted pose rows, zero invalid and zero remaining.

## Blast radius

After repair of the current 18-run corpus, the repository scan found 67 historical table entries representing 66 unique run IDs with receipt-versus-PDBQT disagreement. These are preserved in `ATHENA_POSE_COMPLETENESS_MISMATCH_INVENTORY.csv`. Each row carries a retrospective-impact classification. Historical scientific pose rows are unaffected where the table contains exactly every coordinate model, but historical receipt/completeness assertions require recomputation under the repaired contract. No history was deleted or silently rewritten.

The earlier requirement to profile pose 9 is inapplicable to these two runs: pose 9 has a score-table line but no coordinates. The truthful result is 8/8 emitted coordinate poses profiled for each run.
