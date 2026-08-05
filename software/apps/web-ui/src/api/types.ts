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

export type PocketSource =
  | 'FPOCKET'
  | 'P2RANK'
  | 'BIOHUB'
  | 'MANUAL'
  | 'IMPORTED'

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
  evidence: PocketEvidence | null
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
  evidence: PocketEvidence | null
}

export interface PocketEvidence {
  ligandCcd: string
  model: string
  shellCutoff: number
  directContactCutoff: number
  ptm: number | null
  interfacePtm: number | null
  shellResidueCount: number
  directContactResidueCount: number
  chosenPocketOverlapCount: number
  directChosenPocketOverlapCount: number
  shellResidueIds: number[]
  directContactResidueIds: number[]
  chosenPocketOverlapResidueIds: number[]
  directChosenPocketOverlapResidueIds: number[]
}

export interface StructureReport {
  structureId: number
  title: string
  generatedAt: string
  uniProtId: string | null
  geneName: string | null
  proteinName: string | null
  chosenPocket: ReportPocket | null
  chosenPocketResidues: ReportResidue[]
  ligandEvidence: ReportLigandEvidence[]
  narrative: string
}

export interface ReportPocket {
  id: number
  source: PocketSource
  pocketNumber: number
  score: number | null
  druggabilityScore: number | null
  volume: number | null
  residueCount: number
}

export interface ReportResidue {
  id: number
  chain: string
  residueNumber: number
  insertionCode: string | null
  residueName: string
  oneLetterCode: string
}

export interface ReportLigandEvidence {
  ligandCcd: string
  model: string
  ptm: number | null
  interfacePtm: number | null
  strongContactCutoff: number
  directContactCutoff: number
  contextCutoff: number
  strongContactCount: number
  nearContactCount: number
  directContactCount: number
  contextResidueCount: number
  directChosenPocketOverlapCount: number
  outsideDirectContactCount: number
  residues: ReportContactResidue[]
}

