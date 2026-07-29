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
})
