import type {
  AlignmentHypothesisView,
  PocketComparisonReportView,
} from '../../api/types'
import {
  assessmentClass,
  assessmentLabel,
} from '../similar/comparisonAssessment'
import { alignmentInitializationLabel } from '../similar/alignmentInitialization'

interface Props {
  report: PocketComparisonReportView
}

/**
 * The evidence cards of a pairwise pocket comparison: retrieval
 * provenance, both alignment hypotheses, residue and chemistry
 * conservation, ligand-contact conservation, and the rules verdict.
 * The verdict — not any blended similarity score — is the headline.
 * Unevaluated channels and unavailable annotations are stated
 * explicitly; nothing is fabricated.
 */
export function EvidenceReportSection({ report }: Props) {
  return (
    <section
      className="evidence-report"
      aria-label="Comparison evidence"
    >
      <div className="evidence-report-header">
        <h2>Evidence</h2>
        <a
          className="compare-link"
          href={`/api/pockets/${report.queryPocketId}/compare/`
            + `${report.candidatePocketId}/evidence/report.md`}
          download
        >
          Download Markdown report
        </a>
      </div>

      <div className="compare-grid">
        <section
          className={`panel metrics-card assessment-card ${
            assessmentClass(report.interpretation.verdict)
          }`}
          data-testid="assessment-card"
        >
          <h2>Assessment</h2>
          <p className="assessment-verdict">
            {assessmentLabel(report.interpretation.verdict)}
          </p>
          <p className="muted-note">{report.interpretation.reason}</p>
          <p className="muted-note">
            Rule-based verdict over the preserved evidence dimensions;
            thresholds are uncalibrated. Not a blended score.
          </p>
        </section>

        <RetrievalCard report={report} />
        <AlignmentCard report={report} />
        <ResidueConservationCard report={report} />
        <ChemistryCard report={report} />
        <LigandContactCard report={report} />
      </div>
    </section>
  )
}

function RetrievalCard({ report }: Props) {
  const { retrieval } = report
  return (
    <section className="panel metrics-card">
      <h2>Retrieval</h2>

      <dl className="metrics-grid">
        <dt>Candidate sources</dt>
        <dd>
          {retrieval.candidateSources.length > 0
            ? retrieval.candidateSources.join(', ')
            : '—'}
        </dd>

        <dt>Chosen reference</dt>
        <dd>{retrieval.chosenReference ? 'Yes' : 'No'}</dd>

        <dt>Global shape</dt>
        <dd>
          {retrieval.globalShapeEvaluated
            ? `rank ${retrieval.globalShapeRank ?? '—'}`
              + ` · distance ${formatNumber(
                  retrieval.globalShapeDistance, 3)}`
            : 'Not evaluated (no search was run)'}
        </dd>

        <dt>PocketMatch</dt>
        <dd>
          {retrieval.pocketMatchEvaluated
            ? `symmetric rank ${
                retrieval.pocketMatchSymmetricRank ?? '—'}`
              + ` · coverage rank ${
                retrieval.pocketMatchQueryCoverageRank ?? '—'}`
            : 'Not evaluated (no search was run)'}
        </dd>

        {retrieval.pocketMatchEvaluated && (
          <>
            <dt>PocketMatch scores</dt>
            <dd>
              symmetric {formatNumber(
                retrieval.pocketMatchSymmetricScore, 3)}
              {' · '}query coverage {formatNumber(
                retrieval.pocketMatchQueryCoverage, 2)}
              {' · '}candidate coverage {formatNumber(
                retrieval.pocketMatchCandidateCoverage, 2)}
            </dd>
          </>
        )}
      </dl>
    </section>
  )
}

function AlignmentCard({ report }: Props) {
  const { alignment } = report
  return (
    <section className="panel metrics-card">
      <h2>Alignment</h2>

      <dl className="metrics-grid">
        <dt>Selected initialization</dt>
        <dd>
          {alignmentInitializationLabel(
            alignment.selectedInitialization,
          )}
        </dd>

        <dt>Selection reason</dt>
        <dd>{alignment.selectionReason}</dd>

        <dt>Sequence consistency</dt>
        <dd>
          {alignment.sequenceConsistentCorrespondenceCount}
          {' / '}
          {alignment.sequenceSeedPairCount}
          {' seed pairs ('}
          {Math.round(
            alignment.sequenceConsistentCorrespondenceFraction * 100,
          )}
          {'%)'}
        </dd>
      </dl>

      <HypothesisBlock name="PCA+ICP" hypothesis={alignment.pcaIcp} />
      <HypothesisBlock
        name="Sequence-seeded"
        hypothesis={alignment.sequenceSeeded}
      />
    </section>
  )
}

function HypothesisBlock({
  name,
  hypothesis,
}: {
  name: string
  hypothesis: AlignmentHypothesisView
}) {
  if (!hypothesis.available) {
    return (
      <div className="hypothesis-block">
        <h3>{name} hypothesis</h3>
        <p className="muted-note">Not computed.</p>
      </div>
    )
  }

  return (
    <div className="hypothesis-block">
      <h3>
        {name} hypothesis
        {hypothesis.accepted && (
          <span className="count-badge">selected</span>
        )}
      </h3>
      <dl className="metrics-grid">
        <dt>Geometry similarity</dt>
        <dd>{formatNumber(hypothesis.geometrySimilarity, 3)}</dd>

        <dt>Coverage (query / candidate)</dt>
        <dd>
          {formatNumber(hypothesis.forwardCoverage, 2)}
          {' / '}
          {formatNumber(hypothesis.reverseCoverage, 2)}
        </dd>

        <dt>Bidirectional distance</dt>
        <dd>{formatNumber(hypothesis.bidirectionalDistance, 2)} Å</dd>

        <dt>Max nearest-neighbor</dt>
        <dd>
          {formatNumber(
            hypothesis.maximumNearestNeighborDistance, 2)} Å
        </dd>

        <dt>Residue correspondences</dt>
        <dd>
          {hypothesis.residueCorrespondenceCount}
          {' ('}
          {hypothesis.sequenceConsistentPairCount}
          {' sequence-consistent)'}
        </dd>
      </dl>
    </div>
  )
}

