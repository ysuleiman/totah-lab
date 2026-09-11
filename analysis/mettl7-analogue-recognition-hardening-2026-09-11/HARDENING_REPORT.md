# Hardened analogue recognition verification

`SCIENTIFIC_RESULT_CHANGED = NO`

Experimental analogue assay outcomes remain unopened. Predictions remain interpretive hypotheses. The work reviewed and hardened the analogue manifest/materialization/basin execution path and its focused dependencies; it was not a repository-wide code audit.

## Fixes

1. **Run provenance:** require an existing run ligand, verify its actual SHA-256, and require both its normalized absolute path and hash to equal the prepared ligand assigned to the selected compound. Canonical SDF and prepared-ligand hashes remain checked. Wrong-analogue and enantiomer artifacts fail before materialization; PDBQT serial/type equality is not treated as proof of chemical identity. Stereo validation here is provenance-based: it rejects an enantiomer's prepared artifact, without claiming that PDBQT independently encodes a complete stereo graph.
2. **Coverage:** every selected compound must cover both declared METTL7 arms and every declared seed. The existing campaign ID convention, duplicate IDs, duplicate metadata/seeds, missing arms/seeds and undeclared seeds/IDs are checked. Invalid enzymes cannot silently default to B. Actual pose counts are read and accounted independently; no 20-pose assumption was added. All preflight checks finish before overwriting an existing output manifest.
3. **Receipts:** removed unconditional NETARSUDIL/DCMB adequacy statements from basin, batch, and diagnostic receipts. Generic receipts now report the actual per-arm outcomes and observation qualities. Empty and analogue-only executions are tested against false certification.
4. **Integration:** require exactly 714 ADMITTED_ADEQUATE outcomes and observations, zero other outcomes, exact counts for all 12 arms, correct A/B graph identity, directional SurfDiff hash, expected ligand identity/SDF hash, unique pose IDs, 36 manifest rows, and primary basin rows for every arm. Header-only output cannot pass.

No recognition model, chemistry definition, docking pose, basin threshold, or public API signature was changed by this hardening. No docking, MD/OpenMM, affinity calculation, chemistry generation, or assay comparison was performed. The commit also preserves the previously uncommitted adapter and participant-role repair on which the supplied OLD execution already depended; the feature assigner V2 repair predates this hardening.

## Tests

