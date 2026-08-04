import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type {
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

function dataRows(): HTMLElement[] {
  const table = screen.getAllByRole('table')[0]
  return within(table).getAllByRole('row').slice(1)
}

describe('ResidueCorrespondenceSection', () => {
  it('renders the summary counts, percentages, and distances', () => {
    render(
      <ResidueCorrespondenceSection correspondence={correspondence} />,
    )

    const summaryGrid = screen.getByText('Matched residues')
      .closest('dl')
    expect(summaryGrid).not.toBeNull()
    const summary = within(summaryGrid as HTMLElement)

    expect(summary.getByText('Query residues')).toBeInTheDocument()
    expect(summary.getByText('Candidate residues'))
      .toBeInTheDocument()
    expect(summary.getAllByText('5')).toHaveLength(2)
    expect(summary.getByText('4')).toBeInTheDocument()
    expect(summary.getAllByText('80.0%')).toHaveLength(2)
    expect(summary.getByText('25.0%')).toBeInTheDocument()
    expect(summary.getByText('75.0%')).toBeInTheDocument()
    expect(summary.getByText('2.00 Å')).toBeInTheDocument()
    expect(summary.getByText('3.50 Å')).toBeInTheDocument()
  })

  it('renders every match with its type and compatibility as text', () => {
    render(
      <ResidueCorrespondenceSection correspondence={correspondence} />,
    )

    const table = screen.getAllByRole('table')[0]
    const matches = within(table)

    expect(dataRows()).toHaveLength(4)
    expect(matches.getByText('Identical')).toBeInTheDocument()
    expect(matches.getByText('Conservative')).toBeInTheDocument()
    expect(matches.getByText('Chemistry compatible'))
      .toBeInTheDocument()
    expect(matches.getByText('Different')).toBeInTheDocument()
    expect(matches.getAllByText('yes')).toHaveLength(3)
    expect(matches.getAllByText('no')).toHaveLength(1)
    expect(matches.getByText('A:GLY300')).toBeInTheDocument()
    expect(matches.getByText('B:ASP301')).toBeInTheDocument()
  })

  it('pins key residues to the top with a badge, ordered by distance', () => {
    render(
      <ResidueCorrespondenceSection correspondence={correspondence} />,
    )

    const rows = dataRows()
    expect(within(rows[0]).getByText('A:CYS202')).toBeInTheDocument()
    expect(within(rows[1]).getByText('A:LEU145')).toBeInTheDocument()
    expect(within(rows[0]).getByText('Key residue'))
      .toBeInTheDocument()
    expect(within(rows[1]).getByText('Key residue'))
      .toBeInTheDocument()
    expect(screen.getAllByText('Key residue')).toHaveLength(2)
    // Non-key matches follow, still ascending by distance.
    expect(within(rows[2]).getByText('A:GLY300')).toBeInTheDocument()
    expect(within(rows[3]).getByText('A:ALA100')).toBeInTheDocument()
  })

  it('filters to identical matches only', async () => {
    render(
      <ResidueCorrespondenceSection correspondence={correspondence} />,
    )

    await userEvent.click(
      screen.getByRole('checkbox', { name: 'Identical only' }),
    )

    const rows = dataRows()
    expect(rows).toHaveLength(1)
    expect(within(rows[0]).getByText('A:ALA100')).toBeInTheDocument()
  })

  it('filters by maximum distance', async () => {
    render(
      <ResidueCorrespondenceSection correspondence={correspondence} />,
    )

    await userEvent.type(
      screen.getByRole('spinbutton', { name: 'Max distance (Å)' }),
      '1',
    )

    const rows = dataRows()
    expect(rows).toHaveLength(1)
    expect(within(rows[0]).getByText('A:CYS202')).toBeInTheDocument()
  })

  it('filters to key residues and by label substring together', async () => {
    render(
      <ResidueCorrespondenceSection correspondence={correspondence} />,
    )

    await userEvent.click(
      screen.getByRole('checkbox', { name: 'Key residues only' }),
    )
    expect(dataRows()).toHaveLength(2)

    await userEvent.type(
      screen.getByRole('textbox', { name: 'Query residue' }),
      'leu',
    )
    const rows = dataRows()
    expect(rows).toHaveLength(1)
    expect(within(rows[0]).getByText('A:LEU145')).toBeInTheDocument()
  })

  it('filters to mismatches only', async () => {
    render(
      <ResidueCorrespondenceSection correspondence={correspondence} />,
    )

    await userEvent.click(
      screen.getByRole('checkbox', { name: 'Mismatches only' }),
    )

    const rows = dataRows()
    expect(rows).toHaveLength(1)
    expect(within(rows[0]).getByText('A:GLY300')).toBeInTheDocument()
    expect(within(rows[0]).getByText('Different')).toBeInTheDocument()
  })

  it('lists unmatched residues in collapsible sections with coordinates', async () => {
    render(
      <ResidueCorrespondenceSection correspondence={correspondence} />,
    )

    expect(
      screen.getByText('Unmatched query residues (1)'),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Unmatched candidate residues (1)'),
    ).toBeInTheDocument()

    await userEvent.click(
      screen.getByText('Unmatched query residues (1)'),
    )
    expect(screen.getByText('A:SER50')).toBeInTheDocument()
    expect(screen.getByText('(12.3, -4.5, 7.8)')).toBeInTheDocument()

    await userEvent.click(
      screen.getByText('Unmatched candidate residues (1)'),
    )
    expect(screen.getByText('B:THR60')).toBeInTheDocument()

    // Unmatched residues never appear as rows in the main table.
    for (const row of dataRows()) {
      expect(within(row).queryByText('A:SER50')).toBeNull()
      expect(within(row).queryByText('B:THR60')).toBeNull()
    }
  })

  it('states the spatial-correspondence caveat', () => {
    render(
      <ResidueCorrespondenceSection correspondence={correspondence} />,
    )

    expect(
      screen.getByText(/Matches are spatial correspondences/),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/Maximum correspondence distance is 4\.0 Å/),
    ).toBeInTheDocument()
  })
})
