import type { Point3D } from '../../api/types'
import type { StaticChannelPath, StaticStructureAtom } from './staticChannelAnalysis'

export type LocalRouteLabel = 'PRIMARY' | 'ALTERNATIVE' | 'LOCAL'
export type RouteSegment = 'EXTERNAL_ENTRANCE' | 'BOTTLENECK' | 'CHAMBER' | 'FINAL_DELIVERY'
export type RouteCorrespondenceStatus =
  | 'HOMOLOGOUS_ROUTE' | 'POSSIBLE_CORRESPONDENCE'
  | 'A_SPECIFIC_ROUTE' | 'B_SPECIFIC_ROUTE' | 'NO_CONFIDENT_CORRESPONDENCE'
export type NacDeliveryClassification =
  | 'NAC_APPROACH_COMPATIBLE' | 'REORIENTATION_REQUIRED'
  | 'GEOMETRICALLY_DISFAVORED' | 'UNRESOLVED'

export interface RouteLiningResidue {
  identity: string
  segment: RouteSegment
  minimumDistance: number
}

export interface RouteFingerprint {
  routeId: string
  localLabel: LocalRouteLabel
  entrance: Point3D
  centerline: Point3D[]
  length: number
  tortuosity: number
  clearanceProfile: { distance: number; clearance: number }[]
  minimumBottleneck: number
  bottleneck: Point3D
  bottleneckPathDistance: number
  liningResidues: RouteLiningResidue[]
  finalDeliveryResidues: string[]
  branchTopology: 'UNBRANCHED_CENTERLINE'
  approachVector: Point3D | null
  approachTerminalLength: number
  approachRobustnessAngles: number[]
  closestApproachToMethyl: number | null
  terminalCurvatureDegrees: number | null
  angleToSamAxisDegrees: number | null
  nacDelivery: NacDeliveryClassification
  nacProvenance: string
}

export interface RoutePairEvidence {
  routeA: string
  routeB: string
  labelA: LocalRouteLabel
  labelB: LocalRouteLabel
  alignedCenterlineOverlap: number
  centerlineRmsDistance: number
  entranceDistance: number
  bottleneckDistance: number
  homologousLiningOverlap: number
  branchTopologyMatches: boolean
  approachVectorAngleDegrees: number | null
  lateralDisplacement: number | null
  status: RouteCorrespondenceStatus
  reason: string
}

export const ROUTE_ANALYSIS_PROVENANCE = {
  terminalLengthsAngstrom: [2, 3, 4] as const,
  selectedTerminalLengthAngstrom: 3,
  liningCutoffAngstrom: 4,
  correspondence: {
    homologousRmsMax: 2.0, homologousOverlapMin: 0.6, homologousApproachAngleMax: 35,
    possibleRmsMax: 3.5, possibleOverlapMin: 0.3, possibleApproachAngleMax: 60,
  },
  nac: 'UNRESOLVED unless an evidence-backed Phase I substrate S→methyl attack vector is supplied',
} as const

export function buildRouteFingerprint(
  routeId: string,
  localLabel: LocalRouteLabel,
  path: StaticChannelPath,
  atoms: StaticStructureAtom[],
  methyl: Point3D,
  samSulfur: Point3D | null,
): RouteFingerprint | null {
  if (path.continuity !== 'CONNECTED_TO_REACTION_CENTER' || path.centerline.length < 2 ||
      path.length == null || path.tortuosity == null || path.minimumClearance == null ||
      !path.bottleneck || path.bottleneckPathDistance == null) return null
  const terminalLengths = [...ROUTE_ANALYSIS_PROVENANCE.terminalLengthsAngstrom]
  const vectors = terminalLengths.map((length) => terminalVector(path.centerline, length))
  const selected = vectors[1]
  const samAxis = samSulfur ? normalize(subtract(methyl, samSulfur)) : null
  const liningResidues = classifyLiningResidues(path, atoms)
  return {
    routeId, localLabel, entrance: path.centerline[0], centerline: path.centerline,
    length: path.length, tortuosity: path.tortuosity,
    clearanceProfile: path.profile.map(({ distance, clearance }) => ({ distance, clearance })),
    minimumBottleneck: path.minimumClearance, bottleneck: path.bottleneck,
    bottleneckPathDistance: path.bottleneckPathDistance,
    liningResidues,
    finalDeliveryResidues: liningResidues.filter((residue) => residue.segment === 'FINAL_DELIVERY')
      .map((residue) => residue.identity),
    branchTopology: 'UNBRANCHED_CENTERLINE',
    approachVector: selected,
    approachTerminalLength: ROUTE_ANALYSIS_PROVENANCE.selectedTerminalLengthAngstrom,
    approachRobustnessAngles: selected
      ? vectors.filter((value): value is Point3D => value != null).map((value) => angle(selected, value))
      : [],
    closestApproachToMethyl: minimumPointDistance(path.centerline, methyl),
    terminalCurvatureDegrees: vectors[0] && vectors[2] ? angle(vectors[0], vectors[2]) : null,
    angleToSamAxisDegrees: selected && samAxis ? angle(selected, samAxis) : null,
    nacDelivery: 'UNRESOLVED',
    nacProvenance: ROUTE_ANALYSIS_PROVENANCE.nac,
  }
}

