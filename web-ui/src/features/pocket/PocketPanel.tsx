import type { PocketSummary } from '../../api/types'
import { AsyncState } from '../../components/AsyncState'

interface Props {
  pockets: PocketSummary[]
  chosenPocketId: number | null
  selectedPocketId: number | null
  loading: boolean
  error: Error | null
  onPocketSelect: (pocketId: number) => void
  onRetry: () => void
}

export function PocketPanel({
  pockets,
  chosenPocketId,
  selectedPocketId,
  loading,
  error,
  onPocketSelect,
  onRetry,
}: Props) {
  const chosenPocket = pockets.find((pocket) => pocket.id === chosenPocketId)
  const inspectingChosen = selectedPocketId === chosenPocketId
  const groups = groupPockets(pockets)
  const biohubPockets = groups.find((group) => group.source === 'BIOHUB')
    ?.pockets ?? []
  const identicalBiohubSets = haveIdenticalEvidenceSets(biohubPockets)

  return (
    <section className="panel pocket-panel" aria-labelledby="pockets-heading">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Derived analysis</p>
          <h2 id="pockets-heading">Detected pockets</h2>
        </div>
        <span className="count-badge">{pockets.length}</span>
      </div>
      {chosenPocket && (
        <button
          className="chosen-pocket-summary"
          type="button"
          onClick={() => onPocketSelect(chosenPocket.id)}
        >
          <span>
            <small>Chosen pocket</small>
            <strong>
              {chosenPocket.source} {chosenPocket.pocketNumber}
            </strong>
          </span>
          <span>
            <small>Source score</small>
            <strong>{formatMetric(chosenPocket.score)}</strong>
          </span>
          <span>
            <small>Volume</small>
            <strong>{formatVolume(chosenPocket.volume)}</strong>
          </span>
          <span>
            <small>
              {chosenPocket.source === 'P2RANK'
                ? 'Probability'
                : 'Druggability'}
            </small>
            <strong>
              {chosenPocket.source === 'P2RANK'
                ? formatMetric(chosenPocket.probability)
                : formatMetric(chosenPocket.druggabilityScore)}
            </strong>
          </span>
          <span className="chosen-pocket-action">
            {inspectingChosen ? 'Inspecting now' : 'Inspect'}
          </span>
        </button>
      )}
      {loading && pockets.length === 0 ? (
        <AsyncState loading title="Loading pockets" compact />
      ) : error ? (
        <AsyncState
          title="Pockets unavailable"
          message={error.message}
          onRetry={onRetry}
          compact
        />
      ) : pockets.length === 0 ? (
        <AsyncState title="No pockets detected" compact />
      ) : (
        <div className="pocket-groups">
          {groups.map((group) => (
            <section className="pocket-source-group" key={group.source}>
              <header>
                <strong>{group.source}</strong>
                <span>{group.pockets.length}</span>
              </header>
              <div className="pocket-list">
                {group.pockets.map((pocket) => (
                  <PocketCard
                    key={pocket.id}
                    pocket={pocket}
                    selected={pocket.id === selectedPocketId}
                    chosen={pocket.id === chosenPocketId}
                    onSelect={onPocketSelect}
                  />
                ))}
              </div>
              {group.source === 'BIOHUB' && identicalBiohubSets && (
                <p className="biohub-comparison-note">
                  SAM and SAH produce the same direct-contact evidence.
                </p>
              )}
            </section>
          ))}
        </div>
      )}
    </section>
  )
}

interface PocketCardProps {
  pocket: PocketSummary
  selected: boolean
  chosen: boolean
  onSelect: (pocketId: number) => void
}

function PocketCard({
  pocket,
  selected,
  chosen,
  onSelect,
}: PocketCardProps) {
  const evidence = pocket.evidence
  return (
    <button
      className={`pocket-card${selected ? ' selected' : ''}`}
      type="button"
      aria-pressed={selected}
      onClick={() => onSelect(pocket.id)}
    >
      <span className="pocket-index">
        {evidence?.ligandCcd
          ?? pocket.pocketNumber.toString().padStart(2, '0')}
      </span>
      <span className="pocket-copy">
        <strong>
          {evidence
            ? `${evidence.ligandCcd} contact evidence`
            : pocket.source}
        </strong>
        <small>
          {evidence
            ? `${evidence.shellResidueCount} wall · `
              + `${evidence.directContactResidueCount} direct`
            : pocket.volume == null
              ? 'volume unavailable'
              : `volume ${pocket.volume.toFixed(1)} Å³`}
        </small>
        {evidence && (
          <small>
            {evidence.chosenPocketOverlapCount}
            /{evidence.shellResidueCount} wall overlap with chosen fpocket
          </small>
        )}
      </span>
      <span className="pocket-score">
        {evidence
          ? formatMetric(evidence.interfacePtm)
          : formatMetric(pocket.score)}
        <small>{evidence ? 'interface pTM' : 'score'}</small>
        <small className="secondary-score">
          {evidence
            ? `${evidence.directChosenPocketOverlapCount}`
              + `/${evidence.directContactResidueCount} direct consensus`
            : pocket.source === 'P2RANK'
              ? `probability ${formatMetric(pocket.probability)}`
              : `druggability ${formatMetric(
                pocket.druggabilityScore,
              )}`}
        </small>
      </span>
      {chosen && <span className="chosen-tag">Chosen</span>}
    </button>
  )
}

function groupPockets(pockets: PocketSummary[]) {
  const sourceOrder = ['FPOCKET', 'P2RANK', 'BIOHUB', 'MANUAL', 'IMPORTED']
  return sourceOrder
    .map((source) => ({
      source,
      pockets: pockets.filter((pocket) => pocket.source === source),
    }))
    .filter((group) => group.pockets.length > 0)
}

function haveIdenticalEvidenceSets(pockets: PocketSummary[]) {
  if (pockets.length < 2 || pockets.some((pocket) => !pocket.evidence)) {
    return false
  }
  const signature = (pocket: PocketSummary) => {
    const evidence = pocket.evidence
    return JSON.stringify({
      wall: evidence?.shellResidueIds.slice().sort(),
      direct: evidence?.directContactResidueIds.slice().sort(),
    })
  }
  return pockets.slice(1).every(
    (pocket) => signature(pocket) === signature(pockets[0]),
  )
}

function formatMetric(value: number | null): string {
  return value == null ? '—' : value.toFixed(3)
}

function formatVolume(value: number | null): string {
  return value == null ? '—' : `${value.toFixed(1)} Å³`
}
