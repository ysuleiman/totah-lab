import { useMemo, useState } from 'react'
import type {
  PocketComparisonMetrics,
  ResidueCorrespondenceView,
  ResidueMatchView,
  ResiduePointView,
} from '../../api/types'

const MATCH_TYPE_LABELS: Record<ResidueMatchView['matchType'], string> = {
  IDENTICAL: 'Identical',
  CONSERVATIVE: 'Conservative',
  CHEMISTRY_COMPATIBLE: 'Chemistry compatible',
  DIFFERENT: 'Spatial replacement',
  UNMATCHED: 'Unmatched',
}

const MATCH_TYPE_CLASSES: Record<ResidueMatchView['matchType'], string> = {
  IDENTICAL: 'match-identical',
  CONSERVATIVE: 'match-conservative',
  CHEMISTRY_COMPATIBLE: 'match-compatible',
  DIFFERENT: 'match-different',
  UNMATCHED: 'match-different',
}

type ClassFilter =
  | 'ALL'
  | 'IDENTICAL'
  | 'CONSERVATIVE'
  | 'CHEMISTRY_COMPATIBLE'
  | 'DIFFERENT'
  | 'KEY'
  | 'UNMATCHED'

const CLASS_FILTER_LABELS: Record<ClassFilter, string> = {
  ALL: 'Show all',
  IDENTICAL: 'Identical',
  CONSERVATIVE: 'Conservative',
  CHEMISTRY_COMPATIBLE: 'Chemistry-compatible',
  DIFFERENT: 'Spatial replacements',
  KEY: 'Key residues only',
  UNMATCHED: 'Unmatched only',
}

const CUTOFF_ANGSTROMS = 4.0

interface Props {
  correspondence: ResidueCorrespondenceView
  comparison: PocketComparisonMetrics
  keyResidues: string[]
}