export function compareRouteFingerprints(a: RouteFingerprint, b: RouteFingerprint): RoutePairEvidence {
  const sampleA = resample(a.centerline, 20)
  const sampleB = resample(b.centerline, 20)
  const distances = sampleA.map((point, index) => distance(point, sampleB[index]))
  const rms = Math.sqrt(distances.reduce((sum, value) => sum + value * value, 0) / distances.length)
  const overlap = distances.filter((value) => value <= 2.5).length / distances.length
  // Residue position is the homologous key here; amino-acid identity remains in
  // the fingerprint so substitutions such as F199/G199 are not erased.
  const liningA = new Set(a.liningResidues.map((residue) => homologousSite(residue.identity)))
  const liningB = new Set(b.liningResidues.map((residue) => homologousSite(residue.identity)))
  const shared = [...liningA].filter((identity) => liningB.has(identity)).length
  const union = new Set([...liningA, ...liningB]).size
  const liningOverlap = union ? shared / union : 0
  const approachAngle = a.approachVector && b.approachVector ? angle(a.approachVector, b.approachVector) : null
  const topologyMatches = a.branchTopology === b.branchTopology
  const thresholds = ROUTE_ANALYSIS_PROVENANCE.correspondence
  const homologous = rms <= thresholds.homologousRmsMax && overlap >= thresholds.homologousOverlapMin &&
    topologyMatches && approachAngle != null && approachAngle <= thresholds.homologousApproachAngleMax
  const possible = rms <= thresholds.possibleRmsMax && overlap >= thresholds.possibleOverlapMin &&
    topologyMatches && (approachAngle == null || approachAngle <= thresholds.possibleApproachAngleMax)
  return {
    routeA: a.routeId, routeB: b.routeId, labelA: a.localLabel, labelB: b.localLabel,
    alignedCenterlineOverlap: overlap, centerlineRmsDistance: rms,
    entranceDistance: distance(a.entrance, b.entrance),
    bottleneckDistance: distance(a.bottleneck, b.bottleneck),
    homologousLiningOverlap: liningOverlap, branchTopologyMatches: topologyMatches,
    approachVectorAngleDegrees: approachAngle,
    lateralDisplacement: lateralDisplacement(a, b),
    status: homologous ? 'HOMOLOGOUS_ROUTE' : possible ? 'POSSIBLE_CORRESPONDENCE' : 'NO_CONFIDENT_CORRESPONDENCE',
    reason: homologous ? 'All homologous-route component thresholds passed.'
      : possible ? 'Possible-route component thresholds passed; homologous thresholds did not.'
        : 'One or more possible-correspondence component thresholds failed.',
  }
}

export function bestRouteCorrespondence(
  selected: RouteFingerprint,
  candidates: RouteFingerprint[],
): RoutePairEvidence | null {
  const evidence = candidates.map((candidate) => compareRouteFingerprints(selected, candidate))
    .filter((pair) => pair.status !== 'NO_CONFIDENT_CORRESPONDENCE')
    .sort((left, right) => left.centerlineRmsDistance - right.centerlineRmsDistance)
  return evidence[0] ?? null
}

