// --- Generic receptor-ligand pose analysis ---
// Mirrors PoseAnalysisView in web-api (totah.lab.web.poseanalysis).

import type { ResidueChemistryView } from '../../api/types'

export interface PoseView {
  poseId: number
  label: string
  score: number
  seed: number | null
  mode: number | null
  rank: number | null
  confidence: number | null
}

export interface PoseRunView {
  runId: number
  receptorId: number
  target: string
  uniProtId: string | null
  method: string
  poseCount: number
  bestScore: number | null
  perSeedBestScores: Record<string, number>
  poses: PoseView[]
}

export interface ResidueContactView {
  chain: string
  residueNumber: number
  residueName: string | null
  minimumDistance: number
  chemistry: ResidueChemistryView
  interactions: InteractionView[]
}

export interface InteractionView {
  type: 'HYDROGEN_BOND' | 'SALT_BRIDGE'
  label: string
  receptorAtom: string
  ligandAtom: string
  distance: number
  angleDegrees: number | null
  basis: string
}

export interface ContactProfileView {
  runId: number
  poseId: number | null
  label: string | null
  method: string | null
  target: string
  cutoffAngstroms: number
  available: boolean
  unavailableReason: string | null
  contacts: ResidueContactView[]
}

export interface ClusterMemberView {
  poseId: number
  label: string
  seed: number | null
  mode: number | null
}

export interface ClusterSummaryView {
  runId: number
  target: string
  thresholdAngstroms: number
  poseCount: number
  clusterCount: number
  largestClusterSize: number
  topClusterMembers: ClusterMemberView[]
  available: boolean
  unavailableReason: string | null
}

export interface CysteineProximityView {
  residueNumber: number
  minimumDistance: number
}

export interface SamProximityView {
  runId: number
  poseId: number | null
  label: string | null
  target: string
  samResidueCount: number
  minimumDistanceToSamSet: number | null
  samSetCysteines: CysteineProximityView[]
  available: boolean
  unavailableReason: string | null
}

export interface DockingTarget {
  receptorId: number
  structureId: number
  targetName: string
  uniProtId: string | null
  runCount: number
  ligandCount: number
}

export interface LigandAnalysis {
  receptorId: number
  ligandId: string
  ligandLabel: string
  smiles: string | null
  runs: PoseRunView[]
  contactProfiles: ContactProfileView[]
  clusters: ClusterSummaryView[]
  samProximity: SamProximityView[]
}

export interface LigandOption {
  ligandId: string
  label: string
  smiles: string | null
  runCount: number
  poseCount: number
  bestScore: number | null
}

export interface LigandRunOption {
  ligandId: string
  label: string
  smiles: string | null
  runId: number
  method: string
  poseCount: number
  bestScore: number | null
}

// --- Pose → pocket assignment ---
// Mirrors the pocket-assignment views in web-api; fields are null when
// unavailable, and available=false carries an unavailableReason.

export interface AssignedPocketView {
  pocketId: number
  pocketNumber: number
  source: string
}

export interface PocketAssignmentMetricsView {
  ligandCentroidDistance: number | null
  atomContainmentFraction: number | null
  containmentBasis: string | null
  atomWithin2AOfSphereFraction: number | null
  atomWithin3AOfSphereFraction: number | null
  meanNearestSphereDistance: number | null
  maxNearestSphereDistance: number | null
  contactResidueCoverage: number | null
  pocketContactCoverage: number | null
}

export type PocketAssignmentStatus =
  'ASSIGNED' | 'AMBIGUOUS' | 'NOT_ASSIGNED'

export interface PosePocketAssignmentView {
  poseId: number
  label: string | null
  score: number | null
  available: boolean
  unavailableReason: string | null
  status: PocketAssignmentStatus
  reason: string | null
  assignedPocket: AssignedPocketView | null
  assignmentScore: number | null
  secondBestPocket: AssignedPocketView | null
  secondBestScore: number | null
  scoreMargin: number | null
  ambiguous: boolean
  metrics: PocketAssignmentMetricsView | null
}

export interface PocketOccupancyEntryView {
  pocketId: number
  pocketNumber: number
  source: string
  poseCount: number
  fractionOfPoses: number | null
  bestAffinity: number | null
  medianAffinity: number | null
  meanAssignmentScore: number | null
  bestAssignmentScore: number | null
  poseLabels: string[]
}

export interface RunPocketOccupancyView {
  runId: number
  available: boolean
  unavailableReason: string | null
  entries: PocketOccupancyEntryView[]
  notAssignedCount: number
  ambiguousCount: number
}

export type CrossProteinRelationship =
  'SAME_HOMOLOGOUS_SITE'
  | 'HOMOLOGOUS_SITE_DIFFERENT_POSE'
  | 'DIFFERENT_SITE'
  | 'AMBIGUOUS'

export interface CrossProteinPoseSideView {
  poseId: number
  label: string | null
  score: number | null
  target: string
  uniProtId: string | null
  assignedPocket: AssignedPocketView | null
}

export interface CrossProteinComparisonView {
  available: boolean
  unavailableReason: string | null
  query: CrossProteinPoseSideView
  candidate: CrossProteinPoseSideView
  samePocketNumber: boolean | null
  pocketsStructurallyHomologous: boolean | null
  pocketSimilarity: number | null
  alignedLigandCentroidDistance: number | null
  alignedLigandRmsd: number | null
  sharedAlignedContactResidues: number | null
  contactResidueSimilarity: number | null
  relationship: CrossProteinRelationship
  reason: string | null
}
