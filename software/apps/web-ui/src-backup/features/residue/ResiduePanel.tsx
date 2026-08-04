import { useMemo, useState } from 'react'
import type {
  AtomDistance,
  DockingRunSummary,
  PocketDetails,
  Residue,
  ResidueAnalysis,
  ResidueEvidence,
  ResidueNeighborhood,
  ResidueScoreBand,
} from '../../api/types'
import { useApiQuery } from '../../api/hooks'
import { AtomDistanceControl } from './components/AtomDistanceControl'
import { ResidueSequence } from './components/ResidueSequence'
import { ResidueContactLegend } from './components/ResidueContactLegend'
import { ResidueGuide } from './components/ResidueGuide'
import { ResidueDockingAnalysis } from './components/ResidueDockingAnalysis'
import { ResidueConstraintEvidence } from './components/ResidueConstraintEvidence'
import { BiohubPocketEvidence } from '../pocket/BiohubPocketEvidence'

interface Props {
  structureId: number
  residues: Residue[]
  highlightedResidueIds: Set<number>
  chosenPocketResidueIds?: Set<number>
  directContactResidueIds?: Set<number>
  activePocket: PocketDetails | null
  pocketLoading: boolean
  dockingRuns: DockingRunSummary[]
  selectedRunId: number | null
  onRunSelect: (runId: number) => void
  residueAnalysis: Map<number, ResidueAnalysis>
  analysisLoading: boolean
  residueEvidence?: Map<number, ResidueEvidence>
  evidenceLoading?: boolean
}

