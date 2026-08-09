import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DcmbReportPage } from './DcmbReportPage'

describe('DcmbReportPage', () => {
  it('shows the evidence panel and preserves both readiness gates', () => {
    render(<DcmbReportPage />)

    expect(screen.getByRole('heading', { name: 'DCMB mechanism validation' })).toBeInTheDocument()
    expect(screen.getByText('DCMB / LY-78335')).toBeInTheDocument()
    expect(screen.getByText('CONH / UK-1187A')).toBeInTheDocument()
    expect(screen.getByText('Static geometry: PARTIAL')).toBeInTheDocument()
    expect(screen.getByText('Dynamics: INSUFFICIENT SAMPLING')).toBeInTheDocument()
    expect(screen.getByText('1,690.538 Å³')).toBeInTheDocument()
    expect(screen.getByText(/Scores are reproducible Vina means/)).toBeInTheDocument()
  })
})
