import { useMemo, useState } from 'react'
import { useApiQuery } from '../../api/hooks'
import type {
  SelectivityPage,
  SelectivityScore,
  SelectivitySort,
  SortDirection,
} from '../../api/types'
import { AsyncState } from '../../components/AsyncState'
import { LigandDepiction } from './LigandDepiction'

const PAGE_SIZE = 50

function formatScore(value: number) {
  return value.toFixed(3)
}

function SortButton({
  column,
  active,
  direction,
  children,
  onSort,
}: {
  column: SelectivitySort
  active: boolean
  direction: SortDirection
  children: React.ReactNode
  onSort: (column: SelectivitySort) => void
}) {
  return (
    <button
      className="selectivity-sort"
      onClick={() => onSort(column)}
      aria-label={`Sort by ${String(children)}`}
    >
      {children}
      <span aria-hidden="true">
        {active ? (direction === 'asc' ? '↑' : '↓') : '↕'}
      </span>
    </button>
  )
}

export function SelectivityWorkspace() {
  const [sortBy, setSortBy] = useState<SelectivitySort>('delta')
  const [direction, setDirection] = useState<SortDirection>('desc')
  const [page, setPage] = useState(0)
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [hover, setHover] = useState<{
    score: SelectivityScore
    top: number
    left: number
  } | null>(null)

  function showLigand(
    event: React.SyntheticEvent<HTMLElement>,
    score: SelectivityScore,
  ) {
    if (!score.smiles) return
    const rect = event.currentTarget.getBoundingClientRect()
    const cardWidth = 300
    const left = rect.right + 14 + cardWidth > window.innerWidth
      ? Math.max(8, rect.left - 14 - cardWidth)
      : rect.right + 14
    const top = Math.min(rect.top, Math.max(8, window.innerHeight - 300))
    setHover({ score, top, left })
  }

  const path = useMemo(() => {
    const parameters = new URLSearchParams({
      sortBy,
      direction,
      page: String(page),
      size: String(PAGE_SIZE),
      search,
    })
    return `/api/selectivity/scores?${parameters}`
  }, [direction, page, search, sortBy])
  const query = useApiQuery<SelectivityPage>(path)

  function handleSort(column: SelectivitySort) {
    if (column === sortBy) {
      setDirection((value) => value === 'asc' ? 'desc' : 'asc')
    } else {
      setSortBy(column)
      setDirection(column === 'ligandId' ? 'asc' : 'desc')
    }
    setPage(0)
  }

  function submitSearch(event: React.FormEvent) {
    event.preventDefault()
    setSearch(searchInput.trim())
    setPage(0)
  }

  const totalPages = query.data
    ? Math.max(1, Math.ceil(query.data.total / query.data.size))
    : 1
  const downloadParameters = new URLSearchParams({
    sortBy,
    direction,
    search,
  })

  return (
    <section className="selectivity-workspace">
      <header className="selectivity-hero">
        <div>
          <p className="eyebrow">Paired docking evidence</p>
          <h1>7B / 7A selectivity</h1>
          <p className="selectivity-intro">
            Best observed pose per ligand on each target. Positive delta means
            the ligand scores more favorably on METTL7B.
          </p>
        </div>
        <div className="delta-definition">
          <span>Delta definition</span>
          <strong>7A − 7B</strong>
          <small>Positive = 7B favored</small>
        </div>
      </header>

      <div className="selectivity-toolbar">
        <form onSubmit={submitSearch}>
          <label htmlFor="ligand-search">Find ligand</label>
          <div>
            <input
              id="ligand-search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="External ID or UUID"
            />
            <button type="submit">Search</button>
          </div>
        </form>
        <div className="selectivity-toolbar-actions">
          <span>
            {query.data
              ? `${query.data.total.toLocaleString()} paired ligands`
              : 'Loading paired ligands'}
          </span>
          <a
            className="excel-download"
            href={`/api/selectivity/scores.xlsx?${downloadParameters}`}
          >
            <span aria-hidden="true">↓</span>
            Download Excel
          </a>
        </div>
      </div>

      <section className="panel selectivity-panel">
        {query.loading && (
          <AsyncState loading title="Comparing docking scores" compact />
        )}
        {query.error && (
          <AsyncState
            title="Could not load selectivity scores"
            message={query.error.message}
            onRetry={query.retry}
          />
        )}
        {query.data && (
          <>
            <div className="selectivity-table-wrap">
              <table className="selectivity-table">
                <thead>
                  <tr>
                    <th>
                      <SortButton
                        column="ligandId"
                        active={sortBy === 'ligandId'}
                        direction={direction}
                        onSort={handleSort}
                      >
                        Ligand
                      </SortButton>
                    </th>
                    <th>
                      <SortButton
                        column="score7b"
                        active={sortBy === 'score7b'}
                        direction={direction}
                        onSort={handleSort}
                      >
                        METTL7B
                      </SortButton>
                    </th>
                    <th>
                      <SortButton
                        column="score7a"
                        active={sortBy === 'score7a'}
                        direction={direction}
                        onSort={handleSort}
                      >
                        METTL7A
                      </SortButton>
                    </th>
                    <th>
                      <SortButton
                        column="delta"
                        active={sortBy === 'delta'}
                        direction={direction}
                        onSort={handleSort}
                      >
                        Delta
                      </SortButton>
                    </th>
                    <th>Evidence</th>
                  </tr>
                </thead>
                <tbody>
                  {query.data.items.map((score) => (
                    <tr key={score.ligandId}>
                      <td>
                        <span
                          className="ligand-identity"
                          tabIndex={score.smiles ? 0 : undefined}
                          onMouseEnter={(event) => showLigand(event, score)}
                          onMouseLeave={() => setHover(null)}
                          onFocus={(event) => showLigand(event, score)}
                          onBlur={() => setHover(null)}
                        >
                          <strong>{score.ligandLabel}</strong>
                          <small>{score.ligandId}</small>
                        </span>
                      </td>
                      <td className="score-cell">
                        {formatScore(score.score7b)}
                      </td>
                      <td className="score-cell">
                        {formatScore(score.score7a)}
                      </td>
                      <td>
                        <span className={
                          `delta-chip ${score.delta >= 0 ? 'favored' : 'counter'}`
                        }>
                          {score.delta > 0 ? '+' : ''}
                          {formatScore(score.delta)}
                        </span>
                      </td>
                      <td className="evidence-cell">
                        <span>7B run {score.runId7b} · pose {score.poseId7b}</span>
                        <span>7A run {score.runId7a} · pose {score.poseId7a}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <footer className="selectivity-pagination">
              <button
                disabled={page === 0}
                onClick={() => setPage((value) => Math.max(0, value - 1))}
              >
                Previous
              </button>
              <span>Page {page + 1} of {totalPages}</span>
              <button
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((value) => value + 1)}
              >
                Next
              </button>
            </footer>
          </>
        )}
      </section>

      {hover && hover.score.smiles && (
        <div
          className="ligand-hover-card"
          style={{ top: hover.top, left: hover.left }}
        >
          <strong>{hover.score.ligandLabel}</strong>
          <LigandDepiction
            smiles={hover.score.smiles}
            label={hover.score.ligandLabel}
          />
        </div>
      )}
    </section>
  )
}
