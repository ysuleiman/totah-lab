import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type {
  PocketComparisonMetrics,
  ResidueCorrespondenceView,
  ResidueMatchView,
  ResiduePointView,
} from '../../api/types'
import { ResidueCorrespondenceSection } from './ResidueCorrespondenceSection'

function residuePoint(
  chainId: string,
  residueName: string,
  residueNumber: number,
  chemistry: string,
  position = { x: 0, y: 0, z: 0 },
  insertionCode = '',
): ResiduePointView {
  return {
    chainId,
    residueNumber,
    insertionCode,
    residueName,
    label: `${chainId}:${residueName}${residueNumber}${insertionCode}`,
    chemistry,
    position,
  }
}

function match(
  query: ResiduePointView,
  candidate: ResiduePointView,
  distanceAngstroms: number,
  matchType: ResidueMatchView['matchType'],
  identicalResidue: boolean,
  chemistryCompatible: boolean,
): ResidueMatchView {
  return {
    query,
    candidate,
    distanceAngstroms,
    matchType,
    identicalResidue,
    chemistryCompatible,
  }
}

const correspondence: ResidueCorrespondenceView = {
  matches: [
    match(
      residuePoint('A', 'ALA', 100, 'HYDROPHOBIC'),
      residuePoint('B', 'ALA', 100, 'HYDROPHOBIC'),
      3.5,
      'IDENTICAL',
      true,
      true,
    ),
    match(
      residuePoint('A', 'CYS', 202, 'CYSTEINE'),
      residuePoint('B', 'CYS', 210, 'CYSTEINE'),
      0.8,
      'CONSERVATIVE',
      false,
      true,
    ),
    match(
      residuePoint('A', 'LEU', 145, 'HYDROPHOBIC'),
      residuePoint('B', 'VAL', 150, 'HYDROPHOBIC'),
      1.2,
      'CHEMISTRY_COMPATIBLE',
      false,
      true,
    ),
    match(
      residuePoint('A', 'GLY', 300, 'SPECIAL'),
      residuePoint('B', 'ASP', 301, 'ACIDIC'),
      2.5,
      'DIFFERENT',
      false,
      false,
    ),
  ],
  unmatchedQuery: [
    residuePoint('A', 'SER', 50, 'POLAR', {
      x: 12.34,
      y: -4.46,
      z: 7.75,
    }),
  ],
  unmatchedCandidate: [residuePoint('B', 'THR', 60, 'POLAR')],
  summary: {
    queryResidueCount: 5,
    candidateResidueCount: 5,
    matchedCount: 4,
    unmatchedQueryCount: 1,
    unmatchedCandidateCount: 1,
    matchedFractionQuery: 0.8,
    matchedFractionCandidate: 0.8,
    identicalFraction: 0.25,
    chemistryCompatibleFraction: 0.75,
    meanMatchedDistance: 2.0,
    maximumMatchedDistance: 3.5,
  },
}

const comparison: PocketComparisonMetrics = {
  overallSimilarity: 0.409,
  geometrySimilarity: 0.348,
  sizeSimilarity: 0.756,
  queryCoverage: 0.63,
  candidateCoverage: 1.0,
  queryToCandidateMeanDistance: 1.24,
  candidateToQueryMeanDistance: 1.31,
  meanBidirectionalDistance: 1.28,
  maximumNearestNeighborDistance: 4.82,
  queryPointCount: 78,
  candidatePointCount: 59,
  basis: 'ALPHA_SPHERES',
}

const TEST_KEY_RESIDUES = [
  'CYS148',
  'LEU145',
  'HIS175',
  'GLY199',
  'ASP200',
  'GLY201',
  'CYS202',
  'CYS203',
]

function renderSection(keyResidues = TEST_KEY_RESIDUES) {
  return render(
    <ResidueCorrespondenceSection
      correspondence={correspondence}
      comparison={comparison}
      keyResidues={keyResidues}
    />,
  )
}

function dataRows(): HTMLElement[] {
  const table = screen.getAllByRole('table')[0]
  return within(table)
    .getAllByRole('row')
    .filter((row) => row.querySelector('td') !== null)
    .filter((row) => !row.classList.contains('correspondence-detail-row'))
}

async function selectClassification(label: string) {
  await userEvent.selectOptions(
    screen.getByRole('combobox', { name: 'Classification' }),
    label,
  )
}