export interface ReportContactResidue {
  id: number
  chain: string
  residueNumber: number
  residueName: string
  oneLetterCode: string
  minimumDistance: number
  contactingAtomPairCount: number
  classification: 'STRONG' | 'NEAR' | 'CONTEXT'
  directContact: boolean
  chosenPocketMember: boolean
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

export interface SelectivityScore {
  ligandId: string
  ligandLabel: string
  smiles: string | null
  score7b: number
  score7a: number
  delta: number
  runId7b: number
  runId7a: number
  poseId7b: number
  poseId7a: number
}

export interface SelectivityPage {
  items: SelectivityScore[]
  total: number
  page: number
  size: number
  sortBy: SelectivitySort
  direction: SortDirection
}

export type SelectivitySort = 'delta' | 'score7b' | 'score7a' | 'ligandId'
export type SortDirection = 'asc' | 'desc'

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

export interface PocketReportDocument {
  report: {
    data: {
      pocketId: number
      pocketName: string
      source: PocketSource
      geometry: Record<string, unknown>
      residues: {
        totalResidues: number
        residues: PocketReportResidue[]
      }
      docking: {
        runId: number
        totalLigandCount: number
        totalPoseCount: number
        contactScoreThreshold: number
        residues: PocketReportDockingResidue[]
      }
      hotspots: Record<string, unknown>
    }
    evidence: PocketReportEvidence[]
  }
  narrative: {
    executiveSummary: string
    findings: PocketReportFinding[]
    limitations: string
    conclusions: string
  }
}

export interface PocketReportResidue {
  chain: string
  residueNumber: number
  residueName: string
  categories: string[]
  alphaCarbonDistanceToPocketCentroidAngstrom?: number
}

export interface PocketReportDockingResidue {
  residueId?: number
  chain: string
  residueNumber: number
  residueName: string
  contactingLigandCount: number
  contactingLigandFraction: number
  contactingPoseCount: number
  contactingPoseFraction: number
  scoreFilteredContactingLigandFraction?: number
  enrichmentRatio?: number
  closestDistance?: number
}

export interface PocketReportEvidence {
  id: string
  category: string
  statement: string
  metrics: Record<string, number>
}

export interface PocketReportFinding {
  statement: string
  type: 'OBSERVATION' | 'INTERPRETATION' | 'LIMITATION' | 'RECOMMENDATION'
  confidence: 'LOW' | 'MODERATE' | 'HIGH'
  evidenceIds: string[]
}

export interface PocketSimilarityDiagnosticRow {
  pocketId: number
  structureId: number
  sourceAccession: string
  pocketNumber: number
  stageOneRank: number
  descriptorDistance: number
  volumeDistance: number
  residueDistance: number
  chemistryDistance: number
  stageTwoRank: number
  shapeDistance: number
  stageThreeRank: number
  geometricOverallSimilarity: number
  geometrySimilarity: number
  sizeSimilarity: number
  queryCoverage: number
  candidateCoverage: number
  queryToCandidateMeanDistance: number
  candidateToQueryMeanDistance: number
  meanBidirectionalDistance: number
  maximumNearestNeighborDistance: number
  queryPointCount: number
  candidatePointCount: number
  basis: string
  alphaSphereCount: number
  alignmentInitialization: string
  chemistrySimilarity: number
  chemistryCoverageAdjustedSimilarity: number
  compatibleMatchedFraction: number
  spatialReplacementFraction: number
  identicalCount: number
  conservativeCount: number
  chemistryCompatibleCount: number
  spatialReplacementCount: number
  matchedResidueCount: number
  keyResidueChemistrySimilarity: number
  classification: string
  finalSimilarity: number
  uniProtId: string | null
  proteinName: string | null
  geneName: string | null
  organism: string | null
}

export interface Point3D {
  x: number
  y: number
  z: number
}

export interface AlphaSphereView {
  index: number
  center: Point3D
  radius: number
}

export interface PocketGeometryView {
  pocketId: number
  structureId: number | null
  sourceAccession: string | null
  pocketNumber: number | null
  pointCount: number
  centroid: Point3D
  bounds: { min: Point3D; max: Point3D }
  basis: string
  points: Point3D[]
  alphaSpheres: AlphaSphereView[]
  volume: number | null
  score: number | null
  druggabilityScore: number | null
  residueCount: number | null
  atomCount: number | null
  alphaSphereCount: number | null
}

export interface PocketComparisonMetrics {
  overallSimilarity: number
  geometrySimilarity: number
  sizeSimilarity: number
  queryCoverage: number
  candidateCoverage: number
  queryToCandidateMeanDistance: number
  candidateToQueryMeanDistance: number
  meanBidirectionalDistance: number
  maximumNearestNeighborDistance: number
  queryPointCount: number
  candidatePointCount: number
  basis: string
}

export interface RigidTransformView {
  rotation: number[][]
  translation: Point3D
}

export interface AlignmentMetadataView {
  initialization: string
  sequenceSeedPairCount: number
  sequenceConsistentCorrespondenceCount: number
  sequenceConsistentCorrespondenceFraction: number
  sequenceSeedAvailable: boolean
  sequenceSeedDegenerate: boolean
}

export interface ResiduePointView {
  chainId: string
  residueNumber: number
  insertionCode: string
  residueName: string
  label: string
  chemistry: string
  position: Point3D
}

export interface ResidueMatchView {
  query: ResiduePointView
  candidate: ResiduePointView
  distanceAngstroms: number
  matchType:
    | 'IDENTICAL'
    | 'CONSERVATIVE'
    | 'CHEMISTRY_COMPATIBLE'
    | 'DIFFERENT'
    | 'UNMATCHED'
  identicalResidue: boolean
  chemistryCompatible: boolean
}

export interface ResidueSummaryView {
  queryResidueCount: number
  candidateResidueCount: number
  matchedCount: number
  unmatchedQueryCount: number
  unmatchedCandidateCount: number
  matchedFractionQuery: number
  matchedFractionCandidate: number
  identicalFraction: number
  chemistryCompatibleFraction: number
  meanMatchedDistance: number
  maximumMatchedDistance: number
}

export interface ResidueCorrespondenceView {
  matches: ResidueMatchView[]
  unmatchedQuery: ResiduePointView[]
  unmatchedCandidate: ResiduePointView[]
  summary: ResidueSummaryView
}

export interface ChemistryAssessmentView {
  chemistrySimilarity: number
  chemistryCoverageAdjustedSimilarity: number
  compatibleMatchedFraction: number
  spatialReplacementFraction: number
  identicalCount: number
  conservativeCount: number
  chemistryCompatibleCount: number
  spatialReplacementCount: number
  matchedResidueCount: number
  queryResidueCount: number
  candidateResidueCount: number
  keyResidueChemistrySimilarity: number
  keyMatchedCount: number
  classification: string
  finalSimilarity: number
}

export interface PocketComparisonDetails {
  query: PocketGeometryView
  candidate: PocketGeometryView
  alignedQueryPoints: Point3D[]
  alignedCandidatePoints: Point3D[]
  comparison: PocketComparisonMetrics
  aligner: string
  alignment: AlignmentMetadataView
  transform: RigidTransformView
  residueCorrespondence: ResidueCorrespondenceView | null
  keyResidues: string[]
  chemistryAssessment: ChemistryAssessmentView | null
}
