import type { CSSProperties } from 'react'
import type {
  Residue,
  ResidueAnalysis,
  ResidueEvidence,
} from '../../../api/types'

const ONE_LETTER: Record<string, string> = {
  ALA: 'A', ARG: 'R', ASN: 'N', ASP: 'D', CYS: 'C',
  GLN: 'Q', GLU: 'E', GLY: 'G', HIS: 'H', ILE: 'I',
  LEU: 'L', LYS: 'K', MET: 'M', PHE: 'F', PRO: 'P',
  SER: 'S', THR: 'T', TRP: 'W', TYR: 'Y', VAL: 'V',
}

interface Props {
  residues: Residue[]
  pocketResidueIds: Set<number>
  chosenPocketResidueIds?: Set<number>
  biohubSelected?: boolean
  directContactResidueIds?: Set<number>
  neighborResidueIds: Set<number>
  residueAnalysis: Map<number, ResidueAnalysis>
  residueEvidence?: Map<number, ResidueEvidence>
  selectedResidueId: number | null
  onResidueSelect: (residue: Residue) => void
}

export function ResidueSequence({
  residues,
  pocketResidueIds,
  chosenPocketResidueIds = new Set(),
  biohubSelected = false,
  directContactResidueIds = new Set(),
  neighborResidueIds,
  residueAnalysis,
  residueEvidence = new Map(),
  selectedResidueId,
  onResidueSelect,
}: Props) {
  return (
    <div className="sequence-strip" role="list" aria-label="Structure residues">
      {residues.map((residue) => {
        const inSelectedPocket = pocketResidueIds.has(residue.id)
        const inChosenPocket = chosenPocketResidueIds.has(residue.id)
        const analysis = residueAnalysis.get(residue.id)
        const evidence = residueEvidence.get(residue.id)
        const label = `${residue.residueName} ${residue.residueNumber}, `
            + `chain ${residue.chain}`
        const contactTitle = analysis
          ? `${label} · score < ${formatScore(analysis.contactScoreThreshold)}: `
              + `${formatPercent(
                analysis.scoreFilteredContactingLigandFraction,
              )} contacted ligands `
              + `(${analysis.scoreFilteredContactingLigandCount.toLocaleString()}`
              + ` / ${analysis.scoreFilteredLigandCount.toLocaleString()})`
          : label
        const title = evidence?.score == null
          ? contactTitle
          : `${contactTitle} · ESMC constraint ${evidence.score.toFixed(2)}`
        const className = [
          'sequence-residue',
          !biohubSelected && inSelectedPocket ? 'highlighted' : '',
          biohubSelected && inChosenPocket ? 'highlighted' : '',
          biohubSelected
              && directContactResidueIds.has(residue.id)
              && !inChosenPocket
            ? 'biohub-only'
            : '',
          directContactResidueIds.has(residue.id)
            ? 'biohub-direct-contact'
            : '',
          analysis ? 'has-docking-analysis' : '',
          evidence ? 'has-constraint-evidence' : '',
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
            style={analysis || evidence ? {
              '--contact-rate': `${Math.min(
                100,
                Math.max(
                  0,
                  (analysis?.scoreFilteredContactingLigandFraction ?? 0) * 100,
                ),
              )}%`,
              '--constraint-strength': constraintStrength(evidence?.score),
            } as ResidueSignalStyle : undefined}
            onClick={() => onResidueSelect(residue)}
          >
            <span>{ONE_LETTER[residue.residueName] ?? 'X'}</span>
          </button>
        )
      })}
    </div>
  )
}

type ResidueSignalStyle = CSSProperties & {
  '--contact-rate': string
  '--constraint-strength': string
}

function formatPercent(fraction: number) {
  return `${(fraction * 100).toFixed(2)}%`
}

function formatScore(score: number) {
  return Number.isInteger(score) ? score.toFixed(0) : score.toFixed(1)
}

function constraintStrength(score: number | null | undefined) {
  if (score == null) return '0'
  return `${Math.min(1, Math.max(0.12, score / 15))}`
}
