# Complete-membership aromatic-carbocycle nonpolar predicate

`ATHENA.PERCEPTION.AROMATIC_CARBOCYCLE.ALL_MEMBERS_NONPOLAR/1.0.0` evaluates
`ALL_MEMBERS_SATISFY_REVIEWED_NONPOLAR_ATOM_PREDICATE` on an existing verified
aromatic RING5/RING6 occurrence. The [reviewed definition](../../../../software/qualification/p05-group-definition-review-20261005/DESIGN.txt)
was explicitly approved by the user. The [manifest](../../../../software/modules/athena/src/main/resources/totah/lab/athena/system/rules/all-members-nonpolar-v1/ATHENA.PERCEPTION.AROMATIC_CARBOCYCLE.ALL_MEMBERS_NONPOLAR.rule.json)
pins that definition and four exact source manifests: two cycle identities and the
two applicable aromatic C/H-only atomic predicates. Saturated atom features are not
substituted for aromatic predicates.

## Scientific supporting material

The existing [NONPOLAR dossier](../../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.NONPOLAR.json)
separates atomic neighborhood features from named groups and rejects generic
all-neutral-carbon equivalence. Its pinned source map retains reference-system
behavior and literature locators; no additional external claims are made here.
The [atomic feature qualification](../../../../software/qualification/charge-nonpolar-perception-20261005/CHECKPOINT.txt)
establishes exact charge/H/neighborhood predicates, including nitrobenzene's five
eligible aromatic C-H atoms and ineligible ipso carbon. The
[aromatic attribution support](AROMATIC_SYSTEMS.md) explains overlapping cycles and
why no unique ring basis or larger merged chemical group is implied.
The [Mobley record](MOBLEY_2018.md) supports separate, overlapping, question-specific
perception; it supplies neither a hydrophobic-energy model nor this group predicate.

**ADOPT:** the complete existing chemical membership, exact state/correspondence and
pinned source identities. **MODIFY:** external/lumped hydrophobe ideas into an explicit
all-member proposition whose domain is stated. **REJECT:** maximal eligible subsets as
new fragments, merging fused cycles, universal hydrophobic-group labels, inferred
centroids, burial, favorable contact, affinity and free energy. **UNSUPPORTED:** other
chemical group domains and arbitrary nonpolar region segmentation.

This is an exact logical composition, not a calibrated empirical classifier. No
threshold, empirical dataset or distribution is introduced. The reviewed choice
between partial eligible regions and complete group membership is preserved in the
linked design. Existing atom identities survive either outcome.

## Transport, execution and coverage

Package-private `AllMembersNonpolarRules` uses the existing group replay evaluator.
It returns one existing `SystemGraphAnalyzer.Finding` per source cycle; the pipeline
persists an ordinary `EvidenceInterpretation`. No new payload/schema or public API is
needed. The interpretation inputs retain original source envelope references/hashes;
its subjects retain the exact source occurrence and complete chemical atom membership.
The selected source report hash and definition digest are recorded in measurements.
All role and structural correspondence alternatives remain in those immutable inputs.
No nested replacement molecular/group representation is serialized.

All exact members with supported applicable atom identities establish a positive.
A missing positive identity establishes a failing member only when all requested
source searches, membership, state and correspondence coverage are complete. Otherwise
the outcome remains inconclusive. Pyridine is outside the aromatic-carbocycle domain
(`UNSUPPORTED`), not a universal nonpolar-group negative. No verified cycle object
produces an inconclusive scope finding, not a false property of an invented object.
Unknown/ambiguous correspondence inherits the upstream non-positive limitation.

Nitrobenzene's cycle remains six atoms and its five positive atomic identities stay
available. Its complete-member predicate is false under complete source coverage.
Naphthalene yields two separate source-cycle assessments, not a ten-atom hydrophobe.
No centroid or coordinate-based chemical inference is introduced.

[Qualification checkpoint](../../../../software/qualification/all-members-nonpolar-20261005/CHECKPOINT.txt)
records synthetic cases, exact cycle membership, explicit H, source permutations,
unknown/ambiguous state, tamper checks, actual catalog persistence, independent-JVM
replay and unchanged historical consumers. Production manifest remains NOT_EVALUATED;
bounded implementation qualification does not supply Research Gate eligibility.
