# Source S–S connectivity: completeness limitation

[The preserved characterization and prospective correction](../../../../software/qualification/b06-connectivity-coverage-characterization-20261005/REVIEW_GATE.txt)
identify a boundary in historical `SULF.SS.001`. Fully mapped imported edges (`EXPLICIT`)
do not by themselves prove exhaustive connectivity. A missing edge is therefore not
an authoritative negative without additional coverage evidence.

The exact source behavior is pinned in that checkpoint: Gaia `Structure` and
`ConnectivityMetadata`, Hermes `PdbReader.importConnectivity/importPdbConect`, and
Athena `RuleAnalyzers.sulfur`/evaluator. The synthetic witness holds coordinates and
listed edges constant while changing the provenance enum. It records the historical
negative; it does not endorse it or reinterpret previous scientific artifacts.

The [existing sulfur dossier](../../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.SULF.DISULFIDE.json)
already requires complete authoritative connectivity for absence and separates source
bond state from distance. ADOPT that separation; REJECT equating successful import
mapping with exhaustive chemistry coverage. A versioned correction is proposed for
review. No new implementation, source repair, sulfur chemistry or scientific conclusion
is introduced here. Historical artifacts remain intact pending the consumer-impact gate.
