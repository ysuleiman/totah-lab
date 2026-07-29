import type { PocketEvidence } from '../../api/types'

interface Props {
  evidence: PocketEvidence
}

export function BiohubPocketEvidence({ evidence }: Props) {
  return (
    <section
      className="biohub-pocket-evidence"
      aria-label={`${evidence.ligandCcd} BioHub pocket evidence`}
    >
      <header>
        <div>
          <p className="eyebrow">Ligand-conditioned prediction</p>
          <h3>{evidence.ligandCcd}-bound pocket</h3>
        </div>
        <span className="interface-confidence">
          {formatMetric(evidence.interfacePtm)}
          <small>interface pTM</small>
        </span>
      </header>
      <div className="biohub-evidence-metrics">
        <Metric
          label="Pocket wall"
          value={`${evidence.shellResidueCount} residues`}
          detail={`within ${evidence.shellCutoff.toFixed(1)} Å`}
        />
        <Metric
          label="Direct contacts"
          value={`${evidence.directContactResidueCount} residues`}
          detail={`within ${evidence.directContactCutoff.toFixed(1)} Å`}
        />
        <Metric
          label="Fpocket overlap"
          value={`${evidence.chosenPocketOverlapCount}`
            + ` / ${evidence.shellResidueCount}`}
          detail="shared wall residues"
        />
        <Metric
          label="Direct consensus"
          value={`${evidence.directChosenPocketOverlapCount}`
            + ` / ${evidence.directContactResidueCount}`}
          detail="direct contacts also in fpocket"
        />
        <Metric
          label="Global pTM"
          value={formatMetric(evidence.ptm)}
          detail={evidence.model}
        />
      </div>
    </section>
  )
}

interface MetricProps {
  label: string
  value: string
  detail: string
}

function Metric({ label, value, detail }: MetricProps) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </div>
  )
}

function formatMetric(value: number | null) {
  return value == null ? '—' : value.toFixed(3)
}
