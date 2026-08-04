import { useState } from 'react'
import type {
  PocketComparisonDetails,
  PocketSimilarityDiagnosticRow,
} from '../../api/types'
import { useApiQuery } from '../../api/hooks'
import { AsyncState } from '../../components/AsyncState'
import { PointCloudViewer, type ViewerMode } from './PointCloudViewer'

const RANK_LIMIT = 100

interface Props {
  queryPocketId: number
  candidatePocketId: number
  onNavigate: (path: string) => void
}

export function PocketComparisonPage({
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

  const [mode, setMode] = useState<ViewerMode>('overlay')
  const [pointSize, setPointSize] = useState(4)
  const [opacity, setOpacity] = useState(0.85)
  const [showCentroids, setShowCentroids] = useState(true)
  const [resetKey, setResetKey] = useState(0)

  const ranks = diagnostic.data?.find(
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

  const { query, candidate, comparison } = details.data

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
            {comparison.basis} · query {comparison.queryPointCount} points ·
            candidate {comparison.candidatePointCount} points
          </p>
        </div>
        <button
          type="button"
          className="compare-link"
          onClick={() => onNavigate(`/pockets/${queryPocketId}/similar`)}
        >
          ← Back to results
        </button>
      </header>

      <div className="compare-grid">
        <section className="panel metadata-card">
          <h2>Query</h2>
          <dl>
            <dt>Pocket</dt>
            <dd>{query.pocketId}</dd>
            <dt>Source accession</dt>
            <dd>{query.sourceAccession ?? '—'}</dd>
            <dt>Pocket number</dt>
            <dd>{query.pocketNumber ?? '—'}</dd>
            <dt>Structure</dt>
            <dd>{query.structureId ?? '—'}</dd>
            <dt>Points</dt>
            <dd>{query.pointCount}</dd>
            <dt>Basis</dt>
            <dd>{query.basis}</dd>
          </dl>
        </section>

        <section className="panel metadata-card">
          <h2>Candidate</h2>
          <dl>
            <dt>Pocket</dt>
            <dd>{candidate.pocketId}</dd>
            <dt>Source accession</dt>
            <dd>{candidate.sourceAccession ?? '—'}</dd>
            <dt>Pocket number</dt>
            <dd>{candidate.pocketNumber ?? '—'}</dd>
            <dt>Structure</dt>
            <dd>{candidate.structureId ?? '—'}</dd>
            <dt>Points</dt>
            <dd>{candidate.pointCount}</dd>
            <dt>Basis</dt>
            <dd>{candidate.basis}</dd>
          </dl>
        </section>

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
              <dd>{ranks.descriptorDistance.toFixed(3)}</dd>
              <dt>Shape distance</dt>
              <dd>{ranks.shapeDistance.toFixed(3)}</dd>
            </dl>
          ) : (
            <p className="muted-note">
              Candidate is not in the top {RANK_LIMIT} diagnostic rows for
              this query.
            </p>
          )}
        </section>

        <section className="panel metrics-card">
          <h2>Comparison metrics</h2>
          <dl className="metrics-grid">
            <dt>Overall similarity</dt>
            <dd>{comparison.overallSimilarity.toFixed(3)}</dd>
            <dt>Geometry similarity</dt>
            <dd>{comparison.geometrySimilarity.toFixed(3)}</dd>
            <dt>Size similarity</dt>
            <dd>{comparison.sizeSimilarity.toFixed(3)}</dd>
            <dt>Forward coverage</dt>
            <dd>{comparison.queryCoverage.toFixed(2)}</dd>
            <dt>Reverse coverage</dt>
            <dd>{comparison.candidateCoverage.toFixed(2)}</dd>
            <dt>Forward mean distance</dt>
            <dd>{comparison.queryToCandidateMeanDistance.toFixed(2)} Å</dd>
            <dt>Reverse mean distance</dt>
            <dd>{comparison.candidateToQueryMeanDistance.toFixed(2)} Å</dd>
            <dt>Bidirectional distance</dt>
            <dd>{comparison.meanBidirectionalDistance.toFixed(2)} Å</dd>
            <dt>Max nearest-neighbor</dt>
            <dd>{comparison.maximumNearestNeighborDistance.toFixed(2)} Å</dd>
          </dl>
        </section>
      </div>

      <section className="panel viewer-panel">
        <div className="viewer-controls">
          <div className="viewer-mode" role="group" aria-label="View mode">
            {(['overlay', 'query', 'candidate'] as ViewerMode[]).map(
              (value) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={mode === value}
                  className={mode === value ? 'sort-active' : ''}
                  onClick={() => setMode(value)}
                >
                  {value === 'overlay'
                    ? 'Overlay'
                    : value === 'query'
                      ? 'Query only'
                      : 'Candidate only'}
                </button>
              ),
            )}
          </div>
          <label>
            Point size
            <input
              type="range"
              min={1}
              max={12}
              step={1}
              value={pointSize}
              onChange={(event) =>
                setPointSize(Number(event.target.value))}
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
              onChange={(event) => setOpacity(Number(event.target.value))}
            />
          </label>
          <label className="viewer-checkbox">
            <input
              type="checkbox"
              checked={showCentroids}
              onChange={(event) => setShowCentroids(event.target.checked)}
            />
            Show centroids
          </label>
          <button
            type="button"
            onClick={() => setResetKey((value) => value + 1)}
          >
            Reset camera
          </button>
        </div>
        <PointCloudViewer
          queryPoints={details.data.alignedQueryPoints}
          candidatePoints={details.data.alignedCandidatePoints}
          mode={mode}
          pointSize={pointSize}
          opacity={opacity}
          showCentroids={showCentroids}
          resetKey={resetKey}
        />
        <p className="muted-note">
          Aligned with Athena's centroid alignment (translation removed,
          rotation preserved) — blue: query, orange: candidate.
        </p>
      </section>
    </div>
  )
}
