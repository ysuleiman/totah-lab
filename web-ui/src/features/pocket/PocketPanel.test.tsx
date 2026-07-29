import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PocketPanel } from './PocketPanel'

const pockets = [
  {
    id: 3,
    pocketNumber: 1,
    source: 'FPOCKET' as const,
    volume: 100,
    score: 0.25,
    druggabilityScore: 0.5,
    probability: null,
    artifactId: 9,
    evidence: null,
  },
]

describe('PocketPanel', () => {
  it('marks the chosen pocket and emits selection', async () => {
    const onPocketSelect = vi.fn()
    render(
      <PocketPanel
        pockets={pockets}
        chosenPocketId={3}
        selectedPocketId={3}
        loading={false}
        error={null}
        onPocketSelect={onPocketSelect}
        onRetry={() => undefined}
      />,
    )

    expect(screen.getByText('Chosen')).toBeInTheDocument()
    expect(screen.getByText('Chosen pocket')).toBeInTheDocument()
    expect(screen.getByText('Inspecting now')).toBeInTheDocument()
    expect(screen.getByText('Source score')).toBeInTheDocument()
    expect(screen.getByText('Volume')).toBeInTheDocument()
    expect(screen.getByText('100.0 Å³')).toBeInTheDocument()
    expect(screen.getByText('volume 100.0 Å³')).toBeInTheDocument()
    expect(screen.getByText('druggability 0.500')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { pressed: true }))
    expect(onPocketSelect).toHaveBeenCalledWith(3)
  })

  it('returns to the chosen pocket from another inspected pocket', async () => {
    const onPocketSelect = vi.fn()
    render(
      <PocketPanel
        pockets={pockets}
        chosenPocketId={3}
        selectedPocketId={9}
        loading={false}
        error={null}
        onPocketSelect={onPocketSelect}
        onRetry={() => undefined}
      />,
    )

    await userEvent.click(
      screen.getByRole('button', { name: /Chosen pocket FPOCKET 1/i }),
    )
    expect(onPocketSelect).toHaveBeenCalledWith(3)
  })

  it('presents ligand identity and consensus for BioHub pockets', () => {
    const evidence = {
      ligandCcd: 'SAM',
      model: 'esmfold2-fast',
      shellCutoff: 6,
      directContactCutoff: 4.5,
      ptm: 0.95,
      interfacePtm: 0.981,
      shellResidueCount: 35,
      directContactResidueCount: 24,
      chosenPocketOverlapCount: 27,
      directChosenPocketOverlapCount: 21,
      shellResidueIds: [1, 2],
      directContactResidueIds: [1],
      chosenPocketOverlapResidueIds: [1],
      directChosenPocketOverlapResidueIds: [1],
    }
    render(
      <PocketPanel
        pockets={[
          {
            ...pockets[0],
            id: 105,
            source: 'BIOHUB',
            evidence,
          },
          {
            ...pockets[0],
            id: 106,
            source: 'BIOHUB',
            evidence: { ...evidence, ligandCcd: 'SAH' },
          },
        ]}
        chosenPocketId={null}
        selectedPocketId={105}
        loading={false}
        error={null}
        onPocketSelect={() => undefined}
        onRetry={() => undefined}
      />,
    )

    expect(screen.getByText('SAM-bound pocket')).toBeInTheDocument()
    expect(screen.getByText('SAH-bound pocket')).toBeInTheDocument()
    expect(screen.getAllByText('27/35 overlap with chosen fpocket'))
      .toHaveLength(2)
    expect(screen.getByText(
      'SAM and SAH predict the same wall and direct-contact residues.',
    )).toBeInTheDocument()
  })
})
