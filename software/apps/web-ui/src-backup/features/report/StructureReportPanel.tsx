import type { StructureReport } from '../../api/types'
import { AsyncState } from '../../components/AsyncState'

interface Props {
  report: StructureReport | null
  loading: boolean
  error: Error | null
  onClose: () => void
  onRetry: () => void
}

export function StructureReportPanel({
  report,
  loading,
  error,
  onClose,
  onRetry,
}: Props) {
  if (loading && !report) {
    return (
      <section className="panel structure-report">
        <AsyncState loading title="Generating report" compact />
      </section>
    )
  }
  if (error || !report) {
    return (
      <section className="panel structure-report">
        <AsyncState
          title="Report unavailable"
          message={error?.message}
          onRetry={onRetry}
          compact
        />
      </section>
    )
  }

  return (
    <section
      className="panel structure-report"
      aria-labelledby="structure-report-heading"
    >
      <header className="report-heading">
        <div>
          <p className="eyebrow">Generated from current database state</p>
          <h2 id="structure-report-heading">{report.title}</h2>
          <small>
            {report.geneName ?? 'Unknown gene'}
            {report.uniProtId ? ` · ${report.uniProtId}` : ''}
            {' · '}
            {new Date(report.generatedAt).toLocaleString()}
          </small>
        </div>
        <div className="report-actions">
          <a
            href={`/api/structures/${report.structureId}/report.pdf`}
            download
          >
            Download PDF
          </a>
          <button type="button" onClick={onClose}>Close</button>
        </div>
      </header>

      <p className="report-narrative">{report.narrative}</p>

      {report.chosenPocket && (
        <section className="report-section">
          <header>
            <div>
              <p className="eyebrow">Pocket definition</p>
              <h3>
                {report.chosenPocket.source}{' '}
                {report.chosenPocket.pocketNumber}
              </h3>
            </div>
            <span>
              {report.chosenPocket.residueCount} residues
            </span>
          </header>
          <div className="report-pocket-metrics">
            <Metric label="Score" value={metric(report.chosenPocket.score)} />
            <Metric
              label="Druggability"
              value={metric(report.chosenPocket.druggabilityScore)}
            />
            <Metric
              label="Volume"
              value={report.chosenPocket.volume == null
                ? '—'
                : `${report.chosenPocket.volume.toFixed(1)} Å³`}
            />
          </div>
          <div
            className="report-residue-sequence"
            aria-label="Chosen pocket residues"
          >
            {report.chosenPocketResidues.map((residue) => (
              <span key={residue.id} title={residueLabel(residue)}>
                <strong>{residue.oneLetterCode}</strong>
                {residue.residueNumber}
              </span>
            ))}
          </div>
        </section>
      )}

      {report.ligandEvidence.map((evidence) => (
        <section className="report-section" key={evidence.ligandCcd}>
          <header>
            <div>
              <p className="eyebrow">BioHub contact evidence</p>
              <h3>{evidence.ligandCcd}</h3>
            </div>
            <span>
              {evidence.directContactCount} direct ·{' '}
              {evidence.outsideDirectContactCount} outside fpocket
            </span>
          </header>
          <div className="report-contact-summary">
            <Metric
              label={`Strong ≤ ${evidence.strongContactCutoff.toFixed(1)} Å`}
              value={String(evidence.strongContactCount)}
            />
            <Metric
              label={`${evidence.strongContactCutoff.toFixed(1)}–`
                + `${evidence.directContactCutoff.toFixed(1)} Å`}
              value={String(evidence.nearContactCount)}
            />
            <Metric
              label={`Context ≤ ${evidence.contextCutoff.toFixed(1)} Å`}
              value={String(evidence.contextResidueCount)}
            />
            <Metric
              label="Interface pTM"
              value={metric(evidence.interfacePtm)}
            />
          </div>
          <div className="report-table-scroll">
            <table className="report-residue-table">
              <thead>
                <tr>
                  <th>Residue</th>
                  <th>Sequence</th>
                  <th>Distance</th>
                  <th>Class</th>
                  <th>fpocket</th>
                </tr>
              </thead>
              <tbody>
                {evidence.residues.map((residue) => (
                  <tr
                    key={residue.id}
                    className={
                      residue.directContact && !residue.chosenPocketMember
                        ? 'outside-pocket'
                        : undefined
                    }
                  >
                    <td>
                      {residue.chain}:{residue.residueNumber}{' '}
                      {residue.residueName}
                    </td>
                    <td>
                      {residue.oneLetterCode}{residue.residueNumber}
                    </td>
                    <td>{residue.minimumDistance.toFixed(2)} Å</td>
                    <td>
                      <span className={`contact-class ${
                        residue.classification.toLowerCase()
                      }`}>
                        {residue.classification.toLowerCase()}
                      </span>
                    </td>
                    <td>
                      {residue.chosenPocketMember ? 'Yes' : 'No'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}
    </section>
  )
}

interface MetricProps {
  label: string
  value: string
}

function Metric({ label, value }: MetricProps) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function metric(value: number | null) {
  return value == null ? '—' : value.toFixed(3)
}

function residueLabel(residue: {
  chain: string
  residueNumber: number
  residueName: string
  oneLetterCode: string
}) {
  return `${residue.chain}:${residue.residueNumber} `
    + `${residue.residueName} (${residue.oneLetterCode})`
}
