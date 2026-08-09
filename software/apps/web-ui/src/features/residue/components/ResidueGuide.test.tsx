import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ResidueGuide } from './ResidueGuide'

describe('ResidueGuide', () => {
  it('shows only the evidence layers currently in use', () => {
    render(
      <ResidueGuide
        showChosenPocket
        showBiohub
        showDocking={false}
        showConstraint
        showNeighbors={false}
      />,
    )

    expect(screen.getByText('Chosen fpocket')).toBeInTheDocument()
    expect(screen.getByText('BioHub direct contact')).toBeInTheDocument()
    expect(screen.getByText('BioHub contact outside fpocket'))
      .toBeInTheDocument()
    expect(screen.getByText('ESMC sequence constraint')).toBeInTheDocument()
    expect(screen.queryByText('Docking contact frequency'))
      .not.toBeInTheDocument()
    expect(screen.queryByText('Spatial neighbor')).not.toBeInTheDocument()
  })

  it('lists the pocket categories currently tinted on the strip', () => {
    render(
      <ResidueGuide
        showChosenPocket
        showBiohub={false}
        showDocking={false}
        showConstraint={false}
        showNeighbors={false}
        categories={[
          {
            categories: ['HYDROPHOBIC'],
            primaryCategory: 'HYDROPHOBIC',
            primaryLabel: 'Hydrophobic',
            colorKey: 'HYDROPHOBIC',
          },
          {
            categories: ['NEGATIVELY_CHARGED'],
            primaryCategory: 'NEGATIVELY_CHARGED',
            primaryLabel: 'Negative',
            colorKey: 'NEGATIVELY_CHARGED',
          },
        ]}
      />,
    )

    expect(screen.getByText('Hydrophobic')).toBeInTheDocument()
    expect(screen.getByText('Negative')).toBeInTheDocument()
    expect(screen.queryByText('Aromatic')).not.toBeInTheDocument()
  })
})
