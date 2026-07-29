import { useMemo, useState } from 'react'
import type {
  PocketDetails,
  Residue,
  ResidueNeighborhood,
} from '../../api/types'
import { useApiQuery } from '../../api/hooks'

const ONE_LETTER: Record<string, string> = {
  ALA: 'A', ARG: 'R', ASN: 'N', ASP: 'D', CYS: 'C',
  GLN: 'Q', GLU: 'E', GLY: 'G', HIS: 'H', ILE: 'I',
  LEU: 'L', LYS: 'K', MET: 'M', PHE: 'F', PRO: 'P',
  SER: 'S', THR: 'T', TRP: 'W', TYR: 'Y', VAL: 'V',
}

interface Props {
  structureId: number
  residues: Residue[]
  highlightedResidueIds: Set<number>
  activePocket: PocketDetails | null
  pocketLoading: boolean
}

export function ResiduePanel({
  structureId,
  residues,
  highlightedResidueIds,
  activePocket,
  pocketLoading,
}: Props) {
  const [query, setQuery] = useState('')
  const [selectedResidue, setSelectedResidue] = useState<Residue | null>(null)
  const [cutoff, setCutoff] = useState(6)
  const neighborhood = useApiQuery<ResidueNeighborhood>(
    selectedResidue
      ? `/api/structures/${structureId}/residues/${selectedResidue.id}`
          + `/neighbors?cutoff=${cutoff}`
      : null,
  )
  const neighborResidueIds = useMemo(
    () => new Set(
      neighborhood.data?.neighbors.map((neighbor) => neighbor.id) ?? [],
    ),
    [neighborhood.data],
  )
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
      <div className="sequence-strip" role="list" aria-label="Structure residues">
        {filtered.map((residue) => {
          const highlighted = highlightedResidueIds.has(residue.id)
          const selected = selectedResidue?.id === residue.id
          const neighbor = neighborResidueIds.has(residue.id)
          const label = `${residue.residueName} ${residue.residueNumber}, `
              + `chain ${residue.chain}`
          return (
            <button
              className={[
                'sequence-residue',
                highlighted ? 'highlighted' : '',
                neighbor ? 'spatial-neighbor' : '',
                selected ? 'selected' : '',
              ].filter(Boolean).join(' ')}
              key={residue.id}
              type="button"
              role="listitem"
              aria-label={label}
              aria-pressed={selected}
              title={label}
              onClick={() => setSelectedResidue(residue)}
            >
              {ONE_LETTER[residue.residueName] ?? 'X'}
            </button>
          )
        })}
      </div>
      {selectedResidue && (
        <div className="residue-detail">
          <div className="residue-detail-title">
            <div>
              <p className="eyebrow">Selected residue</p>
              <h3>
                {selectedResidue.residueName} {selectedResidue.residueNumber}
                <small>Chain {selectedResidue.chain}</small>
              </h3>
            </div>
            <label className="cutoff-control">
              <span>
                Neighbor cutoff
                <strong>{cutoff.toFixed(1)} Å</strong>
              </span>
              <input
                type="range"
                min="2"
                max="12"
                step="0.5"
                value={cutoff}
                onChange={(event) => setCutoff(Number(event.target.value))}
              />
            </label>
          </div>
          <div className="neighbor-list" aria-live="polite">
            {neighborhood.loading ? (
              <p>Calculating neighbors from the structure artifact…</p>
            ) : neighborhood.error ? (
              <button type="button" onClick={neighborhood.retry}>
                Neighbor calculation failed. Try again
              </button>
            ) : neighborhood.data?.neighbors.length ? (
              neighborhood.data.neighbors.map((neighbor) => (
                <button
                  className="neighbor-card"
                  key={neighbor.id}
                  type="button"
                  onClick={() => setSelectedResidue(neighbor)}
                >
                  <span>
                    <strong>{neighbor.residueName}</strong>
                    {neighbor.residueNumber}
                  </span>
                  <small>{neighbor.distance.toFixed(2)} Å</small>
                </button>
              ))
            ) : (
              <p>No residues within {cutoff.toFixed(1)} Å.</p>
            )}
          </div>
        </div>
      )}
    </section>
  )
}
