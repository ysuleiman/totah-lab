# P04 supplied chemical-group formal-charge attribution

`ATHENA.PERCEPTION.CHARGED_GROUP/1.0.0` attributes supplied formal charge to an
already qualified chemical identity. It does not perform new perception. The
[approved definition and payload](../../../../software/qualification/charge-group-attribution-contract-20261005/DESIGN.txt)
were preserved at `c844e0c78` and explicitly approved by the user, including the checked
sum invariant. The [manifest](../../../../software/modules/athena/src/main/resources/totah/lab/athena/system/rules/charge-groups-v1/ATHENA.PERCEPTION.CHARGED_GROUP.rule.json)
pins that definition and the exact six source manifests. Existing source queries,
H/charge requirements, role alternatives and limitations remain authoritative.

## Supporting material and reasoning

The [formal-charge dossier](../../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.FORMAL_CHARGE.json)
separates atom formal charge, complete group membership/total and member geometry.
The [ionizable-motif dossier](../../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.IONIZABLE_MOTIF.json)
separates structural motifs from pKa and physiological charge predictions. Their pinned
source maps retain prior implementation audits, primary-literature locators and source
limitations; no additional claim or research acquisition is made here.
The [atomic feature checkpoint](../../../../software/qualification/charge-nonpolar-perception-20261005/CHECKPOINT.txt)
establishes why nitro's internal positive/negative atoms are not a net ion group.
The [validation correction](../../../../software/qualification/disconnected-validation-repair-20261005/CHECKPOINT.txt)
preserves the independent scope of component neutrality. The
[Mobley analysis](MOBLEY_2018.md) supports reuse of question-specific direct perception,
not a universal atom type or force-field assignment.

The operation is exact integer attribution, not an empirical classifier. There is no
fitted threshold, calibration distribution, external dataset or energetic hypothesis.
The source boundary is essential: C/O/O for carboxylate, N for each of the four reviewed
carbon-bound ammonium classes, with attachments/H/context preserved in original reports.
The legacy `ChargedGroupPerception` may use the two oxygens as an interaction
representative. P04 neither imports nor replaces that representation.

Decisions:

- **ADOPT:** existing CARBOXYLATE and CARBOXYLATE.FORMYL `/1` and the four reviewed
  carbon-bound AMMONIUM `/2` identities; exact source correspondence and state.
- **ADOPT:** compute `totalFormalCharge` from authoritative source atom charges with
  checked addition and each distinct member counted once. Never trust an input total.
- **MODIFY relative to legacy interaction attribution:** retain chemical membership
  independently from a coordinate representative. No representative is defined here.
- **REJECT:** atomic charge as automatic group charge, component neutrality as a veto,
  nitro internal charge separation as either supported ion group, partial-charge
  substitution, normalization and merging separately supplied resonance depictions.
- **UNSUPPORTED:** arbitrary charged-group chemistry, larger charge domains,
  delocalization/pKa/ionization propensity and interaction/energy/affinity interpretation.

## Implementation and negative coverage

Package-private `ChargeGroupRules` uses existing `RuleAnalyzers`, `RuleRegistry`,
`FunctionalGroupRules` replay verification and `EvidenceEnvelope` transport. There is
no new molecular representation, query, matcher or modification of prior group payloads.
The collector verifies hashes, method, state, component, definition and B00 receipts;
the evaluator reconstructs the complete attribution and rejects a tampered total,
member charge, mapping or membership. The raw input remains immutable.

Every member carries its chemical ID, stable structural reference and source integer
charge. Whole source mappings are retained; no per-atom Cartesian mapping product is
formed. Missing/ambiguous upstream correspondence remains non-positive and non-negative.
All six exact source searches and complete state/mapping coverage are required for
`ABSENT_FALSE` within the explicit component. Partial verified positives remain visible.

**Formate limitation:** the general CARBOXYLATE manifest explicitly treats formyl
carboxylate as outside its domain. The specialized FORMYL report can establish the
three-member group and total −1, but the composite six-predicate coverage stays
incomplete. P04 preserves this conservative source limitation rather than treating
unsupported as a successful negative or changing the historical general rule.

## Qualification and history

[Qualification checkpoint](../../../../software/qualification/charge-groups-20261005/CHECKPOINT.txt)
contains source hashes, test logs and independent replay. Synthetic witnesses include
swapped carboxylate drawings, all four ammonium states, a zwitterion, neutral and nitro
controls, explicit H, doubled occurrences, atom-order permutations, incomplete state,
source/total tampering and checked overflow. Component neutrality is evaluated under
both policies separately; a neutrality failure never suppresses a valid cation group.
No existing interaction consumer is migrated. The five historical scientific families
retain exact replay. The initial test's overly strong formate completeness assumption
is preserved in characterization; it did not require changing production semantics.

This is bounded implementation qualification, not automatic Research Gate eligibility.
Production manifest status remains `NOT_EVALUATED`; the wider P04 inventory remains
partial. No unknown evidence or original source is destroyed by failed interpretation.
