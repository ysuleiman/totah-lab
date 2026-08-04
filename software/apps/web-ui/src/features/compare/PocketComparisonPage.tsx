import { useState } from 'react'
import type {
  PocketComparisonDetails,
  PocketSimilarityDiagnosticRow,
  Point3D,
} from '../../api/types'
import { useApiQuery } from '../../api/hooks'
import { AsyncState } from '../../components/AsyncState'
import { ErrorBoundary } from '../../components/ErrorBoundary'
import { geometryBasisLabel } from '../similar/geometryBasis'
import { PointCloudViewer } from './PointCloudViewer'
import { ResidueCorrespondenceSection } from './ResidueCorrespondenceSection'
import { interpolateSpheres } from './spheres'

const RANK_LIMIT = 100

interface Props {
  queryPocketId: number
  candidatePocketId: number
  onNavigate: (path: string) => void
}

export function PocketComparisonPage(props: Props) {
  return (
    <ErrorBoundary>
      <PocketComparisonContent {...props} />
    </ErrorBoundary>
  )
}

function PocketComparisonContent({
  queryPocketId,
  candidatePocketId,
  onNavigate,
}: Props) {
  const details = useApiQuery<PocketComparisonDetails>(
    `/api/pockets/${queryPocketId}/compare/${candidatePocketId}`,
  )

  const diagnostic = useApiQuery<PocketSimilarityDiagnosticRow[]>(
    `/api/pockets/${queryPocketId}/similar/diagnostic?limit=${RANK_LIMIT}`,
  )

  const [showQuery, setShowQuery] = useState(true)
  const [showOriginal, setShowOriginal] = useState(false)
  const [showAligned, setShowAligned] = useState(true)
  const [pointSize, setPointSize] = useState(4)
  const [opacity, setOpacity] = useState(0.85)
  const [showCentroids, setShowCentroids] = useState(true)
  const [showMatchedResidues, setShowMatchedResidues] = useState(true)
  const [alignmentProgress, setAlignmentProgress] = useState(1)
  const [sphereScale, setSphereScale] = useState(1)
  const [resetKey, setResetKey] = useState(0)

  const ranks =
    diagnostic.data?.find(
      (row) => row.pocketId === candidatePocketId,
    ) ?? null

  if (details.loading) {
    return (
      <div className="compare-page">
        <AsyncState loading title="Loading comparison" />
      </div>
    )
  }

  if (details.error || !details.data) {
    return (
      <div className="compare-page">
        <AsyncState
          title="Comparison unavailable"
          message={details.error?.message}
          onRetry={details.retry}
        />
      </div>
    )
  }

  const {
    query,
    candidate,
    alignedCandidatePoints,
    comparison,
    residueCorrespondence,
  } = details.data

  const interpolatedCandidate = interpolatePoints(
    candidate.points,
    alignedCandidatePoints,
    alignmentProgress,
  )

  // Rigid alignment preserves radii; only the sphere centers interpolate.
  const interpolatedCandidateSpheres = interpolateSpheres(
    candidate.alphaSpheres,
    alignedCandidatePoints,
    alignmentProgress,
  )

  const hasSpheres =
    query.alphaSpheres.length > 0 || candidate.alphaSpheres.length > 0

  const geometryNoun = comparison.basis === 'ALPHA_SPHERES'
    ? 'alpha spheres'
    : 'residue heavy-atom points'

  const matchedQueryResiduePoints = residueCorrespondence?.matches.map(
    (match) => match.query.position,
  )
  // match.candidate.position is already in the aligned frame: the API
  // returns candidate residue coordinates after the retained PCA+ICP
  // transform. Do NOT apply the transform again here.
  const matchedCandidateResiduePoints =
    residueCorrespondence?.matches.map(
      (match) => match.candidate.position,
    )

  return (
    <div className="compare-page">
      <header className="compare-header">
        <div>
          <p className="eyebrow">Pocket comparison</p>

          <h1>
            {query.sourceAccession ?? `Pocket ${queryPocketId}`}
            {' vs '}
            {candidate.sourceAccession ?? `Pocket ${candidatePocketId}`}
          </h1>

          <p className="similar-subtitle">
            Geometry basis: {geometryBasisLabel(comparison.basis)}
            {' · '}
            Aligner: {details.data.aligner}
            {' · '}
            query {comparison.queryPointCount} points
            {' · '}
            candidate {comparison.candidatePointCount} points
          </p>
        </div>

        <button
          type="button"
          className="compare-link"
          onClick={() =>
            onNavigate(`/pockets/${queryPocketId}/similar`)
          }
        >
          ← Back to results
        </button>
      </header>

      <div className="compare-grid">
        <PocketMetadata title="Query" geometry={query} />
        <PocketMetadata title="Candidate" geometry={candidate} />

        <section className="panel metadata-card">
          <h2>Pipeline ranks</h2>

          {ranks ? (
            <dl>
              <dt>Stage 1 rank</dt>
              <dd>{ranks.stageOneRank}</dd>

              <dt>Stage 2 rank</dt>
              <dd>{ranks.stageTwoRank}</dd>

              <dt>Stage 3 rank</dt>
              <dd>{ranks.stageThreeRank}</dd>

              <dt>Descriptor distance</dt>
              <dd>{formatNumber(ranks.descriptorDistance, 3)}</dd>

              <dt>Shape distance</dt>
              <dd>{formatNumber(ranks.shapeDistance, 3)}</dd>
            </dl>
          ) : diagnostic.loading ? (
            <p className="muted-note">Loading pipeline ranks…</p>
          ) : (
            <p className="muted-note">
              Candidate is not in the top {RANK_LIMIT} diagnostic rows.
            </p>
          )}
        </section>

        <section className="panel metrics-card comparison-metrics-card">
          <h2>Comparison metrics</h2>

          <dl className="metrics-grid">
            <dt>Overall similarity</dt>
            <dd>{formatNumber(comparison.overallSimilarity, 3)}</dd>

            <dt>Geometry similarity</dt>
            <dd>{formatNumber(comparison.geometrySimilarity, 3)}</dd>

            <dt>Size similarity</dt>
            <dd>{formatNumber(comparison.sizeSimilarity, 3)}</dd>

            <dt>Forward coverage</dt>
            <dd>{formatNumber(comparison.queryCoverage, 2)}</dd>

            <dt>Reverse coverage</dt>
            <dd>{formatNumber(comparison.candidateCoverage, 2)}</dd>

            <dt>Forward mean distance</dt>
            <dd>
              {formatNumber(
                comparison.queryToCandidateMeanDistance,
                2,
              )}{' '}
              Å
            </dd>

            <dt>Reverse mean distance</dt>
            <dd>
              {formatNumber(
                comparison.candidateToQueryMeanDistance,
                2,
              )}{' '}
              Å
            </dd>

            <dt>Bidirectional distance</dt>
            <dd>
              {formatNumber(
                comparison.meanBidirectionalDistance,
                2,
              )}{' '}
              Å
            </dd>

            <dt>Max nearest-neighbor</dt>
            <dd>
              {formatNumber(
                comparison.maximumNearestNeighborDistance,
                2,
              )}{' '}
              Å
            </dd>
          </dl>
        </section>
      </div>

      <section className="panel viewer-panel">
        <div className="viewer-controls">
          <label className="viewer-checkbox">
            <input
              type="checkbox"
              checked={showQuery}
              onChange={(event) =>
                setShowQuery(event.target.checked)
              }
            />
            Query
          </label>

          <label className="viewer-checkbox">
            <input
              type="checkbox"
              checked={showOriginal}
              onChange={(event) =>
                setShowOriginal(event.target.checked)
              }
            />
            Original candidate
          </label>

          <label className="viewer-checkbox">
            <input
              type="checkbox"
              checked={showAligned}
              onChange={(event) =>
                setShowAligned(event.target.checked)
              }
            />
            Aligned candidate
          </label>

          <label>
            Point size
            <input
              type="range"
              min={1}
              max={12}
              step={1}
              value={pointSize}
              onChange={(event) =>
                setPointSize(Number(event.target.value))
              }
            />
          </label>

          <label>
            Opacity
            <input
              type="range"
              min={0.1}
              max={1}
              step={0.05}
              value={opacity}
              onChange={(event) =>
                setOpacity(Number(event.target.value))
              }
            />
          </label>

          {hasSpheres && (
            <label>
              Sphere scale
              <input
                type="range"
                min={0.25}
                max={3}
                step={0.05}
                value={sphereScale}
                onChange={(event) =>
                  setSphereScale(Number(event.target.value))
                }
              />
            </label>
          )}

          <label>
            Alignment
            <input
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={alignmentProgress}
              onChange={(event) =>
                setAlignmentProgress(Number(event.target.value))
              }
            />
            <span>{Math.round(alignmentProgress * 100)}%</span>
          </label>

          <label className="viewer-checkbox">
            <input
              type="checkbox"
              checked={showCentroids}
              onChange={(event) =>
                setShowCentroids(event.target.checked)
              }
            />
            Centroids
          </label>

          {residueCorrespondence && (
            <label className="viewer-checkbox">
              <input
                type="checkbox"
                checked={showMatchedResidues}
                onChange={(event) =>
                  setShowMatchedResidues(event.target.checked)
                }
              />
              Matched residue points
            </label>
          )}

          <button
            type="button"
            onClick={() => setResetKey((value) => value + 1)}
          >
            Reset camera
          </button>

          <button
            type="button"
            onClick={() =>
              animateAlignment(setAlignmentProgress)
            }
          >
            Animate
          </button>
        </div>

        <PointCloudViewer
          queryPoints={query.points}
          originalCandidatePoints={candidate.points}
          alignedCandidatePoints={interpolatedCandidate}
          showQuery={showQuery}
          showOriginalCandidate={showOriginal}
          showAlignedCandidate={showAligned}
          pointSize={pointSize}
          opacity={opacity}
          showCentroids={showCentroids}
          resetKey={resetKey}
          matchedQueryResiduePoints={matchedQueryResiduePoints}
          matchedCandidateResiduePoints={matchedCandidateResiduePoints}
          showMatchedResidues={showMatchedResidues}
          querySpheres={query.alphaSpheres}
          candidateSpheres={candidate.alphaSpheres}
          alignedCandidateSpheres={interpolatedCandidateSpheres}
          sphereScale={sphereScale}
        />

        <div
          className="viewer-legend"
          aria-label="Point-cloud legend"
        >
          <span>
            <i className="legend-dot query-dot" />
            Query
          </span>

          <span>
            <i className="legend-dot original-dot" />
            Original candidate
          </span>

          <span>
            <i className="legend-dot aligned-dot" />
            Aligned candidate
          </span>

          {residueCorrespondence && (
            <>
              <span>
                <i className="legend-dot matched-query-dot" />
                Matched query residues
              </span>

              <span>
                <i className="legend-dot matched-candidate-dot" />
                Matched candidate residues
              </span>
            </>
          )}
        </div>

        <p className="muted-note">
          Geometry rendered as {geometryNoun}
          {comparison.basis === 'ALPHA_SPHERES'
            ? ' (true radii; the aligned candidate reuses the original radii)'
            : ''}
          . overallSimilarity combines geometry similarity and point-count
          similarity; raw metrics are shown without quality thresholds.
        </p>

        <p className="muted-note">
          Drag to rotate and use the mouse wheel to zoom. The alignment
          slider moves the candidate from its original coordinates to the
          aligned result.
        </p>
      </section>

      {residueCorrespondence && (
        <ResidueCorrespondenceSection
          correspondence={residueCorrespondence}
          keyResidues={details.data.keyResidues ?? []}
        />
      )}
    </div>
  )
}