function classifyLiningResidues(path: StaticChannelPath, atoms: StaticStructureAtom[]) {
  const byResidue = new Map<string, { identity: string; minimumDistance: number; pathDistance: number }>()
  for (const atom of atoms) {
    const identity = `${atom.residueName}${atom.residueNumber} · ${atom.chain}`
    path.profile.forEach((profile) => {
      const separation = distance(atom.point, profile.center)
      const current = byResidue.get(identity)
      if (!current || separation < current.minimumDistance) {
        byResidue.set(identity, { identity, minimumDistance: separation, pathDistance: profile.distance })
      }
    })
  }
  return [...byResidue.values()]
    .filter(({ minimumDistance }) => minimumDistance <= ROUTE_ANALYSIS_PROVENANCE.liningCutoffAngstrom)
    .map(({ identity, minimumDistance, pathDistance }) => ({
      identity, minimumDistance, segment: segmentAt(pathDistance, path.length ?? 0, path.bottleneckPathDistance),
    }))
    .sort((left, right) => left.segment.localeCompare(right.segment) || left.identity.localeCompare(right.identity))
}

function segmentAt(distanceAlongPath: number, length: number, bottleneckDistance: number | null): RouteSegment {
  if (length - distanceAlongPath <= 4) return 'FINAL_DELIVERY'
  if (bottleneckDistance != null && Math.abs(distanceAlongPath - bottleneckDistance) <= 2) return 'BOTTLENECK'
  if (distanceAlongPath <= length * 0.25) return 'EXTERNAL_ENTRANCE'
  return 'CHAMBER'
}

function terminalVector(points: Point3D[], terminalLength: number): Point3D | null {
  const end = points.at(-1)
  if (!end) return null
  let travelled = 0
  for (let index = points.length - 2; index >= 0; index--) {
    travelled += distance(points[index + 1], points[index])
    if (travelled >= terminalLength || index === 0) return normalize(subtract(end, points[index]))
  }
  return null
}

function resample(points: Point3D[], count: number) {
  const cumulative = [0]
  for (let index = 1; index < points.length; index++) cumulative.push(cumulative[index - 1] + distance(points[index - 1], points[index]))
  const total = cumulative.at(-1) ?? 0
  return Array.from({ length: count }, (_, sample) => {
    const target = total * sample / Math.max(1, count - 1)
    let index = 1
    while (index < cumulative.length && cumulative[index] < target) index++
    if (index >= points.length) return points.at(-1)!
    const span = cumulative[index] - cumulative[index - 1]
    const fraction = span > 0 ? (target - cumulative[index - 1]) / span : 0
    return interpolate(points[index - 1], points[index], fraction)
  })
}

function lateralDisplacement(a: RouteFingerprint, b: RouteFingerprint) {
  if (!a.approachVector) return null
  const delta = subtract(b.centerline.at(-1)!, a.centerline.at(-1)!)
  const parallel = dot(delta, a.approachVector)
  return Math.sqrt(Math.max(0, dot(delta, delta) - parallel * parallel))
}

const minimumPointDistance = (points: Point3D[], target: Point3D) => Math.min(...points.map((point) => distance(point, target)))
const homologousSite = (identity: string) => identity.match(/\d+\s*·\s*\S+/)?.[0] ?? identity
const subtract = (a: Point3D, b: Point3D): Point3D => ({ x: a.x - b.x, y: a.y - b.y, z: a.z - b.z })
const dot = (a: Point3D, b: Point3D) => a.x*b.x + a.y*b.y + a.z*b.z
const normalize = (point: Point3D): Point3D | null => {
  const magnitude = Math.hypot(point.x, point.y, point.z)
  return magnitude > 1e-8 ? { x: point.x/magnitude, y: point.y/magnitude, z: point.z/magnitude } : null
}
const angle = (a: Point3D, b: Point3D) => Math.acos(Math.max(-1, Math.min(1, dot(a, b)))) * 180 / Math.PI
const interpolate = (a: Point3D, b: Point3D, t: number): Point3D => ({ x: a.x+(b.x-a.x)*t, y: a.y+(b.y-a.y)*t, z: a.z+(b.z-a.z)*t })
const distance = (a: Point3D, b: Point3D) => Math.hypot(a.x-b.x, a.y-b.y, a.z-b.z)
