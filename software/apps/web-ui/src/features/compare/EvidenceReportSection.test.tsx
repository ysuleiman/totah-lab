import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { PocketComparisonReportView } from '../../api/types'
import { EvidenceReportSection } from './EvidenceReportSection'

function makeReport(
  overrides: Partial<PocketComparisonReportView> = {},
): PocketComparisonReportView {
  return {
    queryPocketId: 42,
    candidatePocketId: 7,
    retrieval: {
      chosenReference: false,
      candidateSources: ['GLOBAL_SHAPE'],
      globalShapeEvaluated: true,
      globalShapeRank: 3,
      globalShapeDistance: 0.42,
      pocketMatchEvaluated: false,
      pocketMatchSymmetricRank: null,
      pocketMatchQueryCoverageRank: null,
      pocketMatchSymmetricScore: null,
      pocketMatchQueryCoverage: null,
      pocketMatchCandidateCoverage: null,
    },
    alignment: {
      selectedInitialization: 'PCA_ICP',
      selectionReason:
        'PCA_ICP selected: no usable sequence seed',
      sequenceSeedPairCount: 0,
      sequenceConsistentCorrespondenceCount: 0,
      sequenceConsistentCorrespondenceFraction: 0,
      sequenceSeedAvailable: false,
      sequenceSeedDegenerate: false,
      pcaIcp: {
        available: true,
        accepted: true,
        geometrySimilarity: 0.9,
        forwardCoverage: 0.95,
        reverseCoverage: 0.93,
        forwardMeanDistance: 0.4,
        reverseMeanDistance: 0.5,
        bidirectionalDistance: 0.45,
        maximumNearestNeighborDistance: 1.2,
        sequenceConsistentPairCount: 0,
        residueCorrespondenceCount: 8,
      },
      sequenceSeeded: {
        available: false,
        accepted: false,
        geometrySimilarity: 0,
        forwardCoverage: 0,
        reverseCoverage: 0,
        forwardMeanDistance: 0,
        reverseMeanDistance: 0,
        bidirectionalDistance: 0,
        maximumNearestNeighborDistance: 0,
        sequenceConsistentPairCount: 0,
        residueCorrespondenceCount: 0,
      },
    },
    residueComparison: {
      queryResidueCount: 8,
      candidateResidueCount: 8,
      matchedResidueCount: 8,
      unmatchedQueryResidueCount: 0,
      unmatchedCandidateResidueCount: 0,
      identicalCount: 6,
      conservativeSubstitutionCount: 1,
      chemistryCompatibleCount: 1,
      incompatibleReplacementCount: 0,
      identityFraction: 0.75,
      substitutionSimilarity: 0.8,
      chemistrySimilarity: 0.9,
      compatibleMatchedFraction: 1,
      replacementFraction: 0,
      queryResidueCoverage: 1,
      candidateResidueCoverage: 1,
      sequenceConsistentPairCount: 0,
      sequenceConsistentFraction: 0,
      correspondences: [],
    },
    chemistryComparison: {
      chemistrySimilarity: 0.9,
      chemistryCoverageAdjustedSimilarity: 0.9,
      compatibleMatchedFraction: 1,
      spatialReplacementFraction: 0,
      identicalCount: 6,
      conservativeCount: 1,
      chemistryCompatibleCount: 1,
      spatialReplacementCount: 0,
      matchedResidueCount: 8,
      queryResidueCount: 8,
      candidateResidueCount: 8,
      keyResidueChemistrySimilarity: 1,
      keyMatchedCount: 1,
      classification: 'STRONG_SIMILARITY',
      finalSimilarity: 0.95,
      meanSubstitutionSimilarity: 0.8,
      identityFraction: 0.75,
    },
    keyResidueComparison: {
      configuredKeyResidues: ['LEU30'],
      totalKeyResidueCount: 1,
      matchedKeyResidueCount: 1,
      identicalKeyResidueCount: 1,
      chemistryCompatibleKeyResidueCount: 1,
    },
    ligandContactConservation: {
      status: 'NOT_AVAILABLE',
      ligandCcd: null,
      evidenceSource: null,
      queryContactResidueCount: null,
      matchedQueryContactResidueCount: null,
      identicalContactCount: null,
      conservativeContactCount: null,
      chemistryCompatibleContactCount: null,
      incompatibleContactCount: null,
      unmatchedContactCount: null,
      sharedContactAnnotationCount: null,
      contactCoverage: null,
      contactIdentityFraction: null,
      contactSubstitutionSimilarity: null,
      contactChemistrySimilarity: null,
      contacts: [],
    },
    interpretation: {
      verdict: 'STRONG_FUNCTIONAL_MATCH',
      reason: 'All evidence dimensions agree',
    },
    ...overrides,
  }
}

