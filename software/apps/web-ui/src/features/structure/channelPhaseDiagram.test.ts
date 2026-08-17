import { describe, expect, it } from 'vitest'
import {
  buildChannelPhaseDiagram,
  defaultProbeRadii,
  localClearanceMinima,
} from './channelPhaseDiagram'
import type { StaticChannelPath } from './staticChannelAnalysis'

describe('channel probe-radius phase diagram', () => {
  it('uses a dense reproducible 0.05 Å radius series', () => {
    const radii = defaultProbeRadii()
    expect(radii[0]).toBe(0.5)
    expect(radii.at(-1)).toBe(3)
    expect(radii[1] - radii[0]).toBeCloseTo(0.05)
  })

  it('reports per-entrance r* and r*ANY without merging entrance identity', () => {
    const spheres = [0, 2, 4].map((x, index) => ({
      index, center: { x, y: 0, z: 0 }, radius: index === 1 ? 1 : 1.5,
    }))
    const openings = [{
      center: { x: 0, y: 0, z: 0 }, direction: { x: 1, y: 0, z: 0 },
      radius: 1.4, clearance: 1.4,
    }]
    const diagram = buildChannelPhaseDiagram(
      spheres, openings, { x: 5, y: 0, z: 0 },
      { x: 2, y: 0, z: 0 }, [], [0.8, 1, 1.2],
    )
    expect(diagram.entrances).toHaveLength(1)
    expect(diagram.entrances[0].criticalPassableRadius).toBe(1)
    expect(diagram.criticalAnyRadius).toBe(1)
    expect(diagram.radiusResolution).toBeCloseTo(0.2)
    expect(diagram.permeabilityEquivalent).toBe(false)
  })

  it('finds all local profile minima', () => {
    const path = {
      profile: [2, 1, 2, 0.8, 2].map((clearance, index) => ({
        clearance, distance: index, center: { x: index, y: 0, z: 0 },
      })),
    } as StaticChannelPath
    expect(localClearanceMinima(path).map((minimum) => minimum.clearance))
      .toEqual([1, 0.8])
  })
})
