import { describe, expect, it } from 'vitest'
import type { PocketDetails } from '../../../api/types'
import {
  entranceCandidateLabel,
  pocketResidueAlphaCarbons,
  viewerCameraResetKey,
} from './MolstarViewer'

describe('pocketResidueAlphaCarbons', () => {
  it('keeps only pocket residue C-alpha coordinates', () => {
    const pdb = [
      'ATOM      1  CA  LEU A 145      10.000  11.000  12.000  1.00 20.00           C',
      'ATOM      2  CB  LEU A 145      10.500  11.500  12.500  1.00 20.00           C',
      'ATOM      3  CA  GLY A 146      20.000  21.000  22.000  1.00 20.00           C',
    ].join('\n')
    const pocket = {
      residues: [{
        id: 1,
        chain: 'A',
        residueNumber: 145,
        insertionCode: '',
        residueName: 'LEU',
      }],
    } as PocketDetails

    expect(pocketResidueAlphaCarbons(pdb, pocket)).toEqual([
      { x: 10, y: 11, z: 12 },
    ])
  })
})

describe('mechanistic viewer evidence labels', () => {
  it('keeps candidate status and calls clearance a local bottleneck', () => {
    expect(entranceCandidateLabel(0, 2.2)).toBe(
      'PRIMARY_CANDIDATE · local bottleneck 2.2 Å',
    )
    expect(entranceCandidateLabel(1, 1.85)).toContain('ALTERNATIVE_CANDIDATE')
    expect(entranceCandidateLabel(2, 1.1)).toContain('LOCAL_OPENING')
  })

  it('preserves the camera reset identity when switching A/B pockets', () => {
    expect(viewerCameraResetKey('mechanistic', 3)).toBe(
      viewerCameraResetKey('mechanistic', 32),
    )
    expect(viewerCameraResetKey('classic', 3)).not.toBe(
      viewerCameraResetKey('classic', 32),
    )
  })
})
