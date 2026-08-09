import { useMemo } from 'react'
import type { ResidueEvidence } from '../../../api/types'
import { useApiQuery } from '../../../api/hooks'

export function useResidueEvidence(structureId: number | null) {
  const query = useApiQuery<ResidueEvidence[]>(
    structureId != null
      ? `/api/structures/${structureId}/residue-evidence`
        + '?analysisType=ESMC_CONSTRAINT'
      : null,
  )
  const byResidueId = useMemo(
    () => new Map(
      query.data?.map((evidence) => [evidence.residueId, evidence]) ?? [],
    ),
    [query.data],
  )

  return { ...query, byResidueId }
}
