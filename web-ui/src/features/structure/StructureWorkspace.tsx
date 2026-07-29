import { useMemo, useState } from 'react'
import type { PocketDetails, PocketSummary, Structure } from '../../api/types'
import { useApiQuery } from '../../api/hooks'
import { AsyncState } from '../../components/AsyncState'
import { StructureHero } from './components/StructureHero'
import { PocketPanel } from '../pocket/PocketPanel'
import { ResiduePanel } from '../residue/ResiduePanel'

interface Props {
  structureId: number
  onNavigate: (structureId: number) => void
}

export function StructureWorkspace({ structureId, onNavigate }: Props) {
  const structureQuery = useApiQuery<Structure>(
    `/api/structures/${structureId}`,
  )
  const pocketsQuery = useApiQuery<PocketSummary[]>(
    structureQuery.data?.pocketsUrl ?? null,
  )
  const [selectedPocketId, setSelectedPocketId] = useState<number | null>(null)
  const effectivePocketId =
    selectedPocketId ?? structureQuery.data?.chosenPocket?.id ?? null
  const pocketQuery = useApiQuery<PocketDetails>(
    effectivePocketId ? `/api/pockets/${effectivePocketId}` : null,
  )

  const pocketResidueIds = useMemo(
    () => new Set(pocketQuery.data?.residues.map((residue) => residue.id) ?? []),
    [pocketQuery.data],
  )

  function handleStructureSubmit(nextId: number) {
    setSelectedPocketId(null)
    onNavigate(nextId)
  }

  if (structureQuery.loading && !structureQuery.data) {
    return <AsyncState loading title="Loading structure" />
  }

  if (structureQuery.error || !structureQuery.data) {
    return (
      <AsyncState
        title="Structure unavailable"
        message={structureQuery.error?.message}
        onRetry={structureQuery.retry}
      />
    )
  }

  const structure = structureQuery.data
  return (
    <div className="workspace">
      <StructureHero
        structure={structure}
        onStructureSubmit={handleStructureSubmit}
      />
      <div className="workspace-grid">
        <PocketPanel
          pockets={pocketsQuery.data ?? []}
          chosenPocketId={structure.chosenPocket?.id ?? null}
          selectedPocketId={effectivePocketId}
          loading={pocketsQuery.loading}
          error={pocketsQuery.error}
          onPocketSelect={setSelectedPocketId}
          onRetry={pocketsQuery.retry}
        />
        <ResiduePanel
          residues={structure.residues}
          highlightedResidueIds={pocketResidueIds}
          activePocket={pocketQuery.data}
          pocketLoading={pocketQuery.loading}
        />
      </div>
    </div>
  )
}
