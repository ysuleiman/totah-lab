import type { Residue } from '../../../api/types'

const ONE_LETTER: Record<string, string> = {
  ALA: 'A', ARG: 'R', ASN: 'N', ASP: 'D', CYS: 'C',
  GLN: 'Q', GLU: 'E', GLY: 'G', HIS: 'H', ILE: 'I',
  LEU: 'L', LYS: 'K', MET: 'M', PHE: 'F', PRO: 'P',
  SER: 'S', THR: 'T', TRP: 'W', TYR: 'Y', VAL: 'V',
}

interface Props {
  residues: Residue[]
  pocketResidueIds: Set<number>
  neighborResidueIds: Set<number>
  selectedResidueId: number | null
  onResidueSelect: (residue: Residue) => void
}

export function ResidueSequence({
  residues,
  pocketResidueIds,
  neighborResidueIds,
  selectedResidueId,
  onResidueSelect,
}: Props) {
  return (
    <div className="sequence-strip" role="list" aria-label="Structure residues">
      {residues.map((residue) => {
        const label = `${residue.residueName} ${residue.residueNumber}, `
            + `chain ${residue.chain}`
        const className = [
          'sequence-residue',
          pocketResidueIds.has(residue.id) ? 'highlighted' : '',
          neighborResidueIds.has(residue.id) ? 'spatial-neighbor' : '',
          selectedResidueId === residue.id ? 'selected' : '',
        ].filter(Boolean).join(' ')

        return (
          <button
            className={className}
            key={residue.id}
            type="button"
            role="listitem"
            aria-label={label}
            aria-pressed={selectedResidueId === residue.id}
            title={label}
            onClick={() => onResidueSelect(residue)}
          >
            {ONE_LETTER[residue.residueName] ?? 'X'}
          </button>
        )
      })}
    </div>
  )
}