export function ResidueCorrespondenceSection({
  correspondence,
  comparison,
  keyResidues,
}: Props) {
  const [classFilter, setClassFilter] = useState<ClassFilter>('ALL')
  const [maxDistance, setMaxDistance] = useState('')
  const [queryText, setQueryText] = useState('')
  const [candidateText, setCandidateText] = useState('')
  const [expandedRows, setExpandedRows] = useState<ReadonlySet<string>>(
    new Set(),
  )

  const { matches, unmatchedQuery, unmatchedCandidate, summary } =
    correspondence

  const classCounts = useMemo(() => {
    const counts = {
      identical: 0,
      conservative: 0,
      compatible: 0,
      replacements: 0,
    }
    for (const match of matches) {
      if (match.matchType === 'IDENTICAL') counts.identical++
      else if (match.matchType === 'CONSERVATIVE') counts.conservative++
      else if (match.matchType === 'CHEMISTRY_COMPATIBLE') counts.compatible++
      else if (match.matchType === 'DIFFERENT') counts.replacements++
    }
    return counts
  }, [matches])

  const visibleMatches = useMemo(() => {
    const parsedMax =
      maxDistance.trim() === '' ? null : Number(maxDistance)
    const maxDistanceValue =
      parsedMax != null && Number.isFinite(parsedMax) ? parsedMax : null
    const queryNeedle = queryText.trim().toLowerCase()
    const candidateNeedle = candidateText.trim().toLowerCase()

    return matches
      .filter((match) => {
        if (
          classFilter !== 'ALL'
          && classFilter !== 'KEY'
          && classFilter !== 'UNMATCHED'
          && match.matchType !== classFilter
        ) {
          return false
        }
        if (
          classFilter === 'KEY'
          && !isKeyResidue(match.query, keyResidues)
        ) {
          return false
        }
        if (
          maxDistanceValue != null
          && match.distanceAngstroms > maxDistanceValue
        ) {
          return false
        }
        if (
          queryNeedle
          && !match.query.label.toLowerCase().includes(queryNeedle)
        ) {
          return false
        }
        if (
          candidateNeedle
          && !match.candidate.label
            .toLowerCase()
            .includes(candidateNeedle)
        ) {
          return false
        }
        return true
      })
      .sort((a, b) => {
        const keyDiff =
          Number(isKeyResidue(b.query, keyResidues)) - Number(isKeyResidue(a.query, keyResidues))
        if (keyDiff !== 0) return keyDiff
        return a.distanceAngstroms - b.distanceAngstroms
      })
  }, [
    matches,
    classFilter,
    keyResidues,
    maxDistance,
    queryText,
    candidateText,
  ])

  const toggleExpanded = (rowKey: string) => {
    setExpandedRows((previous) => {
      const next = new Set(previous)
      if (next.has(rowKey)) next.delete(rowKey)
      else next.add(rowKey)
      return next
    })
  }

  const showMatchedTable = classFilter !== 'UNMATCHED'

  return (
    <section className="panel correspondence-panel">
      <h2>Residue correspondence</h2>

      <dl className="metrics-grid correspondence-summary">
        <dt>Geometry</dt>
        <dd>
          {geometryLabel(comparison.overallSimilarity)}
          {' '}({comparison.overallSimilarity.toFixed(3)} overall
          similarity, {comparison.meanBidirectionalDistance.toFixed(2)} Å
          mean distance)
        </dd>

        <dt>Residues</dt>
        <dd>
          {summary.queryResidueCount} query
          · {summary.candidateResidueCount} candidate
          · {summary.matchedCount} spatial correspondences
        </dd>

        <dt>Identical</dt>
        <dd>{classCounts.identical}</dd>

        <dt>Conservative</dt>
        <dd>{classCounts.conservative}</dd>

        <dt>Chemistry-compatible</dt>
        <dd>{classCounts.compatible}</dd>

        <dt>Spatial replacements</dt>
        <dd>{classCounts.replacements}</dd>

        <dt>Unmatched</dt>
        <dd>
          {summary.unmatchedQueryCount} query
          · {summary.unmatchedCandidateCount} candidate
        </dd>

        <dt>Matched distance</dt>
        <dd>
          mean {summary.meanMatchedDistance.toFixed(2)} Å
          · max {summary.maximumMatchedDistance.toFixed(2)} Å
        </dd>
      </dl>

      <div className="correspondence-filters">
        <label>
          Classification
          <select
            value={classFilter}
            onChange={(event) =>
              setClassFilter(event.target.value as ClassFilter)}
          >
            {(Object.keys(CLASS_FILTER_LABELS) as ClassFilter[]).map(
              (value) => (
                <option key={value} value={value}>
                  {CLASS_FILTER_LABELS[value]}
                </option>
              ),
            )}
          </select>
        </label>

        <label>
          Max distance (Å)
          <input
            type="number"
            min={0}
            step={0.1}
            value={maxDistance}
            onChange={(event) => setMaxDistance(event.target.value)}
          />
        </label>

        <label>
          Query residue
          <input
            type="text"
            value={queryText}
            onChange={(event) => setQueryText(event.target.value)}
          />
        </label>

        <label>
          Candidate residue
          <input
            type="text"
            value={candidateText}
            onChange={(event) =>
              setCandidateText(event.target.value)
            }
          />
        </label>
      </div>

      {showMatchedTable && (
        <div className="correspondence-table-wrap">
          <table className="correspondence-table">
            <thead>
              <tr>
                <th aria-label="Expand" />
                <th>Key</th>
                <th>Query residue</th>
                <th>Candidate residue</th>
                <th>Distance</th>
                <th>Query chemistry</th>
                <th>Candidate chemistry</th>
                <th>Match type</th>
                <th>Compatible</th>
              </tr>
            </thead>

            <tbody>
              {visibleMatches.map((match) => {
                const matchClass =
                  MATCH_TYPE_CLASSES[match.matchType]
                const rowKey =
                  `${match.query.label}-${match.candidate.label}`
                const expanded = expandedRows.has(rowKey)

                return (
                  <MatchRows
                    key={rowKey}
                    match={match}
                    matchClass={matchClass}
                    expanded={expanded}
                    keyResidue={isKeyResidue(match.query, keyResidues)}
                    onToggle={() => toggleExpanded(rowKey)}
                  />
                )
              })}
            </tbody>
          </table>

          {visibleMatches.length === 0 && (
            <p className="muted-note">
              No matches pass the current filters. The full
              correspondence is still available under “Show all”.
            </p>
          )}
        </div>
      )}

      <details
        className="unmatched-section"
        open={classFilter === 'UNMATCHED' || undefined}
      >
        <summary>
          Unmatched query residues ({unmatchedQuery.length})
        </summary>
        <UnmatchedResidueTable
          residues={unmatchedQuery}
          oppositeResidues={oppositeResidues(
            matches,
            unmatchedCandidate,
            'candidate',
          )}
          matchedOppositeLabels={
            new Set(matches.map((match) => match.candidate.label))
          }
          side="query"
        />
      </details>

      <details
        className="unmatched-section"
        open={classFilter === 'UNMATCHED' || undefined}
      >
        <summary>
          Unmatched candidate residues ({unmatchedCandidate.length})
        </summary>
        <UnmatchedResidueTable
          residues={unmatchedCandidate}
          oppositeResidues={oppositeResidues(
            matches,
            unmatchedQuery,
            'query',
          )}
          matchedOppositeLabels={
            new Set(matches.map((match) => match.query.label))
          }
          side="candidate"
        />
      </details>

      <p className="muted-note">
        Residues are matched by representative side-chain position after
        PCA+ICP pocket alignment. Matches are spatial correspondences,
        not sequence alignments. Glycine uses CA; other residues use
        side-chain heavy-atom centroid (CA fallback when no side-chain
        heavy atoms exist). Maximum correspondence distance is 4.0 Å.
      </p>
      <p className="muted-note">
        The aligned pockets show spatial correspondence at several
        positions, but low residue identity and limited chemistry
        compatibility. This is consistent with structural divergence at
        these representative positions, but does not by itself establish
        catalytic or functional divergence. Spatial replacements —
        positions occupied by residues with different chemistry — are
        shown explicitly because they can explain similar cavity
        geometry with different ligand preferences.
      </p>
    </section>
  )
}