export function ResiduePanel({
  structureId,
  residues,
  highlightedResidueIds,
  chosenPocketResidueIds = new Set(),
  directContactResidueIds = new Set(),
  activePocket,
  pocketLoading,
  dockingRuns,
  selectedRunId,
  onRunSelect,
  residueAnalysis,
  analysisLoading,
  residueEvidence = new Map(),
  evidenceLoading = false,
}: Props) {
  const [query, setQuery] = useState('')
  const [selectedResidue, setSelectedResidue] = useState<Residue | null>(null)
  const [cutoff, setCutoff] = useState(6)
  const [measurementNeighborId, setMeasurementNeighborId] =
    useState<number | null>(null)
  const [firstAtomChoice, setFirstAtomChoice] = useState<string | null>(null)
  const [secondAtomChoice, setSecondAtomChoice] = useState<string | null>(null)
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
  const measurementNeighbor =
    neighborhood.data?.neighbors.find(
      (neighbor) => neighbor.id === measurementNeighborId,
    )
    ?? neighborhood.data?.neighbors.find(
      (neighbor) =>
        neighborhood.data?.selectedAtomNames.includes('SG')
        && neighbor.atomNames.includes('SG'),
    )
    ?? neighborhood.data?.neighbors[0]
    ?? null
  const firstAtom = validAtomChoice(
    firstAtomChoice,
    neighborhood.data?.selectedAtomNames ?? [],
  )
  const secondAtom = validAtomChoice(
    secondAtomChoice,
    measurementNeighbor?.atomNames ?? [],
  )
  const atomDistance = useApiQuery<AtomDistance>(
    selectedResidue && measurementNeighbor && firstAtom && secondAtom
      ? `/api/structures/${structureId}/residues/${selectedResidue.id}`
          + `/distance?toResidueId=${measurementNeighbor.id}`
          + `&fromAtom=${encodeURIComponent(firstAtom)}`
          + `&toAtom=${encodeURIComponent(secondAtom)}`
      : null,
  )
  const scoreBands = useApiQuery<ResidueScoreBand[]>(
    selectedRunId && selectedResidue
      ? `/api/docking-runs/${selectedRunId}/residue-score-bands`
          + `?residueId=${selectedResidue.id}`
      : null,
  )
  const filtered = useMemo(() => {
    const normalized = query.trim().toUpperCase()
    if (!normalized) return residues
    return residues.filter((residue) =>
      `${residue.residueName}${residue.residueNumber}`.includes(normalized),
    )
  }, [query, residues])
  const contactScoreThreshold =
    residueAnalysis.values().next().value?.contactScoreThreshold ?? null

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
        <span>
          {pocketLoading ? (
            'Loading residue evidence…'
          ) : activePocket ? (
            activePocket.evidence ? (
              <>
                Chosen fpocket with{' '}
                <strong>
                  {activePocket.evidence.directContactResidueCount}{' '}
                  {activePocket.evidence.ligandCcd} direct contacts
                </strong>
              </>
            ) : (
              <>
                Highlighting {activePocket.residues.length} residues for{' '}
                <strong>
                  {activePocket.source} {activePocket.pocketNumber}
                </strong>
              </>
            )
          ) : (
            'Select a pocket to highlight its residues'
          )}
        </span>
        {dockingRuns.length > 0 && (
          <label className="run-selector">
            <span>Docking run</span>
            <select
              value={selectedRunId ?? ''}
              onChange={(event) => onRunSelect(Number(event.target.value))}
            >
              {dockingRuns.map((run) => (
                <option key={run.id} value={run.id}>
                  Run {run.id} · {run.totalLigandCount.toLocaleString()} ligands
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
      <ResidueContactLegend threshold={contactScoreThreshold} />
      {activePocket?.evidence && (
        <BiohubPocketEvidence evidence={activePocket.evidence} />
      )}
      <ResidueGuide
        showChosenPocket={
          highlightedResidueIds.size > 0 || chosenPocketResidueIds.size > 0
        }
        showBiohub={activePocket?.source === 'BIOHUB'}
        showDocking={residueAnalysis.size > 0}
        showConstraint={residueEvidence.size > 0}
        showNeighbors={neighborResidueIds.size > 0}
      />
      <ResidueSequence
        residues={filtered}
        pocketResidueIds={highlightedResidueIds}
        chosenPocketResidueIds={chosenPocketResidueIds}
        biohubSelected={activePocket?.source === 'BIOHUB'}
        directContactResidueIds={directContactResidueIds}
        neighborResidueIds={neighborResidueIds}
        residueAnalysis={residueAnalysis}
        residueEvidence={residueEvidence}
        selectedResidueId={selectedResidue?.id ?? null}
        onResidueSelect={setSelectedResidue}
      />
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
                  className={[
                    'neighbor-card',
                    measurementNeighbor?.id === neighbor.id
                      ? 'measuring'
                      : '',
                  ].filter(Boolean).join(' ')}
                  key={neighbor.id}
                  type="button"
                  onClick={() => setMeasurementNeighborId(neighbor.id)}
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
          <ResidueDockingAnalysis
            analysis={residueAnalysis.get(selectedResidue.id) ?? null}
            analysisLoading={analysisLoading}
            bands={scoreBands.data ?? []}
            bandsLoading={scoreBands.loading}
          />
          <ResidueConstraintEvidence
            evidence={residueEvidence.get(selectedResidue.id) ?? null}
            loading={evidenceLoading}
          />
          {neighborhood.data
              && measurementNeighbor
              && firstAtom
              && secondAtom && (
            <footer className="residue-detail-footer">
              <AtomDistanceControl
                neighborhood={neighborhood.data}
                neighbor={measurementNeighbor}
                firstAtom={firstAtom}
                secondAtom={secondAtom}
                distance={atomDistance.data}
                loading={atomDistance.loading}
                onNeighborChange={setMeasurementNeighborId}
                onFirstAtomChange={setFirstAtomChoice}
                onSecondAtomChange={setSecondAtomChoice}
              />
            </footer>
          )}
        </div>
      )}
    </section>
  )
}

function validAtomChoice(
  choice: string | null,
  atomNames: string[],
): string | null {
  if (choice && atomNames.includes(choice)) return choice
  if (atomNames.includes('SG')) return 'SG'
  if (atomNames.includes('CA')) return 'CA'
  return atomNames[0] ?? null
}
