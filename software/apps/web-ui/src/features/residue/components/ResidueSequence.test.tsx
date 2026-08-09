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
  chemistry: {
    categories: ['NEGATIVELY_CHARGED'],
    primaryCategory: 'NEGATIVELY_CHARGED' as const,
    primaryLabel: 'Negative',
    colorKey: 'NEGATIVELY_CHARGED' as const,
  },
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
    )
    expect(cell).toHaveStyle({ '--contact-rate': '30%' })
    expect(cell).toHaveAttribute(
      'title',
      expect.stringContaining('score < -5: 30.00% contacted ligands (30 / 100)'),
    )
  })

  it('keeps fpocket green and marks only direct contacts outside it red', () => {
    const directOutsideResidue = { ...residue, id: 446, residueNumber: 201 }
    const wallOutsideResidue = { ...residue, id: 447, residueNumber: 202 }
    render(
      <ResidueSequence
        residues={[residue, directOutsideResidue, wallOutsideResidue]}
        pocketResidueIds={new Set([
          residue.id,
          directOutsideResidue.id,
          wallOutsideResidue.id,
        ])}
        chosenPocketResidueIds={new Set([residue.id])}
        biohubSelected
        directContactResidueIds={new Set([
          residue.id,
          directOutsideResidue.id,
        ])}
        neighborResidueIds={new Set()}
        residueAnalysis={new Map()}
        selectedResidueId={null}
        onResidueSelect={() => undefined}
      />,
    )

    expect(screen.getByRole('listitem', {
      name: 'ASP 200, chain A',
    })).toHaveClass('highlighted', 'biohub-direct-contact')
    expect(screen.getByRole('listitem', {
      name: 'ASP 201, chain A',
    })).toHaveClass('biohub-only', 'biohub-direct-contact')
    expect(screen.getByRole('listitem', {
      name: 'ASP 202, chain A',
    })).not.toHaveClass('highlighted', 'biohub-only', 'biohub-direct-contact')
  })

  it('tints non-contact pocket residues by category when enabled', () => {
    const pocketResidue = { ...residue, id: 448, residueNumber: 203 }
    const contactResidue = { ...residue, id: 449, residueNumber: 204 }
    render(
      <ResidueSequence
        residues={[pocketResidue, contactResidue]}
        pocketResidueIds={new Set([pocketResidue.id, contactResidue.id])}
        chosenPocketResidueIds={new Set([
          pocketResidue.id,
          contactResidue.id,
        ])}
        ligandContactResidueIds={new Set([contactResidue.id])}
        colorPocketByCategory
        neighborResidueIds={new Set()}
        residueAnalysis={new Map()}
        selectedResidueId={null}
        onResidueSelect={() => undefined}
      />,
    )

    const tinted = screen.getByRole('listitem', {
      name: 'ASP 203, chain A',
    })
    expect(tinted).toHaveClass('category')
    expect(tinted).toHaveStyle({ '--category-color': '#8a4fbf' })
    expect(tinted).toHaveAttribute('title', expect.stringContaining('Negative'))
    const contact = screen.getByRole('listitem', {
      name: 'ASP 204, chain A',
    })
    expect(contact).toHaveClass('ligand-contact-inside')
    expect(contact).not.toHaveClass('category')
  })
})
