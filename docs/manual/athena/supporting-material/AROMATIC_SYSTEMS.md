# Bounded aromatic cycle and fused-system attribution

This is the supporting-material record for `ATHENA.PERCEPTION.AROMATIC.SYSTEM/1.0.0`,
covering the aromatic attribution subset of P02/P06. The authoritative definition is
[the approved contract](../../../../software/qualification/aromatic-system-contract-20261005/DESIGN.txt).
Its exact digest and the four exact source manifests are pinned by
[the production candidate manifest](../../../../software/modules/athena/src/main/resources/totah/lab/athena/system/rules/aromatic-systems-v1/ATHENA.PERCEPTION.AROMATIC.SYSTEM.rule.json).
The user approved this contract on 2026-10-05. This is not an invented Research Gate
review expiry or a current-policy qualification receipt.

## Scientific reasoning and sources

The existing [aromatic perception dossier](../../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.AROMATIC.json)
requires chemical aromaticity before geometry and preservation of simple rings and
fused-system identities separately. Its source map, exact implementation versions,
literature locators, limitations and rejected alternatives remain linked in that dossier.
The [group aromatic dossier](../../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.AROMATIC.json)
and [vocabulary checkpoint](../../../../software/qualification/foundation-vocabulary-20261005/CHECKPOINT.txt)
establish the existing literal 5/6-cycle predicates and pinned OCL model. Their
underlying supporting material is reused, not reacquired or silently upgraded.
The [Mobley architectural analysis](MOBLEY_2018.md) explains the direct-perception
architecture and its source limitations; it does not establish this fusion definition.

The new grouping definition is an explicitly reviewed **structural convention**:
a maximal collection connected by shared source covalent bonds. Sharing an atom
alone does not establish fusion. No empirical threshold, calibration distribution,
aromatic stabilization energy or favorable-interaction inference is introduced.
No empirical dataset was acquired or analyzed for this step. The geometry of a flat
ring does not establish aromaticity. Larger cycles are outside exhaustive claims.

Decisions:

- **ADOPT:** the qualified B00/group `/2` five/six-vertex cycle occurrences with
  OCL `2026.7.2` and matcher `2026.7.2/athena-ocl-occurrences/2` provenance.
- **ADOPT:** shared source bond as the explicit fusion relation; preserve singleton
  cycles and disconnected systems.
- **MODIFY:** general and heteroaromatic labels over the same cycle are co-attributed,
  retaining both source occurrences rather than multiplying the chemical identity.
- **REJECT for this attribution:** silently substituting OCL `RingCollection`
  representatives from `OclLigandFeaturePerceiver`. They answer a different ring
  representation question and bypass the reviewed B00 occurrence definition.
- **REJECT:** coordinate planarity as aromaticity, a unique cycle-basis claim,
  last-match-wins labels, and independent per-atom mapping combinations.
- **UNSUPPORTED:** exhaustive arbitrary-size cycle perception, ambiguous source
  mapping positives under the current upstream group report contract, energetic
  aromaticity, and unreviewed interaction activation.

## Implementation, provenance and coverage

`AromaticSystemRules` is a package-private read-side interpreter reached through
existing `RuleAnalyzers` and `RuleRegistry`. It invokes the existing group evaluator
on each preserved source report. That evaluator replays the stored B00 receipts,
checks exact definition and state binding, and reconstructs the historical group
payload. There is no additional matcher or ring-perception engine.

The source group's bond list includes context bonds. The implementation uses every
preserved `vertex0…vertexN` role correspondence to recover **cycle edges only** from
the authoritative `MolecularGraph`. Different cycle bond sets survive; automorphic
vertex orderings of the same mapped cycle do not multiply it. General/hetero labels
retain distinct source links. The source reports retain all original role alternatives.
Atoms in the derived report are stable `AtomReference`s, never copied molecular state.

Only whole source correspondence maps can establish simultaneous relations. Existing
group reports with missing/ambiguous structural correspondence suppress positive
occurrences. This milestone preserves that behavior and emits inconclusive coverage;
it does not pick a mapping or widen the upstream positive domain. The durable schema
can retain separate alternatives, but broader ambiguous-positive qualification is not
claimed. This is an explicit limitation, not absence of an aromatic system.

`ABSENT_FALSE` requires verified exhaustive **general RING5 and RING6** reports,
complete source chemistry and correspondence, and satisfied request bounds in the
explicit supplied component. Hetero labels add provenance; the general predicates
already cover those same aromatic vertices. A missing report, unknown chemical state,
unsupported perception or incomplete correspondence prevents absence. Positive
partial cycle attribution remains visible with incomplete coverage. Failed source
verification fails closed; input envelopes/artifacts remain unchanged and must be
preserved by the existing pipeline before evaluation.

## Qualification and permanent witnesses

[Execution checkpoint](../../../../software/qualification/aromatic-systems-20261005/CHECKPOINT.txt)
links exact hashes, tests and independent-JVM replay. Synthetic graph oracles cover
benzene, pyridine, pyrrole, naphthalene, indole, nonaromatic rings, overlapping labels,
disconnected rings, unknown state, missing/ambiguous correspondence, false source
hashes/state/definitions, source/report immutability and atom-order permutations.
The shared-atom-only witness tests the structural relation directly; it does **not**
assert that an arbitrary synthetic spiro graph is chemically aromatic.

No historical manifest, group payload, evidence, interaction behavior or certificate
is rewritten. Current-policy scientific qualification remains separate from bounded
implementation qualification. Geometry consumers may cite this payload as an explicit
group source; this step does not automatically activate or migrate any consumer.
