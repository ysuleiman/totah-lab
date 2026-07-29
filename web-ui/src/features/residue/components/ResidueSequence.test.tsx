import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ResidueAnalysis } from '../../../api/types'
import { ResidueSequence } from './ResidueSequence'

const residue = {
  id: 445,
  chain: 'A',
  residueNumber: 200,
  insertionCode: ' ',
  residueName: 'ASP',
}

const analysis: ResidueAnalysis = {
  runId: 3,
  structureId: 3,
  receptorId: 2,
  residueId: residue.id,
  chain: residue.chain,
  residueNumber: residue.residueNumber,
  residueName: residue.residueName,
  contactScoreThreshold: -5,
  scoreFilteredLigandCount: 100,
  scoreFilteredContactingLigandCount: 30,
  scoreFilteredContactingLigandFraction: 0.3,
  scoreFilteredPoseCount: 120,
  scoreFilteredContactingPoseCount: 36,
  scoreFilteredContactingPoseFraction: 0.3,
  totalLigandCount: 110,
  contactingLigandCount: 31,
  contactingLigandFraction: 31 / 110,
  totalPoseCount: 130,
  contactingPoseCount: 38,
  contactingPoseFraction: 38 / 130,
  totalGoodLigandCount: 20,
  goodContactingLigandCount: 10,
  goodContactingLigandFraction: 0.5,
  totalBadLigandCount: 10,
  badContactingLigandCount: 1,
  badContactingLigandFraction: 0.1,
  contactFractionDifference: 0.4,
  enrichmentRatio: 5,
  log2Enrichment: 2.3,
  avgContactingScore: -8.4,
  medianContactingScore: -8.2,
  bestContactingScore: -11,
  worstContactingScore: -5.1,
  closestDistance: 2.1,
  avgLigandMinDistance: 3,
  avgPoseMinDistance: 3,
}

describe('ResidueSequence', () => {
  it('encodes score-filtered contact rate without replacing other states', () => {
    render(
      <ResidueSequence
        residues={[residue]}
        pocketResidueIds={new Set([residue.id])}
        directContactResidueIds={new Set([residue.id])}
        consensusResidueIds={new Set([residue.id])}
        directConsensusResidueIds={new Set([residue.id])}
        neighborResidueIds={new Set([residue.id])}
        residueAnalysis={new Map([[residue.id, analysis]])}
        selectedResidueId={null}
        onResidueSelect={() => undefined}
      />,
    )

    const cell = screen.getByRole('listitem', {
      name: 'ASP 200, chain A',
    })
    expect(cell).toHaveClass(
      'highlighted',
      'spatial-neighbor',
      'has-docking-analysis',
      'biohub-direct-contact',
      'chosen-pocket-consensus',
      'direct-consensus',
    )
    expect(cell).toHaveStyle({ '--contact-rate': '30%' })
    expect(cell).toHaveAttribute(
      'title',
      expect.stringContaining('score < -5: 30.00% contacted ligands (30 / 100)'),
    )
  })
})
