import { useMemo, useState } from 'react'
import type {
  ResidueCorrespondenceView,
  ResidueMatchView,
  ResiduePointView,
} from '../../api/types'

const MATCH_TYPE_LABELS: Record<ResidueMatchView['matchType'], string> = {
  IDENTICAL: 'Identical',
  CONSERVATIVE: 'Conservative',
  CHEMISTRY_COMPATIBLE: 'Chemistry compatible',
  DIFFERENT: 'Different',
  UNMATCHED: 'Unmatched',
}

const MATCH_TYPE_CLASSES: Record<ResidueMatchView['matchType'], string> = {
  IDENTICAL: 'match-identical',
  CONSERVATIVE: 'match-conservative',
  CHEMISTRY_COMPATIBLE: 'match-compatible',
  DIFFERENT: 'match-different',
  UNMATCHED: 'match-different',
}

interface Props {
  correspondence: ResidueCorrespondenceView
  keyResidues: string[]
}

export function ResidueCorrespondenceSection({
  correspondence,
  keyResidues,
}: Props) {
  const [keyOnly, setKeyOnly] = useState(false)
  const [identicalOnly, setIdenticalOnly] = useState(false)
  const [compatibleOnly, setCompatibleOnly] = useState(false)
  const [mismatchesOnly, setMismatchesOnly] = useState(false)
  const [maxDistance, setMaxDistance] = useState('')
  const [queryText, setQueryText] = useState('')
  const [candidateText, setCandidateText] = useState('')

  const { matches, unmatchedQuery, unmatchedCandidate, summary } =
    correspondence

  const visibleMatches = useMemo(() => {
    const parsedMax =
      maxDistance.trim() === '' ? null : Number(maxDistance)
    const maxDistanceValue =
      parsedMax != null && Number.isFinite(parsedMax) ? parsedMax : null
    const queryNeedle = queryText.trim().toLowerCase()
    const candidateNeedle = candidateText.trim().toLowerCase()

    return matches
      .filter((match) => {
        if (keyOnly && !isKeyResidue(match.query, keyResidues)) return false
        if (identicalOnly && !match.identicalResidue) return false
        if (compatibleOnly && !match.chemistryCompatible) return false
        if (
          mismatchesOnly
          && match.matchType !== 'DIFFERENT'
          && match.chemistryCompatible
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
    keyOnly,
    identicalOnly,
    compatibleOnly,
    mismatchesOnly,
    maxDistance,
    queryText,
    candidateText,
  ])

  return (
    <section className="panel correspondence-panel">
      <h2>Residue correspondence</h2>

      <dl className="metrics-grid">
        <dt>Query residues</dt>
        <dd>{summary.queryResidueCount}</dd>

        <dt>Candidate residues</dt>
        <dd>{summary.candidateResidueCount}</dd>

        <dt>Matched residues</dt>
        <dd>{summary.matchedCount}</dd>

        <dt>Matched query</dt>
        <dd>{formatPercent(summary.matchedFractionQuery)}</dd>

        <dt>Matched candidate</dt>
        <dd>{formatPercent(summary.matchedFractionCandidate)}</dd>

        <dt>Identical</dt>
        <dd>{formatPercent(summary.identicalFraction)}</dd>

        <dt>Chemistry-compatible</dt>
        <dd>{formatPercent(summary.chemistryCompatibleFraction)}</dd>

        <dt>Mean matched distance</dt>
        <dd>{summary.meanMatchedDistance.toFixed(2)} Å</dd>

        <dt>Max matched distance</dt>
        <dd>{summary.maximumMatchedDistance.toFixed(2)} Å</dd>
      </dl>

      <div className="correspondence-filters">
        <label className="viewer-checkbox">
          <input
            type="checkbox"
            checked={keyOnly}
            onChange={(event) => setKeyOnly(event.target.checked)}
          />
          Key residues only
        </label>

        <label className="viewer-checkbox">
          <input
            type="checkbox"
            checked={identicalOnly}
            onChange={(event) =>
              setIdenticalOnly(event.target.checked)
            }
          />
          Identical only
        </label>

        <label className="viewer-checkbox">
          <input
            type="checkbox"
            checked={compatibleOnly}
            onChange={(event) =>
              setCompatibleOnly(event.target.checked)
            }
          />
          Chemistry-compatible only
        </label>

        <label className="viewer-checkbox">
          <input
            type="checkbox"
            checked={mismatchesOnly}
            onChange={(event) =>
              setMismatchesOnly(event.target.checked)
            }
          />
          Mismatches only
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

      <div className="correspondence-table-wrap">
        <table className="correspondence-table">
          <thead>
            <tr>
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

              return (
                <tr
                  key={`${match.query.label}-${match.candidate.label}`}
                  className={matchClass}
                >
                  <td>
                    {isKeyResidue(match.query, keyResidues) ? (
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
              )
            })}
          </tbody>
        </table>

        {visibleMatches.length === 0 && (
          <p className="muted-note">
            No matches pass the current filters.
          </p>
        )}
      </div>

      <details className="unmatched-section">
        <summary>
          Unmatched query residues ({unmatchedQuery.length})
        </summary>
        <UnmatchedResidueTable residues={unmatchedQuery} />
      </details>

      <details className="unmatched-section">
        <summary>
          Unmatched candidate residues ({unmatchedCandidate.length})
        </summary>
        <UnmatchedResidueTable residues={unmatchedCandidate} />
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
        catalytic or functional divergence.
      </p>
    </section>
  )
}

function UnmatchedResidueTable({
  residues,
}: {
  residues: ResiduePointView[]
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
        </tr>
      </thead>

      <tbody>
        {residues.map((residue) => (
          <tr key={residue.label}>
            <td>{residue.label}</td>
            <td>{residue.chemistry}</td>
            <td>{formatPosition(residue.position)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function isKeyResidue(
  residue: ResiduePointView,
  keyResidues: string[],
): boolean {
  const name =
    `${residue.residueName}${residue.residueNumber}`.toUpperCase()
  return keyResidues.includes(name)
}

function formatPercent(fraction: number): string {
  return `${(fraction * 100).toFixed(1)}%`
}

function formatPosition(position: ResiduePointView['position']): string {
  return `(${position.x.toFixed(1)}, ${position.y.toFixed(1)}, ${position.z.toFixed(1)})`
}
