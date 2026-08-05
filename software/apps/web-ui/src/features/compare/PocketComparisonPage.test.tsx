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
    alphaSpheres: [],
    volume: 691.9,
    score: 0.5,
    druggabilityScore: 0.6,
    residueCount: 16,
    atomCount: 20,
    alphaSphereCount: 78,
  }
}

function sphereGeometryView(
  pocketId: number,
  sourceAccession: string,
): PocketComparisonDetails['query'] {
  return {
    ...geometryView(pocketId, sourceAccession),
    basis: 'ALPHA_SPHERES',
    alphaSpheres: [
      { index: 0, center: { x: 0, y: 0, z: 0 }, radius: 1.2 },
      { index: 1, center: { x: 1, y: 1, z: 1 }, radius: 0.9 },
    ],
    volume: 691.9,
    score: 0.5,
    druggabilityScore: 0.6,
    residueCount: 16,
    atomCount: 20,
    alphaSphereCount: 78,
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
  transform: {
    rotation: [
      [1, 0, 0],
      [0, 1, 0],
      [0, 0, 1],
    ],
    translation: { x: 0, y: 0, z: 0 },
  },
  residueCorrespondence: {
    matches: [
      {
        query: {
          chainId: 'A',
          residueNumber: 202,
          insertionCode: '',
          residueName: 'CYS',
          label: 'A:CYS202',
          chemistry: 'CYSTEINE',
          position: { x: 0, y: 0, z: 0 },
        },
        candidate: {
          chainId: 'B',
          residueNumber: 210,
          insertionCode: '',
          residueName: 'CYS',
          label: 'B:CYS210',
          chemistry: 'CYSTEINE',
          position: { x: 0.1, y: 0, z: 0 },
        },
        distanceAngstroms: 0.8,
        matchType: 'CONSERVATIVE',
        identicalResidue: false,
        chemistryCompatible: true,
      },
    ],
    unmatchedQuery: [],
    unmatchedCandidate: [],
    summary: {
      queryResidueCount: 2,
      candidateResidueCount: 2,
      matchedCount: 1,
      unmatchedQueryCount: 0,
      unmatchedCandidateCount: 0,
      matchedFractionQuery: 0.5,
      matchedFractionCandidate: 0.5,
      identicalFraction: 0,
      chemistryCompatibleFraction: 1,
      meanMatchedDistance: 0.8,
      maximumMatchedDistance: 0.8,
    },
  },
  keyResidues: ['CYS202'],
  chemistryAssessment: {
    chemistrySimilarity: 0.72,
    chemistryCoverageAdjustedSimilarity: 0.64,
    compatibleMatchedFraction: 0.75,
    spatialReplacementFraction: 0.25,
    identicalCount: 2,
    conservativeCount: 1,
    chemistryCompatibleCount: 1,
    spatialReplacementCount: 1,
    matchedResidueCount: 4,
    queryResidueCount: 8,
    candidateResidueCount: 9,
    keyResidueChemistrySimilarity: 0.95,
    keyMatchedCount: 2,
    classification: 'MODERATE_SIMILARITY',
    finalSimilarity: 0.81,
  },
}

const sphereDetails: PocketComparisonDetails = {
  ...details,
  query: sphereGeometryView(7, '1ABC'),
  candidate: sphereGeometryView(8, '2XYZ'),
  comparison: { ...details.comparison, basis: 'ALPHA_SPHERES' },
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
  geometricOverallSimilarity: 0.91,
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
  alphaSphereCount: 0,
  chemistrySimilarity: 0.72,
  chemistryCoverageAdjustedSimilarity: 0.64,
  compatibleMatchedFraction: 0.75,
  spatialReplacementFraction: 0.25,
  identicalCount: 2,
  conservativeCount: 1,
  chemistryCompatibleCount: 1,
  spatialReplacementCount: 1,
  matchedResidueCount: 4,
  keyResidueChemistrySimilarity: 0.95,
  classification: 'MODERATE_SIMILARITY',
  finalSimilarity: 0.81,
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

function stubFetch(compareOk: boolean, comparePayload: unknown = details) {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/compare/')) {
        return Promise.resolve(
          compareOk
            ? jsonResponse(comparePayload)
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

  it('renders the residue correspondence section when present', async () => {
    stubFetch(true)
    render(
      <PocketComparisonPage
        queryPocketId={7}
        candidatePocketId={8}
        onNavigate={() => undefined}
      />,
    )

    expect(
      await screen.findByRole('heading', {
        name: 'Residue correspondence',
      }),
    ).toBeInTheDocument()
    expect(screen.getByText('A:CYS202')).toBeInTheDocument()
    expect(screen.getByText('B:CYS210')).toBeInTheDocument()
    expect(screen.getByText('Key residue')).toBeInTheDocument()
    expect(
      screen.getByRole('checkbox', { name: 'Matched residue points' }),
    ).toBeChecked()
    // Existing viewer fallback and metrics still render.
    expect(
      await screen.findByText('3D viewer unavailable in this browser.'),
    ).toBeInTheDocument()
    expect(screen.getByText('0.910')).toBeInTheDocument()
  })

  it('omits the residue section when correspondence is null', async () => {
    stubFetch(true, { ...details, residueCorrespondence: null })
    render(
      <PocketComparisonPage
        queryPocketId={7}
        candidatePocketId={8}
        onNavigate={() => undefined}
      />,
    )

    await screen.findByRole('heading', { name: '1ABC vs 2XYZ' })
    expect(
      screen.queryByRole('heading', { name: 'Residue correspondence' }),
    ).toBeNull()
    expect(
      screen.queryByRole('checkbox', { name: 'Matched residue points' }),
    ).toBeNull()
    expect(screen.getByText('0.910')).toBeInTheDocument()
  })

  it('shows the sphere scale control for alpha-sphere geometry', async () => {
    stubFetch(true, sphereDetails)
    render(
      <PocketComparisonPage
        queryPocketId={7}
        candidatePocketId={8}
        onNavigate={() => undefined}
      />,
    )

    await screen.findByRole('heading', { name: '1ABC vs 2XYZ' })
    expect(
      screen.getByRole('slider', { name: 'Sphere scale' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/Geometry basis: Alpha spheres/),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/Geometry rendered as alpha spheres/),
    ).toBeInTheDocument()
  })

  it('renders the chemistry assessment card when present', async () => {
    stubFetch(true)
    render(
      <PocketComparisonPage
        queryPocketId={7}
        candidatePocketId={8}
        onNavigate={() => undefined}
      />,
    )

    await screen.findByRole('heading', { name: '1ABC vs 2XYZ' })
    expect(
      screen.getByRole('heading', { name: 'Chemistry assessment' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Moderate similarity'))
      .toHaveClass('cls-moderate')
    expect(screen.getByText('0.810')).toBeInTheDocument()
    expect(screen.getByText('0.720')).toBeInTheDocument()
    expect(screen.getByText('0.640')).toBeInTheDocument()
    // Compatible correspondences: 2 identical + 1 conservative
    // + 1 chemistry-compatible = 4 of 4 matched residues.
    expect(screen.getByText('4 / 4')).toBeInTheDocument()
    expect(screen.getByText('1 / 4')).toBeInTheDocument()
    expect(screen.getByText('0.950 · 2 matched')).toBeInTheDocument()
    expect(screen.getByText('4 / 8 / 9')).toBeInTheDocument()
    expect(screen.getByText('Geometric similarity score'))
      .toBeInTheDocument()
  })

  it('omits the chemistry card when chemistryAssessment is null', async () => {
    stubFetch(true, { ...details, chemistryAssessment: null })
    render(
      <PocketComparisonPage
        queryPocketId={7}
        candidatePocketId={8}
        onNavigate={() => undefined}
      />,
    )

    await screen.findByRole('heading', { name: '1ABC vs 2XYZ' })
    expect(
      screen.queryByRole('heading', { name: 'Chemistry assessment' }),
    ).toBeNull()
    expect(screen.queryByText('Moderate similarity')).toBeNull()
    expect(screen.getByText('Geometric similarity score'))
      .toBeInTheDocument()
    expect(screen.getByText('0.910')).toBeInTheDocument()
  })

  it('hides the sphere scale control for residue-atom geometry', async () => {
    stubFetch(true)
    render(
      <PocketComparisonPage
        queryPocketId={7}
        candidatePocketId={8}
        onNavigate={() => undefined}
      />,
    )

    await screen.findByRole('heading', { name: '1ABC vs 2XYZ' })
    expect(
      screen.queryByRole('slider', { name: 'Sphere scale' }),
    ).toBeNull()
    expect(
      screen.getByText(/Geometry rendered as residue heavy-atom points/),
    ).toBeInTheDocument()
  })
})