describe('ResidueCorrespondenceSection', () => {
  it('renders the geometry line and per-class summary counts', () => {
    renderSection()

    expect(screen.getByText('Geometry')).toBeInTheDocument()
    expect(screen.getByText('Good', { exact: false }))
      .toBeInTheDocument()
    expect(screen.getByText(/0\.409 overall similarity/))
      .toBeInTheDocument()
    expect(screen.getByText(/1\.28 Å mean distance/))
      .toBeInTheDocument()
    expect(screen.getByText(/5 query/)).toBeInTheDocument()
    expect(screen.getByText(/5 candidate/)).toBeInTheDocument()
    expect(screen.getByText(/4 spatial correspondences/))
      .toBeInTheDocument()
    expect(screen.getByText(/1 query/)).toBeInTheDocument()
    expect(screen.getByText(/1 candidate/)).toBeInTheDocument()
    expect(screen.getByText(/mean 2\.00 Å/)).toBeInTheDocument()
    expect(screen.getByText(/max 3\.50 Å/)).toBeInTheDocument()
  })

  it('keeps every correspondence visible by default, including spatial replacements', () => {
    renderSection()

    expect(dataRows()).toHaveLength(4)
    const table = within(screen.getAllByRole('table')[0])
    expect(table.getByText('Identical')).toBeInTheDocument()
    expect(table.getByText('Conservative')).toBeInTheDocument()
    expect(table.getByText('Chemistry compatible')).toBeInTheDocument()
    expect(table.getByText('Spatial replacement')).toBeInTheDocument()
    expect(table.getByText('A:GLY300')).toBeInTheDocument()
    expect(table.getByText('B:ASP301')).toBeInTheDocument()
    expect(table.getAllByText('yes')).toHaveLength(3)
    expect(table.getAllByText('no')).toHaveLength(1)
  })

  it('pins key residues to the top with a badge, ordered by distance', () => {
    renderSection()

    const rows = dataRows()
    expect(within(rows[0]).getByText('A:CYS202')).toBeInTheDocument()
    expect(within(rows[1]).getByText('A:LEU145')).toBeInTheDocument()
    expect(screen.getAllByText('Key residue')).toHaveLength(2)
    expect(within(rows[2]).getByText('A:GLY300')).toBeInTheDocument()
    expect(within(rows[3]).getByText('A:ALA100')).toBeInTheDocument()
  })

  it('shows no key badges for a query without key configuration', () => {
    renderSection([])

    expect(screen.queryByText('Key residue')).not.toBeInTheDocument()
    expect(dataRows()).toHaveLength(4)
  })

  it('does not let a different query inherit another target’s keys', () => {
    renderSection(['GLY300'])

    expect(screen.getAllByText('Key residue')).toHaveLength(1)
    expect(dataRows()[0]).toHaveTextContent('A:GLY300')
  })

  it('filters by classification without losing information permanently', async () => {
    renderSection()

    await selectClassification('Identical')
    expect(dataRows()).toHaveLength(1)
    expect(dataRows()[0]).toHaveTextContent('A:ALA100')

    await selectClassification('Spatial replacements')
    const rows = dataRows()
    expect(rows).toHaveLength(1)
    expect(rows[0]).toHaveTextContent('A:GLY300')
    expect(within(rows[0]).getByText('Spatial replacement'))
      .toBeInTheDocument()

    await selectClassification('Show all')
    expect(dataRows()).toHaveLength(4)
  })

  it('filters to key residues only via the classification select', async () => {
    renderSection()

    await selectClassification('Key residues only')

    const rows = dataRows()
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('A:CYS202')
    expect(rows[1]).toHaveTextContent('A:LEU145')
  })

  it('filters by maximum distance and label text', async () => {
    renderSection()

    await userEvent.type(
      screen.getByRole('spinbutton', { name: 'Max distance (Å)' }),
      '1',
    )
    expect(dataRows()).toHaveLength(1)
    expect(dataRows()[0]).toHaveTextContent('A:CYS202')

    await userEvent.clear(
      screen.getByRole('spinbutton', { name: 'Max distance (Å)' }),
    )
    await userEvent.type(
      screen.getByRole('textbox', { name: 'Query residue' }),
      'leu',
    )
    expect(dataRows()).toHaveLength(1)
    expect(dataRows()[0]).toHaveTextContent('A:LEU145')
  })

  it('shows only unmatched residues when requested', async () => {
    renderSection()

    await selectClassification('Unmatched only')

    // Only the two unmatched tables remain visible.
    expect(screen.getAllByRole('table')).toHaveLength(2)
    expect(screen.getByText('A:SER50')).toBeInTheDocument()
    expect(screen.getByText('B:THR60')).toBeInTheDocument()
    expect(screen.queryByText('A:ALA100')).not.toBeInTheDocument()
  })

  it('expands a row to show representative coordinates', async () => {
    renderSection()

    expect(screen.queryByText(/Query position:/)).not.toBeInTheDocument()

    await userEvent.click(
      screen.getAllByRole('button', { name: 'Show coordinates' })[0],
    )

    expect(screen.getByText(/Query position:/)).toBeInTheDocument()
    expect(screen.getByText(/Candidate position:/)).toBeInTheDocument()
  })

  it('explains why each unmatched residue is unmatched', () => {
    renderSection()

    // A:SER50 sits ~15 Å from every candidate residue.
    expect(
      screen.getByText(/no candidate residue within 4\.0 Å/),
    ).toBeInTheDocument()
    // B:THR60 sits on top of already-matched query residues.
    expect(
      screen.getByText(/already matched by a closer residue/),
    ).toBeInTheDocument()
  })

  it('states the spatial-correspondence and divergence caveats', () => {
    renderSection()

    expect(
      screen.getByText(/Matches are spatial correspondences/),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/Maximum correspondence distance is 4\.0 Å/),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/does not by itself establish/),
    ).toBeInTheDocument()
  })
})
