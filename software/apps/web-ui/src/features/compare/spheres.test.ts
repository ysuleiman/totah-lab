import { describe, expect, it } from 'vitest'
import type { AlphaSphereView } from '../../api/types'
import { interpolateSpheres } from './spheres'

const original: AlphaSphereView[] = [
  { index: 0, center: { x: 0, y: 0, z: 0 }, radius: 1.2 },
  { index: 1, center: { x: 2, y: 4, z: 6 }, radius: 0.9 },
]

const alignedCenters = [
  { x: 10, y: 10, z: 10 },
  { x: 4, y: 8, z: 12 },
]

describe('interpolateSpheres', () => {
  it('lerps centers while keeping radii untouched', () => {
    const halfway = interpolateSpheres(original, alignedCenters, 0.5)

    expect(halfway).toHaveLength(2)
    expect(halfway[0].center).toEqual({ x: 5, y: 5, z: 5 })
    expect(halfway[1].center).toEqual({ x: 3, y: 6, z: 9 })
    expect(halfway[0].radius).toBe(1.2)
    expect(halfway[1].radius).toBe(0.9)
    expect(halfway[0].index).toBe(0)
    expect(halfway[1].index).toBe(1)
  })

  it('returns the original centers at progress 0 and aligned at 1', () => {
    const atStart = interpolateSpheres(original, alignedCenters, 0)
    expect(atStart[0].center).toEqual({ x: 0, y: 0, z: 0 })
    expect(atStart[1].center).toEqual({ x: 2, y: 4, z: 6 })

    const atEnd = interpolateSpheres(original, alignedCenters, 1)
    expect(atEnd[0].center).toEqual({ x: 10, y: 10, z: 10 })
    expect(atEnd[1].center).toEqual({ x: 4, y: 8, z: 12 })
  })

  it('clamps out-of-range progress', () => {
    const clamped = interpolateSpheres(original, alignedCenters, 2)
    expect(clamped[0].center).toEqual({ x: 10, y: 10, z: 10 })
    expect(clamped[0].radius).toBe(1.2)
  })

  it('returns the original spheres when lengths differ', () => {
    const result = interpolateSpheres(original, [alignedCenters[0]], 0.5)
    expect(result).toBe(original)
  })
})
