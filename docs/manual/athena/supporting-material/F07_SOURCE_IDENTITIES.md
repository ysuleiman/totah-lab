# F07 — imine, oxime and nitroso source identities

Authority: the user's 2026-10-06 F07 approval and subsequent instruction to include
representable cyclic imines. The [original review](../../../../software/qualification/f07-source-identity-review-20261006/REVIEW.txt)
is preserved; the [scope amendment](../../../../software/qualification/f07-source-identity-20261006/SCOPE_AMENDMENT.txt)
records the later instruction and exact topology/state decomposition. Neither is a
current-policy receipt or an invented review-expiry date.

## Definition and implementation

The nine opt-in manifests under `groups-f07-v1` are authoritative, rendered in
[the rule reference](../RULES.md). Four identities implement the original source
motifs. Five additional primary/secondary imine identities distinguish acyclic,
exocyclic and endocyclic connectivity. They use the existing `FunctionalGroupRules`
`athena.group/2` interpreter and B00 `2026.7.2/athena-ocl-occurrences/2`; no new Java
production calculator, matcher, ring engine, public API or schema is introduced.

The generator composes the N-methyl query from the existing approved methyl query
and reuses its authoritative H3 and heavy-degree-one requirements. Tests compare
its stable methyl-atom correspondence with the original methyl identity. Query H
inference alone is insufficient. The imine carbon's complete graph, exact single
carbon substituents and authoritative H consistency establish H2/H1/H0; extra
multiple bonds or heteroatom substituents are outside that carbon-bound proposition.

Member sets, context, all B00 correspondence alternatives, explicit versus supplied
implicit H, charge, bond order, source state and provenance are preserved by the
existing group payload. Overlapping specializations coexist. No last-match-wins
assignment occurs. Negative assertions require the manifest's complete state and
exhaustive search coverage. Unknown H, unsupported representation and failed
processing are not negative identity evidence.

## Supporting material and decisions

* **Source precedent:** RDKit `Data/FunctionalGroups.txt`, commit
  `fece8caa860bdf6c9c82bdb9253f22a0cf87150c`, lines 28–31. Local pinned bytes:
  `software/qualification/scientific-rule-knowledge-audit-20261004/reference/rdkit--Data__FunctionalGroups.txt`,
  SHA256 `9faf6e0f5ace2da09eb655270c335df11193cb2b55e1b63f83cc5b2dde69fb86`.
  RDKit BSD license is retained beside the pinned source. These are implementation
  motifs, not primary experimental evidence or comprehensive chemical nomenclature.
* **ADOPT:** explicit C=N, C=N–OH and carbon–N=O source connectivity as identity
  questions; exact atom/context correspondence and preserved representations.
* **MODIFY:** restrict each proposition by its stated source charge, authoritative
  H, bond order and membership. Unlike RDKit's historical R0 motifs, the amended
  Athena family also attributes nonaromatic cyclic imines through qualified ring
  predicates. The two original terminal-carbon subsets remain specializations.
* **REUSE:** original B01 methyl predicate and source-H contract; B00 all-occurrence
  matching; P02 aromatic/heteroaromatic group identities; group `/2` coverage,
  correspondence, immutable evidence and replay. Their existing checkpoints remain
  unchanged and are linked in the master catalog.
* **REJECT:** treating aromatic nitrogen as an arbitrary localized C=N bond,
  normalization across supplied resonance/tautomer states, inferring chemical
  function from identity, or using a narrow external motif catalog as universal
  chemistry coverage.
* **NOT APPLICABLE:** empirical distance cutoffs and structural calibration datasets
  for these exact connectivity predicates. The synthetic graph/state corpus is
  an implementation oracle, not experimental evidence of stability or prevalence.

## Explicit boundaries

Neutral carbon-substituted primary and secondary imines include cyclic structures;
there is no introduced ring-size cutoff. Synthetic 3/4/5/6/7/8-member ring witnesses
exercise source topology, not energetic plausibility. Aromatic classes retain the
existing heteroaromatic 5/6-ring/system model; pyridine is a positive control for
that separate identity, not an unsupported substitute for a nonaromatic imine.

Charged iminium/resonance propositions, heteroatom-substituted imine carbon
(amidines/guanidines/imidates), and additional N-hetero subclasses require exact
separate definitions. Their absence from these definitions is not evidence of
invalid chemistry. Oxime ethers are not hydroxy oximes; nitrites/nitro are not
neutral nitroso. Component-level unsupported-query guards conservatively prevent
resolved assessment when conflicting/out-of-domain core representations occur in
the selected component. They do not normalize or delete those structures.

No donor/acceptor, pKa, tautomerization, E/Z equivalence, reactivity, covalent
inhibition, toxicity, affinity or biological-function claim is made. Existing
interaction consumers and their domains remain untouched. Source E/Z information
is transported without interpretation.

## Qualification and audit

[F07 checkpoint](../../../../software/qualification/f07-source-identity-20261006/CHECKPOINT.txt)
links exact definitions, tests, replay and preservation. Pre-amendment witnesses are
retained as characterization, not retrospectively rewritten as the final scope.
`F07SourceIdentityTest` exercises positives, near misses, source-H/charge/graph
incompleteness, overlap, symmetry, atom order, explicit H, cyclic boundaries,
aromatic attribution, evidence round-trip and immutable source state.

Qualification establishes implementation of these attributed definitions, not a
production Research Gate receipt. No existing detector is migrated automatically.
