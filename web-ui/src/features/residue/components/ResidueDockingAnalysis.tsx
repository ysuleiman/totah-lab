import type {
  ResidueAnalysis,
  ResidueScoreBand,
} from '../../../api/types'

interface Props {
  analysis: ResidueAnalysis | null
  analysisLoading: boolean
  bands: ResidueScoreBand[]
  bandsLoading: boolean
}

export function ResidueDockingAnalysis({
  analysis,
  analysisLoading,
  bands,
  bandsLoading,
}: Props) {
  if (analysisLoading) {
    return (
      <section className="docking-analysis" aria-label="Docking contacts">
        <p>Loading docking contact analysis…</p>
      </section>
    )
  }
  if (!analysis) return null

  return (
    <section className="docking-analysis" aria-label="Docking contacts">
      <header>
        <div>
          <p className="eyebrow">MODEL 1 · heavy atoms · 4 Å</p>
          <h4>
            Docking contacts · score &lt;{' '}
            {formatScore(analysis.contactScoreThreshold)}
          </h4>
        </div>
        <strong className="primary-contact-rate">
          {formatPercent(analysis.scoreFilteredContactingLigandFraction)}
        </strong>
      </header>
      <div className="contact-metrics">
        <ContactMetric
          label="Ligands"
          count={analysis.scoreFilteredContactingLigandCount}
          total={analysis.scoreFilteredLigandCount}
          fraction={analysis.scoreFilteredContactingLigandFraction}
        />
        <ContactMetric
          label="Poses"
          count={analysis.scoreFilteredContactingPoseCount}
          total={analysis.scoreFilteredPoseCount}
          fraction={analysis.scoreFilteredContactingPoseFraction}
        />
      </div>
      <div className="score-band-analysis">
        <h5>
          Complete score bands below{' '}
          {formatScore(analysis.contactScoreThreshold)}
        </h5>
        {bandsLoading ? (
          <p>Loading score bands…</p>
        ) : (
          bands
            .filter((band) =>
              band.scoreUpper <= analysis.contactScoreThreshold
            )
            .map((band) => (
            <div className="score-band-row" key={band.scoreLower}>
              <span>{formatBand(band.scoreLower, band.scoreUpper)}</span>
              <div className="score-band-track" aria-hidden="true">
                <i style={{
                  width: `${Math.min(
                    100,
                    band.contactingLigandFraction * 100,
                  )}%`,
                }} />
              </div>
              <strong>{formatPercent(band.contactingLigandFraction)}</strong>
              <small>
                {band.contactingLigandCount.toLocaleString()}
                {' / '}
                {band.ligandCount.toLocaleString()}
              </small>
            </div>
          ))
        )}
      </div>
    </section>
  )
}

interface MetricProps {
  label: string
  count: number
  total: number
  fraction: number
}

function ContactMetric({ label, count, total, fraction }: MetricProps) {
  return (
    <div>
      <span>{label}</span>
      <strong>{formatPercent(fraction)}</strong>
      <small>
        {count.toLocaleString()} / {total.toLocaleString()}
      </small>
    </div>
  )
}

function formatPercent(fraction: number) {
  return `${(fraction * 100).toFixed(2)}%`
}

function formatBand(lower: number, upper: number) {
  return `${lower.toFixed(0)} to ${upper.toFixed(0)}`
}

function formatScore(score: number) {
  return Number.isInteger(score) ? score.toFixed(0) : score.toFixed(1)
}
