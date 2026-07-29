import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Structure } from '../../../api/types'
import { StructureHero } from './StructureHero'

const structure: Structure = {
  id: 2,
  source: 'ALPHAFOLD',
  sourceAccession: 'AF-Q6UX53-F1-model_v6',
  chain: 'A',
  modelNumber: 1,
  preparationState: 'RAW',
  parentStructureId: null,
  receptor: {
    id: 1,
    targetName: 'METTL7B',
    uniProtId: 'Q6UX53',
    proteinName: 'Thiol S-methyltransferase TMT1B',
    geneName: 'METTL7B',
    organism: 'Homo sapiens',
  },
  artifact: {
    id: 6,
    filename: 'Q6UX53.pdb',
    label: 'RAW_PDB_FILE',
    storageLocation: '/structures/Q6UX53.pdb',
  },
  chosenPocket: null,
  residues: [],
  pocketsUrl: '/api/structures/2/pockets',
}

describe('StructureHero', () => {
  it('presents the biological and model identity', () => {
    render(
      <StructureHero
        structure={structure}
        onStructureSubmit={() => undefined}
      />,
    )

    expect(screen.getByRole('heading')).toHaveTextContent(
      'Thiol S-methyltransferase TMT1B',
    )
    expect(screen.getByText('METTL7B')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'UniProt Q6UX53' }))
      .toHaveAttribute(
        'href',
        'https://www.uniprot.org/uniprotkb/Q6UX53/entry',
      )
    expect(screen.getByText('AF-Q6UX53-F1-model_v6')).toBeInTheDocument()
  })
})
