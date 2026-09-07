# Wider Athena/Daedalus/METTL7 defect audit

## Scope

The audit covered Gaia, Hermes, Hephaestus, Athena, Daedalus and METTL7 tests; Java pose-count paths; repository receipt/PDBQT/postprocessing accounting; Stage A completeness logic; protocol/hash checks; and stale METTL7 chemistry claims. It did not generate compounds or modify scientific docking inputs.

## Test results

Recorded suites after repair: Gaia 158/158, Hermes 163/163 with two skipped, Hephaestus 151/151 with one skipped, Athena 524/524, Daedalus 91/91, and METTL7 54/54. No assertion failures remain. The wide lifecycle did reveal that some Daedalus tests actually invoke local Vina and that BioJava attempted a network chemical-component lookup before falling back; these are recorded as isolation defects rather than hidden.

## New findings

Seven findings are listed in `WIDER_DEFECT_AUDIT.csv`. All seven are closed. The 66 historical runs have versioned coordinate-authoritative receipts and are all `METADATA_CORRECTED`; no coordinate evidence required reprocessing. Failed validation now invalidates stale success manifests. Daedalus external docking tests are opt-in integration tests. Hermes/BioJava default tests now establish an offline provider before BioJava can initialize a network-first provider.

The frozen 24-case repository acceptance specification is `REPOSITORY_WIDE_CLOSURE_TEST_CASES.md`. The 18-defect closure remains valid for the repaired 18-run trio, and the wider seven-finding closure is independently recorded here.

The final default reactor `mvn test` completed successfully: 1,899 tests, zero failures, zero errors and five declared skips. No Daedalus docking-integration test appeared in the default Surefire reports, and no BioJava download attempt appeared in the test reports.

`WIDER_AUDIT_NEW_FINDINGS = 7`

`WIDER_AUDIT_FIXED = 7`

`WIDER_AUDIT_OPEN = 0`

`REPOSITORY_WIDE_ALL_BUGS_CLOSED = true`

`NEW_CHEMICAL_CONCLUSIONS_AUTHORIZED = true`
