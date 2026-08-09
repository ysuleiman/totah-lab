import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import { LigandAnalysisPage } from './LigandAnalysisPage'

const targets = [
  {
    receptorId: 1,
    structureId: 1,
    targetName: 'METTL7B',
    uniProtId: 'Q6UX53',
    runCount: 2,
    ligandCount: 2,
  },
  {
    receptorId: 2,
    structureId: 2,
    targetName: 'METTL7A',
    uniProtId: 'Q9H8H3',
    runCount: 1,
    ligandCount: 1,
  },
]

const structure = {
  id: 1,
  chosenPocket: null,
  residues: [],
}

const ligands = [
  {
    ligandId: 'lig-r',
    label: 'DCMB-R',
    smiles: 'C[C@@H](N)c1cccc(Cl)c1Cl',
    runId: 11,
    method: 'vina',
    poseCount: 27,
    bestScore: -6.25,
  },
  {
    ligandId: 'lig-s',
    label: 'DCMB-S',
    smiles: 'C[C@H](N)c1cccc(Cl)c1Cl',
    runId: 12,
    method: 'vina',
    poseCount: 27,
    bestScore: -6.64,
  },
]

const analysis = {
  receptorId: 1,
  ligandId: 'lig-r',
  ligandLabel: 'DCMB-R',
  smiles: 'C[C@@H](N)c1cccc(Cl)c1Cl',
  runs: [
    {
      runId: 11,
      receptorId: 1,
      target: 'METTL7B',
      uniProtId: 'Q6UX53',
      method: 'vina',
      poseCount: 27,
      bestScore: -5.35,
      perSeedBestScores: { 1: -5.35, 7: -5.37 },
      poses: [
        {
          poseId: 101,
          label: 'DCMB-R vina s1 m1',
          score: -5.35,
          seed: 1,
          mode: 1,
          rank: null,
          confidence: null,
        },
        {
          poseId: 102,
          label: 'DCMB-R vina s1 m2',
          score: -5.21,
          seed: 1,
          mode: 2,
          rank: null,
          confidence: null,
        },
      ],
    },
  ],
  contactProfiles: [
    {
      runId: 11,
      poseId: 101,
      label: 'DCMB-R vina s1 m1',
      method: 'vina',
      target: 'METTL7B',
      cutoffAngstroms: 4.5,
      available: true,
      unavailableReason: null,
      contacts: [
        {
          chain: 'A',
          residueNumber: 148,
          residueName: 'CYS',
          minimumDistance: 3.21,
          chemistry: {
            categories: ['CYSTEINE', 'POLAR'],
            primaryCategory: 'CYSTEINE',
            primaryLabel: 'Cysteine',
            colorKey: 'CYSTEINE',
          },
          interactions: [
            {
              type: 'HYDROGEN_BOND',
              label: 'Hydrogen bond',
              receptorAtom: 'SG',
              ligandAtom: 'O1',
              distance: 3.21,
              angleDegrees: 156.0,
              basis: 'AD4 donor/acceptor types with explicit D-H-A geometry',
            },
          ],
        },
      ],
    },
  ],
  clusters: [
    {
      runId: 11,
      target: 'METTL7B',
      thresholdAngstroms: 2,
      poseCount: 27,
      clusterCount: 4,
      largestClusterSize: 18,
      topClusterMembers: [
        { poseId: 101, label: 'DCMB-R vina s1 m1', seed: 1, mode: 1 },
      ],
      available: true,
      unavailableReason: null,
    },
  ],
  samProximity: [
    {
      runId: 11,
      poseId: 101,
      label: 'DCMB-R vina s1 m1',
      target: 'METTL7B',
      samResidueCount: 9,
      minimumDistanceToSamSet: 2.87,
      samSetCysteines: [
        { residueNumber: 148, minimumDistance: 3.21 },
      ],
      available: true,
      unavailableReason: null,
    },
  ],
}

const topScorer = {
  ligandId: 'lig-top',
  label: 'WH-0183',
  smiles: 'C=C(C#N)C=O',
  runId: 99,
  method: 'vina',
  poseCount: 3,
  bestScore: -13.76,
}

