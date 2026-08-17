import type { AlphaSphereView, Point3D } from '../../api/types'
import type { PocketOpening } from './pocketOpenings'

export type PathContinuity =
  | 'CONNECTED_TO_REACTION_CENTER'
  | 'CONNECTED_TO_CHAMBER_ONLY'
  | 'DEAD_END'
  | 'UNRESOLVED'

export type ProbeSurvival = 'passable' | 'constricted' | 'disconnected'

export interface StaticStructureAtom {
  point: Point3D
  chain: string
  residueNumber: number
  residueName: string
}

export interface ChannelProfilePoint {
  distance: number
  clearance: number
  center: Point3D
}

export interface StaticChannelPath {
  entranceIndex: number
  continuity: PathContinuity
  centerline: Point3D[]
  profile: ChannelProfilePoint[]
  length: number | null
  tortuosity: number | null
  minimumClearance: number | null
  bottleneck: Point3D | null
  bottleneckDistanceToTarget: number | null
  bottleneckPathDistance: number | null
  bottleneckResidues: string[]
  probeRadius: number
  probeSurvival: ProbeSurvival
  algorithm: 'ALPHA_SPHERE_GRAPH_V1'
}

export function reconstructStaticChannels(
  spheres: AlphaSphereView[],
  openings: PocketOpening[],
  reactionTarget: Point3D | null,
  chamberCenter: Point3D,
  probeRadius: number,
  atoms: StaticStructureAtom[] = [],
): StaticChannelPath[] {
  if (!reactionTarget || spheres.length === 0) {
    return openings.map((_, entranceIndex) => unresolved(entranceIndex, probeRadius))
  }
  const usable = spheres.filter((sphere) => sphere.radius >= probeRadius)
  const graph = buildGraph(usable)
  return openings.map((opening, entranceIndex) => {
    const start = nearestIndex(usable, opening.center)
    if (start < 0) return unresolved(entranceIndex, probeRadius)
    if (distance(usable[start].center, opening.center) > usable[start].radius + 2.5) {
      return unresolved(entranceIndex, probeRadius)
    }
    const targetNodes = usable.flatMap((sphere, index) =>
      distance(sphere.center, reactionTarget) <= sphere.radius + 1.8 ? [index] : [],
    )
    const chamberNodes = usable.flatMap((sphere, index) =>
      distance(sphere.center, chamberCenter) <= sphere.radius + 2 ? [index] : [],
    )
    const targetPath = shortestPath(graph, usable, start, new Set(targetNodes))
    const chamberPath = targetPath.length === 0
      ? shortestPath(graph, usable, start, new Set(chamberNodes))
      : []
    const indices = targetPath.length > 0 ? targetPath : chamberPath
    if (indices.length === 0) {
      return {
        ...unresolved(entranceIndex, probeRadius),
        continuity: graph[start]?.length ? 'DEAD_END' : 'DEAD_END',
        probeSurvival: 'disconnected',
      }
    }
    const pathSpheres = indices.map((index) => usable[index])
    const centerline = [opening.center, ...pathSpheres.map((sphere) => sphere.center)]
    if (targetPath.length > 0) centerline.push(reactionTarget)
    const profile = buildProfile(centerline, pathSpheres, opening.clearance)
    const minimum = profile.reduce((best, point) =>
      point.clearance < best.clearance ? point : best,
    )
    const length = profile.at(-1)?.distance ?? 0
    const direct = distance(centerline[0], centerline.at(-1)!)
    const minimumClearance = minimum.clearance
    return {
      entranceIndex,
      continuity: targetPath.length > 0
        ? 'CONNECTED_TO_REACTION_CENTER'
        : 'CONNECTED_TO_CHAMBER_ONLY',
      centerline,
      profile,
      length,
      tortuosity: direct > 0 ? length / direct : 1,
      minimumClearance,
      bottleneck: minimum.center,
      bottleneckDistanceToTarget: distance(minimum.center, reactionTarget),
      bottleneckPathDistance: minimum.distance,
      bottleneckResidues: nearbyResidues(minimum.center, minimumClearance, atoms),
      probeRadius,
      probeSurvival: minimumClearance >= probeRadius + 0.35
        ? 'passable' : 'constricted',
      algorithm: 'ALPHA_SPHERE_GRAPH_V1',
    }
  })
}

