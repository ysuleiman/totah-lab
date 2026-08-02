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
})
