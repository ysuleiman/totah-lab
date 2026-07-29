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
            <small>Druggability</small>
            <strong>
              {chosenPocket.druggabilityScore?.toFixed(3) ?? 'Not scored'}
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
        <div className="pocket-list">
          {pockets.map((pocket) => {
            const selected = pocket.id === selectedPocketId
            const chosen = pocket.id === chosenPocketId
            return (
              <button
                className={`pocket-card${selected ? ' selected' : ''}`}
                key={pocket.id}
                type="button"
                aria-pressed={selected}
                onClick={() => onPocketSelect(pocket.id)}
              >
                <span className="pocket-index">
                  {pocket.pocketNumber.toString().padStart(2, '0')}
                </span>
                <span className="pocket-copy">
                  <strong>{pocket.source}</strong>
                  <small>
                    {pocket.volume == null
                      ? 'Volume unavailable'
                      : `${pocket.volume.toFixed(1)} Å³`}
                  </small>
                </span>
                <span className="pocket-score">
                  {pocket.druggabilityScore?.toFixed(3) ?? '—'}
                  <small>score</small>
                </span>
                {chosen && <span className="chosen-tag">Chosen</span>}
              </button>
            )
          })}
        </div>
      )}
    </section>
  )
}
