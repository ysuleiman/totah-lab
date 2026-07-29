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

afterEach(() => vi.restoreAllMocks())

describe('ResiduePanel', () => {
  it('opens artifact-backed neighbors for a compact residue', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({
        selectedResidue: residue,
        cutoff: 6,
        neighbors: [{
          id: 203,
          chain: 'A',
          residueNumber: 203,
          insertionCode: ' ',
          residueName: 'ASN',
          distance: 3.2,
        }],
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    render(
      <ResiduePanel
        structureId={2}
        residues={[residue]}
        highlightedResidueIds={new Set([202])}
        activePocket={null}
        pocketLoading={false}
      />,
    )

    const residueButton = screen.getByRole('listitem', {
      name: 'CYS 202, chain A',
    })
    expect(residueButton).toHaveTextContent('C')
    await userEvent.click(residueButton)

    expect(await screen.findByText('3.20 Å')).toBeInTheDocument()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/structures/2/residues/202/neighbors?cutoff=6',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    ))
  })
})