function ResidueConservationCard({ report }: Props) {
  const residues = report.residueComparison
  return (
    <section className="panel metrics-card">
      <h2>Residue conservation</h2>

      <dl className="metrics-grid">
        <dt>Matched / query / candidate</dt>
        <dd>
          {residues.matchedResidueCount}
          {' / '}
          {residues.queryResidueCount}
          {' / '}
          {residues.candidateResidueCount}
        </dd>

        <dt>Identical / conservative / compatible</dt>
        <dd>
          {residues.identicalCount}
          {' / '}
          {residues.conservativeSubstitutionCount}
          {' / '}
          {residues.chemistryCompatibleCount}
        </dd>

        <dt>Identity fraction</dt>
        <dd>{formatNumber(residues.identityFraction, 3)}</dd>

        <dt>Substitution similarity (BLOSUM62)</dt>
        <dd>{formatNumber(residues.substitutionSimilarity, 3)}</dd>

        <dt>Chemistry similarity</dt>
        <dd>{formatNumber(residues.chemistrySimilarity, 3)}</dd>

        <dt>Residue coverage (query / candidate)</dt>
        <dd>
          {formatNumber(residues.queryResidueCoverage, 2)}
          {' / '}
          {formatNumber(residues.candidateResidueCoverage, 2)}
        </dd>

        <dt>Sequence-consistent pairs</dt>
        <dd>
          {residues.sequenceConsistentPairCount}
          {' ('}
          {formatNumber(residues.sequenceConsistentFraction, 2)}
          {')'}
        </dd>
      </dl>
    </section>
  )
}

function ChemistryCard({ report }: Props) {
  const chemistry = report.chemistryComparison
  const keyResidues = report.keyResidueComparison
  return (
    <section className="panel metrics-card">
      <h2>Chemistry</h2>

      <dl className="metrics-grid">
        <dt>Chemistry similarity</dt>
        <dd>{formatNumber(chemistry.chemistrySimilarity, 3)}</dd>

        <dt>Compatible matched fraction</dt>
        <dd>{formatNumber(chemistry.compatibleMatchedFraction, 3)}</dd>

        <dt>Spatial replacement fraction</dt>
        <dd>{formatNumber(chemistry.spatialReplacementFraction, 3)}</dd>

        <dt>Key residues</dt>
        <dd>
          {keyResidues.configuredKeyResidues.length === 0
            ? 'None configured'
            : `${keyResidues.matchedKeyResidueCount}`
              + ` / ${keyResidues.totalKeyResidueCount} matched`
              + ` · ${keyResidues.identicalKeyResidueCount} identical`
              + ` · ${keyResidues.chemistryCompatibleKeyResidueCount}`
              + ' compatible'}
        </dd>
      </dl>
    </section>
  )
}

function LigandContactCard({ report }: Props) {
  const ligand = report.ligandContactConservation
  const available = ligand.status === 'AVAILABLE'

  const queryAnnotated = ligand.contacts.some(
    (contact) =>
      contact.pocketReference === String(report.queryPocketId),
  )
  const candidateAnnotated = ligand.contacts.some(
    (contact) =>
      contact.pocketReference === String(report.candidatePocketId),
  )

  return (
    <section className="panel metrics-card">
      <h2>SAM-contact conservation</h2>

      {!available ? (
        <p className="muted-note">
          Not available: no BioHub ligand-contact evidence for this
          structure pair.
        </p>
      ) : (
        <dl className="metrics-grid">
          <dt>Ligand</dt>
          <dd>
            {ligand.ligandCcd ?? '—'}
            {ligand.evidenceSource
              ? ` (${ligand.evidenceSource})`
              : ''}
          </dd>

          <dt>Annotation</dt>
          <dd>
            {queryAnnotated && candidateAnnotated
              ? 'both sides'
              : queryAnnotated
                ? 'query only'
                : candidateAnnotated
                  ? 'candidate only'
                  : '—'}
          </dd>

          <dt>Contact coverage</dt>
          <dd>{formatNumber(ligand.contactCoverage, 3)}</dd>

          <dt>Contact identity fraction</dt>
          <dd>{formatNumber(ligand.contactIdentityFraction, 3)}</dd>

          <dt>Contact substitution similarity</dt>
          <dd>
            {formatNumber(ligand.contactSubstitutionSimilarity, 3)}
          </dd>

          <dt>Contact chemistry similarity</dt>
          <dd>
            {formatNumber(ligand.contactChemistrySimilarity, 3)}
          </dd>

          <dt>Query contacts matched</dt>
          <dd>
            {ligand.matchedQueryContactResidueCount ?? '—'}
            {' / '}
            {ligand.queryContactResidueCount ?? '—'}
          </dd>

          <dt>Shared annotation</dt>
          <dd>{ligand.sharedContactAnnotationCount ?? '—'}</dd>
        </dl>
      )}
    </section>
  )
}

function formatNumber(
  value: number | null,
  digits: number,
): string {
  if (value == null || !Number.isFinite(value)) {
    return '—'
  }
  return value.toFixed(digits)
}
