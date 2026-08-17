import type { AlphaSphereView, Point3D } from '../../api/types'
import type { PocketOpening } from './pocketOpenings'
import {
  reconstructStaticChannels,
  type PathContinuity,
  type StaticChannelPath,
  type StaticStructureAtom,
} from './staticChannelAnalysis'

export interface RadiusSweepPoint {
  probeRadius: number
  continuity: PathContinuity
  connectedToReactionCenter: boolean
  minimumClearance: number | null
  pathLength: number | null
  tortuosity: number | null
}

export interface EntranceRadiusSweep {
  entranceIndex: number
  samples: RadiusSweepPoint[]
  criticalPassableRadius: number | null
  criticalRadiusResolution: number
}

export interface LocalClearanceMinimum {
  profileIndex: number
  distanceAlongPath: number
  clearance: number
  center: Point3D
}

export interface ChannelPhaseDiagram {
  radii: number[]
  entrances: EntranceRadiusSweep[]
  criticalAnyRadius: number | null
  radiusResolution: number
  accessibilityMeaning: 'STATIC_GEOMETRIC_ACCESSIBILITY'
  permeabilityEquivalent: false
  algorithm: 'ALPHA_SPHERE_GRAPH_RADIUS_SWEEP_V1'
}

export function defaultProbeRadii() {
  return Array.from({ length: 51 }, (_, index) =>
    Number((0.5 + index * 0.05).toFixed(2)))
}

export function buildChannelPhaseDiagram(
  spheres: AlphaSphereView[],
  openings: PocketOpening[],
  reactionTarget: Point3D | null,
  chamberCenter: Point3D,
  atoms: StaticStructureAtom[],
  radii = defaultProbeRadii(),
): ChannelPhaseDiagram {
  const orderedRadii = [...radii].sort((first, second) => first - second)
  const pathsByRadius = orderedRadii.map((radius) => reconstructStaticChannels(
    spheres, openings, reactionTarget, chamberCenter, radius, atoms,
  ))
  const resolution = orderedRadii.length > 1
    ? Math.min(...orderedRadii.slice(1).map((radius, index) =>
        radius - orderedRadii[index]))
    : 0
  const entrances = openings.map((_, entranceIndex) => {
    const samples = orderedRadii.map((probeRadius, radiusIndex) => {
      const path = pathsByRadius[radiusIndex][entranceIndex]
      return toSweepPoint(probeRadius, path)
    })
    return {
      entranceIndex,
      samples,
      criticalPassableRadius: largestConnectedRadius(samples),
      criticalRadiusResolution: resolution,
    }
  })
  return {
    radii: orderedRadii,
    entrances,
    criticalAnyRadius: largestConnectedRadius(
      orderedRadii.map((probeRadius, radiusIndex) => ({
        probeRadius,
        connectedToReactionCenter: pathsByRadius[radiusIndex].some(
          (path) => path.continuity === 'CONNECTED_TO_REACTION_CENTER',
        ),
      })),
    ),
    radiusResolution: resolution,
    accessibilityMeaning: 'STATIC_GEOMETRIC_ACCESSIBILITY',
    permeabilityEquivalent: false,
    algorithm: 'ALPHA_SPHERE_GRAPH_RADIUS_SWEEP_V1',
  }
}

export function localClearanceMinima(path: StaticChannelPath): LocalClearanceMinimum[] {
  return path.profile.flatMap((point, index, profile) => {
    const before = profile[index - 1]?.clearance ?? Number.POSITIVE_INFINITY
    const after = profile[index + 1]?.clearance ?? Number.POSITIVE_INFINITY
    return point.clearance <= before && point.clearance <= after
      ? [{
          profileIndex: index,
          distanceAlongPath: point.distance,
          clearance: point.clearance,
          center: point.center,
        }]
      : []
  })
}

function toSweepPoint(probeRadius: number, path: StaticChannelPath): RadiusSweepPoint {
  return {
    probeRadius,
    continuity: path.continuity,
    connectedToReactionCenter:
      path.continuity === 'CONNECTED_TO_REACTION_CENTER',
    minimumClearance: path.minimumClearance,
    pathLength: path.length,
    tortuosity: path.tortuosity,
  }
}

function largestConnectedRadius(
  samples: Array<{ probeRadius: number; connectedToReactionCenter: boolean }>,
) {
  return samples.reduce<number | null>((largest, sample) =>
    sample.connectedToReactionCenter ? sample.probeRadius : largest, null)
}