const assignments: Record<number, unknown> = {
  101: {
    poseId: 101,
    label: 'DCMB-R vina s1 m1',
    score: -5.35,
    available: true,
    unavailableReason: null,
    status: 'ASSIGNED',
    reason: null,
    assignedPocket: { pocketId: 501, pocketNumber: 1, source: 'fpocket' },
    assignmentScore: 0.87,
    secondBestPocket: { pocketId: 502, pocketNumber: 2, source: 'fpocket' },
    secondBestScore: 0.41,
    scoreMargin: 0.46,
    ambiguous: false,
    metrics: {
      ligandCentroidDistance: 2.14,
      atomContainmentFraction: 0.92,
      containmentBasis: 'heavy atoms',
      atomWithin2AOfSphereFraction: 0.75,
      atomWithin3AOfSphereFraction: 0.9,
      meanNearestSphereDistance: 1.23,
      maxNearestSphereDistance: 2.98,
      contactResidueCoverage: 0.8,
      pocketContactCoverage: 0.6,
    },
  },
  102: {
    poseId: 102,
    label: 'DCMB-R vina s1 m2',
    score: -5.21,
    available: true,
    unavailableReason: null,
    status: 'NOT_ASSIGNED',
    reason: 'Pose centroid lies outside every characterized pocket',
    assignedPocket: null,
    assignmentScore: 0.12,
    secondBestPocket: null,
    secondBestScore: null,
    scoreMargin: null,
    ambiguous: false,
    metrics: {
      ligandCentroidDistance: 12.4,
      atomContainmentFraction: 0.05,
      containmentBasis: 'heavy atoms',
      atomWithin2AOfSphereFraction: 0,
      atomWithin3AOfSphereFraction: 0.02,
      meanNearestSphereDistance: 9.8,
      maxNearestSphereDistance: 14.1,
      contactResidueCoverage: 0,
      pocketContactCoverage: 0,
    },
  },
}

const occupancy = {
  runId: 11,
  available: true,
  unavailableReason: null,
  entries: [
    {
      pocketId: 501,
      pocketNumber: 1,
      source: 'fpocket',
      poseCount: 18,
      fractionOfPoses: 0.6667,
      bestAffinity: -6.1,
      medianAffinity: -5.4,
      meanAssignmentScore: 0.81,
      bestAssignmentScore: 0.93,
      poseLabels: ['DCMB-R vina s1 m1', 'DCMB-R vina s7 m1'],
    },
    {
      pocketId: 502,
      pocketNumber: 2,
      source: 'fpocket',
      poseCount: 5,
      fractionOfPoses: 0.1852,
      bestAffinity: -5.2,
      medianAffinity: -4.8,
      meanAssignmentScore: 0.44,
      bestAssignmentScore: 0.52,
      poseLabels: ['DCMB-R vina s3 m2'],
    },
  ],
  notAssignedCount: 3,
  ambiguousCount: 1,
}

const otherAnalysis = {
  receptorId: 2,
  ligandId: 'lig-r',
  ligandLabel: 'DCMB-R',
  smiles: 'C[C@@H](N)c1cccc(Cl)c1Cl',
  runs: [
    {
      runId: 21,
      receptorId: 2,
      target: 'METTL7A',
      uniProtId: 'Q9H8H3',
      method: 'vina',
      poseCount: 10,
      bestScore: -5.9,
      perSeedBestScores: {},
      poses: [
        {
          poseId: 201,
          label: 'DCMB-R vina s1 m1',
          score: -5.9,
          seed: 1,
          mode: 1,
          rank: null,
          confidence: null,
        },
      ],
    },
  ],
  contactProfiles: [],
  clusters: [],
  samProximity: [],
}

const comparison = {
  available: true,
  unavailableReason: null,
  query: {
    poseId: 101,
    label: 'DCMB-R vina s1 m1',
    score: -5.35,
    target: 'METTL7B',
    uniProtId: 'Q6UX53',
    assignedPocket: { pocketId: 501, pocketNumber: 1, source: 'fpocket' },
  },
  candidate: {
    poseId: 201,
    label: 'DCMB-R vina s1 m1',
    score: -5.9,
    target: 'METTL7A',
    uniProtId: 'Q9H8H3',
    assignedPocket: { pocketId: 601, pocketNumber: 1, source: 'fpocket' },
  },
  samePocketNumber: true,
  pocketsStructurallyHomologous: true,
  pocketSimilarity: 0.78,
  alignedLigandCentroidDistance: 1.84,
  alignedLigandRmsd: 2.31,
  sharedAlignedContactResidues: 6,
  contactResidueSimilarity: 0.67,
  relationship: 'SAME_HOMOLOGOUS_SITE',
  reason: 'Both poses occupy the structurally homologous pocket 1',
}

