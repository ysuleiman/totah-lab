import { render, screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import { LigandDepiction } from './LigandDepiction'

const parse = vi.fn()

vi.mock('smiles-drawer', () => ({
  default: {
    SvgDrawer: class {
      draw() {
        return document.createElementNS('http://www.w3.org/2000/svg', 'svg')
      }
    },
    parse: (...args: unknown[]) => parse(...args),
  },
}))

test('renders the parsed structure as SVG', () => {
  parse.mockImplementationOnce(
    (_smiles: string, onSuccess: (tree: unknown) => void) => onSuccess({}),
  )
  const { container } = render(
    <LigandDepiction smiles="CCO" label="MCULE-1" />,
  )

  expect(parse).toHaveBeenCalledWith(
    'CCO',
    expect.any(Function),
    expect.any(Function),
  )
  expect(container.querySelector('svg')).not.toBeNull()
  expect(
    screen.getByRole('img', { name: '2D structure of MCULE-1' }),
  ).toBeInTheDocument()
})

test('falls back to the raw SMILES when parsing fails', () => {
  parse.mockImplementationOnce(
    (
      _smiles: string,
      _onSuccess: (tree: unknown) => void,
      onError: (error: Error) => void,
    ) => onError(new Error('bad smiles')),
  )
  render(<LigandDepiction smiles="not-a-smiles" label="MCULE-2" />)

  expect(screen.getByText('not-a-smiles')).toBeInTheDocument()
})
