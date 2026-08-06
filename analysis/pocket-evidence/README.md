# Pocket comparison evidence pipeline

How the pipeline answers "are these two pockets the same binding site?"
without collapsing the answer into one number: every evidence dimension
is preserved end to end, and a small, explicit rule set turns the
preserved dimensions into a verdict.

Code:

- evidence model and rules — `modules/athena/.../pocket/evidence/`
  (`totah.lab.athena.pocket.evidence`)
- assembly (Stage 3 of similarity search) —
  `apps/web-api/.../service/PocketComparisonEvidenceAssembler.java`
- assembly (pairwise report) —
  `apps/web-api/.../service/PocketComparisonReportService.java`
- DTOs — `apps/web-api/.../service/PocketComparisonReportView.java`
- Markdown — `apps/web-api/.../service/PocketComparisonReportMarkdown.java`
- UI cards — `apps/web-ui/.../features/compare/EvidenceReportSection.tsx`

## The independent-metrics principle

There is deliberately **no master score**. Identity, substitution
(BLOSUM62), chemistry-class agreement, spatial geometry, sequence
consistency, and ligand-contact conservation are different measurements
that can legitimately disagree (a homologue can have strong geometry and
weak chemistry; a merged pocket can contain a smaller one). The evidence
bundle keeps them as separate fields all the way into the API and the
UI; only the rule-based verdict interprets them, and the verdict always
ships with the *reason* naming the deciding dimensions and their values.
Older blended metrics (`overallSimilarity`, `finalSimilarity`) still
exist and are displayed, but they are labeled as convenience metrics and
are never presented as the answer.

## Retrieval: three channels, no invented ranks

Stage 1 of the similarity search is a union of up to three channels:

| Channel | Config | Notes |
|---|---|---|
| Global shape | `pocket.search.global-shape.enabled` (default `true`), `pocket.search.global-shape.limit` (default `500`) | The production SQL descriptor retrieval. Its natural rank is the 1-based position in the Stage 1 ordering. |
| PocketMatch | `pocket.search.pocket-match.enabled` (default **`false`**), `.limit`, `.ranking`, `.distance-tolerance` | Experimental; see the recommendation below. Carries its own two natural ranks (symmetric score and query coverage), scores, and tolerance. |
| Chosen reference | `pocket.search.include-chosen-references` (default `true`) | Pockets referenced by `docking.structure.chosen_pocket_id`. |

Semantics, as implemented in
`PocketSimilarityService.unionStageOneCandidates`:

- The union **deduplicates by pocket id and unions the source flags**;
  `candidateSources` on a row can be any subset of
  `GLOBAL_SHAPE`, `POCKET_MATCH`, `CHOSEN_REFERENCE`.
- **Chosen means guaranteed evaluation, never a bonus**: a chosen pocket
  is included even outside the Stage 1 limit and survives the Stage 2
  truncation, but it gets no score bonus, no rank bonus, and can still
  be assessed `REJECTED` or `CONFLICTING_EVIDENCE`. It is never an
  automatic positive.
- **Ranks are never invented.** A channel that did not evaluate a
  candidate reports `evaluated = false` with empty ranks/scores (the
  athena records reject `evaluated = false` combined with a present rank
  or score). A direct pairwise comparison (the `/evidence` endpoint)
  does not pass through retrieval at all, so both channels report
  `evaluated = false`; only the chosen-reference fact is recovered from
  the database.

## The evidence model

`PocketComparisonEvidence` bundles five dimensions. All records live in
`totah.lab.athena.pocket.evidence`.

### Retrieval — `PocketRetrievalEvidence`

- `globalShape` — `GlobalShapeRetrievalEvidence(evaluated, rank,
  distance)`: the global-shape channel's rank and descriptor distance,
  empty when unevaluated.
- `pocketMatch` — `PocketMatchRetrievalEvidence(evaluated,
  symmetricRank, queryCoverageRank, symmetricScore, queryCoverage,
  candidateCoverage, toleranceAngstroms)`: the PocketMatch channel's two
  natural orderings and its scores, kept on the channel's own scale —
  never blended with descriptor distances.
- `chosenReference` — whether the candidate is a chosen pocket.
- `candidateSources` — the unioned channel membership.

### Alignment — `PocketAlignmentEvidence`

Both hypotheses the multi-hypothesis aligner evaluated are preserved:

- `pcaIcp` / `sequenceSeeded` — `AlignmentHypothesisEvidence(available,
  accepted, geometrySimilarity, forwardCoverage, reverseCoverage,
  forwardMeanDistance, reverseMeanDistance, bidirectionalDistance,
  maximumNearestNeighborDistance, sequenceConsistentPairCount,
  residueCorrespondenceCount)`. The *losing* hypothesis keeps its real
  metrics; a hypothesis that was never computed is
  `AlignmentHypothesisEvidence.unavailable()` (`available = false`,
  zeroed metrics that must not be read as measurements).
