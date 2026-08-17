import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { PocketDetails, PocketSummary, Structure } from '../../api/types'
import { StructureComparisonPage } from './StructureComparisonPage'

const structure = (id: number, gene: string, pocketId: number): Structure => ({
  id,
  source: 'ALPHAFOLD',
  sourceAccession: `AF-${gene}`,
  chain: 'A',
  modelNumber: 1,
  preparationState: 'RAW',
  parentStructureId: null,
  receptor: {
    id,
    targetName: gene,
    geneName: gene,
    proteinName: `${gene} protein`,
    uniProtId: `UP${id}`,
    organism: 'Homo sapiens',
  },
  artifact: { id, filename: `${gene}.pdb`, label: 'PDB', storageLocation: `/tmp/${gene}.pdb` },
  chosenPocket: { id: pocketId, pocketNumber: id === 2 ? 1 : 14, source: 'FPOCKET' },
  residues: Array.from({ length: 244 }, (_, index) => ({
    id: id * 1000 + index,
    chain: 'A',
    residueNumber: index + 1,
    insertionCode: '',
    residueName: 'ALA',
  })),
  pocketsUrl: `/api/structures/${id}/pockets`,
})

const pocket = (id: number, pocketNumber: number): PocketSummary => ({
  id,
  pocketNumber,
  source: 'FPOCKET',
  volume: 100 + id,
  score: 0.1,
  druggabilityScore: 0.8,
  probability: null,
  artifactId: id,
  evidence: null,
})

const pocketDetails = (id: number, pocketNumber: number, residueId: number): PocketDetails => ({
  ...pocket(id, pocketNumber),
  artifact: { id, filename: `${id}.pdb`, label: 'PDB', storageLocation: `/tmp/${id}.pdb` },
  residues: [{ id: residueId, chain: 'A', residueNumber: 1, insertionCode: '', residueName: 'ALA' }],
})

const dataByPath: Record<string, unknown> = {
  '/api/structures/2': structure(2, 'METTL7B', 21),
  '/api/structures/3': structure(3, 'METTL7A', 31),
  '/api/structures/2/pockets': [pocket(21, 1), pocket(22, 2)],
  '/api/structures/3/pockets': [pocket(31, 14), pocket(32, 15)],
  '/api/pockets/21': pocketDetails(21, 1, 2000),
  '/api/pockets/31': pocketDetails(31, 14, 3000),
}

vi.mock('../../api/hooks', () => ({
  useApiQuery: (path: string | null) => ({
    data: path ? dataByPath[path] ?? null : null,
    error: null,
    loading: false,
    retry: vi.fn(),
  }),
}))

vi.mock('../residue/hooks/useResidueEvidence', () => ({
  useResidueEvidence: () => ({ byResidueId: new Map(), loading: false }),
}))

describe('StructureComparisonPage', () => {
  it('stacks one complete residue map for each structure', () => {
    render(
      <StructureComparisonPage
        leftStructureId={2}
        rightStructureId={3}
        onNavigate={() => undefined}
      />,
    )

    expect(screen.getByRole('heading', { name: 'METTL7B' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'METTL7A' })).toBeInTheDocument()
    expect(screen.getAllByRole('list', { name: 'Structure residues' })).toHaveLength(2)
    expect(screen.getAllByRole('listitem')).toHaveLength(488)
    expect(screen.getAllByText(/Chosen FPOCKET/)).toHaveLength(2)
  })

  it('allows residues in either map to be selected without navigating', async () => {
    const user = userEvent.setup()
    const onNavigate = vi.fn()
    render(
      <StructureComparisonPage
        leftStructureId={2}
        rightStructureId={3}
        onNavigate={onNavigate}
      />,
    )

    const residues = screen.getAllByRole('listitem')
    await user.click(residues[0])
    await user.click(residues[244])
    expect(residues[0]).toHaveAttribute('aria-pressed', 'true')
    expect(residues[244]).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getAllByRole('slider', { name: 'Neighbor cutoff' }))
      .toHaveLength(1)
    expect(screen.queryByText('ATOM DISTANCE')).not.toBeInTheDocument()
    expect(onNavigate).not.toHaveBeenCalled()
  })

  it('uses one shared neighbor cutoff for both maps', () => {
    render(
      <StructureComparisonPage
        leftStructureId={2}
        rightStructureId={3}
        onNavigate={() => undefined}
      />,
    )

    const slider = screen.getByRole('slider', { name: 'Neighbor cutoff' })
    fireEvent.input(slider, { target: { value: '12' } })

    expect(slider).toHaveValue('12')
    expect(screen.getByText('12.0 Å')).toBeInTheDocument()
  })
})
