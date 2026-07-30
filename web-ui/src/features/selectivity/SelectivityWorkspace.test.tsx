import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import { SelectivityWorkspace } from './SelectivityWorkspace'

vi.mock('../../api/hooks', () => ({
  useApiQuery: () => ({
    loading: false,
    error: null,
    retry: vi.fn(),
    data: {
      items: [{
        ligandId: 'compact-id',
        ligandLabel: 'MCULE-1',
        score7b: -9.2,
        score7a: -7,
        delta: 2.2,
        runId7b: 11,
        runId7a: 12,
        poseId7b: 101,
        poseId7a: 102,
      }],
      total: 1,
      page: 0,
      size: 50,
      sortBy: 'delta',
      direction: 'desc',
    },
  }),
}))

test('shows paired scores and explains positive delta', async () => {
  const user = userEvent.setup()
  render(<SelectivityWorkspace />)

  expect(screen.getByText('MCULE-1')).toBeInTheDocument()
  expect(screen.getByText('-9.200')).toBeInTheDocument()
  expect(screen.getByText('+2.200')).toBeInTheDocument()
  expect(screen.getByText('Positive = 7B favored')).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: 'Sort by METTL7B' }))
})
