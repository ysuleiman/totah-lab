import type { ResidueEvidence } from '../../../api/types'

interface Props {
  evidence: ResidueEvidence | null
  loading: boolean
}

export function ResidueConstraintEvidence({ evidence, loading }: Props) {
  if (loading) {
    return <section className="constraint-evidence"><p>Loading ESMC evidence…</p></section>
  }
  if (!evidence) {
    return (
      <section className="constraint-evidence">
        <p>No sequence-constraint evidence is stored for this residue.</p>
      </section>
    )
  }

  return (
    <section className="constraint-evidence" aria-label="ESMC constraint evidence">
      <header>
        <div>
          <p className="eyebrow">Sequence evidence</p>
          <h4>ESMC constraint</h4>
        </div>
        <strong className="constraint-score">
          {formatMetric(evidence.score)}
        </strong>
      </header>
      <div className="constraint-metrics">
        <div>
          <span>Wild-type rank</span>
          <strong>{evidence.rank ?? '—'}</strong>
          <small>{evidence.rank === 1 ? 'model-preferred' : 'not first'}</small>
        </div>
        <div>
          <span>Best alternative</span>
          <strong>{evidence.bestAlternative ?? '—'}</strong>
          <small>Δ {formatMetric(evidence.wildTypeMinusBestAlternative)}</small>
        </div>
        <div>
          <span>Entropy</span>
          <strong>{formatMetric(evidence.aminoAcidEntropy)}</strong>
          <small>lower is more focused</small>
        </div>
        <div>
          <span>Provenance</span>
          <strong>{evidence.provider ?? '—'}</strong>
          <small>{evidence.model ?? `artifact ${evidence.artifactId}`}</small>
        </div>
      </div>
    </section>
  )
}

function formatMetric(value: number | null) {
  return value == null ? '—' : value.toFixed(2)
}
