import { useMemo, useState } from 'react'
import { useApiQuery } from '../../api/hooks'

interface Scope { dockingRuns: number; runsWithPoses: number; poseRecords: number; distinctLigands: number; cohortContactRows: number; cohortLigands: number; dcmbContactRows: number }
interface Headlines { mettl7aLys151Preference: string; mettl7bHydrophobicFacePreference: string; mettl7aMixedPharmacophorePattern: string; dcmbChemistryMatches7aGrammar: string; functionalGroupSelectivityMechanism: string }
interface ResidueGroup { paralog: string; residue: string; residueNumber: number; functionalGroup: string; contactCount: number; uniqueLigands: number; fraction: number; denominator: number; powerFlag: string }
interface DcmbContact { ligand: string; paralog: string; condition: string; enantiomer: string; residue: string; residueNumber: number; functionalGroup: string; minimumDistanceA: number }
interface Report { runKey: string; databaseScope: Scope; headlines: Headlines; residueFunctionalGroups: ResidueGroup[]; dcmbContacts: DcmbContact[] }

const percent = (value: number) => `${(value * 100).toFixed(1)}%`

export function Mettl7FunctionalContactsPage() {
  const { data, loading, error } = useApiQuery<Report>('/api/reports/mettl7-functional-contacts')
  const [paralog, setParalog] = useState('7A')
  const [residue, setResidue] = useState('ALL')
  const residues = useMemo(() => data ? [...new Set(data.residueFunctionalGroups.filter((row) => row.paralog === paralog).map((row) => row.residue))] : [], [data, paralog])
  const rows = useMemo(() => data ? data.residueFunctionalGroups.filter((row) => row.paralog === paralog && (residue === 'ALL' || row.residue === residue)) : [], [data, paralog, residue])

  if (loading) return <article className="dcmb-report"><p>Loading contact chemistry…</p></article>
  if (error || !data) return <article className="dcmb-report"><h1>METTL7 contact chemistry</h1><p role="alert">{error instanceof Error ? error.message : 'Report unavailable.'}</p></article>

  return <article className="dcmb-report functional-contact-report">
    <header className="dcmb-report-hero functional-contact-hero">
      <div><p className="dcmb-kicker">Live PostgreSQL analysis · no new docking</p><h1>Ligand-side contact chemistry</h1><p className="dcmb-lede">Which functional groups are presented to METTL7A and METTL7B residues, with DCMB retained as a dedicated external comparator.</p></div>
      <div className="dcmb-gate-stack"><span className="dcmb-gate">74-ligand paired cohort</span><span className="dcmb-gate insufficient">Mechanism · {data.headlines.functionalGroupSelectivityMechanism}</span></div>
    </header>

    <section className="database-scope-grid" aria-label="Database scope">
      <div><strong>{data.databaseScope.dockingRuns.toLocaleString()}</strong><span>database docking runs</span></div>
      <div><strong>{data.databaseScope.runsWithPoses.toLocaleString()}</strong><span>runs with poses</span></div>
      <div><strong>{data.databaseScope.poseRecords.toLocaleString()}</strong><span>pose records</span></div>
      <div><strong>{data.databaseScope.distinctLigands.toLocaleString()}</strong><span>distinct ligand labels</span></div>
    </section>
    <p className="scope-warning"><strong>Scope:</strong> the database totals above are not the analysis denominator. Functional-group inference uses the frozen {data.databaseScope.cohortLigands}-ligand paired native-box cohort; DCMB contributes {data.databaseScope.dcmbContactRows} separate contact rows.</p>

    <section className="dcmb-section"><div className="dcmb-section-heading"><p>Functional grammar</p><h2>Resolved headline signals</h2></div><div className="functional-headline-grid"><article><span>7A · K151</span><strong>{data.headlines.mettl7aLys151Preference}</strong></article><article><span>7B · F36/M40/L145</span><strong>{data.headlines.mettl7bHydrophobicFacePreference}</strong></article><article><span>Two-part 7A pattern</span><strong>{data.headlines.mettl7aMixedPharmacophorePattern}</strong></article></div></section>

    <section className="dcmb-section dcmb-specialized"><div className="dcmb-section-heading"><p>Dedicated comparator</p><h2>Where DCMB fits</h2></div><div className="dcmb-callout"><strong>DCMB matches the 7A grammar: {data.headlines.dcmbChemistryMatches7aGrammar}</strong><p>DCMB supplies dichlorophenyl/hydrophobic chemistry but no oxygen acceptor, so it is an outlier to the strict hydrophobic-plus-K151-acceptor chemotype. R and S remain separate below.</p></div><div className="contact-table-wrap"><table><thead><tr><th>DCMB</th><th>Paralog</th><th>Residue</th><th>Ligand group</th><th>Distance</th></tr></thead><tbody>{data.dcmbContacts.map((row) => <tr key={`${row.ligand}-${row.paralog}-${row.residue}`}><td>{row.ligand}</td><td>{row.paralog}</td><td>{row.residue}</td><td>{row.functionalGroup}</td><td>{row.minimumDistanceA.toFixed(3)} Å</td></tr>)}</tbody></table></div></section>

    <section className="dcmb-section"><div className="dcmb-section-heading"><p>Residue × functional group</p><h2>Browse the persisted assignments</h2></div><div className="contact-filters"><div>{['7A', '7B'].map((value) => <button className={paralog === value ? 'active' : ''} key={value} onClick={() => { setParalog(value); setResidue('ALL') }}>{value}</button>)}</div><label>Residue<select value={residue} onChange={(event) => setResidue(event.target.value)}><option value="ALL">All residues</option>{residues.map((value) => <option key={value}>{value}</option>)}</select></label></div><div className="contact-table-wrap"><table><thead><tr><th>Residue</th><th>Functional group</th><th>Contacts</th><th>Unique ligands</th><th>Fraction</th><th>Power</th></tr></thead><tbody>{rows.map((row) => <tr key={`${row.paralog}-${row.residue}-${row.functionalGroup}`}><td>{row.residue}</td><td>{row.functionalGroup}</td><td>{row.contactCount}/{row.denominator}</td><td>{row.uniqueLigands}</td><td>{percent(row.fraction)}</td><td><span className={`power-flag ${row.powerFlag === 'UNDERPOWERED' ? 'low' : ''}`}>{row.powerFlag.replaceAll('_', ' ')}</span></td></tr>)}</tbody></table></div></section>

    <footer className="dcmb-report-footer"><strong>Evidence boundary</strong><p>Ligand chemistry ↔ docking contact geometry is not affinity and is not causal selectivity. DCMB is external to n=74 and is never folded into the paired cohort statistics.</p><small>{data.runKey}</small></footer>
  </article>
}
