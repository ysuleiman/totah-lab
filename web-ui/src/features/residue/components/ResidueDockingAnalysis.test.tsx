import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type {
  ResidueAnalysis,
  ResidueScoreBand,
} from '../../../api/types'
import { ResidueDockingAnalysis } from './ResidueDockingAnalysis'

const analysis: ResidueAnalysis = {
  runId: 3,
  structureId: 3,
  receptorId: 2,
  residueId: 445,
  chain: 'A',
  residueNumber: 200,
  residueName: 'ASP',
  totalLigandCount: 9999,
  contactingLigandCount: 9724,
  contactingLigandFraction: 0.9724972497249725,
  totalPoseCount: 9999,
  contactingPoseCount: 9724,
  contactingPoseFraction: 0.9724972497249725,
  totalGoodLigandCount: 9613,
  goodContactingLigandCount: 9400,
  goodContactingLigandFraction: 0.9778425059814834,
  totalBadLigandCount: 1,
  badContactingLigandCount: 1,
  badContactingLigandFraction: 1,
  contactFractionDifference: -0.0221574940185166,
  enrichmentRatio: 1,
  log2Enrichment: 0,
  avgContactingScore: -10.2,
  medianContactingScore: -10.1,
  bestContactingScore: -13.6,
  worstContactingScore: -5.9,
  closestDistance: 1.9,
  avgLigandMinDistance: 3.1,
  avgPoseMinDistance: 3.1,
}

const bands: ResidueScoreBand[] = [{
  runId: 3,
  structureId: 3,
  receptorId: 2,
  scoreLower: -12,
  scoreUpper: -10,
  residueId: 445,
  chain: 'A',
  residueNumber: 200,
  residueName: 'ASP',
  ligandCount: 7043,
  contactingLigandCount: 6900,
  contactingLigandFraction: 0.979696152207866,
  poseCount: 7043,
  contactingPoseCount: 6900,
  contactingPoseFraction: 0.979696152207866,
  avgContactingScore: -10.7,
  medianContactingScore: -10.6,
  bestContactingScore: -11.9,
  worstContactingScore: -10,
  closestDistance: 1.9,
  avgLigandMinDistance: 3,
  avgPoseMinDistance: 3,
}]

describe('ResidueDockingAnalysis', () => {
  it('presents database-provided ligand and score-band contact rates', () => {
    render(
      <ResidueDockingAnalysis
        analysis={analysis}
        analysisLoading={false}
        bands={bands}
        bandsLoading={false}
      />,
    )

    expect(screen.getByLabelText('Docking contacts'))
      .toHaveTextContent('97.25%')
    expect(screen.getAllByText('9,724 / 9,999')).toHaveLength(2)
    expect(screen.getByText('-12 to -10')).toBeInTheDocument()
    expect(screen.getByText('6,900 / 7,043')).toBeInTheDocument()
  })
})
