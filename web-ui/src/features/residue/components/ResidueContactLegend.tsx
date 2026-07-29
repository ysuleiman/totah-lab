interface Props {
  threshold: number | null
}

export function ResidueContactLegend({ threshold }: Props) {
  if (threshold === null) return null

  return (
    <div className="residue-contact-legend" aria-label="Docking contact legend">
      <span className="contact-legend-track" aria-hidden="true">
        <i />
      </span>
      <span>Contacted ligands, score &lt; {formatScore(threshold)}</span>
      <small>0%</small>
      <small>100%</small>
    </div>
  )
}

function formatScore(score: number) {
  return Number.isInteger(score) ? score.toFixed(0) : score.toFixed(1)
}