- `selectedInitialization` + `selectionReason` — which frame won and a
  human-readable description of the decisive criterion.

### Residues — `PocketResidueEvidence`

Aggregates over the matched pairs under the *selected* alignment, kept
distinct on purpose: counts (`matchedResidueCount`, `identicalCount`,
`conservativeSubstitutionCount`, `chemistryCompatibleCount`,
`incompatibleReplacementCount`, unmatched counts), fractions
(`identityFraction`, `substitutionSimilarity` (mean normalized
BLOSUM62), `chemistrySimilarity` (identical 1.00 / conservative 0.70 /
chemistry-compatible 0.80 / different 0.00), `compatibleMatchedFraction`,
`replacementFraction`, `queryResidueCoverage`,
`candidateResidueCoverage`, `sequenceConsistentPairCount/Fraction`), and
one `ResidueCorrespondenceEvidence` per matched pair (references,
distance, `matchType`, `chemistryScore`, `substitutionScore`,
sequence-aligned flag, key-residue and contact flags).

### Functional — `PocketFunctionalEvidence`

- `ligandContacts` — `Optional<LigandContactEvidence>`: contact
  conservation for one ligand (counts, `contactCoverage`,
  identity/substitution/chemistry fractions, per-residue
  `FunctionalResidueCorrespondence` with both sides' annotation flags).
  **One-sided annotation is preserved**: a ligand annotated on only one
  side is evaluated with an empty contact set on the other. Empty only
  when neither structure has ligand evidence — absence is reported,
  never fabricated. Canonical per-residue contacts are carried as
  `LigandContact(status, pocketReference, ligandCcd, residue,
  minimumDistance, contactType ∈ {DIRECT, SHELL}, evidenceSource)`.
- `keyResidues` — `KeyResidueEvidence(totalKeyResidueCount,
  matchedKeyResidueCount, identicalKeyResidueCount,
  chemistryCompatibleKeyResidueCount)` for the configured key residues
  (`totah.key-residues.<uniprotId>`, e.g. `"LEU145"`).

### Assessment — `PocketAssessmentVerdict`

`PocketAssessmentVerdict(verdict, reason)`: `verdict` is one of
`PocketComparisonAssessment`'s six values; `reason` names the deciding
dimensions with their values and thresholds, so the decision can be
audited without re-running the rules.

## The assessment rules (uncalibrated)

`PocketAssessmentRules.defaults()` — first match wins:

| # | Verdict | Rule (defaults) |
|---|---|---|
| 1 | `INSUFFICIENT_EVIDENCE` | Selected hypothesis unavailable, or fewer than **3** matched residue pairs |
| 2 | `REJECTED` | geometry < **0.25** AND chemistry < **0.40** |
| 3 | `CONFLICTING_EVIDENCE` | geometry ≥ **0.60** with chemistry < **0.40**, or geometry < **0.25** with chemistry ≥ **0.60** |
| 4 | `STRONG_FUNCTIONAL_MATCH` | geometry ≥ 0.25, chemistry ≥ **0.60**, substitution ≥ **0.60**, sequence-consistent fraction ≥ **0.80** (when sequence evidence exists), contact conservation ≥ **0.70** (when ligand evidence exists) |
| 5 | `PROBABLE_FUNCTIONAL_MATCH` | geometry ≥ 0.25, chemistry ≥ 0.40, sequence-consistent fraction ≥ **0.50**, contact conservation ≥ **0.50** (incomplete annotation allowed) |
| 6 | `GEOMETRIC_MATCH_ONLY` | geometry ≥ 0.25 but poor residue agreement |
| 7 | `CONFLICTING_EVIDENCE` | everything else (unacceptable geometry with moderate residue evidence) |

Contact conservation = (identical + conservative +
chemistry-compatible contacts) / annotated query contacts. Sequence and
ligand dimensions are vacuously satisfied when that evidence does not
exist.

**These thresholds are uncalibrated** — they are the current best guess,
tuned so the METTL7A/METTL7B pair lands in the functional-match band,
and are expected to move once calibrated against known binders.

## Worked example: METTL7A pocket 32 vs METTL7B pocket 3

The regression fixture (asserted in
`Mettl7PocketComparisonEvidenceTest` and
`PocketComparisonReportServiceTest`):

- **PCA+ICP hypothesis**: geometry similarity ≈ **0.263**, 27 residue
  correspondences, **0/27** sequence-consistent.
- **Sequence-seeded hypothesis**: geometry similarity ≈ **0.265** —
  statistically the same geometry — but **31/31** sequence-consistent
  pairs.
- The aligner selects the sequence-seeded frame because the geometry
  difference is within tolerance and sequence consistency is higher. The
  discarded PCA+ICP frame stays inspectable in the report.
- Under the selected frame: 31 matched pairs, chemistry similarity ≈
  0.82, high substitution similarity, SAM-contact evidence available →
  the verdict is in the functional-match band (the tests assert
  `STRONG_FUNCTIONAL_MATCH` or `PROBABLE_FUNCTIONAL_MATCH`; the exact
  tier can move while the thresholds are uncalibrated).

The point of the example: geometry alone (0.263 vs 0.265) cannot choose
between the two frames — the *preserved* sequence-consistency dimension
can, and a blended score would have hidden that.

## API

- `GET /api/pockets/{q}/compare/{c}/report` and
  `GET /api/pockets/{q}/compare/{c}/evidence` →
  `PocketComparisonReportView` JSON: `retrieval`, `alignment` (both
  hypotheses), `residueComparison`, `chemistryComparison`,
  `keyResidueComparison`, `ligandContactConservation`
  (`NOT_AVAILABLE` with null metrics when absent), `interpretation`
  (verdict + reason). Web DTOs only — no athena types in the JSON.
- `GET /api/pockets/{q}/compare/{c}/evidence/report.md` → the same
  bundle as a human-readable Markdown report (Retrieval / Alignment /
  Residue / Functional / Assessment).
- `GET /api/pockets/{id}/similar/diagnostic` rows additionally carry
  `candidateSources`, `pocketMatchSymmetricRank`,
  `pocketMatchQueryCoverageRank`, and `assessment` (the verdict name).
- The compare page renders the bundle as evidence cards with the
  assessment as the headline; the similar-pockets table shows sources,
  ranks, and the assessment per row.

## Configuration keys

| Key | Default | Meaning |
|---|---|---|
| `pocket.search.global-shape.enabled` | `true` | Global-shape SQL channel on/off |
| `pocket.search.global-shape.limit` | `500` | Max global-shape candidates in the union |
| `pocket.search.include-chosen-references` | `true` | Inject chosen pockets as guaranteed-evaluation candidates |
| `pocket.search.pocket-match.enabled` | `false` | Experimental PocketMatch channel (see below) |
| `pocket.search.pocket-match.limit` | `500` | Max PocketMatch candidates in the union |
| `pocket.search.pocket-match.ranking` | `QUERY_COVERAGE` | Which PocketMatch score orders the channel top-N |
| `pocket.search.pocket-match.distance-tolerance` | `0.50` | Distance-list matching tolerance (Å) |
| `pocket.search.pocket-match.signature-store` | `workspace/output/pocketmatch/pocket-match-signatures.bin` | Precomputed signature store |

## Recommendation: keep `pocket.search.pocket-match.enabled = false`

**Recommendation: keep the channel disabled by default.** This matches
the benchmark's own conclusion in
[../pocketmatch/POCKETMATCH_EVALUATION.md](../pocketmatch/POCKETMATCH_EVALUATION.md)
and nothing in the evidence-panel work changes it.

Reasoning, from the committed benchmark and code:

1. *Retrieval lift is real but insufficiently selective.* For the
   METTL7A pocket 32 query, PocketMatch lifts the homologous METTL7B
   pocket 3 from descriptor rank 6840 to 902 (query-coverage ranking) —
   but the geometry-only Q14112 false positive stays *above* the true
   homolog on the symmetric score (0.615 vs 0.589; ranks 1453 vs 2338),
   and no tolerance in the 0.25–1.00 Å sweep fixes that ordering.
2. *The channel answers a different question than the descriptor
   channel*: top-100 overlap between the two is zero. That is valuable
   as a recall channel but unjustified as a default.
3. *Cost is batch-shaped*: ~12.3 s single-threaded query-versus-all
   over the 510k-pocket corpus with precomputed signatures (plus a 1.6
   GiB signature store that must be rebuilt when pockets change).
4. *The evidence pipeline already de-risks enabling it later*: channel
   provenance, both natural ranks, and scores are preserved per
   candidate and never blended, and the union only adds candidates.
   Enabling the channel cannot corrupt existing rankings — but the UI
   would surface a channel whose selectivity is not yet validated.

What would change the recommendation: a broader validated
positive/negative pocket panel (a single query is anecdote) showing the
channel separates homologs from chemistry-rich decoys — e.g.
query-coverage-led or hybrid ranking and/or per-category weighting, as
suggested in the benchmark's next steps — plus a signature-store
refresh story. A fresh benchmark run was not repeated here: it needs
the 1.6 GiB signature store, the live database, and ~440 s of corpus
scanning, and the existing evaluation already answers the default-value
question.
