import { useMemo } from 'react'
import { useApiQuery } from '../../../api/hooks'
import type { ResidueAnalysis } from '../../../api/types'

export function useResidueContactAnalysis(runId: number | null) {
  const query = useApiQuery<ResidueAnalysis[]>(
    runId ? `/api/docking-runs/${runId}/residue-summary` : null,
  )
  const byResidueId = useMemo(
    () => new Map(
      query.data?.map((analysis) => [analysis.residueId, analysis]) ?? [],
    ),
    [query.data],
  )

  return {
    byResidueId,
    loading: query.loading,
    error: query.error,
    retry: query.retry,
  }
}
