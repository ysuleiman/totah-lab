import { useMemo, useState } from 'react'
import type {
  PocketGeometryView,
  PocketSimilarityDiagnosticRow,
} from '../../api/types'
import { useApiQuery } from '../../api/hooks'
import { AsyncState } from '../../components/AsyncState'

const RESULT_LIMIT = 100
const PAGE_SIZE = 20

type SortDirection = 'asc' | 'desc'

type SortKey =
  | 'stageThreeRank'
  | 'pocketId'
  | 'sourceAccession'
  | 'pocketNumber'
  | 'descriptorDistance'
  | 'shapeDistance'
  | 'overallSimilarity'
  | 'queryCoverage'
  | 'candidateCoverage'
  | 'queryToCandidateMeanDistance'
  | 'candidateToQueryMeanDistance'

interface Column {
  key: SortKey
  label: string
  defaultDirection: SortDirection
  value: (row: PocketSimilarityDiagnosticRow) => string
}

const COLUMNS: Column[] = [
  {
    key: 'stageThreeRank',
    label: 'Rank',
    defaultDirection: 'asc',
    value: (row) => String(row.stageThreeRank),
  },
  {
    key: 'pocketId',
    label: 'Pocket ID',
    defaultDirection: 'asc',
    value: (row) => String(row.pocketId),
  },
  {
    key: 'sourceAccession',
    label: 'Source accession',
    defaultDirection: 'asc',
    value: (row) => row.sourceAccession,
  },
  {
    key: 'pocketNumber',
    label: 'Pocket #',
    defaultDirection: 'asc',
    value: (row) => String(row.pocketNumber),
  },
  {
    key: 'descriptorDistance',
    label: 'Descriptor distance',
    defaultDirection: 'asc',
    value: (row) => row.descriptorDistance.toFixed(3),
  },
  {
    key: 'shapeDistance',
    label: 'Shape distance',
    defaultDirection: 'asc',
    value: (row) => row.shapeDistance.toFixed(3),
  },
  {
    key: 'overallSimilarity',
    label: 'Overall similarity',
    defaultDirection: 'desc',
    value: (row) => row.overallSimilarity.toFixed(3),
  },
  {
    key: 'queryCoverage',
    label: 'Forward coverage',
    defaultDirection: 'desc',
    value: (row) => row.queryCoverage.toFixed(2),
  },
  {
    key: 'candidateCoverage',
    label: 'Reverse coverage',
    defaultDirection: 'desc',
    value: (row) => row.candidateCoverage.toFixed(2),
  },
  {
    key: 'queryToCandidateMeanDistance',
    label: 'Forward distance',
    defaultDirection: 'asc',
    value: (row) => row.queryToCandidateMeanDistance.toFixed(2),
  },
  {
    key: 'candidateToQueryMeanDistance',
    label: 'Reverse distance',
    defaultDirection: 'asc',
    value: (row) => row.candidateToQueryMeanDistance.toFixed(2),
  },
]

interface Props {
  pocketId: number
  onNavigate: (path: string) => void
}

export function SimilarPocketsPage({ pocketId, onNavigate }: Props) {
  const geometry = useApiQuery<PocketGeometryView>(
    `/api/pockets/${pocketId}/geometry`,
  )
  const diagnostic = useApiQuery<PocketSimilarityDiagnosticRow[]>(
    `/api/pockets/${pocketId}/similar/diagnostic?limit=${RESULT_LIMIT}`,
  )

  const [sortKey, setSortKey] = useState<SortKey>('stageThreeRank')
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc')
  const [page, setPage] = useState(0)

  const rows = diagnostic.data ?? []

  const sortedRows = useMemo(() => {
    const sorted = rows.slice()
    sorted.sort((first, second) => {
      const firstValue = first[sortKey]
      const secondValue = second[sortKey]
      const order = typeof firstValue === 'string'
        ? firstValue.localeCompare(String(secondValue))
        : firstValue - Number(secondValue)
      return sortDirection === 'asc' ? order : -order
    })
    return sorted
  }, [rows, sortKey, sortDirection])

  const pageCount = Math.max(1, Math.ceil(sortedRows.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount - 1)
  const pageRows = sortedRows.slice(
    currentPage * PAGE_SIZE,
    (currentPage + 1) * PAGE_SIZE,
  )

  const selectSort = (column: Column) => {
    setPage(0)
    if (column.key === sortKey) {
      setSortDirection((direction) =>
        direction === 'asc' ? 'desc' : 'asc'
      )
    } else {
      setSortKey(column.key)
      setSortDirection(column.defaultDirection)
    }
  }

  const query = geometry.data

  return (
    <div className="similar-page">
      <header className="similar-header">
        <div>
          <p className="eyebrow">Pocket similarity</p>
          <h1>
            {query
              ? `${query.sourceAccession ?? 'Unknown source'}`
              : `Pocket ${pocketId}`}
          </h1>
          {query && (
            <p className="similar-subtitle">
              pocket {query.pocketNumber ?? '—'} · {query.pointCount} points ·
              {' '}{query.basis} · structure {query.structureId ?? '—'}
            </p>
          )}
        </div>
        {diagnostic.data && (
          <span className="count-badge">
            {diagnostic.data.length} candidates
          </span>
        )}
      </header>

      {diagnostic.loading ? (
        <AsyncState loading title="Loading similar pockets" />
      ) : diagnostic.error ? (
        <AsyncState
          title="Similar pockets unavailable"
          message={diagnostic.error.message}
          onRetry={diagnostic.retry}
        />
      ) : rows.length === 0 ? (
        <AsyncState title="No similar pockets found" />
      ) : (
        <>
          <table className="similar-table">
            <thead>
              <tr>
                {COLUMNS.map((column) => (
                  <th key={column.key}>
                    <button
                      type="button"
                      className={sortKey === column.key
                        ? 'sort-active'
                        : ''}
                      onClick={() => selectSort(column)}
                    >
                      {column.label}
                      {sortKey === column.key && (
                        <span aria-hidden="true">
                          {sortDirection === 'asc' ? ' ▲' : ' ▼'}
                        </span>
                      )}
                    </button>
                  </th>
                ))}
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {pageRows.map((row) => (
                <tr key={row.pocketId}>
                  {COLUMNS.map((column) => (
                    <td key={column.key}>{column.value(row)}</td>
                  ))}
                  <td>
                    <button
                      type="button"
                      className="compare-link"
                      onClick={() => onNavigate(
                        `/pockets/${pocketId}/compare/${row.pocketId}`,
                      )}
                    >
                      Compare
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="pagination">
            <button
              type="button"
              disabled={currentPage === 0}
              onClick={() => setPage(currentPage - 1)}
            >
              Previous
            </button>
            <span>
              Page {currentPage + 1} of {pageCount}
            </span>
            <button
              type="button"
              disabled={currentPage >= pageCount - 1}
              onClick={() => setPage(currentPage + 1)}
            >
              Next
            </button>
          </div>
        </>
      )}
    </div>
  )
}
