import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { PocketReportDocument } from '../../api/types'
import { PocketReportPanel } from './PocketReportPanel'

const document: PocketReportDocument = {
  report: {
    data: {
      pocketId: 1,
      pocketName: 'FPOCKET pocket 2',
      source: 'FPOCKET',
      geometry: {
        estimatedVolumeAngstrom3: 1578.3,
        druggabilityScore: 0.82,
      },
      residues: {
        totalResidues: 1,
        residues: [],
      },
      docking: {
        runId: 7,
        totalLigandCount: 1000,
        totalPoseCount: 2000,
        contactScoreThreshold: -5,
        residues: [{
          residueId: 103,
          chain: 'A',
          residueNumber: 103,
          residueName: 'PHE',
          contactingLigandCount: 824,
          contactingLigandFraction: 0.824,
          contactingPoseCount: 1200,
          contactingPoseFraction: 0.6,
          scoreFilteredContactingLigandFraction: 0.913,
          enrichmentRatio: 1.31,
          closestDistance: 3.6,
        }],
      },
      hotspots: {},
    },
    evidence: [],
  },
  narrative: {
    executiveSummary: 'Evidence-linked pocket summary.',
    findings: [{
      statement: 'PHE103 is the contact-frequency leader.',
      type: 'OBSERVATION',
      confidence: 'HIGH',
      evidenceIds: ['H-001'],
    }],
    limitations: 'Docking does not establish affinity.',
    conclusions: 'PHE103 is a descriptive candidate.',
  },
}

describe('PocketReportPanel', () => {
  it('renders database metrics and the run-scoped PDF link', () => {
    render(
      <PocketReportPanel
        document={document}
        pocketId={1}
        runId={7}
        loading={false}
        error={null}
        onClose={() => undefined}
        onRetry={() => undefined}
      />,
    )

    expect(screen.getByText('Evidence-linked pocket summary.'))
      .toBeInTheDocument()
    expect(screen.getByText('A:PHE103')).toBeInTheDocument()
    expect(screen.getByText('82.4%')).toBeInTheDocument()
    expect(screen.getByText('91.3%')).toBeInTheDocument()
    expect(screen.getByText('1.310×')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Download PDF' }))
      .toHaveAttribute('href', '/api/pockets/1/report.pdf?runId=7')
    expect(screen.getByRole('link', { name: 'Download for Google Docs' }))
      .toHaveAttribute('href', '/api/pockets/1/report.docx?runId=7')
  })
})
