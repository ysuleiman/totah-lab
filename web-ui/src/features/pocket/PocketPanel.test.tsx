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
    druggabilityScore: 0.5,
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
    await userEvent.click(screen.getByRole('button', { pressed: true }))
    expect(onPocketSelect).toHaveBeenCalledWith(3)
  })
})
