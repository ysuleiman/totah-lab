# Analogue recognition implementation audit — 2026-09-11

Scope: current manifest adapter, changed recognition materializer/executor, associated tests, and the reported analogue artifacts. Experimental assay outcomes and historical Python analogue classifications were not opened. This is a review; production code and frozen result artifacts were not changed.

## Findings

### P1 — Run-level ligand provenance is ignored

`GenericRecognitionLigandManifestAdapter.java:50–56` reads the run compound label, receptor, and raw output, but never validates `runs[].ligand` or `runs[].ligand_sha256` against the prepared ligand selected from `ligands[]`. Serial/element/type equality does not independently establish molecular graph or stereochemical identity. A mismatched run can therefore receive the selected compound's canonical chemistry without detection.

Reproduction: copied the source JSON to `/tmp/recognition-audit-incomplete.json`, retained one des-para A run, replaced its run-level ligand path with `intentionally-nonexistent.pdbqt` and its ligand hash with 64 zeros. The adapter exited successfully and admitted 19 poses. Original source data were untouched. The real source manifest currently has no run/ligand mismatches, so this demonstrates a validation defect, not proven corruption of the 714 current poses.

Fix: validate the run-level ligand artifact and its identity against the chosen prepared ligand before materialization; add negative manifest tests.

### P1 — Selected compounds with no runs silently disappear

`GenericRecognitionLigandManifestAdapter.java:44,49–62` only checks selected IDs against ligand metadata and requires the overall source list to be nonempty. It never requires runs for each selected ligand. Accounting derives its expected count from only the runs that survived selection.

The same reproduction selected both des-para and des-ortho but retained only one des-para A run. Execution succeeded with 19 admitted poses, no B arm, and no des-ortho output. A missing compound or arm can silently produce an incomplete comparison.

Fix: require run coverage for every selected ligand. For this matched campaign, validate the declared enzyme/seed coverage explicitly against the campaign contract; do not hard-code a 20-pose requirement because valid runs here contain fewer poses.

### P2 — Generic receipts assert evidence for absent compounds

`Mettl7RecognitionBasinExecutor.java:71–72` unconditionally writes a matched 60-pose NETARSUDIL_A claim and adequate DCMB evidence. Neither compound is selected in the analogue execution. The materializer also unconditionally writes the DCMB adequacy claim (`Mettl7RecognitionBatchMaterializer.java:253`). These are present in actual saved analogue receipts, not merely hypothetical malformed input behavior. They also appear in the successful 19-pose probe.

Fix: derive claims from supplied observations, or omit them for arms not evaluated; never certify adequacy with hard-coded text.

### P2 — The integration test can pass with every pose rejected

`GenericRecognitionLigandManifestAdapterTest.java:19–23` requires 714 outcomes, unique IDs, 36 manifest rows, and existence of BASINS.csv. Rejected outcomes still count, and the executor creates a header-only BASINS.csv even with no admitted observations. Consequently the test does not protect the central 714-admitted claim or the A/B routing fix.

Fix: assert 714 admitted adequate outcomes and evidence observations, all 12 expected arms with their exact counts, correct A/B observation identity/directional surface provenance, and nonempty primary basin evidence. Add negative tests for provenance and missing run coverage.

## Interpretation audit

The CSV deliberately separates any contact occurrence in a recurrent basin from persistent edges across basin members. The handoff reports the former as contact recurrence. For example, AR-13503 B R206 PI_CATION is present in 3/17 recurrent basins but persistent in only 1/17. Des-para B K196 PI_CATION is 2/13 by presence and 1/13 by persistence. Neither metric should be silently substituted for the other; both should accompany claims about preserved architecture.

The executor explicitly supplies inadequate alternative-basin contribution evidence to its role classifier and leaves formal roles unresolved. The selectivity/inhibition labels and confidence ratings in the handoff are narrative interpretations; this execution does not emit or validate those labels. Distinct structural summaries demonstrate descriptive differentiation, but do not establish predictive discrimination of inhibition or selectivity. Keep these predictions labeled as hypotheses until a fixed interpretation rule and the blind assay comparison are evaluated. No assay comparison was performed here.

The current diff also includes an InteractionStableFeatureAssigner version change to V2 and changed frozen-recognition tests. These may belong to preceding work; the provided base commit alone does not establish a purely adapter-only delta or unchanged tests.

## Artifact checks

- 36 run rows, 714 unique outcomes; all outcomes are ADMITTED_ADEQUATE.
- Every SDF, prepared ligand, receptor, and raw-pose hash referenced by the generated manifest matches the current file.
- All twelve primary basin/recurrent counts match the handoff.
- Six of the seven quoted artifact hashes match exactly. The manifest hash in the handoff is truncated by one final character: the actual SHA-256 is `b9639b6a13a0ec12c127a7e74bda4284202184bebc963e9181babcad6366ab1a`.
- The existing real source manifest's run-level ligand paths and hashes agree with the corresponding ligand entries; the adapter currently fails to enforce this agreement.

## Focused test execution

Java 21, offline Maven reactor build: BUILD SUCCESS. All 27 selected tests passed with zero failures, errors, or skips:

- InteractionStableFeatureAssignerTest: 19
- GenericRecognitionLigandManifestAdapterTest: 1
- Mettl7RecognitionBatchMaterializerTest: 4
- Mettl7RecognitionBasinExecutorTest: 3, including byte-identical scientific artifact replay.

Command: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn -o -f software/modules/pom.xml -pl mettl7 -am -Dtest=GenericRecognitionLigandManifestAdapterTest,Mettl7RecognitionBasinExecutorTest,Mettl7RecognitionBatchMaterializerTest,InteractionStableFeatureAssignerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Log: `/tmp/recognition-audit-maven.log`. Malformed-manifest probe log: `/tmp/recognition-audit-probe.log`; exit code 0. A contact comparison extract accompanies this report as `CONTACT_PRESENCE_VS_PERSISTENCE.csv`.

Passing existing tests does not close the negative-input validation and assertion gaps identified above.
