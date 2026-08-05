import { useMemo, useState } from 'react'
import type {
  PocketGeometryView,
  PocketSimilarityDiagnosticRow,
} from '../../api/types'
import { useApiQuery } from '../../api/hooks'
import { AsyncState } from '../../components/AsyncState'
import { geometryBasisLabel } from './geometryBasis'

const RESULT_LIMIT = 100
const PAGE_SIZE = 20

type SortDirection = 'asc' | 'desc'
type ViewMode = 'pockets' | 'proteins'

type SortKey =
  | 'stageThreeRank'
  | 'pocketId'
  | 'uniProtId'
  | 'sourceAccession'
  | 'proteinName'
  | 'geneName'
  | 'pocketNumber'
  | 'basis'
  | 'descriptorDistance'
  | 'shapeDistance'
  | 'overallSimilarity'
  | 'geometrySimilarity'
  | 'sizeSimilarity'
  | 'queryCoverage'
  | 'candidateCoverage'
  | 'queryToCandidateMeanDistance'
  | 'candidateToQueryMeanDistance'
  | 'meanBidirectionalDistance'
  | 'maximumNearestNeighborDistance'
  | 'queryPointCount'
  | 'candidatePointCount'
  | 'alphaSphereCount'

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
    key: 'uniProtId',
    label: 'UniProt',
    defaultDirection: 'asc',
    value: (row) => row.uniProtId ?? '—',
  },
  {
    key: 'sourceAccession',
    label: 'Source accession',
    defaultDirection: 'asc',
    value: (row) => row.sourceAccession,
  },
  {
    key: 'proteinName',
    label: 'Protein name',
    defaultDirection: 'asc',
    value: (row) => row.proteinName ?? '—',
  },
  {
    key: 'geneName',
    label: 'Gene',
    defaultDirection: 'asc',
    value: (row) => row.geneName ?? '—',
  },
  {
    key: 'pocketNumber',
    label: 'Pocket #',
    defaultDirection: 'asc',
    value: (row) => String(row.pocketNumber),
  },
  {
    key: 'basis',
    label: 'Basis',
    defaultDirection: 'asc',
    value: (row) => geometryBasisLabel(row.basis),
  },
  {
    key: 'alphaSphereCount',
    label: 'Alpha spheres',
    defaultDirection: 'desc',
    value: (row) =>
      row.basis === 'ALPHA_SPHERES' ? String(row.alphaSphereCount) : '—',
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
    key: 'geometrySimilarity',
    label: 'Geometry similarity',
    defaultDirection: 'desc',
    value: (row) => row.geometrySimilarity.toFixed(3),
  },
  {
    key: 'sizeSimilarity',
    label: 'Size similarity',
    defaultDirection: 'desc',
    value: (row) => row.sizeSimilarity.toFixed(3),
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
  {
    key: 'meanBidirectionalDistance',
    label: 'Bidirectional distance',
    defaultDirection: 'asc',
    value: (row) => row.meanBidirectionalDistance.toFixed(2),
  },
  {
    key: 'maximumNearestNeighborDistance',
    label: 'Max NN distance',
    defaultDirection: 'asc',
    value: (row) => row.maximumNearestNeighborDistance.toFixed(2),
  },
  {
    key: 'queryPointCount',
    label: 'Query points',
    defaultDirection: 'desc',
    value: (row) => String(row.queryPointCount),
  },
  {
    key: 'candidatePointCount',
    label: 'Candidate points',
    defaultDirection: 'desc',
    value: (row) => String(row.candidatePointCount),
  },
]

interface Filters {
  uniProtId: string
  proteinName: string
  geneName: string
  basis: string
  minOverallSimilarity: string
  minCoverage: string
}

const EMPTY_FILTERS: Filters = {
  uniProtId: '',
  proteinName: '',
  geneName: '',
  basis: '',
  minOverallSimilarity: '',
  minCoverage: '',
}