function MatchRows({
  match,
  matchClass,
  expanded,
  keyResidue,
  onToggle,
}: {
  match: ResidueMatchView
  matchClass: string
  expanded: boolean
  keyResidue: boolean
  onToggle: () => void
}) {
  return (
    <>
      <tr className={matchClass}>
        <td>
          <button
            type="button"
            className="row-expander"
            aria-expanded={expanded}
            aria-label={
              expanded ? 'Hide coordinates' : 'Show coordinates'
            }
            onClick={onToggle}
          >
            {expanded ? '▾' : '▸'}
          </button>
        </td>
        <td>
          {keyResidue ? (
            <span className="key-badge">Key residue</span>
          ) : (
            '—'
          )}
        </td>
        <td>{match.query.label}</td>
        <td>{match.candidate.label}</td>
        <td>{match.distanceAngstroms.toFixed(2)} Å</td>
        <td>{match.query.chemistry}</td>
        <td>{match.candidate.chemistry}</td>
        <td>
          <span className={`match-label ${matchClass}`}>
            {MATCH_TYPE_LABELS[match.matchType]}
          </span>
        </td>
        <td>{match.chemistryCompatible ? 'yes' : 'no'}</td>
      </tr>
      {expanded && (
        <tr className="correspondence-detail-row">
          <td colSpan={9}>
            <span className="detail-label">Query position:</span>
            {' '}{formatPosition(match.query.position)}
            {' · '}
            <span className="detail-label">Candidate position:</span>
            {' '}{formatPosition(match.candidate.position)}
            {' · '}
            <span className="detail-label">Chain:</span>
            {' '}{match.query.chainId} → {match.candidate.chainId}
          </td>
        </tr>
      )}
    </>
  )
}