/**
 * Mirrors the backend: an empty search returns the top-scoring runs
 * (which need not contain a specific ligand), a query filters by label.
 */
function filterRunOptions(path: string) {
  const query = new URLSearchParams(path.split('?')[1]).get('query')
  const all = [topScorer, ...ligands]
  if (!query) return [topScorer, ligands[0]]
  return all.filter((option) =>
    option.label.toLowerCase().includes(query.toLowerCase()))
}

const analysisRequests: string[] = []

vi.mock('../../api/hooks', () => ({
  useApiQuery: (path: string | null) => {
    if (path === null) {
      return { loading: false, error: null, retry: vi.fn(), data: null }
    }
    if (path.includes('ligand-analysis')) {
      analysisRequests.push(path)
    }
    const data = path.includes('cross-protein-comparison')
      ? comparison
      : path.includes('pocket-assignment')
        ? assignments[Number(path.match(/docking-poses\/(\d+)/)?.[1])]
        : path.includes('pocket-occupancy')
          ? occupancy
          : path.includes('docking-targets')
            ? targets
            : path.includes('docking-ligand-runs')
              ? filterRunOptions(path)
              : path.includes('/api/targets/2/ligand-analysis')
                ? otherAnalysis
                : path.includes('ligand-analysis')
                  ? analysis
                  : path.includes('residue-evidence')
                    ? []
                    : path.includes('/api/structures/')
                      ? structure
                      : null
    return { loading: false, error: null, retry: vi.fn(), data }
  },
}))

vi.mock('./useTextQuery', () => ({
  useTextQuery: () => ({
    loading: false,
    error: null,
    retry: vi.fn(),
    data: null,
  }),
}))

test('offers the docked ligand runs and renders the analysis sections',
  async () => {
    const user = userEvent.setup()
    render(<LigandAnalysisPage />)

    expect(screen.getByLabelText('Target')).toBeInTheDocument()
    expect(screen.getByLabelText('Ligand')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Ligand'), 'DCMB-R')
    await user.click(
      await screen.findByRole('button', { name: /DCMB-R · vina/ }),
    )

    expect(screen.getAllByText(/run 11/).length).toBeGreaterThan(0)
    expect(screen.getByText('CYS 148')).toBeInTheDocument()
    expect(screen.getByText('Hydrogen bond')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /Download CSV/ }),
    ).toBeInTheDocument()
  })

test('switches the analyzed ligand run from the search results', async () => {
  const user = userEvent.setup()
  render(<LigandAnalysisPage />)

  await user.type(screen.getByLabelText('Ligand'), 'DCMB-S')
  const option = await screen.findByRole('button', { name: /DCMB-S · vina/ })
  await user.click(option)
  expect(
    (screen.getByLabelText('Ligand') as HTMLInputElement).value,
  ).toBe('')

  // Clearing the search refetches the unfiltered top-scoring runs once
  // the 300 ms debounce fires; the picked run must survive that reset.
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 500))
  })
  expect(analysisRequests.at(-1)).toContain('ligandId=lig-s')
})

async function pickDcmbR(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Ligand'), 'DCMB-R')
  await user.click(
    await screen.findByRole('button', { name: /DCMB-R · vina/ }),
  )
}

