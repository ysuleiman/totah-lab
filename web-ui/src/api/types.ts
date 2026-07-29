export interface Residue {
  id: number
  chain: string
  residueNumber: number
  insertionCode: string
  residueName: string
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
  druggabilityScore: number | null
  artifactId: number
}

export interface PocketDetails {
  id: number
  pocketNumber: number
  source: PocketSource
  volume: number | null
  druggabilityScore: number | null
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
