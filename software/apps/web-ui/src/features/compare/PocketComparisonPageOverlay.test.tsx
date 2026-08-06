import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type {
  PocketComparisonDetails,
  PocketSimilarityDiagnosticRow,
  Point3D,
} from '../../api/types'
import { PocketComparisonPage } from './PocketComparisonPage'

interface ViewerProps {
  matchedQueryResiduePoints?: Point3D[]
  matchedCandidateResiduePoints?: Point3D[]
}

let capturedViewerProps: ViewerProps | null = null

vi.mock('./PointCloudViewer', () => ({
  PointCloudViewer: (props: ViewerProps) => {
    capturedViewerProps = props
    return <div data-testid="viewer-stub" />
  },
}))

// Candidate residue position is already in the aligned frame: the
// reported match distance equals the distance between query and
// candidate coordinates as serialized. The transform is deliberately
// NON-identity so that re-applying it (the original bug) would move
// the point ~10 Å and fail these assertions.
const MATCH_QUERY_POSITION: Point3D = { x: 1.0, y: 2.0, z: 3.0 }
const MATCH_CANDIDATE_POSITION: Point3D = { x: 1.5, y: 2.0, z: 3.0 }

const details: PocketComparisonDetails = {
  query: {
    pocketId: 1,
    structureId: 2,
    sourceAccession: 'QUERY',
    pocketNumber: 2,
    pointCount: 1,
    centroid: { x: 0, y: 0, z: 0 },
    bounds: { min: { x: 0, y: 0, z: 0 }, max: { x: 1, y: 1, z: 1 } },
    basis: 'RESIDUE_ATOMS',
    points: [{ x: 0, y: 0, z: 0 }],
    alphaSpheres: [],
    volume: 691.9,
    score: 0.5,
    druggabilityScore: 0.6,
    residueCount: 16,
    atomCount: 20,
    alphaSphereCount: 78,
  },
  candidate: {
    pocketId: 338969,
    structureId: 3,
    sourceAccession: 'CANDIDATE',
    pocketNumber: 1,
    pointCount: 1,
    centroid: { x: 0, y: 0, z: 0 },
    bounds: { min: { x: 0, y: 0, z: 0 }, max: { x: 1, y: 1, z: 1 } },
    basis: 'RESIDUE_ATOMS',
    points: [{ x: 0, y: 0, z: 0 }],
    alphaSpheres: [],
    volume: 691.9,
    score: 0.5,
    druggabilityScore: 0.6,
    residueCount: 16,
    atomCount: 20,
    alphaSphereCount: 78,
  },
  alignedQueryPoints: [{ x: 0, y: 0, z: 0 }],
  alignedCandidatePoints: [{ x: 0.1, y: 0, z: 0 }],
  comparison: {
    overallSimilarity: 0.5,
    geometrySimilarity: 0.4,
    sizeSimilarity: 1,
    queryCoverage: 0.6,
    candidateCoverage: 0.6,
    queryToCandidateMeanDistance: 1,
    candidateToQueryMeanDistance: 1,
    meanBidirectionalDistance: 1,
    maximumNearestNeighborDistance: 2,
    queryPointCount: 20,
    candidatePointCount: 20,
    basis: 'RESIDUE_ATOMS',
  },
  aligner: 'PCA_ICP',
  alignment: {
    initialization: 'PCA_ICP',
    sequenceSeedPairCount: 0,
    sequenceConsistentCorrespondenceCount: 0,
    sequenceConsistentCorrespondenceFraction: 0,
    sequenceSeedAvailable: false,
    sequenceSeedDegenerate: false,
  },
  transform: {
    rotation: [
      [0, -1, 0],
      [1, 0, 0],
      [0, 0, 1],
    ],
    translation: { x: 10, y: 0, z: 0 },
  },
  residueCorrespondence: {
    matches: [
      {
        query: {
          chainId: 'A',
          residueNumber: 52,
          insertionCode: '',
          residueName: 'GLU',
          label: 'A:GLU52',
          chemistry: 'NEGATIVE',
          position: MATCH_QUERY_POSITION,
        },
        candidate: {
          chainId: 'A',
          residueNumber: 1231,
          insertionCode: '',
          residueName: 'GLU',
          label: 'A:GLU1231',
          chemistry: 'NEGATIVE',
          position: MATCH_CANDIDATE_POSITION,
        },
        distanceAngstroms: 0.5,
        matchType: 'IDENTICAL',
        identicalResidue: true,
        chemistryCompatible: true,
      },
    ],
    unmatchedQuery: [],
    unmatchedCandidate: [],
    summary: {
      queryResidueCount: 1,
      candidateResidueCount: 1,
      matchedCount: 1,
      unmatchedQueryCount: 0,
      unmatchedCandidateCount: 0,
      matchedFractionQuery: 1,
      matchedFractionCandidate: 1,
      identicalFraction: 1,
      chemistryCompatibleFraction: 1,
      meanMatchedDistance: 0.5,
      maximumMatchedDistance: 0.5,
    },
  },
  keyResidues: [],
  chemistryAssessment: null,
}

function stubFetch() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/evidence')) {
        return { ok: false, status: 404, json: async () => ({}) } as Response
      }
      if (url.includes('/compare/')) {
        return { ok: true, json: async () => details } as Response
      }
      if (url.includes('/similar/diagnostic')) {
        return {
          ok: true,
          json: async () => [] as PocketSimilarityDiagnosticRow[],
        } as Response
      }
      return { ok: false, status: 404, json: async () => ({}) } as Response
    }),
  )
}

describe('PocketComparisonPage residue overlay frame', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    capturedViewerProps = null
  })

  it('renders matched candidate residue points from the API coordinates without re-applying the transform', async () => {
    stubFetch()

    render(
      <PocketComparisonPage
        queryPocketId={1}
        candidatePocketId={338969}
        onNavigate={() => {}}
      />,
    )

    await screen.findByText('Matched residue points')

    expect(capturedViewerProps).not.toBeNull()
    expect(
      capturedViewerProps?.matchedQueryResiduePoints,
    ).toEqual([MATCH_QUERY_POSITION])
    // The original bug applied details.transform a second time,
    // producing { x: 8, y: 1, z: 3 } — ~10 Å from the aligned frame.
    expect(
      capturedViewerProps?.matchedCandidateResiduePoints,
    ).toEqual([MATCH_CANDIDATE_POSITION])
  })
})
