# Athena Scientific Rule Reference

This living reference is the user/developer entry point. [The capability catalog](CATALOG.md)
is rendered from the accepted program ledger, not a second roadmap. [Rule records](RULES.md)
link exact authoritative manifests; definitions must be changed there, never edited in
generated documentation. Historical qualification records remain immutable.

## Architecture

Authoritative molecular graph and coordinate/state identity → direct chemical perception
(B00/OCL) → independent group/role identities (B01 /1, /2) → continuous measurements
→ versioned scientific classifiers → immutable evidence → qualification/certification.

Graph connectivity, charge, supplied H state and geometry retain separate provenance.
Athena's foundation does not use universal atom types, destructive last-match-wins
identity classification, or contact cutoffs as substitutes for raw geometry. Overlapping
identities and contradictory observations coexist. Unsupported or failed evaluation
never deletes evidence. Spatial proximity alone establishes no favorable interaction.
Historical native detectors have their own bounded chemistry and are not silently widened
by newer roles. See [Mobley supporting analysis](supporting-material/MOBLEY_2018.md).

Implementation qualification, research eligibility and current-policy qualification are
separate. A historical manifest marked QUALIFIED does not establish current-policy
eligibility. Consult the ledger and exact checkpoint; do not infer activation from counts.

The [I02 H-bond expansion proposal](../../../software/qualification/i02-directional-candidate-review-20261006/REVIEW.txt)
and its [scientific supporting analysis](../../../software/qualification/i02-directional-candidate-review-20261006/SOURCE_NOTES.txt)
are pending scientific review. They propose an explicit-H geometric candidate predicate,
not an activated interaction rule. The historical alcohol/oxygen rule remains unchanged;
raw-measurement tests do not qualify the proposed expansion.

The [terminal-alkyne candidate](../../../software/qualification/f18-terminal-alkyne-contract-20261006/CHECKPOINT.txt)
has engineering tests through the existing B00/B01 evaluator. Its exact terminal-role
definition awaits scientific adoption; it is not a production-qualified group.

## Supporting material and completeness

Every rule needs both scientific supporting material and execution qualification.
[The blueprint source map](../../../software/qualification/rule-qualification-blueprint-20261005/FIVE_PILLAR_SOURCES.json)
and [dossier index](../../../software/qualification/rule-qualification-blueprint-20261005/DOSSIER_INDEX.json)
retain existing pinned research. Follow the dossier's source locators and digests; citation
strings alone are insufficient. Missing full text, empirical data, exact locators or
review decisions remain explicit gaps, not reconstructed facts. Mobley is a shared
architectural record, not a substitute for each chemical definition's supporting evidence.

A complete provenance package records bibliography/version/DOI/commit and exact locators;
what the primary source establishes; external behavior; datasets and exclusions;
methodology and hashed calibration outputs; alternatives and ADOPT/MODIFY/REJECT/
UNSUPPORTED rationale; disagreement, assumptions, failures, uncertainty, and motivated
fixtures. Link existing immutable material instead of copying publications. Retain lawful
metadata/digests and retrieval provenance when redistribution is inappropriate.

## Adding or closing a rule

1. Locate its accepted ledger capability and existing dossier; reuse authoritative graph,
   matcher, measurements and generic evaluator. Do not add an alternative molecular model.
2. Review the exact domain, state prerequisites and scientific support. Research eligibility
   makes a rule testable; it does not qualify implementation. Missing support is a local gap.
3. Version declarative definitions and negative-coverage requirements. ABSENT_FALSE requires
   exhaustive evaluable chemistry/search coverage; UNKNOWN, UNSUPPORTED, FAILED and
   NOT_EVALUATED are not negatives. Keep measurements independent of thresholds.
4. Exercise positive, near-miss, unknown-state, invalid, symmetry/overlap, preservation,
   evidence round-trip, deterministic replay and relevant consumer regressions.
5. Stop for review before public API/schema changes or migration of qualified consumers.
6. Preserve old definitions/evidence/checkpoints. Record exact implementation, source and
   fixture hashes, qualifications and limitations. Never fabricate review validity dates.
7. Update the ledger, permanent catalog/reference and supporting-material links in the same
   focused commit as implementation/qualification. A capability is not closed without them.

Run `python3 docs/manual/athena/render_reference.py` to render from repository artifacts;
use `--check` to verify no stale generated pages. Documentation rendering is not chemistry
execution, research review, or a scientific qualification test.

Bounded aromatic cycle/fused-system attribution: [definition rationale, source limitations and qualification](supporting-material/AROMATIC_SYSTEMS.md).

Supplied group formal-charge attribution: [P04 scientific support and domain limits](supporting-material/CHARGE_GROUPS.md).

Complete-membership aromatic-carbocycle predicate: [P05 scientific support and coverage](supporting-material/ALL_MEMBERS_NONPOLAR.md).

Independent centroid and plane geometry: [P06 V2 definition, partial coverage and historical preservation](supporting-material/CENTROID_PLANE.md).

Mixed atom/group-centroid geometry: [V3 definition, mathematical support and partial truth](supporting-material/POINT_PAIR_GROUP.md).

Source S–S negative coverage: [historical limitation and correction review](supporting-material/SS_CONNECTIVITY_COVERAGE.md).

## Direct assessment execution

`RuleExecutionPipeline.evaluateCurrent` is the current-policy path for attributed Findings
without a measurement payload. `run` remains historical and `runCurrent` retains its two-stage
measurement path. All share existing evidence/qualification machinery. Admission preserves
all explicit artifacts; only verified, applicable selected evidence reaches the direct
evaluator. History is never an implicit input. See [supporting rationale and boundaries](supporting-material/DIRECT_ASSESSMENT_EXECUTION.md).

Bounded sulfur/backbone attribution: [support and limitations](supporting-material/CYSTEINE_BACKBONE.md).

Source representation boundary: [V03 evidence and pending contract](supporting-material/V03_REPRESENTATION.md).

V03 opt-in representation and operation matrix: [supporting material](supporting-material/EXPLICIT_RADICAL_STATE.md).

Review authority and per-rule validity: [approved semantics and prospective implementation](supporting-material/RESEARCH_AUTHORITY_SEPARATION.md).

Opt-in research separation `/2`: [authority, projection, implementation binding and historical compatibility](supporting-material/RESEARCH_SEPARATION_V2.md). This mechanism does not activate real policies or issue production scientific receipts.

Real-policy preparation and local caller trust boundary: [supporting record](supporting-material/REAL_POLICY_PACKAGE.md).
