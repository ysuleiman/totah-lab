import { type FormEvent, useMemo, useState } from 'react'
import type { PocketDetails, PocketSummary, Structure } from '../../api/types'
import { useApiQuery } from '../../api/hooks'
import { AsyncState } from '../../components/AsyncState'
import { ResiduePanel } from '../residue/ResiduePanel'
import { ResidueGuide } from '../residue/components/ResidueGuide'
import { useResidueEvidence } from '../residue/hooks/useResidueEvidence'

interface Props {
  leftStructureId: number
  rightStructureId: number
  onNavigate: (path: string) => void
}

export function StructureComparisonPage({
  leftStructureId,
  rightStructureId,
  onNavigate,
}: Props) {
  const leftStructure = useApiQuery<Structure>(`/api/structures/${leftStructureId}`)
  const rightStructure = useApiQuery<Structure>(`/api/structures/${rightStructureId}`)
  const [leftInput, setLeftInput] = useState(String(leftStructureId))
  const [rightInput, setRightInput] = useState(String(rightStructureId))
  const [neighborCutoff, setNeighborCutoff] = useState(6)

  function submitStructures(event: FormEvent) {
    event.preventDefault()
    const left = Number(leftInput)
    const right = Number(rightInput)
    if (isPositiveInteger(left) && isPositiveInteger(right)) {
      onNavigate(`/structures/${left}/compare/${right}`)
    }
  }

  if ((leftStructure.loading && !leftStructure.data)
      || (rightStructure.loading && !rightStructure.data)) {
    return <AsyncState loading title="Loading structures" />
  }

  if (leftStructure.error || rightStructure.error
      || !leftStructure.data || !rightStructure.data) {
    return (
      <AsyncState
        title="Comparison unavailable"
        message={(leftStructure.error ?? rightStructure.error)?.message}
        onRetry={() => {
          leftStructure.retry()
          rightStructure.retry()
        }}
      />
    )
  }

  const left = leftStructure.data
  const right = rightStructure.data

  return (
    <div className="structure-comparison workspace">
      <header className="structure-comparison-hero">
        <div>
          <p className="eyebrow">Residue comparison</p>
          <h1>Compare structures</h1>
          <p>Two complete residue maps with chosen-pocket and BioHub evidence.</p>
        </div>
        <form className="comparison-picker" onSubmit={submitStructures}>
          <label>
            Structure A
            <input aria-label="Structure A" inputMode="numeric" value={leftInput} onChange={(event) => setLeftInput(event.target.value)} />
          </label>
          <span>with</span>
          <label>
            Structure B
            <input aria-label="Structure B" inputMode="numeric" value={rightInput} onChange={(event) => setRightInput(event.target.value)} />
          </label>
          <button type="submit">Compare</button>
          <button type="button" className="secondary" onClick={() => onNavigate(`/structures/${right.id}/compare/${left.id}`)}>Swap</button>
        </form>
      </header>

      <div className="comparison-shared-guide">
        <ResidueGuide
          showChosenPocket
          showBiohub
          showDocking={false}
          showConstraint
          showNeighbors
        />
        <label className="comparison-shared-cutoff">
          <span>Neighbor cutoff</span>
          <input
            aria-label="Neighbor cutoff"
            type="range"
            min="2"
            max="12"
            step="0.5"
            value={neighborCutoff}
            onChange={(event) => setNeighborCutoff(Number(event.currentTarget.value))}
            onInput={(event) => setNeighborCutoff(Number(event.currentTarget.value))}
          />
          <strong>{neighborCutoff.toFixed(1)} Å</strong>
        </label>
      </div>

      <StructureResidueMap label="A" structure={left} neighborCutoff={neighborCutoff} />
      <StructureResidueMap label="B" structure={right} neighborCutoff={neighborCutoff} />
    </div>
  )
}

function StructureResidueMap({ label, structure, neighborCutoff }: {
  label: 'A' | 'B'
  structure: Structure
  neighborCutoff: number
}) {
  const pockets = useApiQuery<PocketSummary[]>(structure.pocketsUrl)
  const chosenPocketId = structure.chosenPocket?.id ?? null
  const biohubPocketId = pockets.data?.find((pocket) =>
    pocket.source === 'BIOHUB' && pocket.evidence?.ligandCcd === 'SAM',
  )?.id ?? pockets.data?.find((pocket) => pocket.source === 'BIOHUB')?.id ?? null
  const chosenPocket = useApiQuery<PocketDetails>(
    chosenPocketId ? `/api/pockets/${chosenPocketId}` : null,
  )
  const biohubPocket = useApiQuery<PocketDetails>(
    biohubPocketId ? `/api/pockets/${biohubPocketId}` : null,
  )
  const residueEvidence = useResidueEvidence(structure.id)
  const chosenResidueIds = useMemo(
    () => new Set(chosenPocket.data?.residues.map((residue) => residue.id) ?? []),
    [chosenPocket.data],
  )
  const directContactIds = useMemo(
    () => new Set(biohubPocket.data?.evidence?.directContactResidueIds ?? []),
    [biohubPocket.data],
  )
  const gene = structure.receptor.geneName ?? structure.receptor.targetName
  const protein = structure.receptor.proteinName ?? structure.receptor.targetName

  return (
    <section className="panel comparison-residue-map" aria-labelledby={`structure-${label}-residues`}>
      <header className="comparison-table-heading">
        <span className={`structure-label structure-label-${label.toLowerCase()}`}>{label}</span>
        <div>
          <p className="eyebrow">Structure {structure.id} · {structure.residues.length} residues</p>
          <h2 id={`structure-${label}-residues`}>{gene}</h2>
          <p>{protein}{structure.receptor.uniProtId ? ` · ${structure.receptor.uniProtId}` : ''}</p>
        </div>
        <span className="comparison-pocket-caption">
          {structure.chosenPocket
            ? `Chosen ${structure.chosenPocket.source} ${structure.chosenPocket.pocketNumber}`
            : 'No chosen pocket'}
        </span>
      </header>
      {(pockets.loading || chosenPocket.loading || (biohubPocketId != null && biohubPocket.loading)) ? (
        <AsyncState loading compact title="Loading residue map" />
      ) : pockets.error || chosenPocket.error || biohubPocket.error ? (
        <AsyncState compact title="Residue evidence unavailable" message={(pockets.error ?? chosenPocket.error ?? biohubPocket.error)?.message} />
      ) : (
        <div className="comparison-residue-content">
          <ResiduePanel
            structureId={structure.id}
            residues={structure.residues}
            highlightedResidueIds={chosenResidueIds}
            chosenPocketResidueIds={chosenResidueIds}
            directContactResidueIds={directContactIds}
            activePocket={biohubPocket.data ?? chosenPocket.data}
            pocketLoading={false}
            dockingRuns={[]}
            selectedRunId={null}
            onRunSelect={() => undefined}
            residueAnalysis={new Map()}
            analysisLoading={false}
            residueEvidence={residueEvidence.byResidueId}
            evidenceLoading={residueEvidence.loading}
            contextNote="Select a residue to inspect spatial neighbors"
            bare
            neighborhoodOnly
            hideGuide
            neighborCutoff={neighborCutoff}
            hideNeighborCutoff
          />
        </div>
      )}
    </section>
  )
}

function isPositiveInteger(value: number) {
  return Number.isSafeInteger(value) && value > 0
}
