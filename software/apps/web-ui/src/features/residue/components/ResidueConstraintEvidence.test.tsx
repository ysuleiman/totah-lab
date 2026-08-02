import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ResidueConstraintEvidence } from './ResidueConstraintEvidence'

describe('ResidueConstraintEvidence', () => {
  it('shows the typed ESMC metrics and provenance', () => {
    render(
      <ResidueConstraintEvidence
        loading={false}
        evidence={{
          residueId: 78,
          analysisType: 'ESMC_CONSTRAINT',
          score: 15.140625,
          rank: 1,
          provider: 'BIOHUB_ESMC',
          model: 'esmc-300m-2024-12',
          bestAlternative: 'A',
          wildTypeMinusBestAlternative: 12,
          aminoAcidEntropy: 0.1,
          artifactId: 43,
        }}
      />,
    )

    expect(screen.getByText('15.14')).toBeInTheDocument()
    expect(screen.getByText('BIOHUB_ESMC')).toBeInTheDocument()
    expect(screen.getByText('esmc-300m-2024-12')).toBeInTheDocument()
    expect(screen.getByText('model-preferred')).toBeInTheDocument()
  })
})
