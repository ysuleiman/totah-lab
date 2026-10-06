# Explicit-H directional candidates and adopted source-group identities

The project user approved `I02.EXPLICIT_H_DIRECTIONAL_CANDIDATE/1` and the five
previously tested group definitions on 2026-10-06. The attributed approval and exact
source hashes are preserved in the [qualification package](../../../../software/qualification/i02-approved-candidate-20261006/APPROVAL.json).
This is scientific-definition adoption plus bounded implementation qualification;
it does not invent an expiring current-policy review or a production receipt.

## I02: scientific supporting material

Use the existing [source analysis](../../../../software/qualification/i02-directional-candidate-review-20261006/SOURCE_NOTES.txt),
[source pins and acquisition limitations](../../../../software/qualification/i02-directional-candidate-review-20261006/),
and [class-pair proposal](../../../../software/qualification/i02-directional-candidate-review-20261006/CLASS_PAIRS.json).
These are preserved research, not replaced by this page. The implementation manifest
links their digests. No new literature search or empirical calibration was performed.

ProLIF's pinned implementation provides precedent for an explicit-H operational
3.5 Å / 130° screen. The associated paper does not establish universal class-specific
physical thresholds. PLIP and HBPLUS use different geometric criteria and perception;
those alternatives and source-access/license limitations remain in the source analysis.
ADOPT the explicitly reviewed candidate definition; REJECT extrapolation to physical
favorability or importing other packages' thresholds/perception without review.

The opt-in `athena.hbond-candidate/1` composes verified existing group/role reports and
`athena.geometry/1`. It never infers donors from element alone or constructs missing H.
The two request residue scopes must select whole, distinct source components. Each
source report is bound to the exact state, component, definition, method and preserved
B00 results and is verified with the existing group evaluator. All role correspondence
alternatives remain in the output. All explicitly bonded donor H atoms are enumerated.

Each D/H/A tuple retains D–A, H–A, D–H–A and existing H–A–X/D–A–X measurements for
all heavy acceptor antecedents, along with the measurement plan and source references.
Only D–A ≤3.5 Å and D–H–A ≥130° classify a candidate. There is no H–A or acceptor-angle
cutoff, best-H selection, favorable-energy inference, persistence or causality claim.
The implementation uses the measured floating-point quantities without an added tolerance.

The manifest separately describes 100 supported class pairs and ten unsupported
pyridinium pairs. The latter always report `UNKNOWN_INCONCLUSIVE`, never absence.
Missing source state, explicit H, frame, mapping or exhausted search coverage cannot
produce absence. `completeSupportedScope` explicitly excludes the unsupported ten;
a report-level negative is restricted to the supported class-pair universe, never a
claim about all possible hydrogen bonding. Positive tuples do not complete otherwise
incomplete negative coverage. Unevaluable quantities remain unavailable/inconclusive.

Historical `ATHENA.HBOND.DIRECTIONAL` is unchanged; this is an independent opt-in rule.
Production ownership: Athena `HbondCandidateRules`, `HbondCandidateSources`,
`HbondCandidateEnumeration`, `HbondCandidateGeometry`; Daedalus retains the existing
execution pipeline. No public API, schema, matcher or molecular representation was added.

## Five source identities

The production `groups-adopted-v1` resources are exact byte copies of these approved
candidate manifests, preserving their tested definition and coverage semantics:

- [Terminal alkyne dossier and fixtures](../../../../software/qualification/f18-terminal-alkyne-contract-20261006/): exact terminal authoritative H and C≡C membership; acetylene correspondence alternatives remain one occurrence.
- [Acyl/sulfonyl chloride supporting material](../../../../software/qualification/f05-acyl-sulfonyl-chloride-review-20261006/DESIGN.txt): neutral carbon-bound R–C(=O)–Cl and R–S(=O)₂–Cl. Charged/resonance and other attachment forms remain outside the reviewed domain.
- [Isocyanate/isothiocyanate supporting material](../../../../software/qualification/f14-isocyanate-review-20261006/DESIGN.txt): neutral carbon-bound R–N=C=O/S; cyanate/thiocyanate connectivity is distinct. Excluded charge depictions remain unsupported rather than normalized.

Pinned RDKit functional-group source locations supply motif precedent; the respective
SOURCE_PINS records preserve exact versions/lines/hashes and decisions. Execution remains
B00/OCL plus B01 /2. Identities establish no electrophilicity, reaction, donor/acceptor
role, assay artifact or favorable interaction. Source graph and H/charge provenance
are authoritative; incomplete coverage is not a negative. Original candidate checkpoints
remain immutable and correctly describe their pre-adoption status.

## Qualification and use

Load the opt-in manifest directory through existing `RuleRegistry.load`. The generic
collector/evaluator APIs and `EvidenceEnvelope` transport are unchanged. Current-policy
execution still requires `/3` binding, authorized review validity, research eligibility,
implementation qualification and verified caller/time/receipt. Historical manifests
remain `NOT_EVALUATED` where no current-policy receipt exists; a test-only qualification
or synthetic authority is never a production grant.

See the [checkpoint](../../../../software/qualification/i02-approved-candidate-20261006/CHECKPOINT.txt)
for exact tests, independent-JVM replay, preservation and consumer comparison. The
synthetic 110-pair corpus tests this operational definition, not population validity.

### Receipt transport integration finding

I02's real partner-residue request exposed an existing receipt-verification mismatch:
Gaia `ResidueId` permits null `insertionCode` to mean no insertion code, while the
research metadata decoder rejected every null creator property. The new internal
`QualificationRequestCodec` handles that existing source-state contract at receipt
read-back only. Research metadata stays null-free. Exact residue fields, chain, integer
number and nullable one-character insertion code are checked; unknown/missing fields
and null required request fields remain errors. No request bytes or public contracts
change. Empty-selection historical receipts retain their existing replay. Synthetic
current-policy tests include positive execution, missing/expired authority and inherited
versus explicitly selected role evidence.
