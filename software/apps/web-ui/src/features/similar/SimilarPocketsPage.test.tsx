import { render, screen, within } from '@testing-library/react'
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

function stubFetch(rows: PocketSimilarityDiagnosticRow[] | null) {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/similar/diagnostic')) {
        return Promise.resolve(
          rows === null
            ? jsonResponse({ message: 'boom' }, 500)
            : jsonResponse(rows),
        )
      }
      if (url.includes('/geometry')) {
        return Promise.resolve(jsonResponse(geometry))
      }
      return Promise.resolve(jsonResponse(null, 404))
    }),
  )
}

function makeRankedRows(count: number): PocketSimilarityDiagnosticRow[] {
  return Array.from({ length: count }, (_, index) =>
    makeRow({
      pocketId: 2000 - index,
      stageThreeRank: 101 + index,
    }))
}

afterEach(() => vi.unstubAllGlobals())

describe('SimilarPocketsPage', () => {
  it('renders the query identity and diagnostic rows', async () => {
    stubFetch([makeRow()])
    render(<SimilarPocketsPage pocketId={7} onNavigate={() => undefined} />)

    expect(
      await screen.findByRole('heading', { name: '1ABC' }),
    ).toBeInTheDocument()
    expect(screen.getByText('1 candidates')).toBeInTheDocument()
    expect(screen.getByText('2XYZ')).toBeInTheDocument()
    expect(screen.getAllByText('Residue heavy atoms').length)
      .toBeGreaterThan(0)
    expect(screen.getByText('P12345')).toBeInTheDocument()
    // Final-rank cell exposes the alignment initialization as a tooltip.
    expect(screen.getByTitle('sequence-seeded Kabsch'))
      .toHaveTextContent('1')
  })

  it('paginates without refetching and keeps server rank values', async () => {
    stubFetch(makeRankedRows(25))
    render(<SimilarPocketsPage pocketId={7} onNavigate={() => undefined} />)

    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    expect(screen.getByText('101')).toBeInTheDocument()
    expect(screen.queryByText('121')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument()
    expect(screen.getByText('121')).toBeInTheDocument()
    expect(screen.queryByText('101')).not.toBeInTheDocument()
  })

  it('re-sorts on column click while displaying ranks unchanged', async () => {
    stubFetch(makeRankedRows(25))
    render(<SimilarPocketsPage pocketId={7} onNavigate={() => undefined} />)

    const firstRow = () => screen.getAllByRole('row')[1]
    expect((await screen.findByText('101')).closest('tr'))
      .toBe(firstRow())

    await userEvent.click(
      screen.getByRole('button', { name: 'Pocket ID' }),
    )
    // Pocket IDs run opposite to rank order, so rank 125 now leads,
    // still displayed with its original server value.
    expect(within(firstRow()).getByText('125')).toBeInTheDocument()
    expect(within(firstRow()).getByText('1976')).toBeInTheDocument()
  })

  it('narrows rows with the UniProt filter', async () => {
    stubFetch([
      makeRow({ pocketId: 1000, sourceAccession: '2AAA', uniProtId: 'P12345' }),
      makeRow({ pocketId: 1001, sourceAccession: '2BBB', uniProtId: 'Q99999' }),
    ])
    render(<SimilarPocketsPage pocketId={7} onNavigate={() => undefined} />)

    expect(await screen.findByText('2AAA')).toBeInTheDocument()
    expect(screen.getByText('2BBB')).toBeInTheDocument()

    await userEvent.type(
      screen.getByLabelText('UniProt accession'),
      'p123',
    )
    expect(screen.getByText('2AAA')).toBeInTheDocument()
    expect(screen.queryByText('2BBB')).not.toBeInTheDocument()
  })

  it('groups rows by protein and shows the best pocket per group', async () => {
    stubFetch([
      makeRow({
        pocketId: 1000,
        stageThreeRank: 101,
        uniProtId: 'P12345',
        sourceAccession: '2AAA',
      }),
      makeRow({
        pocketId: 1001,
        stageThreeRank: 102,
        uniProtId: 'P12345',
        sourceAccession: '2AAB',
      }),
      makeRow({
        pocketId: 1002,
        stageThreeRank: 103,
        uniProtId: 'Q99999',
        sourceAccession: '2BBB',
      }),
    ])
    render(<SimilarPocketsPage pocketId={7} onNavigate={() => undefined} />)

    await screen.findByText('2AAA')
    await userEvent.click(screen.getByRole('button', { name: 'Proteins' }))

    expect(screen.getByRole('heading', { name: 'P12345' }))
      .toBeInTheDocument()
    expect(screen.getByText('2 pockets')).toBeInTheDocument()
    expect(screen.getByText('1 pocket')).toBeInTheDocument()

    const group = screen.getByRole('heading', { name: 'P12345' })
      .closest('section')
    expect(group).not.toBeNull()
    expect(within(group as HTMLElement).getByText('Best final rank'))
      .toBeInTheDocument()
    expect(within(group as HTMLElement).getByText('101', { selector: 'dd' }))
      .toBeInTheDocument()
  })

  it('navigates to the comparison of the best pocket in a group', async () => {
    const onNavigate = vi.fn()
    stubFetch([
      makeRow({
        pocketId: 1000,
        stageThreeRank: 101,
        uniProtId: 'P12345',
        sourceAccession: '2AAA',
      }),
      makeRow({
        pocketId: 1001,
        stageThreeRank: 102,
        uniProtId: 'P12345',
        sourceAccession: '2AAB',
      }),
    ])
    render(<SimilarPocketsPage pocketId={7} onNavigate={onNavigate} />)

    await screen.findByText('2AAA')
    await userEvent.click(screen.getByRole('button', { name: 'Proteins' }))
    await userEvent.click(
      screen.getByRole('button', { name: 'Inspect best match' }),
    )
    expect(onNavigate).toHaveBeenCalledWith('/pockets/7/compare/1000')
  })

  it('shows the alpha sphere count for sphere pockets and a dash otherwise', async () => {
    stubFetch([
      makeRow({
        pocketId: 1000,
        sourceAccession: '2AAA',
        basis: 'ALPHA_SPHERES',
        alphaSphereCount: 42,
      }),
      makeRow({
        pocketId: 1001,
        sourceAccession: '2BBB',
        basis: 'RESIDUE_ATOMS',
        alphaSphereCount: 0,
      }),
    ])
    render(<SimilarPocketsPage pocketId={7} onNavigate={() => undefined} />)

    const sphereRow = (await screen.findByText('2AAA')).closest('tr')
    expect(screen.getByRole('button', { name: 'Alpha spheres' }))
      .toBeInTheDocument()
    expect(sphereRow).not.toBeNull()
    expect(within(sphereRow as HTMLElement).getByText('42'))
      .toBeInTheDocument()

    const residueRow = screen.getByText('2BBB').closest('tr')
    expect(residueRow).not.toBeNull()
    expect(within(residueRow as HTMLElement).getAllByText('—').length)
      .toBeGreaterThanOrEqual(1)
  })

  it('narrows rows with the geometry basis filter', async () => {
    stubFetch([
      makeRow({
        pocketId: 1000,
        sourceAccession: '2AAA',
        basis: 'ALPHA_SPHERES',
        alphaSphereCount: 42,
      }),
      makeRow({
        pocketId: 1001,
        sourceAccession: '2BBB',
        basis: 'RESIDUE_ATOMS',
        alphaSphereCount: 0,
      }),
    ])
    render(<SimilarPocketsPage pocketId={7} onNavigate={() => undefined} />)

    expect(await screen.findByText('2AAA')).toBeInTheDocument()
    expect(screen.getByText('2BBB')).toBeInTheDocument()

    await userEvent.selectOptions(
      screen.getByLabelText('Geometry basis'),
      'ALPHA_SPHERES',
    )
    expect(screen.getByText('2AAA')).toBeInTheDocument()
    expect(screen.queryByText('2BBB')).not.toBeInTheDocument()
  })

  it('renders classification labels with their classes', async () => {
    stubFetch([
      makeRow({
        pocketId: 1000,
        sourceAccession: '2AAA',
        stageThreeRank: 101,
        classification: 'STRONG_SIMILARITY',
      }),
      makeRow({
        pocketId: 1001,
        sourceAccession: '2BBB',
        stageThreeRank: 102,
        classification: 'SHAPE_ONLY_NEIGHBOR',
      }),
      makeRow({
        pocketId: 1002,
        sourceAccession: '2CCC',
        stageThreeRank: 103,
        classification: 'REJECTED',
      }),
    ])
    render(<SimilarPocketsPage pocketId={7} onNavigate={() => undefined} />)

    expect(await screen.findByText('Strong similarity'))
      .toHaveClass('cls-strong')
    expect(screen.getByText('Shape-only neighbor'))
      .toHaveClass('cls-shape-only')
    expect(screen.getByText('Rejected')).toHaveClass('cls-rejected')
    expect(
      screen.getByRole('button', { name: 'Geometric similarity' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Final similarity' }),
    ).toBeInTheDocument()
  })

  it('shows the error state with retry when the request fails', async () => {
    stubFetch(null)
    render(<SimilarPocketsPage pocketId={7} onNavigate={() => undefined} />)

    expect(await screen.findByText('Similar pockets unavailable'))
      .toBeInTheDocument()
    expect(screen.getByText('Request failed with status 500'))
      .toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' }))
      .toBeInTheDocument()
  })
})
