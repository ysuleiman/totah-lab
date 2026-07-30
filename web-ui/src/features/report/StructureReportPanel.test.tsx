import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { StructureReport } from '../../api/types'
import { StructureReportPanel } from './StructureReportPanel'

const report: StructureReport = {
  structureId: 2,
  title: 'TMT1B structure report',
  generatedAt: '2026-07-29T17:00:00Z',
  uniProtId: 'Q6UX53',
  geneName: 'METTL7B',
  proteinName: 'TMT1B',
  chosenPocket: {
    id: 1,
    source: 'FPOCKET',
    pocketNumber: 2,
    score: 0.003,
    druggabilityScore: 0.832,
    volume: 1690.538,
    residueCount: 2,
  },
  chosenPocketResidues: [
    {
      id: 76,
      chain: 'A',
      residueNumber: 76,
      insertionCode: '',
      residueName: 'GLU',
      oneLetterCode: 'E',
    },
  ],
  ligandEvidence: [{
    ligandCcd: 'SAM',
    model: 'esmfold2-fast',
    ptm: 0.94,
    interfacePtm: 0.98,
    strongContactCutoff: 4,
    directContactCutoff: 4.5,
    contextCutoff: 6,
    strongContactCount: 1,
    nearContactCount: 1,
    directContactCount: 2,
    contextResidueCount: 3,
    directChosenPocketOverlapCount: 1,
    outsideDirectContactCount: 1,
    residues: [{
      id: 100,
      chain: 'A',
      residueNumber: 100,
      residueName: 'ASN',
      oneLetterCode: 'N',
      minimumDistance: 3.89,
      contactingAtomPairCount: 12,
      classification: 'STRONG',
      directContact: true,
      chosenPocketMember: false,
    }],
  }],
  narrative: 'The chosen pocket is unchanged.',
}

describe('StructureReportPanel', () => {
  it('renders numbered sequence and residue-level ligand evidence', () => {
    render(
      <StructureReportPanel
        report={report}
        loading={false}
        error={null}
        onClose={() => undefined}
        onRetry={() => undefined}
      />,
    )

    expect(screen.getByTitle('A:76 GLU (E)')).toHaveTextContent('E76')
    expect(screen.getByText('A:100 ASN')).toBeInTheDocument()
    expect(screen.getByText('N100')).toBeInTheDocument()
    expect(screen.getByText('3.89 Å')).toBeInTheDocument()
    expect(screen.getByText('strong')).toBeInTheDocument()
    expect(screen.getByText('No')).toBeInTheDocument()
  })
})
