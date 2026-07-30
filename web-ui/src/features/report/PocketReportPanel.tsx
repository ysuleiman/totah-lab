import type { PocketReportDocument } from '../../api/types'
import { AsyncState } from '../../components/AsyncState'

interface Props {
  document: PocketReportDocument | null
  pocketId: number
  runId: number
  loading: boolean
  error: Error | null
  onClose: () => void
  onRetry: () => void
}

export function PocketReportPanel({
  document,
  pocketId,
  runId,
  loading,
  error,
  onClose,
  onRetry,
}: Props) {
  if (loading && !document) {
    return (
      <section className="panel structure-report">
        <AsyncState loading title="Generating pocket report" compact />
      </section>
    )
  }
  if (error || !document) {
    return (
      <section className="panel structure-report">
        <AsyncState
          title="Pocket report unavailable"
          message={error?.message}
          onRetry={onRetry}
          compact
        />
      </section>
    )
  }

  const { report, narrative } = document
  const { data } = report
  const volume = numberValue(data.geometry.estimatedVolumeAngstrom3)
  const druggability = numberValue(data.geometry.druggabilityScore)

  return (
    <section
      className="panel structure-report"
      aria-labelledby="pocket-report-heading"
    >
      <header className="report-heading">
        <div>
          <p className="eyebrow">Current database state</p>
          <h2 id="pocket-report-heading">{data.pocketName} report</h2>
          <small>
            Pocket {data.pocketId} · docking run {data.docking.runId}
            {' · '}{data.source}
          </small>
        </div>
        <div className="report-actions">
          <a
            href={`/api/pockets/${pocketId}/report.pdf?runId=${runId}`}
            download
          >
            Download PDF
          </a>
          <button type="button" onClick={onClose}>Close</button>
        </div>
      </header>

      <p className="report-narrative">{narrative.executiveSummary}</p>

      <section className="report-section">
        <header>
          <div>
            <p className="eyebrow">Pocket and docking scope</p>
            <h3>Measured evidence</h3>
          </div>
          <span>Score &lt; {data.docking.contactScoreThreshold}</span>
        </header>
        <div className="report-pocket-metrics">
          <Metric label="Residues" value={String(data.residues.totalResidues)} />
          <Metric label="Volume" value={format(volume, ' Å³')} />
          <Metric label="Druggability" value={format(druggability)} />
          <Metric
            label="Unique ligands"
            value={data.docking.totalLigandCount.toLocaleString()}
          />
          <Metric
            label="Poses"
            value={data.docking.totalPoseCount.toLocaleString()}
          />
        </div>
      </section>

      <section className="report-section">
        <header>
          <div>
            <p className="eyebrow">Evidence-linked narrative</p>
            <h3>Findings</h3>
          </div>
          <span>{narrative.findings.length} findings</span>
        </header>
        <ol className="report-findings">
          {narrative.findings.map((finding) => (
            <li key={`${finding.evidenceIds.join('-')}-${finding.statement}`}>
              <p>{finding.statement}</p>
              <small>[{finding.evidenceIds.join(', ')}]</small>
            </li>
          ))}
        </ol>
      </section>

      <section className="report-section">
        <header>
          <div>
            <p className="eyebrow">Residue interaction landscape</p>
            <h3>Docking contacts</h3>
          </div>
          <span>{data.docking.residues.length} pocket residues</span>
        </header>
        <div className="report-table-scroll">
          <table className="report-residue-table">
            <thead>
              <tr>
                <th>Residue</th>
                <th>Ligands</th>
                <th>Poses</th>
                <th>Filtered ligands</th>
                <th>Enrichment</th>
                <th>Closest</th>
              </tr>
            </thead>
            <tbody>
              {data.docking.residues.map((residue) => (
                <tr key={`${residue.chain}-${residue.residueNumber}`}>
                  <td>
                    {residue.chain}:{residue.residueName}
                    {residue.residueNumber}
                  </td>
                  <td>{percent(residue.contactingLigandFraction)}</td>
                  <td>{percent(residue.contactingPoseFraction)}</td>
                  <td>
                    {percent(residue.scoreFilteredContactingLigandFraction)}
                  </td>
                  <td>{format(residue.enrichmentRatio, '×')}</td>
                  <td>{format(residue.closestDistance, ' Å')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="report-section report-interpretation">
        <div>
          <p className="eyebrow">Conclusion</p>
          <p>{narrative.conclusions}</p>
        </div>
        <div>
          <p className="eyebrow">Limitations</p>
          <p>{narrative.limitations}</p>
        </div>
      </section>
    </section>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><strong>{value}</strong></div>
}

function numberValue(value: unknown) {
  return typeof value === 'number' ? value : undefined
}

function percent(value?: number) {
  return value == null ? '—' : `${(value * 100).toFixed(1)}%`
}

function format(value?: number, suffix = '') {
  return value == null ? '—' : `${value.toFixed(3)}${suffix}`
}
