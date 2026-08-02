import { useMemo, useState } from 'react'
import type {
  DockingRunSummary,
  PocketDetails,
  PocketReportDocument,
  PocketSummary,
  Structure,
} from '../../api/types'
import { useApiQuery } from '../../api/hooks'
import { AsyncState } from '../../components/AsyncState'
import { StructureHero } from './components/StructureHero'
import { PocketPanel } from '../pocket/PocketPanel'
import { ResiduePanel } from '../residue/ResiduePanel'
import { useResidueContactAnalysis } from '../residue/hooks/useResidueContactAnalysis'
import { useResidueEvidence } from '../residue/hooks/useResidueEvidence'
import { PocketReportPanel } from '../report/PocketReportPanel'

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
  const [selectedRunId, setSelectedRunId] = useState<number | null>(null)
  const [reportRequested, setReportRequested] = useState(false)
  const effectivePocketId =
    selectedPocketId ?? structureQuery.data?.chosenPocket?.id ?? null
  const pocketQuery = useApiQuery<PocketDetails>(
    effectivePocketId ? `/api/pockets/${effectivePocketId}` : null,
  )
  const chosenPocketQuery = useApiQuery<PocketDetails>(
    structureQuery.data?.chosenPocket?.id
      ? `/api/pockets/${structureQuery.data.chosenPocket.id}`
      : null,
  )
  const dockingRunsQuery = useApiQuery<DockingRunSummary[]>(
    `/api/structures/${structureId}/docking-runs`,
  )
  const effectiveRunId = selectedRunId ?? dockingRunsQuery.data?.[0]?.id ?? null
  const residueAnalysis = useResidueContactAnalysis(effectiveRunId)
  const residueEvidence = useResidueEvidence(structureId)
  const reportQuery = useApiQuery<PocketReportDocument>(
    reportRequested && effectivePocketId && effectiveRunId
      ? `/api/pockets/${effectivePocketId}/report/document?runId=${effectiveRunId}`
      : null,
  )

  const pocketResidueIds = useMemo(
    () => new Set(pocketQuery.data?.residues.map((residue) => residue.id) ?? []),
    [pocketQuery.data],
  )
  const chosenPocketResidueIds = useMemo(
    () => new Set(
      chosenPocketQuery.data?.residues.map((residue) => residue.id) ?? [],
    ),
    [chosenPocketQuery.data],
  )
  const directContactResidueIds = useMemo(
    () => new Set(
      pocketQuery.data?.evidence?.directContactResidueIds ?? [],
    ),
    [pocketQuery.data],
  )
  function handleStructureSubmit(nextId: number) {
    setSelectedPocketId(null)
    setSelectedRunId(null)
    setReportRequested(false)
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
  const residues = structure.residues ?? []
  return (
    <div className="workspace">
      <StructureHero
        structure={structure}
        onStructureSubmit={handleStructureSubmit}
        onReportRequest={
          effectivePocketId && effectiveRunId
            ? () => setReportRequested(true)
            : undefined
        }
      />
      {reportRequested && effectivePocketId && effectiveRunId && (
        <PocketReportPanel
          document={reportQuery.data}
          pocketId={effectivePocketId}
          runId={effectiveRunId}
          loading={reportQuery.loading}
          error={reportQuery.error}
          onClose={() => setReportRequested(false)}
          onRetry={reportQuery.retry}
        />
      )}
      <div className="workspace-grid">
        <ResiduePanel
          key={structure.id}
          structureId={structure.id}
          residues={residues}
          highlightedResidueIds={pocketResidueIds}
          chosenPocketResidueIds={chosenPocketResidueIds}
          directContactResidueIds={directContactResidueIds}
          activePocket={pocketQuery.data}
          pocketLoading={pocketQuery.loading}
          dockingRuns={dockingRunsQuery.data ?? []}
          selectedRunId={effectiveRunId}
          onRunSelect={setSelectedRunId}
          residueAnalysis={residueAnalysis.byResidueId}
          analysisLoading={
            dockingRunsQuery.loading || residueAnalysis.loading
          }
          residueEvidence={residueEvidence.byResidueId}
          evidenceLoading={residueEvidence.loading}
        />
        <PocketPanel
          pockets={pocketsQuery.data ?? []}
          chosenPocketId={structure.chosenPocket?.id ?? null}
          selectedPocketId={effectivePocketId}
          loading={pocketsQuery.loading}
          error={pocketsQuery.error}
          onPocketSelect={setSelectedPocketId}
          onRetry={pocketsQuery.retry}
        />
      </div>
    </div>
  )
}
