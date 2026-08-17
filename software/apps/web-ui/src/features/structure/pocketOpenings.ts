import type { AlphaSphereView, Point3D } from '../../api/types'

export interface PocketOpening {
  center: Point3D
  direction: Point3D
  radius: number
  clearance: number
}

/** Web port of pocket-viewer's directional heavy-atom clearance detector. */
export function detectPocketOpenings(
  spheres: AlphaSphereView[],
  origin: Point3D,
  heavyAtoms: Point3D[],
  maximumOpenings = 3,
): PocketOpening[] {
  const candidates = Array.from({ length: 256 }, (_, index) => {
    const direction = fibonacciDirection(index)
    const extent = Math.max(...spheres.map((sphere) =>
      dot(subtract(sphere.center, origin), direction) + sphere.radius,
    ))
    const clearance = directionalClearance(origin, direction, extent, heavyAtoms)
    return { direction, extent, clearance }
  }).sort((left, right) => right.clearance - left.clearance)

  const openings: PocketOpening[] = []
  for (const candidate of candidates) {
    if (openings.some((opening) => dot(opening.direction, candidate.direction) > 0.55)) continue
    openings.push({
      center: add(origin, scale(candidate.direction, candidate.extent)),
      direction: candidate.direction,
      radius: Math.max(0.8, Math.min(4, candidate.clearance)),
      clearance: candidate.clearance,
    })
    if (openings.length === maximumOpenings) break
  }
  return openings
}

export function pdbHeavyAtoms(pdb: string): Point3D[] {
  const atoms: Point3D[] = []
  for (const line of pdb.split(/\r?\n/)) {
    if (!line.startsWith('ATOM') && !line.startsWith('HETATM')) continue
    const element = line.slice(76, 78).trim().toUpperCase()
      || line.slice(12, 16).trim().replace(/^\d+/, '')[0]?.toUpperCase()
    if (element === 'H') continue
    const point = {
      x: Number(line.slice(30, 38)),
      y: Number(line.slice(38, 46)),
      z: Number(line.slice(46, 54)),
    }
    if ([point.x, point.y, point.z].every(Number.isFinite)) atoms.push(point)
  }
  return atoms
}

function fibonacciDirection(index: number): Point3D {
  const y = 1 - 2 * (index + 0.5) / 256
  const radial = Math.sqrt(Math.max(0, 1 - y * y))
  const angle = index * Math.PI * (3 - Math.sqrt(5))
  return { x: Math.cos(angle) * radial, y, z: Math.sin(angle) * radial }
}

function directionalClearance(origin: Point3D, direction: Point3D, extent: number, atoms: Point3D[]) {
  let clearance = Number.POSITIVE_INFINITY
  for (const atom of atoms) {
    const relative = subtract(atom, origin)
    const projection = dot(relative, direction)
    if (projection < extent * 0.35 || projection > extent + 5) continue
    const perpendicular = subtract(relative, scale(direction, projection))
    clearance = Math.min(clearance, Math.hypot(perpendicular.x, perpendicular.y, perpendicular.z) - 1.7)
  }
  return Number.isFinite(clearance) ? Math.max(0.25, clearance) : 4
}

const dot = (a: Point3D, b: Point3D) => a.x*b.x + a.y*b.y + a.z*b.z
const subtract = (a: Point3D, b: Point3D): Point3D => ({ x:a.x-b.x, y:a.y-b.y, z:a.z-b.z })
const add = (a: Point3D, b: Point3D): Point3D => ({ x:a.x+b.x, y:a.y+b.y, z:a.z+b.z })
const scale = (a: Point3D, factor: number): Point3D => ({ x:a.x*factor, y:a.y*factor, z:a.z*factor })
