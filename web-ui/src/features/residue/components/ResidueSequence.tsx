import type { CSSProperties } from 'react'
import type { Residue, ResidueAnalysis } from '../../../api/types'

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
  residueAnalysis: Map<number, ResidueAnalysis>
  selectedResidueId: number | null
  onResidueSelect: (residue: Residue) => void
}

export function ResidueSequence({
  residues,
  pocketResidueIds,
  neighborResidueIds,
  residueAnalysis,
  selectedResidueId,
  onResidueSelect,
}: Props) {
  return (
    <div className="sequence-strip" role="list" aria-label="Structure residues">
      {residues.map((residue) => {
        const analysis = residueAnalysis.get(residue.id)
        const label = `${residue.residueName} ${residue.residueNumber}, `
            + `chain ${residue.chain}`
        const title = analysis
          ? `${label} · score < ${formatScore(analysis.contactScoreThreshold)}: `
              + `${formatPercent(
                analysis.scoreFilteredContactingLigandFraction,
              )} contacted ligands `
              + `(${analysis.scoreFilteredContactingLigandCount.toLocaleString()}`
              + ` / ${analysis.scoreFilteredLigandCount.toLocaleString()})`
          : label
        const className = [
          'sequence-residue',
          pocketResidueIds.has(residue.id) ? 'highlighted' : '',
          analysis ? 'has-docking-analysis' : '',
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
            title={title}
            style={analysis ? {
              '--contact-rate': `${Math.min(
                100,
                Math.max(
                  0,
                  analysis.scoreFilteredContactingLigandFraction * 100,
                ),
              )}%`,
            } as ContactRateStyle : undefined}
            onClick={() => onResidueSelect(residue)}
          >
            <span>{ONE_LETTER[residue.residueName] ?? 'X'}</span>
          </button>
        )
      })}
    </div>
  )
}

type ContactRateStyle = CSSProperties & {
  '--contact-rate': string
}

function formatPercent(fraction: number) {
  return `${(fraction * 100).toFixed(2)}%`
}

function formatScore(score: number) {
  return Number.isInteger(score) ? score.toFixed(0) : score.toFixed(1)
}