function unresolved(entranceIndex: number, probeRadius: number): StaticChannelPath {
  return {
    entranceIndex,
    continuity: 'UNRESOLVED',
    centerline: [], profile: [], length: null, tortuosity: null,
    minimumClearance: null, bottleneck: null,
    bottleneckDistanceToTarget: null, bottleneckPathDistance: null,
    bottleneckResidues: [], probeRadius,
    probeSurvival: 'disconnected', algorithm: 'ALPHA_SPHERE_GRAPH_V1',
  }
}

function buildGraph(spheres: AlphaSphereView[]): number[][] {
  const graph = spheres.map(() => [] as number[])
  for (let first = 0; first < spheres.length; first++) {
    for (let second = first + 1; second < spheres.length; second++) {
      const separation = distance(spheres[first].center, spheres[second].center)
      if (separation <= spheres[first].radius + spheres[second].radius + 0.35) {
        graph[first].push(second)
        graph[second].push(first)
      }
    }
  }
  return graph
}

function shortestPath(
  graph: number[][],
  spheres: AlphaSphereView[],
  start: number,
  targets: Set<number>,
): number[] {
  if (targets.size === 0) return []
  const distances = spheres.map(() => Number.POSITIVE_INFINITY)
  const previous = spheres.map(() => -1)
  const visited = new Set<number>()
  distances[start] = 0
  while (visited.size < spheres.length) {
    let current = -1
    for (let index = 0; index < spheres.length; index++) {
      if (!visited.has(index) && (current < 0 || distances[index] < distances[current])) current = index
    }
    if (current < 0 || !Number.isFinite(distances[current])) return []
    if (targets.has(current)) {
      const path: number[] = []
      for (let node = current; node >= 0; node = previous[node]) path.unshift(node)
      return path
    }
    visited.add(current)
    for (const neighbor of graph[current]) {
      const cost = distance(spheres[current].center, spheres[neighbor].center)
        + 0.2 / Math.max(0.1, spheres[neighbor].radius)
      if (distances[current] + cost < distances[neighbor]) {
        distances[neighbor] = distances[current] + cost
        previous[neighbor] = current
      }
    }
  }
  return []
}

function buildProfile(
  centerline: Point3D[],
  spheres: AlphaSphereView[],
  entranceClearance: number,
): ChannelProfilePoint[] {
  let travelled = 0
  return centerline.map((center, index) => {
    if (index > 0) travelled += distance(centerline[index - 1], center)
    const sphereIndex = Math.max(0, Math.min(spheres.length - 1, index - 1))
    return {
      distance: travelled,
      clearance: index === 0
        ? entranceClearance
        : spheres[sphereIndex]?.radius ?? 0,
      center,
    }
  })
}

function nearbyResidues(center: Point3D, clearance: number, atoms: StaticStructureAtom[]) {
  const cutoff = clearance + 2.2
  return [...new Set(atoms
    .filter((atom) => distance(atom.point, center) <= cutoff)
    .map((atom) => `${atom.residueName}${atom.residueNumber} · ${atom.chain}`))]
    .sort()
}

function nearestIndex(spheres: AlphaSphereView[], point: Point3D) {
  let nearest = -1
  let best = Number.POSITIVE_INFINITY
  spheres.forEach((sphere, index) => {
    const value = distance(sphere.center, point)
    if (value < best) { best = value; nearest = index }
  })
  return nearest
}

const distance = (a: Point3D, b: Point3D) =>
  Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z)
