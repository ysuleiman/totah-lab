import { describe, expect, it } from 'vitest'
import type { StaticChannelPath, StaticStructureAtom } from './staticChannelAnalysis'
import { bestRouteCorrespondence, buildRouteFingerprint, compareRouteFingerprints } from './routeTopologyAnalysis'

const path = (offset = 0): StaticChannelPath => ({
  entranceIndex: 0,
  continuity: 'CONNECTED_TO_REACTION_CENTER',
  centerline: [0, 2, 4, 6, 8].map((z) => ({ x: offset, y: 0, z })),
  profile: [0, 2, 4, 6, 8].map((distance) => ({
    distance, clearance: distance === 2 ? 1.2 : 2.2, center: { x: offset, y: 0, z: distance },
  })),
  length: 8, tortuosity: 1, minimumClearance: 1.2,
  bottleneck: { x: offset, y: 0, z: 2 }, bottleneckDistanceToTarget: 6,
  bottleneckPathDistance: 2, bottleneckResidues: ['PHE43 · A'], probeRadius: 1.2,
  probeSurvival: 'constricted', algorithm: 'ALPHA_SPHERE_GRAPH_V1',
})

const atoms: StaticStructureAtom[] = [
  { point: { x: 2, y: 0, z: 1 }, chain: 'A', residueNumber: 43, residueName: 'PHE' },
  { point: { x: 2, y: 0, z: 7 }, chain: 'A', residueNumber: 199, residueName: 'GLY' },
]

describe('route topology analysis', () => {
  it('builds segmented fingerprints and keeps NAC unresolved without substrate sulfur geometry', () => {
    const result = buildRouteFingerprint('A-PRIMARY', 'PRIMARY', path(), atoms,
      { x: 0, y: 0, z: 8 }, { x: 0, y: 0, z: 7 })!
    expect(result.approachTerminalLength).toBe(3)
    expect(result.finalDeliveryResidues).toContain('GLY199 · A')
    expect(result.nacDelivery).toBe('UNRESOLVED')
    expect(result.angleToSamAxisDegrees).toBeCloseTo(0)
  })

  it('finds cross-label correspondence from component evidence', () => {
    const a = buildRouteFingerprint('A-PRIMARY', 'PRIMARY', path(), atoms,
      { x: 0, y: 0, z: 8 }, { x: 0, y: 0, z: 7 })!
    const bAlternative = buildRouteFingerprint('B-ALTERNATIVE', 'ALTERNATIVE', path(0.2), atoms,
      { x: 0.2, y: 0, z: 8 }, { x: 0.2, y: 0, z: 7 })!
    const bPrimary = buildRouteFingerprint('B-PRIMARY', 'PRIMARY', path(5), [],
      { x: 5, y: 0, z: 8 }, { x: 5, y: 0, z: 7 })!
    const selected = bestRouteCorrespondence(a, [bPrimary, bAlternative])!
    expect(selected.routeB).toBe('B-ALTERNATIVE')
    expect(selected.status).toBe('HOMOLOGOUS_ROUTE')
    expect(compareRouteFingerprints(a, bPrimary).status).toBe('NO_CONFIDENT_CORRESPONDENCE')
  })
})
