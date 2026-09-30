import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Mettl7FunctionalContactsPage } from './Mettl7FunctionalContactsPage'

describe('Mettl7FunctionalContactsPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => ({
      runKey: 'CONTACTS', databaseScope: { dockingRuns: 713, runsWithPoses: 680, poseRecords: 540289, distinctLigands: 467025, cohortContactRows: 1971, cohortLigands: 74, dcmbContactRows: 16 },
      headlines: { mettl7aLys151Preference: 'ether-dominant', mettl7bHydrophobicFacePreference: 'phenyl-dominant', mettl7aMixedPharmacophorePattern: 'WEAK', dcmbChemistryMatches7aGrammar: 'PARTIAL', functionalGroupSelectivityMechanism: 'PARTIAL' },
      residueFunctionalGroups: [{ paralog: '7A', residue: 'LYS151', residueNumber: 151, functionalGroup: 'ether oxygen', contactCount: 23, uniqueLigands: 23, fraction: 23 / 68, denominator: 68, powerFlag: 'ADEQUATE_FOR_SCREENING' }],
      dcmbContacts: [{ ligand: 'DCMB-R', paralog: '7A', condition: '7A_WT_APO', enantiomer: 'R', residue: 'PHE43', residueNumber: 43, functionalGroup: 'halogen atom', minimumDistanceA: 3.4079 }],
    }) })))
  })

  it('separates database scope from the 74-ligand cohort and shows DCMB', async () => {
    render(<Mettl7FunctionalContactsPage />)
    expect(await screen.findByText('713')).toBeInTheDocument()
    expect(screen.getByText(/frozen 74-ligand paired native-box cohort/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Where DCMB fits' })).toBeInTheDocument()
    expect(screen.getByText('DCMB-R')).toBeInTheDocument()
    expect(screen.getByText('ether oxygen')).toBeInTheDocument()
  })
})
