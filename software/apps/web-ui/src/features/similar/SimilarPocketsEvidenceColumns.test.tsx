import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type {
  PocketGeometryView,
  PocketSimilarityDiagnosticRow,
} from '../../api/types'
import { SimilarPocketsPage } from './SimilarPocketsPage'

const geometry: PocketGeometryView = {
  pocketId: 7,
  structureId: 3,
  sourceAccession: '1ABC',
  pocketNumber: 2,
  pointCount: 20,
  centroid: { x: 0, y: 0, z: 0 },
  bounds: { min: { x: 0, y: 0, z: 0 }, max: { x: 1, y: 1, z: 1 } },
  basis: 'RESIDUE_ATOMS',
  points: [],
  alphaSpheres: [],
  volume: 691.9,
  score: 0.5,
  druggabilityScore: 0.6,
  residueCount: 16,
  atomCount: 20,
  alphaSphereCount: 78,
}

function makeRow(
  overrides: Partial<PocketSimilarityDiagnosticRow> = {},
): PocketSimilarityDiagnosticRow {
  return {
    pocketId: 1000,
    structureId: 5,
    sourceAccession: '2XYZ',
    pocketNumber: 1,
    stageOneRank: 1,
    descriptorDistance: 0.1,
    volumeDistance: 0.1,
    residueDistance: 0.1,
    chemistryDistance: 0.1,
    stageTwoRank: 1,
    shapeDistance: 0.2,
    stageThreeRank: 1,
    geometricOverallSimilarity: 0.9,
    geometrySimilarity: 0.85,
    sizeSimilarity: 0.95,
    queryCoverage: 0.8,
    candidateCoverage: 0.75,
    queryToCandidateMeanDistance: 1.1,
    candidateToQueryMeanDistance: 1.2,
    meanBidirectionalDistance: 1.15,
    maximumNearestNeighborDistance: 2.5,
    queryPointCount: 20,
    candidatePointCount: 21,
    basis: 'RESIDUE_ATOMS',
    alphaSphereCount: 0,
    alignmentInitialization: 'SEQUENCE_SEEDED_KABSCH',
    chemistrySimilarity: 0.8,
    chemistryCoverageAdjustedSimilarity: 0.7,
    compatibleMatchedFraction: 0.6,
    spatialReplacementFraction: 0.2,
    identicalCount: 3,
    conservativeCount: 2,
    chemistryCompatibleCount: 1,
    spatialReplacementCount: 2,
    matchedResidueCount: 10,
    keyResidueChemistrySimilarity: 0.9,
    classification: 'STRONG_SIMILARITY',
    finalSimilarity: 0.88,
    uniProtId: 'P12345',
    proteinName: 'Test protein',
    geneName: 'TST',
    organism: 'Homo sapiens',
    provenance: 'GLOBAL_SHAPE',
    pocketMatchQueryCoverage: null,
    pocketMatchRank: null,
    pocketMatchSymmetricRank: null,
    pocketMatchQueryCoverageRank: null,
    candidateSources: ['GLOBAL_SHAPE'],
    assessment: null,
    ...overrides,
  }
}

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }
}

function stubFetch(rows: PocketSimilarityDiagnosticRow[]) {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/similar/diagnostic')) {
        return Promise.resolve(jsonResponse(rows))
      }
      if (url.includes('/geometry')) {
        return Promise.resolve(jsonResponse(geometry))
      }
      return Promise.resolve(jsonResponse(null, 404))
    }),
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('SimilarPocketsPage evidence columns', () => {
  it('renders the retrieval and assessment columns', async () => {
    stubFetch([
      makeRow({
        candidateSources: ['GLOBAL_SHAPE', 'POCKET_MATCH'],
        stageOneRank: 3,
        pocketMatchRank: 5,
        assessment: 'STRONG_FUNCTIONAL_MATCH',
      }),
    ])
    render(
      <SimilarPocketsPage pocketId={7} onNavigate={() => undefined} />,
    )

    expect(
      await screen.findByRole('button', { name: 'Chosen' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Sources' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Global rank' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'PocketMatch rank' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Residue identity' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Assessment' }),
    ).toBeInTheDocument()

    expect(screen.getByText('No')).toBeInTheDocument()
    expect(
      screen.getByText('GLOBAL_SHAPE, POCKET_MATCH'),
    ).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByText('3 / 10')).toBeInTheDocument()
    expect(screen.getByText('0.800')).toBeInTheDocument()
    expect(
      screen.getByText('Strong functional match'),
    ).toBeInTheDocument()
  })

  it('marks chosen candidates and keeps null fields honest', async () => {
    stubFetch([
      makeRow({
        pocketId: 1001,
        sourceAccession: 'CHOS1',
        candidateSources: ['CHOSEN_REFERENCE'],
        stageOneRank: 0,
        pocketMatchRank: null,
        matchedResidueCount: 0,
        identicalCount: 0,
        assessment: 'CONFLICTING_EVIDENCE',
      }),
    ])
    render(
      <SimilarPocketsPage pocketId={7} onNavigate={() => undefined} />,
    )

    expect(await screen.findByText('Yes')).toBeInTheDocument()
    expect(screen.getByText('CHOSEN_REFERENCE')).toBeInTheDocument()
    expect(
      screen.getByText('Conflicting evidence'),
    ).toBeInTheDocument()
    // No global rank, no PocketMatch rank, no residue matches: all
    // rendered as an honest placeholder, never invented.
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(3)
  })

  it('filters the table to a single geometry basis', async () => {
    stubFetch([
      makeRow({
        pocketId: 1001,
        sourceAccession: 'ATOMS1',
        basis: 'RESIDUE_ATOMS',
      }),
      makeRow({
        pocketId: 1002,
        sourceAccession: 'SPHERE',
        basis: 'ALPHA_SPHERES',
        alphaSphereCount: 42,
      }),
    ])
    render(
      <SimilarPocketsPage pocketId={7} onNavigate={() => undefined} />,
    )

    expect(await screen.findByText('ATOMS1')).toBeInTheDocument()
    expect(screen.getByText('SPHERE')).toBeInTheDocument()

    await userEvent.selectOptions(
      screen.getByRole('combobox'),
      'ALPHA_SPHERES',
    )

    expect(screen.queryByText('ATOMS1')).not.toBeInTheDocument()
    expect(screen.getByText('SPHERE')).toBeInTheDocument()
  })
})
