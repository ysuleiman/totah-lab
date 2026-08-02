import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ResiduePanel } from './ResiduePanel'

const residue = {
  id: 202,
  chain: 'A',
  residueNumber: 202,
  insertionCode: ' ',
  residueName: 'CYS',
}
const neighborResidue = {
  id: 203,
  chain: 'A',
  residueNumber: 203,
  insertionCode: ' ',
  residueName: 'ASN',
}

afterEach(() => vi.restoreAllMocks())

describe('ResiduePanel', () => {
  it('opens artifact-backed neighbors for a compact residue', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(
      async (input) => {
        const url = String(input)
        const body = url.includes('/distance?')
          ? {
              firstResidue: residue,
              firstAtom: 'SG',
              secondResidue: neighborResidue,
              secondAtom: 'SG',
              distance: 4.8,
            }
          : {
              selectedResidue: residue,
              selectedAtomNames: ['CA', 'CB', 'SG'],
              cutoff: 6,
              neighbors: [{
                ...neighborResidue,
                residueName: 'CYS',
                atomNames: ['CA', 'CB', 'SG'],
                distance: 3.2,
              }],
            }
        return new Response(JSON.stringify(body), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      },
    )

    render(
      <ResiduePanel
        structureId={2}
        residues={[residue, neighborResidue]}
        highlightedResidueIds={new Set([202])}
        activePocket={null}
        pocketLoading={false}
        dockingRuns={[]}
        selectedRunId={null}
        onRunSelect={() => undefined}
        residueAnalysis={new Map()}
        analysisLoading={false}
      />,
    )

    const residueButton = screen.getByRole('listitem', {
      name: 'CYS 202, chain A',
    })
    expect(residueButton).toHaveTextContent('C')
    await userEvent.click(residueButton)

    expect(await screen.findByText('3.20 Å')).toBeInTheDocument()
    expect(screen.getByRole('listitem', { name: 'ASN 203, chain A' }))
      .toHaveClass('spatial-neighbor')
    expect(await screen.findByText('4.80 Å')).toBeInTheDocument()
    expect(screen.getByLabelText('Selected residue atom')).toHaveValue('SG')
    expect(screen.getByLabelText('Neighbor residue atom')).toHaveValue('SG')
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/structures/2/residues/202/neighbors?cutoff=6',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    ))
  })
})