interface ProteinGroup {
  uniProtId: string | null
  proteinName: string | null
  geneName: string | null
  organism: string | null
  rows: PocketSimilarityDiagnosticRow[]
  best: PocketSimilarityDiagnosticRow
}

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

  const [view, setView] = useState<ViewMode>('pockets')
  const [sortKey, setSortKey] = useState<SortKey>('stageThreeRank')
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc')
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)

  const rows = useMemo(() => diagnostic.data ?? [], [diagnostic.data])

  // Client-side filters apply to the single diagnostic fetch and feed
  // both the Pockets table and the Proteins grouping below.
  const filteredRows = useMemo(
    () => rows.filter((row) => matchesFilters(row, filters)),
    [rows, filters],
  )

  const sortedRows = useMemo(() => {
    const sorted = filteredRows.slice()
    sorted.sort((first, second) => {
      const firstValue = first[sortKey]
      const secondValue = second[sortKey]
      const order = typeof firstValue === 'string'
        || firstValue === null
        || typeof secondValue === 'string'
        || secondValue === null
        ? String(firstValue ?? '').localeCompare(String(secondValue ?? ''))
        : firstValue - secondValue
      return sortDirection === 'asc' ? order : -order
    })
    return sorted
  }, [filteredRows, sortKey, sortDirection])

  const proteinGroups = useMemo(
    () => groupByProtein(filteredRows),
    [filteredRows],
  )

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

  const updateFilter = (key: keyof Filters, value: string) => {
    setPage(0)
    setFilters((current) => ({ ...current, [key]: value }))
  }

  const viewProteinPockets = (uniProtId: string | null) => {
    setPage(0)
    setFilters((current) => ({ ...current, uniProtId: uniProtId ?? '' }))
    setView('pockets')
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
            <div className="similar-subtitle">
              <p>
                pocket {query.pocketNumber ?? '—'} · {query.pointCount}{' '}
                points · {geometryBasisLabel(query.basis)} · structure{' '}
                {query.structureId ?? '—'}
              </p>
              <p>
                volume {formatMetric(query.volume, 1, ' Å³')} · score{' '}
                {formatMetric(query.score, 3)} · druggability{' '}
                {formatMetric(query.druggabilityScore, 3)} ·{' '}
                {query.residueCount ?? '—'} residues ·{' '}
                {query.atomCount ?? '—'} atoms ·{' '}
                {query.alphaSphereCount ?? '—'} alpha spheres
              </p>
            </div>
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
          <div
            className="viewer-mode similar-view-toggle"
            role="group"
            aria-label="Results view"
          >
            <button
              type="button"
              aria-pressed={view === 'pockets'}
              className={view === 'pockets' ? 'sort-active' : ''}
              onClick={() => setView('pockets')}
            >
              Pockets
            </button>
            <button
              type="button"
              aria-pressed={view === 'proteins'}
              className={view === 'proteins' ? 'sort-active' : ''}
              onClick={() => setView('proteins')}
            >
              Proteins
            </button>
          </div>

          <div className="similar-filters">
            <label>
              UniProt accession
              <input
                type="text"
                value={filters.uniProtId}
                onChange={(event) =>
                  updateFilter('uniProtId', event.target.value)
                }
              />
            </label>
            <label>
              Protein name
              <input
                type="text"
                value={filters.proteinName}
                onChange={(event) =>
                  updateFilter('proteinName', event.target.value)
                }
              />
            </label>
            <label>
              Gene
              <input
                type="text"
                value={filters.geneName}
                onChange={(event) =>
                  updateFilter('geneName', event.target.value)
                }
              />
            </label>
            <label>
              Geometry basis
              <select
                value={filters.basis}
                onChange={(event) =>
                  updateFilter('basis', event.target.value)
                }
              >
                <option value="">All</option>
                <option value="RESIDUE_ATOMS">Residue heavy atoms</option>
                <option value="ALPHA_SPHERES">Alpha spheres</option>
              </select>
            </label>
            <label>
              Min overall similarity
              <input
                type="number"
                step="any"
                value={filters.minOverallSimilarity}
                onChange={(event) =>
                  updateFilter('minOverallSimilarity', event.target.value)
                }
              />
            </label>
            <label>
              Min coverage
              <input
                type="number"
                step="any"
                value={filters.minCoverage}
                onChange={(event) =>
                  updateFilter('minCoverage', event.target.value)
                }
              />
            </label>
          </div>

          {view === 'pockets' ? (
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
          ) : (
            <div className="protein-groups">
              {proteinGroups.length === 0 ? (
                <AsyncState title="No proteins match the filters" compact />
              ) : (
                proteinGroups.map((group) => (
                  <ProteinCard
                    key={group.uniProtId ?? '—'}
                    group={group}
                    queryPocketId={pocketId}
                    onNavigate={onNavigate}
                    onViewPockets={viewProteinPockets}
                  />
                ))
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}

interface ProteinCardProps {
  group: ProteinGroup
  queryPocketId: number
  onNavigate: (path: string) => void
  onViewPockets: (uniProtId: string | null) => void
}

function ProteinCard({
  group,
  queryPocketId,
  onNavigate,
  onViewPockets,
}: ProteinCardProps) {
  const { best } = group
  return (
    <section className="panel protein-card">
      <header className="protein-card-header">
        <div>
          <h2>{group.uniProtId ?? '—'}</h2>
          <p className="similar-subtitle">
            {group.proteinName ?? 'Unknown protein'}
            {' · '}
            {group.geneName ?? '—'}
            {' · '}
            {group.organism ?? '—'}
          </p>
        </div>
        <span className="count-badge">
          {group.rows.length}{' '}
          {group.rows.length === 1 ? 'pocket' : 'pockets'}
        </span>
      </header>

      <dl className="metrics-grid">
        <dt>Best final rank</dt>
        <dd>{best.stageThreeRank}</dd>

        <dt>Best pocket ID</dt>
        <dd>{best.pocketId}</dd>

        <dt>Best pocket number</dt>
        <dd>{best.pocketNumber}</dd>

        <dt>Descriptor distance</dt>
        <dd>{best.descriptorDistance.toFixed(3)}</dd>

        <dt>Shape distance</dt>
        <dd>{best.shapeDistance.toFixed(3)}</dd>

        <dt>Overall similarity</dt>
        <dd>{best.overallSimilarity.toFixed(3)}</dd>

        <dt>Geometry similarity</dt>
        <dd>{best.geometrySimilarity.toFixed(3)}</dd>

        <dt>Forward coverage</dt>
        <dd>{best.queryCoverage.toFixed(2)}</dd>

        <dt>Reverse coverage</dt>
        <dd>{best.candidateCoverage.toFixed(2)}</dd>

        <dt>Bidirectional distance</dt>
        <dd>{best.meanBidirectionalDistance.toFixed(2)} Å</dd>
      </dl>

      <details className="protein-pocket-list">
        <summary>All pockets ({group.rows.length})</summary>
        <table className="similar-table">
          <thead>
            <tr>
              <th>Pocket ID</th>
              <th>Source accession</th>
              <th>Pocket #</th>
              <th>Rank</th>
              <th>Overall similarity</th>
            </tr>
          </thead>
          <tbody>
            {group.rows.map((row) => (
              <tr key={row.pocketId}>
                <td>{row.pocketId}</td>
                <td>{row.sourceAccession}</td>
                <td>{row.pocketNumber}</td>
                <td>{row.stageThreeRank}</td>
                <td>{row.overallSimilarity.toFixed(3)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>

      <div className="protein-actions">
        <button
          type="button"
          className="compare-link"
          onClick={() => onViewPockets(group.uniProtId)}
        >
          View pockets
        </button>
        <button
          type="button"
          className="compare-link"
          onClick={() => onNavigate(
            `/pockets/${queryPocketId}/compare/${best.pocketId}`,
          )}
        >
          Inspect best match
        </button>
        <button
          type="button"
          className="compare-link"
          onClick={() => onNavigate(`/structures/${best.structureId}`)}
        >
          Open structure
        </button>
      </div>
    </section>
  )
}

function matchesFilters(
  row: PocketSimilarityDiagnosticRow,
  filters: Filters,
): boolean {
  if (!containsIgnoreCase(row.uniProtId, filters.uniProtId)) return false
  if (!containsIgnoreCase(row.proteinName, filters.proteinName)) return false
  if (!containsIgnoreCase(row.geneName, filters.geneName)) return false
  if (filters.basis && row.basis !== filters.basis) return false

  const minOverall = parseOptionalNumber(filters.minOverallSimilarity)
  if (minOverall !== null && row.overallSimilarity < minOverall) return false

  const minCoverage = parseOptionalNumber(filters.minCoverage)
  if (
    minCoverage !== null
    && (row.queryCoverage < minCoverage
      || row.candidateCoverage < minCoverage)
  ) {
    return false
  }

  return true
}

function containsIgnoreCase(
  value: string | null,
  needle: string,
): boolean {
  const trimmed = needle.trim()
  if (!trimmed) return true
  return (value ?? '').toLowerCase().includes(trimmed.toLowerCase())
}

function parseOptionalNumber(value: string): number | null {
  const trimmed = value.trim()
  if (!trimmed) return null
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) ? parsed : null
}

function groupByProtein(
  rows: PocketSimilarityDiagnosticRow[],
): ProteinGroup[] {
  const groups = new Map<string, PocketSimilarityDiagnosticRow[]>()
  for (const row of rows) {
    const key = row.uniProtId ?? '—'
    const group = groups.get(key)
    if (group) {
      group.push(row)
    } else {
      groups.set(key, [row])
    }
  }

  return Array.from(groups.values()).map((groupRows) => {
    const best = groupRows.reduce((current, row) =>
      row.stageThreeRank < current.stageThreeRank ? row : current
    )
    const prefer = (
      pick: (row: PocketSimilarityDiagnosticRow) => string | null,
    ) => groupRows.map(pick).find((value) => value !== null) ?? null
    return {
      uniProtId: best.uniProtId,
      proteinName: prefer((row) => row.proteinName),
      geneName: prefer((row) => row.geneName),
      organism: prefer((row) => row.organism),
      rows: groupRows,
      best,
    }
  }).sort((first, second) =>
    first.best.stageThreeRank - second.best.stageThreeRank
  )
}

function formatMetric(
  value: number | null,
  digits: number,
  suffix = '',
): string {
  return value == null ? '—' : `${value.toFixed(digits)}${suffix}`
}
