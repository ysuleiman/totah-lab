import { describe, expect, it } from 'vitest'
import { detectPocketOpenings } from './pocketOpenings'

describe('detectPocketOpenings', () => {
  it('returns a solvent-facing mouth on the pocket boundary', () => {
    const openings = detectPocketOpenings([
      { index: 0, center: { x: 0, y: 0, z: 0 }, radius: 2 },
    ], { x: 0, y: 0, z: 0 }, [], 1)

    expect(openings).toHaveLength(1)
    expect(Math.hypot(
      openings[0].center.x,
      openings[0].center.y,
      openings[0].center.z,
    )).toBeCloseTo(2)
    expect(openings[0].clearance).toBe(4)
  })
})
