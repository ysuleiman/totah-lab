export interface Residue {
  id: number
  chain: string
  residueNumber: number
  insertionCode: string
  residueName: string
}

export interface ResidueEvidence {
  residueId: number
  analysisType: string
  score: number | null
  rank: number | null
  provider: string | null
  model: string | null
  bestAlternative: string | null
  wildTypeMinusBestAlternative: number | null
  aminoAcidEntropy: number | null
  artifactId: number
}

export interface ChosenPocket {
  id: number
  pocketNumber: number
  source: PocketSource
}

export type PocketSource = 'FPOCKET' | 'P2RANK' | 'MANUAL' | 'IMPORTED'

export interface Structure {
  id: number
  source: string
  sourceAccession: string | null
  chain: string | null
  modelNumber: number | null
  preparationState: string
  parentStructureId: number | null
  receptor: {
    id: number
    targetName: string
    uniProtId?: string | null
    proteinName?: string | null
    geneName?: string | null
    organism?: string | null
  }
  artifact: Artifact
  chosenPocket: ChosenPocket | null
  residues: Residue[]
  pocketsUrl: string
}

export interface Artifact {
  id: number
  filename: string
  label: string
  storageLocation: string
}

export interface PocketSummary {
  id: number
  pocketNumber: number
  source: PocketSource
  volume: number | null
  score: number | null
  druggabilityScore: number | null
  probability: number | null
  artifactId: number
}

export interface PocketDetails {
  id: number
  pocketNumber: number
  source: PocketSource
  volume: number | null
  score: number | null
  druggabilityScore: number | null
  probability: number | null
  artifact: Artifact
  residues: Residue[]
}

export interface ResidueNeighbor extends Residue {
  atomNames: string[]
  distance: number
}

export interface ResidueNeighborhood {
  selectedResidue: Residue
  selectedAtomNames: string[]
  cutoff: number
  neighbors: ResidueNeighbor[]
}

export interface AtomDistance {
  firstResidue: Residue
  firstAtom: string
  secondResidue: Residue
  secondAtom: string
  distance: number
}

export interface DockingRunSummary {
  id: number
  structureId: number
  receptorId: number
  createdAt: string
  totalLigandCount: number
  totalPoseCount: number
}

export interface ResidueAnalysis {
  runId: number
  structureId: number
  receptorId: number
  residueId: number
  chain: string
  residueNumber: number
  residueName: string
  contactScoreThreshold: number
  scoreFilteredLigandCount: number
  scoreFilteredContactingLigandCount: number
  scoreFilteredContactingLigandFraction: number
  scoreFilteredPoseCount: number
  scoreFilteredContactingPoseCount: number
  scoreFilteredContactingPoseFraction: number
  totalLigandCount: number
  contactingLigandCount: number
  contactingLigandFraction: number
  totalPoseCount: number
  contactingPoseCount: number
  contactingPoseFraction: number
  totalGoodLigandCount: number
  goodContactingLigandCount: number
  goodContactingLigandFraction: number
  totalBadLigandCount: number
  badContactingLigandCount: number
  badContactingLigandFraction: number
  contactFractionDifference: number
  enrichmentRatio: number | null
  log2Enrichment: number | null
  avgContactingScore: number | null
  medianContactingScore: number | null
  bestContactingScore: number | null
  worstContactingScore: number | null
  closestDistance: number | null
  avgLigandMinDistance: number | null
  avgPoseMinDistance: number | null
}

export interface ResidueScoreBand {
  runId: number
  structureId: number
  receptorId: number
  scoreLower: number
  scoreUpper: number
  residueId: number
  chain: string
  residueNumber: number
  residueName: string
  ligandCount: number
  contactingLigandCount: number
  contactingLigandFraction: number
  poseCount: number
  contactingPoseCount: number
  contactingPoseFraction: number
  avgContactingScore: number | null
  medianContactingScore: number | null
  bestContactingScore: number | null
  worstContactingScore: number | null
  closestDistance: number | null
  avgLigandMinDistance: number | null
  avgPoseMinDistance: number | null
}