function UnmatchedResidueTable({
  residues,
  oppositeResidues,
  matchedOppositeLabels,
  side,
}: {
  residues: ResiduePointView[]
  oppositeResidues: ResiduePointView[]
  matchedOppositeLabels: ReadonlySet<string>
  side: 'query' | 'candidate'
}) {
  if (residues.length === 0) {
    return <p className="muted-note">None.</p>
  }

  return (
    <table className="correspondence-table">
      <thead>
        <tr>
          <th>Residue</th>
          <th>Chemistry</th>
          <th>Position</th>
          <th>Reason unmatched</th>
        </tr>
      </thead>

      <tbody>
        {residues.map((residue) => (
          <tr key={residue.label}>
            <td>{residue.label}</td>
            <td>{residue.chemistry}</td>
            <td>{formatPosition(residue.position)}</td>
            <td>
              {unmatchedReason(
                residue,
                oppositeResidues,
                matchedOppositeLabels,
                side,
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function unmatchedReason(
  residue: ResiduePointView,
  oppositeResidues: ResiduePointView[],
  matchedOppositeLabels: ReadonlySet<string>,
  side: 'query' | 'candidate',
): string {
  if (oppositeResidues.length === 0) {
    return `no ${side === 'query' ? 'candidate' : 'query'} residues available`
  }

  let nearest: ResiduePointView = oppositeResidues[0]
  let nearestDistance = distance(residue.position, nearest.position)

  for (const other of oppositeResidues) {
    const current = distance(residue.position, other.position)
    if (current < nearestDistance) {
      nearest = other
      nearestDistance = current
    }
  }

  if (nearestDistance > CUTOFF_ANGSTROMS) {
    return `no ${side === 'query' ? 'candidate' : 'query'} residue within `
      + `${CUTOFF_ANGSTROMS.toFixed(1)} Å `
      + `(nearest ${nearest.label} at ${nearestDistance.toFixed(2)} Å)`
  }

  if (matchedOppositeLabels.has(nearest.label)) {
    return `nearest ${side === 'query' ? 'candidate' : 'query'} residue `
      + `${nearest.label} already matched by a closer residue `
      + `(${nearestDistance.toFixed(2)} Å)`
  }

  return `not selected by one-to-one matching `
    + `(nearest ${nearest.label} at ${nearestDistance.toFixed(2)} Å)`
}

function oppositeResidues(
  matches: ResidueMatchView[],
  unmatched: ResiduePointView[],
  side: 'query' | 'candidate',
): ResiduePointView[] {
  return [
    ...matches.map((match) =>
      side === 'query' ? match.query : match.candidate),
    ...unmatched,
  ]
}

function isKeyResidue(
  residue: ResiduePointView,
  keyResidues: string[],
): boolean {
  const name =
    `${residue.residueName}${residue.residueNumber}`.toUpperCase()
  return keyResidues.includes(name)
}

// Presentation-only display banding for the summary header. These are
// unvalidated reading aids, not scientific thresholds; the raw values
// are always shown next to the label.
function geometryLabel(overallSimilarity: number): string {
  if (overallSimilarity >= 0.5) return 'Excellent'
  if (overallSimilarity >= 0.3) return 'Good'
  if (overallSimilarity >= 0.15) return 'Moderate'
  return 'Weak'
}

function distance(
  first: ResiduePointView['position'],
  second: ResiduePointView['position'],
): number {
  return Math.sqrt(
    (first.x - second.x) ** 2
    + (first.y - second.y) ** 2
    + (first.z - second.z) ** 2
  )
}

function formatPosition(position: ResiduePointView['position']): string {
  return `(${position.x.toFixed(1)}, ${position.y.toFixed(1)}, ${position.z.toFixed(1)})`
}