test('shows the pocket assignment of the focused pose', async () => {
  const user = userEvent.setup()
  render(<LigandAnalysisPage />)
  await pickDcmbR(user)

  const panel = screen.getByLabelText('Pocket assignment')
  expect(within(panel).getByText('Assigned')).toBeInTheDocument()
  expect(
    within(panel).getByText(/occupies pocket 1 \(fpocket\)/),
  ).toBeInTheDocument()
  // Vina score and assignment score stay separate labeled fields.
  expect(within(panel).getByText('Vina score')).toBeInTheDocument()
  expect(within(panel).getByText('Assignment score')).toBeInTheDocument()
  expect(within(panel).getByText('0.87')).toBeInTheDocument()
  expect(within(panel).getByText(/Pocket 2 \(fpocket\)/)).toBeInTheDocument()
  expect(within(panel).getByText('0.46')).toBeInTheDocument()
  // Metrics: containment as %, distances in Å.
  expect(within(panel).getByText('92%')).toBeInTheDocument()
  expect(within(panel).getByText('75%')).toBeInTheDocument()
  expect(within(panel).getByText('80%')).toBeInTheDocument()
  expect(within(panel).getByText('2.14 Å')).toBeInTheDocument()
  expect(within(panel).getByText('1.23 Å')).toBeInTheDocument()
})

test('updates the pocket assignment when the pose dropdown changes',
  async () => {
    const user = userEvent.setup()
    render(<LigandAnalysisPage />)
    await pickDcmbR(user)

    const panel = screen.getByLabelText('Pocket assignment')
    expect(within(panel).getByText('Assigned')).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Pose'), '102')

    expect(within(panel).getByText('Not assigned')).toBeInTheDocument()
    expect(
      within(panel).getByText(/is not assigned to any pocket/),
    ).toBeInTheDocument()
    expect(
      within(panel).getByText(/centroid lies outside/),
    ).toBeInTheDocument()
  })

test('renders the pose occupancy table grouped by pocket', async () => {
  const user = userEvent.setup()
  render(<LigandAnalysisPage />)
  await pickDcmbR(user)

  const panel = screen.getByLabelText('Pose occupancy')
  expect(
    within(panel).getByRole('heading', { name: /pose occupancy/i }),
  ).toBeInTheDocument()
  expect(within(panel).getByText(/Pocket 1/)).toBeInTheDocument()
  expect(within(panel).getByText(/Pocket 2/)).toBeInTheDocument()
  expect(within(panel).getByText('18')).toBeInTheDocument()
  expect(within(panel).getByText('5')).toBeInTheDocument()
  expect(within(panel).getByText('67%')).toBeInTheDocument()
  expect(within(panel).getByText('19%')).toBeInTheDocument()
  expect(within(panel).getByText('-6.10')).toBeInTheDocument()
  expect(within(panel).getByText('-5.40')).toBeInTheDocument()
  expect(within(panel).getByText('0.81')).toBeInTheDocument()
  expect(
    within(panel).getByText(/DCMB-R vina s7 m1/),
  ).toBeInTheDocument()
  expect(
    within(panel).getByText(/3 poses not assigned to any pocket/),
  ).toBeInTheDocument()
})

test('compares the focused pose with a pose on another target', async () => {
  const user = userEvent.setup()
  render(<LigandAnalysisPage />)
  await pickDcmbR(user)

  const panel = screen.getByLabelText('Cross-protein comparison')
  const select = within(panel).getByLabelText('Compare with')
  expect(
    within(select).getByRole('option', { name: /· METTL7A \(-5\.90\)/ }),
  ).toBeInTheDocument()
  expect(
    within(panel).getByText(/Pick a pose of the same ligand/),
  ).toBeInTheDocument()

  await user.selectOptions(select, '201')

  expect(
    within(panel).getByText('Same homologous site'),
  ).toBeInTheDocument()
  expect(
    within(panel).getByText(/METTL7A · Q9H8H3/),
  ).toBeInTheDocument()
  expect(
    within(panel).getByText(/METTL7B · Q6UX53/),
  ).toBeInTheDocument()
  expect(within(panel).getByText('homologous')).toBeInTheDocument()
  expect(within(panel).getByText('0.78')).toBeInTheDocument()
  expect(within(panel).getByText('1.84 Å')).toBeInTheDocument()
  expect(within(panel).getByText('2.31 Å')).toBeInTheDocument()
  expect(within(panel).getByText('6')).toBeInTheDocument()
  expect(within(panel).getByText('67%')).toBeInTheDocument()
  expect(
    within(panel).getByText(/Both poses occupy the structurally/),
  ).toBeInTheDocument()
})
