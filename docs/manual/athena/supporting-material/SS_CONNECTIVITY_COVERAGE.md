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

## Corrected, separately versioned implementation

The user approved the bounded correction on 2026-10-05. The [implementation checkpoint](../../../../software/qualification/b06-source-connectivity-20261005/CHECKPOINT.txt)
and [manifest](../../../../software/modules/athena/src/main/resources/totah/lab/athena/system/rules/ss-connectivity-v1/ATHENA.SULF.SS_CONNECTIVITY.rule.json)
are authoritative. Historical `SULF.SS.001` is unchanged. The new implementation is
`athena.ss-connectivity/1`, profile `ATHENA_SOURCE_SS_CONNECTIVITY_V1`, rule
`ATHENA.SULF.SS_CONNECTIVITY`.

The proposition is whether the supplied authoritative graph lists a covalent connection
between an explicit pair of distinct sulfur atoms. Element identities are checked;
charge, H state, oxidation, Cys classification, geometry and function are not inferred.
Existing `athena-group-source-coverage/1` supplies component completeness. Existing
`SystemStateView` binds source graphs and correspondence. Generic Findings and immutable
EvidenceInterpretations carry assertions and assessment; no new payload schema/API exists.

A listed structure edge with EXPLICIT/PARTIAL source provenance, or a listed mapped
component-graph edge, supplies positive evidence without exhaustive negative coverage.
Unknown structure bond order remains UNKNOWN. MolecularGraph bond IDs are retained;
Gaia bonds have no separate source ID, so their exact endpoint/order tuple and state
binding are retained without inventing an ID. Inferred/otherwise unauthoritative listed
edges remain visible but do not independently establish a positive.

Absence requires an applicable complete same-component source graph, valid topology,
state binding, complete unambiguous correspondence, budget and no conflicting listed
assertion. Multiple supplied completeness records must all establish completeness for
that component; their order is irrelevant. Missing/partial/unsupported records cannot
be replaced by EXPLICIT import provenance. Cross-component missing edges remain
inconclusive. Complete component chemistry is not an intercomponent completeness proof.

All supplied coverage records are hash-checked and preserved with method/provenance.
State/component/atom mapping tampering or malformed input fails explicitly. Atomic
annotation shape is validated under the existing coverage schema, but this topology
predicate does not reinterpret its charge/H/aromaticity evidence. Negative coverage
requires no pKa, H placement, coordinate comparison or valence normalization.

When a listed edge conflicts with a complete no-edge assertion, the positive finding
survives separately and the combined finding is inconclusive. The source assertions
remain visible together, with no overwrite or source preference. Empty geometry or
bond-order uncertainty is never repaired. No current-policy Research Gate receipt is
invented: the shipped manifest remains NOT_EVALUATED, while its bounded raw assessment
is implementation-qualified and independently inspectable.

## Qualification and limitations

The exact old negative/new inconclusive witness is permanently tested. Other fixtures
cover independent complete absence, mapped/partial positives, unrelated explicit edges,
unknown bond order, cross-component pairs, contradictions, malformed topology,
ambiguous/incomplete mapping, state/hash tampering, deterministic source order,
immutable source graphs and journal read-back. Legacy consumers/replay remain unchanged.
Execution uses the existing generic SystemQualificationPipeline with explicitly supplied
coverage envelopes; inherited evidence is never silently selected as current coverage.
This does not qualify biological disulfides, vicinal conformations or intercomponent
negative claims. Those remain independent capabilities.
