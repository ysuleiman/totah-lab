import { useMemo, useState } from 'react'
import type { PocketDetails, Residue } from '../../api/types'

interface Props {
  residues: Residue[]
  highlightedResidueIds: Set<number>
  activePocket: PocketDetails | null
  pocketLoading: boolean
}

export function ResiduePanel({
  residues,
  highlightedResidueIds,
  activePocket,
  pocketLoading,
}: Props) {
  const [query, setQuery] = useState('')
  const filtered = useMemo(() => {
    const normalized = query.trim().toUpperCase()
    if (!normalized) return residues
    return residues.filter((residue) =>
      `${residue.residueName}${residue.residueNumber}`.includes(normalized),
    )
  }, [query, residues])

  return (
    <section className="panel residue-panel" aria-labelledby="residues-heading">
      <div className="panel-heading residue-heading">
        <div>
          <p className="eyebrow">Primary sequence</p>
          <h2 id="residues-heading">Residues</h2>
        </div>
        <label className="residue-search">
          <span className="sr-only">Filter residues</span>
          <input
            type="search"
            placeholder="Find MET1…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>
      </div>
      <div className="residue-context">
        {pocketLoading ? (
          'Loading pocket residues…'
        ) : activePocket ? (
          <>
            Highlighting {activePocket.residues.length} residues for{' '}
            <strong>{activePocket.source} {activePocket.pocketNumber}</strong>
          </>
        ) : (
          'Select a pocket to highlight its residues'
        )}
      </div>
      <div className="residue-grid" role="list" aria-label="Structure residues">
        {filtered.map((residue) => {
          const highlighted = highlightedResidueIds.has(residue.id)
          return (
            <div
              className={`residue-chip${highlighted ? ' highlighted' : ''}`}
              key={residue.id}
              role="listitem"
              title={`${residue.residueName} ${residue.residueNumber}, chain ${residue.chain}`}
            >
              <strong>{residue.residueName}</strong>
              <span>{residue.residueNumber}</span>
            </div>
          )
        })}
      </div>
    </section>
  )
}
