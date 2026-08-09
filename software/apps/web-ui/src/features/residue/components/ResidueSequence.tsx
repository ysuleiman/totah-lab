import type { CSSProperties } from 'react'
import type {
  Residue,
  ResidueAnalysis,
  ResidueEvidence,
} from '../../../api/types'
import {
  CATEGORY_COLORS,
} from '../../ligands/residueCategory'

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
  ligandContactResidueIds?: Set<number>
  neighborResidueIds: Set<number>
  residueAnalysis: Map<number, ResidueAnalysis>
  residueEvidence?: Map<number, ResidueEvidence>
  selectedResidueId: number | null
  onResidueSelect: (residue: Residue) => void
  /**
   * Tints chosen-pocket residues (that are not ligand contacts) by
   * physicochemical category. Contacts keep their green/red meaning.
   */
  colorPocketByCategory?: boolean
}

export function ResidueSequence({
  residues,
  pocketResidueIds,
  chosenPocketResidueIds = new Set(),
  biohubSelected = false,
  directContactResidueIds = new Set(),
  ligandContactResidueIds = new Set(),
  neighborResidueIds,
  residueAnalysis,
  residueEvidence = new Map(),
  selectedResidueId,
  onResidueSelect,
  colorPocketByCategory = false,
}: Props) {
  return (
    <div className="sequence-strip" role="list" aria-label="Structure residues">
      {residues.map((residue) => {
        const inSelectedPocket = pocketResidueIds.has(residue.id)
        const inChosenPocket = chosenPocketResidueIds.has(residue.id)
        const isLigandContact = ligandContactResidueIds.has(residue.id)
        const chemistry = colorPocketByCategory
            && inChosenPocket
            && !isLigandContact
          ? residue.chemistry
          : null
        const category = chemistry?.colorKey ?? null
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
        let title = evidence?.score == null
          ? contactTitle
          : `${contactTitle} · ESMC constraint ${evidence.score.toFixed(2)}`
        if (chemistry?.primaryLabel) title += ` · ${chemistry.primaryLabel}`
        const className = [
          'sequence-residue',
          !biohubSelected && inSelectedPocket ? 'highlighted' : '',
          biohubSelected && inChosenPocket ? 'highlighted' : '',
          biohubSelected
              && directContactResidueIds.has(residue.id)
              && !inChosenPocket
            ? 'biohub-only'
            : '',
          biohubSelected
              && directContactResidueIds.has(residue.id)
              && inChosenPocket
            ? 'biohub-direct-inside'
            : '',
          directContactResidueIds.has(residue.id)
            ? 'biohub-direct-contact'
            : '',
          isLigandContact
            ? (inChosenPocket
              ? 'ligand-contact-inside'
              : 'ligand-contact')
            : '',
          category ? 'category' : '',
          analysis ? 'has-docking-analysis' : '',
          evidence ? 'has-constraint-evidence' : '',
          neighborResidueIds.has(residue.id) ? 'spatial-neighbor' : '',
          selectedResidueId === residue.id ? 'selected' : '',
        ].filter(Boolean).join(' ')

        const style: ResidueSignalStyle = {}
        if (analysis || evidence) {
          style['--contact-rate'] = `${Math.min(
            100,
            Math.max(
              0,
              (analysis?.scoreFilteredContactingLigandFraction ?? 0) * 100,
            ),
          )}%`
          style['--constraint-strength'] = constraintStrength(evidence?.score)
        }
        if (category) style['--category-color'] = CATEGORY_COLORS[category]

        return (
          <button
            className={className}
            key={residue.id}
            type="button"
            role="listitem"
            aria-label={label}
            aria-pressed={selectedResidueId === residue.id}
            title={title}
            style={Object.keys(style).length > 0 ? style : undefined}
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
  '--contact-rate'?: string
  '--constraint-strength'?: string
  '--category-color'?: string
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
