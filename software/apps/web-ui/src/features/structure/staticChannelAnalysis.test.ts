import { describe, expect, it } from 'vitest'
import { reconstructStaticChannels } from './staticChannelAnalysis'

const opening = {
  center: { x: 0, y: 0, z: 0 }, direction: { x: 1, y: 0, z: 0 },
  radius: 2, clearance: 2,
}

describe('reconstructStaticChannels', () => {
  it('reproducibly targets the SAM methyl region', () => {
    const spheres = [0, 2, 4, 6].map((x, index) => ({
      index, center: { x, y: 0, z: 0 }, radius: 1.2,
    }))
    const first = reconstructStaticChannels(spheres, [opening], { x: 7, y: 0, z: 0 }, { x: 4, y: 0, z: 0 }, 1)
    const second = reconstructStaticChannels(spheres, [opening], { x: 7, y: 0, z: 0 }, { x: 4, y: 0, z: 0 }, 1)
    expect(first).toEqual(second)
    expect(first[0].continuity).toBe('CONNECTED_TO_REACTION_CENTER')
    expect(first[0].centerline.at(-1)).toEqual({ x: 7, y: 0, z: 0 })
  })

  it('does not infer reaction-center continuity from pocket overlap alone', () => {
    const spheres = [{ index: 0, center: { x: 0, y: 0, z: 0 }, radius: 1 }]
    const result = reconstructStaticChannels(spheres, [opening], { x: 20, y: 0, z: 0 }, { x: 0, y: 0, z: 0 }, 0.8)
    expect(result[0].continuity).toBe('CONNECTED_TO_CHAMBER_ONLY')
  })

  it('disconnects a path when the probe exceeds its bottleneck', () => {
    const spheres = [0, 2, 4].map((x, index) => ({
      index, center: { x, y: 0, z: 0 }, radius: index === 1 ? 0.9 : 1.5,
    }))
    const passable = reconstructStaticChannels(spheres, [opening], { x: 5, y: 0, z: 0 }, { x: 2, y: 0, z: 0 }, 0.8)
    const blocked = reconstructStaticChannels(spheres, [opening], { x: 5, y: 0, z: 0 }, { x: 2, y: 0, z: 0 }, 1.1)
    expect(passable[0].continuity).toBe('CONNECTED_TO_REACTION_CENTER')
    expect(blocked[0].continuity).not.toBe('CONNECTED_TO_REACTION_CENTER')
  })

  it('identifies residues around the minimum-clearance point', () => {
    const spheres = [0, 2, 4].map((x, index) => ({
      index, center: { x, y: 0, z: 0 }, radius: index === 1 ? 0.9 : 1.5,
    }))
    const result = reconstructStaticChannels(spheres, [opening], { x: 5, y: 0, z: 0 }, { x: 2, y: 0, z: 0 }, 0.8, [{
      point: { x: 2, y: 2, z: 0 }, chain: 'A', residueNumber: 98, residueName: 'ASP',
    }])
    expect(result[0].bottleneckResidues).toContain('ASP98 · A')
  })

  it('includes the independently measured entrance clearance in the profile', () => {
    const narrowOpening = { ...opening, clearance: 0.7 }
    const spheres = [0, 2, 4].map((x, index) => ({
      index, center: { x, y: 0, z: 0 }, radius: 1.5,
    }))
    const result = reconstructStaticChannels(spheres, [narrowOpening], { x: 5, y: 0, z: 0 }, { x: 2, y: 0, z: 0 }, 0.6)
    expect(result[0].minimumClearance).toBe(0.7)
    expect(result[0].bottleneckPathDistance).toBe(0)
  })
})
