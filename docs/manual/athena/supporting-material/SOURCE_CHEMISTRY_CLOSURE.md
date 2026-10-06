# Source chemistry: N–N states, boron and carbon topology

The exact definitions are the versioned manifests in `groups-f08-v1`, `groups-f15-v1`, and `groups-f19-v1`; the generated rule reference renders them. All use the corrected B00 query substrate and existing `athena-group-definition/2`, `athena-group-source-coverage/1`, and `athena-group-identities/2` transport. No new matcher, molecular representation, payload schema or public API was introduced.

## Authority and sources

[Execution instruction](../../../../software/qualification/foundation-closure-execution-20261006/REQUEST.txt) authorizes adoption of scientifically supportable supplied-state definitions from pinned material. This is not a signed human review of a newly invented digest/expiry, and does not grant a current-policy production receipt.

Pinned source version/retrieval/hash metadata remains in [five-pillar sources](../../../../software/qualification/rule-qualification-blueprint-20261005/FIVE_PILLAR_SOURCES.json) and each manifest's `scientificSources`. The RDKit `Data/FunctionalGroups.txt` and `Data/Functional_Group_Hierarchy.txt` snapshots in `scientific-rule-knowledge-audit-20261004/reference` supply motif precedents. They establish software pattern practice, not reactivity, energetic favorability, toxicity, physiology or biological function. No new literature or empirical distribution was acquired. These exact source-graph predicates require synthetic connectivity/state witnesses rather than empirical binding calibration.

**ADOPT:** declarative local graph matching, explicit source bond/charge/H state, separate questions, stable atom correspondence.

**MODIFY:** ambiguous source labels and broad patterns into explicitly named source states; all valid cyclic/shared-attachment contexts remain permitted. Overlapping identities and symmetry correspondence alternatives coexist.

**REJECT:** automatic chemical transformations in RDKit's hierarchy; pattern labels as scientific authority; resonance/tautomer normalization; hydrophobicity or reactivity inferred from the motif; maximal-fragment extraction without a reviewed boundary.

## F08 — N–N source states

`FunctionalGroups.txt` lines 28–34 are especially instructive: the source labels “hydrazines” alongside an N=N pattern. Athena does not inherit that label/bond-order ambiguity. Six neutral N–N SINGLE states distinguish unordered endpoint H counts 0/1/2; three neutral N=N DOUBLE states distinguish endpoint H counts 0/1. Required heavy degree and exclusion of extra multiple bonds preserve the exact state. Aromatic endpoints are not these nonaromatic identities. Three-membered cyclic hydrazine and diazene fixtures, including a shared carbon attachment, pass; larger ring topology is not excluded by a query ring-size condition.

Diazo C=N(+)=N(−), diazo C(−)–N(+)≡N, carbon-bound diazonium C(0)–N(+)≡N, and two carbon-bound azide depictions are separately attributed. No depiction is converted into another. Azide precedent is also in `Functional_Group_Hierarchy.txt` lines 66–68. The initial diazonium query's unconstrained carbon matched the carbanionic diazo fixture; the retained draft and failed test motivated the explicit neutral-carbon context, preventing that false synonym.

Aromatic N–N systems, protonated hydrazines, metal-bound N–N complexes and other charged states retain their underlying source evidence but are not assigned these identities. They require their own exact charge/bond/coordination definitions, not a missing ring engine. No E/Z equivalence or donor/acceptor identity is inferred.

## F15 — boron source states

`Functional_Group_Hierarchy.txt` lines 39–41 show B–C/O/O motif precedent but do not distinguish OH from OR, nor establish charged-boron speciation. Athena distinguishes neutral B(OH)2, B(OH)(OR), B(OR)2 with B H0/heavy degree3 and exact O H/degree/carbon attachment. Ester attachments may share a carbon or form cycles. No source `!@` ring exclusion is copied merely for convenience.

A separate `BORON.FOUR_SINGLE_BONDS.FORMAL_MINUS_ONE` predicate reports a supplied B(−1) with four distinct heavy-atom SINGLE bonds and H0. It does not declare every such object a boronic acid/ester or establish solution boronate populations. A C–B(OH)3 anion is positive for this topology/charge descriptor and not for a neutral three-coordinate identity. Other ligand/charge/coordination states remain separately unclassified. Exact atom charge, source H, bond order and all mapped alternatives survive.

The first mixed OH/OR draft had symmetric query oxygen positions despite different source-H requirements. The final query explicitly distinguishes H0/H1 positions; source H coverage is still independently required. B00's inferred H is never substituted for source provenance. The failure and correction are retained in the checkpoint characterization.

## F19 — branching and three-carbon cycles

`FunctionalGroups.txt` contains tert-butyl/cyclopropyl motif precedents. Athena exposes neutral nonaromatic carbon branch points with three carbon SINGLE neighbors/H1 or four/H0; exact tert-butyl membership is center plus three authoritative methyl groups, attachment separately attributed. It does not invent a maximal hydrophobic fragment. Neopentane has four distinct tert-butyl member sets, each retaining its symmetry role alternatives; equivalent permutations do not multiply group count.

A carbon SINGLE-bond triangle is a source-topology proposition. Substitution, fused/spiro connectivity and an exocyclic multiple bond do not erase the three supplied edges. It does not assert that every atom is a saturated tetrahedral center, or that the complete component is valid/stable. H state is retained but is not a prerequisite for this edge predicate. Cyclopropene's non-SINGLE ring edge is a constitutional near miss. Individual branching and ring identities coexist.

## Source-H correction and historical compatibility

`athena.group/4`, profile `ATHENA_GROUP_SOURCE_H_V4`, enforces the already-declared `requiredState.hydrogenElements` on every matched query atom, including variable-H roles. Historical implementations 1/2/3 checked fixed `hydrogenRoles` locally but could admit a variable-H carbon with missing source H, even while reporting `REQUIRED_H_STATE=false` for exhaustive negatives. Seven F07 fixtures preserve this historical positive behavior as characterization. Opt-in /4 gives inconclusive for those unknown-state occurrences and preserves independent known occurrences. Nine complete-state F07 reports are unchanged apart from implementation/provenance attribution. The exact F07 scientific definition strings are unchanged in the /4 manifests.

No legacy scientific consumer is silently migrated. Historical manifests, evidence and replay remain immutable. /4 also retains /3's incomplete-mapping protection. Full consumer regression and byte-identical historical replay are required before checkpoint qualification.

## Reproduction and limits

[Batch checkpoint](../../../../software/qualification/foundation-closure-execution-20261006/CHECKPOINT.txt) links source pins, focused tests, whole-foundation consumer run, independent JVM output comparison and clean-source follow-up. Tests cover state/charge/bond alternatives, explicit/unknown H, constitutional near misses, cyclic/shared/fused/spiro examples, overlapping identities, target occurrences, role automorphisms, atom/bond permutation and source immutability. The initial failing drafts are preserved, not rewritten as successful runs.

These are group identity and source-topology descriptors only. No donor/acceptor expansion, interaction thresholds, normalization, energy, potency, fragment generation or METTL7 conclusions follow.