Java 21 offline reactor build: **54 focused tests passed**, zero failures/errors/skips. These include **17 negative manifest tests**, the full 714-pose integration test, empty-receipt checks, and deterministic replay within the basin executor test. See [TEST_RESULTS.csv](TEST_RESULTS.csv).

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f software/modules/pom.xml -pl mettl7 -am -Dtest=GenericRecognitionManifestValidationTest,GenericRecognitionLigandManifestAdapterTest,Mettl7RecognitionBasinExecutorTest,Mettl7RecognitionBatchMaterializerTest,Mettl7RecognitionStateAdapterTest,Mettl7RecognitionGraphRegressionTest,InteractionStableFeatureAssignerTest -Dsurefire.failIfNoSpecifiedTests=false -Dmettl7.analogue.output=/Users/yazan/totah-lab/analysis/mettl7-analogue-recognition-v1-hardened test
python3 analysis/mettl7-analogue-recognition-hardening-2026-09-11/compare_hardened.py
```

## Exact accounting and basin comparison

All 714 poses remain adequate; degraded, pending, rejected, and unmappable counts are all zero. Both executions have 36 runs and 12 arms.

| Arm | OLD adequate → HARDENED | OLD basins/recurrent → HARDENED |
|---|---:|---:|
| AR_13503_DEESTERIFIED_A | 60 → 60 | 33/19 → 33/19 |
| AR_13503_DEESTERIFIED_B | 59 → 59 | 34/17 → 34/17 |
| BRANCH_POINT_ENANTIOMER_A | 60 → 60 | 49/7 → 49/7 |
| BRANCH_POINT_ENANTIOMER_B | 60 → 60 | 39/14 → 39/14 |
| DES_ORTHO_METHYL_A | 59 → 59 | 39/13 → 39/13 |
| DES_ORTHO_METHYL_B | 60 → 60 | 43/13 → 43/13 |
| DES_PARA_METHYL_A | 59 → 59 | 42/12 → 42/12 |
| DES_PARA_METHYL_B | 60 → 60 | 40/13 → 40/13 |
| N_ACETYL_AMINE_A | 59 → 59 | 43/12 → 43/12 |
| N_ACETYL_AMINE_B | 60 → 60 | 40/15 → 40/15 |
| QUINOLINE_REGIOISOMER_A | 58 → 58 | 40/11 → 40/11 |
| QUINOLINE_REGIOISOMER_B | 60 → 60 | 39/13 → 39/13 |

## Contact presence and persistence

- **RECURRENT_BASIN_PRESENCE:** contact occurs in at least one member of a recurrent basin.
- **PERSISTENT_WITHIN_BASIN:** the same frozen edge key occurs in every member, following the existing basin definition.

All **171 contact rows**, including every presence count, persistence count, denominator, feature set, distance statistic, evidence quality and role, match OLD exactly. [CONTACT_COMPARISON.csv](CONTACT_COMPARISON.csv) contains every OLD → HARDENED value. The regenerated [CONTACT_EVIDENCE.csv](../mettl7-analogue-recognition-v1-hardened/CONTACT_EVIDENCE.csv) uses the explicit terminology.

For B R206 π-cation, the values below are unchanged between OLD and HARDENED; the correction is in reporting their meaning:

| Analogue arm | RECURRENT_BASIN_PRESENCE | PERSISTENT_WITHIN_BASIN |
|---|---:|---:|
| AR_13503_DEESTERIFIED_B | 3/17 | 1/17 |
| BRANCH_POINT_ENANTIOMER_B | 7/14 | 7/14 |
| DES_ORTHO_METHYL_B | 4/13 | 3/13 |
| DES_PARA_METHYL_B | 6/13 | 5/13 |
| N_ACETYL_AMINE_B | 6/15 | 6/15 |
| QUINOLINE_REGIOISOMER_B | 4/13 | 4/13 |

AR-13503 is therefore **present in 3/17**, **persistent in 1/17**. Des-para B K196 π-cation is present in 2/13 and persistent in 1/13. All analogous distinctions are retained in the full contact table.

## Prospective predictions

The original state was `PROVISIONAL_PENDING_EXECUTION_HARDENING`. Hardened execution is now verified, while inhibition/selectivity predictions remain hypotheses, not assay-validated conclusions. No label or confidence rating was reinterpreted after seeing outcomes.

| Analogue | OLD prediction | HARDENED prediction | Confidence OLD → HARDENED |
|---|---|---|---|
| AR-13503 | A_INHIBITION_LIKELY_INCREASED | A_INHIBITION_LIKELY_INCREASED | MEDIUM → MEDIUM |
| Des-ortho-methyl | B_SELECTIVITY_WEAKENED | B_SELECTIVITY_WEAKENED | MEDIUM → MEDIUM |
| Des-para-methyl | B_SELECTIVITY_RETAINED | B_SELECTIVITY_RETAINED | MEDIUM → MEDIUM |
| N-acetyl-aminomethyl | B_SELECTIVITY_WEAKENED | B_SELECTIVITY_WEAKENED | LOW → LOW |
| Branch-point enantiomer | B_SELECTIVITY_RETAINED | B_SELECTIVITY_RETAINED | MEDIUM → MEDIUM |
| Quinoline N-position | B_SELECTIVITY_WEAKENED | B_SELECTIVITY_WEAKENED | MEDIUM → MEDIUM |

Quinoline interpretation: **YES, partial B recognition disruption/reorganization → unchanged**. The model discrimination verdict remains **YES → YES**, meaning descriptive structural differentiation. Predictive discrimination of inhibition/selectivity is not established by this execution. [PREDICTION_EVIDENCE.csv](../mettl7-analogue-recognition-v1-hardened/PREDICTION_EVIDENCE.csv) binds each unchanged hypothesis to both contact metrics.

## Exact hashes and diagnostic byte differences

Correct manifest SHA-256:

`b9639b6a13a0ec12c127a7e74bda4284202184bebc963e9181babcad6366ab1a`

This is identical in OLD and HARDENED. The one-character truncation in the original session handoff is corrected here. All 144 per-run input hash references and all embedded output hashes in both executions were verified against files. [ARTIFACT_HASH_COMPARISON.csv](ARTIFACT_HASH_COMPARISON.csv) records exact OLD and HARDENED hashes. [PREDICTION_RECEIPT.json](../mettl7-analogue-recognition-v1-hardened/PREDICTION_RECEIPT.json) binds the hypotheses to exact hardened artifacts; SHA256SUMS also covers that receipt.

Manifest, materialization outcomes, BASINS.csv, PAIR_ADMISSIONS.csv and recurrent-edge evidence are byte-identical. Receipts and diagnostic summary hashes change because the false certification text was removed and actual execution/terminology fields were added.

The auxiliary PAIRWISE_DIAGNOSTICS.csv is **not byte-identical**: 15 of 20,887 RMSD entries differ by exactly 0.000000000001 Å (the last printed decimal). All other cells, including every diagnostic policy decision, are identical. [EXACT_DIAGNOSTIC_DIFFERENCES.csv](EXACT_DIAGNOSTIC_DIFFERENCES.csv) preserves every difference. The RMSD implementation is unchanged and sums over a Map.copyOf key iteration order; these final-digit variations are consistent with floating-point summation order between JVM processes. No model threshold was altered to obtain an unchanged verdict. The NO verdict concerns the requested scientific outcomes, basins, contacts, predictions and interpretations, all of which remain unchanged; it does not claim all diagnostic bytes match.

## Commit scope and unblinding

[FILES_CHANGED.csv](FILES_CHANGED.csv) lists every file included in the hardening commit: the required existing implementation baseline, fixes, tests, small provenance fixtures, exact selected inputs needed for replay, OLD execution snapshots, HARDENED outputs, and comparison/receipt artifacts. Unrelated working-tree changes are excluded. OLD snapshots are historical evidence only and are superseded by hardened receipts.

The prediction receipt is bound to the commit containing it. Push status and the verified commit SHA are returned after committing; `HEAD == origin/main` is checked after a normal push. Assay outcomes remain unopened in this task.