describe('EvidenceReportSection', () => {
  it('presents the assessment as the headline verdict', () => {
    render(<EvidenceReportSection report={makeReport()} />)

    const card = screen.getByTestId('assessment-card')
    expect(card).toHaveTextContent('Strong functional match')
    expect(card).toHaveTextContent('All evidence dimensions agree')
    expect(card).toHaveTextContent('Rule-based verdict')
  })

  it('states unevaluated retrieval channels without inventing ranks', () => {
    render(
      <EvidenceReportSection
        report={makeReport({
          retrieval: {
            chosenReference: false,
            candidateSources: [],
            globalShapeEvaluated: false,
            globalShapeRank: null,
            globalShapeDistance: null,
            pocketMatchEvaluated: false,
            pocketMatchSymmetricRank: null,
            pocketMatchQueryCoverageRank: null,
            pocketMatchSymmetricScore: null,
            pocketMatchQueryCoverage: null,
            pocketMatchCandidateCoverage: null,
          },
        })}
      />,
    )

    expect(
      screen.getAllByText(/Not evaluated \(no search was run\)/),
    ).toHaveLength(2)
    expect(screen.queryByText(/symmetric rank/)).toBeNull()
    expect(screen.queryByText(/^rank /)).toBeNull()
  })

  it('renders pocket-match ranks and scores when evaluated', () => {
    render(
      <EvidenceReportSection
        report={makeReport({
          retrieval: {
            ...makeReport().retrieval,
            pocketMatchEvaluated: true,
            pocketMatchSymmetricRank: 3,
            pocketMatchQueryCoverageRank: 2,
            pocketMatchSymmetricScore: 0.8,
            pocketMatchQueryCoverage: 0.9,
            pocketMatchCandidateCoverage: 0.7,
          },
        })}
      />,
    )

    expect(screen.getByText(/symmetric rank 3/)).toBeInTheDocument()
    expect(screen.getByText(/coverage rank 2/)).toBeInTheDocument()
    expect(screen.getByText(/symmetric 0\.800/)).toBeInTheDocument()
  })

  it('marks unavailable ligand evidence explicitly', () => {
    render(<EvidenceReportSection report={makeReport()} />)

    expect(
      screen.getByText(/Not available: no BioHub ligand-contact/),
    ).toBeInTheDocument()
  })

  it('reports one-sided ligand annotation as such', () => {
    render(
      <EvidenceReportSection
        report={makeReport({
          ligandContactConservation: {
            ...makeReport().ligandContactConservation,
            status: 'AVAILABLE',
            ligandCcd: 'SAM',
            evidenceSource: 'BIOHUB',
            queryContactResidueCount: 2,
            matchedQueryContactResidueCount: 2,
            identicalContactCount: 2,
            sharedContactAnnotationCount: 0,
            contactCoverage: 1,
            contactIdentityFraction: 1,
            contactSubstitutionSimilarity: 1,
            contactChemistrySimilarity: 1,
            contacts: [
              {
                status: 'AVAILABLE',
                pocketReference: '42',
                ligandCcd: 'SAM',
                residue: {
                  chainId: 'A',
                  residueNumber: 30,
                  insertionCode: ' ',
                  residueName: 'LEU',
                },
                minimumDistance: 2.5,
                contactType: 'DIRECT',
                evidenceSource: 'BIOHUB',
              },
            ],
          },
        })}
      />,
    )

    expect(screen.getByText('query only')).toBeInTheDocument()
    expect(screen.getByText('SAM (BIOHUB)')).toBeInTheDocument()
    expect(screen.getAllByText('1.000').length)
      .toBeGreaterThanOrEqual(3)
  })

  it('keeps the losing alignment hypothesis visible', () => {
    render(<EvidenceReportSection report={makeReport()} />)

    expect(screen.getByText(/PCA\+ICP hypothesis/)).toBeInTheDocument()
    expect(
      screen.getByText(/Sequence-seeded hypothesis/),
    ).toBeInTheDocument()
    expect(screen.getByText('Not computed.')).toBeInTheDocument()
    expect(screen.getByText('selected')).toBeInTheDocument()
  })

  it('links to the markdown evidence report', () => {
    render(<EvidenceReportSection report={makeReport()} />)

    const link = screen.getByRole('link', {
      name: 'Download Markdown report',
    })
    expect(link).toHaveAttribute(
      'href',
      '/api/pockets/42/compare/7/evidence/report.md',
    )
  })
})