interface PocketMetadataProps {
  title: string
  geometry: PocketComparisonDetails['query']
}

function PocketMetadata({
  title,
  geometry,
}: PocketMetadataProps) {
  return (
    <section className="panel metadata-card">
      <h2>{title}</h2>

      <dl>
        <dt>Pocket</dt>
        <dd>{geometry.pocketId}</dd>

        <dt>Source accession</dt>
        <dd>{geometry.sourceAccession ?? '—'}</dd>

        <dt>Pocket number</dt>
        <dd>{geometry.pocketNumber ?? '—'}</dd>

        <dt>Structure</dt>
        <dd>{geometry.structureId ?? '—'}</dd>

        <dt>Points</dt>
        <dd>{geometry.pointCount}</dd>

        <dt>Basis</dt>
        <dd>{geometry.basis}</dd>
      </dl>
    </section>
  )
}

function interpolatePoints(
  original: Point3D[],
  aligned: Point3D[],
  progress: number,
): Point3D[] {
  if (original.length !== aligned.length) {
    return aligned
  }

  const clampedProgress = Math.max(0, Math.min(1, progress))

  return original.map((point, index) => {
    const target = aligned[index]

    return {
      x:
        point.x
        + (target.x - point.x) * clampedProgress,
      y:
        point.y
        + (target.y - point.y) * clampedProgress,
      z:
        point.z
        + (target.z - point.z) * clampedProgress,
    }
  })
}

function animateAlignment(
  setProgress: (value: number) => void,
) {
  const durationMs = 900
  const startedAt = performance.now()

  setProgress(0)

  const tick = (now: number) => {
    const progress = Math.min(
      1,
      (now - startedAt) / durationMs,
    )

    const eased = 1 - (1 - progress) ** 3
    setProgress(eased)

    if (progress < 1) {
      requestAnimationFrame(tick)
    }
  }

  requestAnimationFrame(tick)
}

function formatNumber(
  value: number | null | undefined,
  digits: number,
): string {
  if (value == null || !Number.isFinite(value)) {
    return '—'
  }

  return value.toFixed(digits)
}