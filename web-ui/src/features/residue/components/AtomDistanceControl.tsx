import type {
  AtomDistance,
  ResidueNeighbor,
  ResidueNeighborhood,
} from '../../../api/types'

interface Props {
  neighborhood: ResidueNeighborhood
  neighbor: ResidueNeighbor
  firstAtom: string
  secondAtom: string
  distance: AtomDistance | null
  loading: boolean
  onNeighborChange: (residueId: number) => void
  onFirstAtomChange: (atomName: string) => void
  onSecondAtomChange: (atomName: string) => void
}

export function AtomDistanceControl({
  neighborhood,
  neighbor,
  firstAtom,
  secondAtom,
  distance,
  loading,
  onNeighborChange,
  onFirstAtomChange,
  onSecondAtomChange,
}: Props) {
  return (
    <div className="atom-distance-control">
      <span className="atom-distance-label">Atom distance</span>
      <select
        aria-label="Selected residue atom"
        value={firstAtom}
        onChange={(event) => onFirstAtomChange(event.target.value)}
      >
        {neighborhood.selectedAtomNames.map((atomName) => (
          <option key={atomName}>{atomName}</option>
        ))}
      </select>
      <span>→</span>
      <select
        aria-label="Neighbor residue"
        value={neighbor.id}
        onChange={(event) => onNeighborChange(Number(event.target.value))}
      >
        {neighborhood.neighbors.map((candidate) => (
          <option key={candidate.id} value={candidate.id}>
            {candidate.residueName} {candidate.residueNumber}
          </option>
        ))}
      </select>
      <select
        aria-label="Neighbor residue atom"
        value={secondAtom}
        onChange={(event) => onSecondAtomChange(event.target.value)}
      >
        {neighbor.atomNames.map((atomName) => (
          <option key={atomName}>{atomName}</option>
        ))}
      </select>
      <strong className="atom-distance-value">
        {loading ? '…' : distance ? `${distance.distance.toFixed(2)} Å` : '—'}
      </strong>
    </div>
  )
}
