import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type {
  PocketComparisonDetails,
  PocketSimilarityDiagnosticRow,
} from '../../api/types'
import { PocketComparisonPage } from './PocketComparisonPage'

function geometryView(
  pocketId: number,
  sourceAccession: string,
): PocketComparisonDetails['query'] {
  return {
    pocketId,
    structureId: 5,
    sourceAccession,
    pocketNumber: 1,
    pointCount: 2,
    centroid: { x: 0, y: 0, z: 0 },
    bounds: { min: { x: 0, y: 0, z: 0 }, max: { x: 1, y: 1, z: 1 } },
    basis: 'RESIDUE_ATOMS',
    points: [
      { x: 0, y: 0, z: 0 },
      { x: 1, y: 1, z: 1 },
    ],
  }
}

const details: PocketComparisonDetails = {
  query: geometryView(7, '1ABC'),
  candidate: geometryView(8, '2XYZ'),
  alignedQueryPoints: [
    { x: 0, y: 0, z: 0 },
    { x: 1, y: 1, z: 1 },
  ],
  alignedCandidatePoints: [
    { x: 0.1, y: 0, z: 0 },
    { x: 1.1, y: 1, z: 1 },
  ],
  comparison: {
    overallSimilarity: 0.91,
    geometrySimilarity: 0.88,
    sizeSimilarity: 0.97,
    queryCoverage: 0.8,
    candidateCoverage: 0.77,
    queryToCandidateMeanDistance: 1.05,
    candidateToQueryMeanDistance: 1.15,
    meanBidirectionalDistance: 1.1,
    maximumNearestNeighborDistance: 2.4,
    queryPointCount: 20,
    candidatePointCount: 21,
    basis: 'RESIDUE_ATOMS',
  },
  aligner: 'PCA_ICP',
}

const diagnosticRow: PocketSimilarityDiagnosticRow = {
  pocketId: 8,
  structureId: 5,
  sourceAccession: '2XYZ',
  pocketNumber: 1,
  stageOneRank: 3,
  descriptorDistance: 0.1,
  volumeDistance: 0.1,
  residueDistance: 0.1,
  chemistryDistance: 0.1,
  stageTwoRank: 2,
  shapeDistance: 0.2,
  stageThreeRank: 1,
  overallSimilarity: 0.91,
  geometrySimilarity: 0.88,
  sizeSimilarity: 0.97,
  queryCoverage: 0.8,
  candidateCoverage: 0.77,
  queryToCandidateMeanDistance: 1.05,
  candidateToQueryMeanDistance: 1.15,
  meanBidirectionalDistance: 1.1,
  maximumNearestNeighborDistance: 2.4,
  queryPointCount: 20,
  candidatePointCount: 21,
  basis: 'RESIDUE_ATOMS',
  uniProtId: 'P12345',
  proteinName: 'Test protein',
  geneName: 'TST',
  organism: 'Homo sapiens',
}

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }
}

function stubFetch(compareOk: boolean) {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/compare/')) {
        return Promise.resolve(
          compareOk
            ? jsonResponse(details)
            : jsonResponse({ message: 'boom' }, 500),
        )
      }
      if (url.includes('/similar/diagnostic')) {
        return Promise.resolve(jsonResponse([diagnosticRow]))
      }
      return Promise.resolve(jsonResponse(null, 404))
    }),
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('PocketComparisonPage', () => {
  it('renders query and candidate metadata with metrics', async () => {
    stubFetch(true)
    render(
      <PocketComparisonPage
        queryPocketId={7}
        candidatePocketId={8}
        onNavigate={() => undefined}
      />,
    )

    expect(
      await screen.findByRole('heading', { name: '1ABC vs 2XYZ' }),
    ).toBeInTheDocument()
    expect(screen.getByText('0.910')).toBeInTheDocument()
    expect(screen.getByText('1.10 Å')).toBeInTheDocument()
    expect(screen.getByText('Stage 1 rank')).toBeInTheDocument()
  })

  it('labels the basis honestly and shows the active aligner', async () => {
    stubFetch(true)
    render(
      <PocketComparisonPage
        queryPocketId={7}
        candidatePocketId={8}
        onNavigate={() => undefined}
      />,
    )

    await screen.findByRole('heading', { name: '1ABC vs 2XYZ' })
    expect(screen.getAllByText(/Residue heavy atoms/).length)
      .toBeGreaterThan(0)
    expect(screen.getByText(/Aligner: PCA_ICP/)).toBeInTheDocument()
    expect(
      screen.getByText(/overallSimilarity combines geometry similarity/),
    ).toBeInTheDocument()
  })

  it('flips the viewer checkboxes and exposes the alignment slider', async () => {
    stubFetch(true)
    render(
      <PocketComparisonPage
        queryPocketId={7}
        candidatePocketId={8}
        onNavigate={() => undefined}
      />,
    )

    await screen.findByRole('heading', { name: '1ABC vs 2XYZ' })

    const queryToggle = screen.getByRole('checkbox', { name: 'Query' })
    const originalToggle = screen.getByRole('checkbox', {
      name: 'Original candidate',
    })
    const alignedToggle = screen.getByRole('checkbox', {
      name: 'Aligned candidate',
    })

    expect(queryToggle).toBeChecked()
    expect(originalToggle).not.toBeChecked()
    expect(alignedToggle).toBeChecked()

    await userEvent.click(queryToggle)
    await userEvent.click(originalToggle)
    expect(queryToggle).not.toBeChecked()
    expect(originalToggle).toBeChecked()

    expect(
      screen.getByRole('slider', { name: /Alignment/ }),
    ).toBeInTheDocument()
  })

  it('renders the viewer fallback when WebGL is unavailable', async () => {
    stubFetch(true)
    render(
      <PocketComparisonPage
        queryPocketId={7}
        candidatePocketId={8}
        onNavigate={() => undefined}
      />,
    )

    expect(
      await screen.findByText('3D viewer unavailable in this browser.'),
    ).toBeInTheDocument()
  })

  it('shows a visible error state when the request fails', async () => {
    stubFetch(false)
    render(
      <PocketComparisonPage
        queryPocketId={7}
        candidatePocketId={8}
        onNavigate={() => undefined}
      />,
    )

    expect(await screen.findByText('Comparison unavailable'))
      .toBeInTheDocument()
    expect(screen.getByText('Request failed with status 500'))
      .toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' }))
      .toBeInTheDocument()
  })
})
