import { Fragment, useEffect, useMemo, useState } from 'react'
import { useApiQuery } from '../../api/hooks'
import type {
  PocketDetails,
  ResidueChemistryView,
  ResidueAnalysis,
  Structure,
} from '../../api/types'
import { AsyncState } from '../../components/AsyncState'
import { ResiduePanel } from '../residue/ResiduePanel'
import { useResidueEvidence } from '../residue/hooks/useResidueEvidence'
import { LigandDepiction } from '../selectivity/LigandDepiction'
import { PoseViewer } from './PoseViewer'
import { useTextQuery } from './useTextQuery'
import {
  backboneTrace,
  parsePdbqtModels,
  proteinAtoms,
  type PdbqtAtom,
} from './pdbqt'
import {
  CATEGORY_COLORS,
  type ResidueCategoryKey,
} from './residueCategory'
import type {
  AssignedPocketView,
  ContactProfileView,
  CrossProteinComparisonView,
  CrossProteinRelationship,
  DockingTarget,
  LigandAnalysis,
  LigandRunOption,
  PocketAssignmentStatus,
  PosePocketAssignmentView,
  PoseRunView,
  PoseView,
  RunPocketOccupancyView,
} from './types'

const EMPTY_RESIDUE_ANALYSIS = new Map<number, ResidueAnalysis>()

function optionKey(option: LigandRunOption) {
  return `${option.ligandId}:${option.runId}`
}

function formatScore(value: number | null | undefined) {
  return value == null ? '—' : value.toFixed(2)
}

function formatDistance(value: number | null | undefined) {
  return value == null ? '—' : `${value.toFixed(2)} Å`
}

function formatPercent(value: number | null | undefined) {
  return value == null ? '—' : `${(value * 100).toFixed(0)}%`
}

function formatPocket(pocket: AssignedPocketView | null | undefined) {
  return pocket ? `Pocket ${pocket.pocketNumber} (${pocket.source})` : '—'
}

/** Lowest-score pose of a run; highest-confidence when ranks exist. */
function bestPose(run: PoseRunView): PoseView | null {
  let best: PoseView | null = null
  for (const pose of run.poses) {
    if (!best) {
      best = pose
    } else if (pose.confidence != null && best.confidence != null) {
      if (pose.confidence > best.confidence) best = pose
    } else if (pose.score < best.score) {
      best = pose
    }
  }
  return best
}

function csvCell(value: string | number | null | undefined) {
  const text = value == null ? '' : String(value)
  return /[",\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text
}

function downloadRunsCsv(ligandLabel: string, runs: PoseRunView[]) {
  const header = [
    'run_id', 'method', 'target', 'uniprot_id',
    'pose_id', 'label', 'score', 'seed', 'mode', 'rank', 'confidence',
  ]
  const lines = [header.join(',')]
  for (const run of runs) {
    for (const pose of run.poses) {
      lines.push([
        run.runId, run.method, run.target, run.uniProtId,
        pose.poseId, pose.label, pose.score, pose.seed, pose.mode,
        pose.rank, pose.confidence,
      ].map(csvCell).join(','))
    }
  }
  const blob = new Blob([lines.join('\n')], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${ligandLabel}-poses.csv`
  anchor.click()
  URL.revokeObjectURL(url)
}

function PosePanel({
  run,
  resetKey,
  selectedPoseId,
}: {
  run: PoseRunView
  resetKey: string
  selectedPoseId: number | null
}) {
  const best = useMemo(() => bestPose(run), [run])
  const pose = run.poses.find((candidate) =>
    candidate.poseId === selectedPoseId) ?? best

  const receptor = useTextQuery(`/api/docking-runs/${run.runId}/receptor-file`)
  const poseFile = useTextQuery(
    pose ? `/api/docking-poses/${pose.poseId}/file` : null,
  )

  const protein = useMemo<PdbqtAtom[]>(
    () => (receptor.data ? proteinAtoms(receptor.data) : []),
    [receptor.data],
  )
  const backbone = useMemo(() => backboneTrace(protein), [protein])
  const ligand = useMemo<PdbqtAtom[]>(() => {
    if (!poseFile.data || !pose) return []
    const models = parsePdbqtModels(poseFile.data)
    return models[(pose.mode ?? 1) - 1] ?? models[0] ?? []
  }, [poseFile.data, pose])

  return (
    <div className="pose-viewer-card">
      <header className="pose-viewer-caption">
        <strong>{run.target}</strong>
        <span>run {run.runId} · {run.method}</span>
        {pose && (
          <span>{pose.label} · score {formatScore(pose.score)}</span>
        )}
      </header>
      {(receptor.loading || (pose && poseFile.loading)) && (
        <AsyncState loading title="Loading pose" compact />
      )}
      {(receptor.error || poseFile.error) && (
        <AsyncState
          title="Could not load 3D data"
          message={(receptor.error ?? poseFile.error)?.message}
          onRetry={receptor.error ? receptor.retry : poseFile.retry}
          compact
        />
      )}
      {receptor.data && (!pose || poseFile.data) && (
        <PoseViewer
          protein={protein}
          backbone={backbone}
          ligand={ligand}
          resetKey={`${resetKey}-${pose?.poseId ?? 'none'}`}
        />
      )}
    </div>
  )
}

/** Colored dot marking a residue's primary physicochemical category. */
function CategoryDot({
  category,
  label,
}: {
  category: ResidueCategoryKey
  label: string
}) {
  return (
    <span
      className="category-dot"
      style={{ background: CATEGORY_COLORS[category] }}
      title={label}
    />
  )
}

/**
 * Contact profile of the pose currently selected for a run: the
 * embedded best-pose profile when the selection is the best pose,
 * otherwise fetched on demand. The focused run reuses the page-level
 * fetch (`fetchedProfile`/`fetchedError`) so its URL is requested once.
 */
function RunContactProfileCard({
  profile,
  selectedPoseId,
  fetchedProfile,
  fetchedError,
}: {
  profile: ContactProfileView
  selectedPoseId: number | null
  fetchedProfile?: ContactProfileView | null
  fetchedError?: Error | null
}) {
  const needsFetch = selectedPoseId != null
    && profile.poseId !== selectedPoseId
  const shared = fetchedProfile !== undefined
  const query = useApiQuery<ContactProfileView>(
    needsFetch && !shared
      ? `/api/docking-poses/${selectedPoseId}/contact-profile`
      : null,
  )
  const effective = needsFetch
    ? (shared ? (fetchedProfile ?? null) : query.data)
    : profile
  const error = needsFetch
    ? (shared ? (fetchedError ?? null) : query.error)
    : null

  if (needsFetch && !effective && !error) {
    return (
      <div className="pose-contact-card">
        <AsyncState loading title="Loading contact profile" compact />
      </div>
    )
  }
  if (needsFetch && error) {
    return (
      <div className="pose-contact-card">
        <AsyncState
          title="Contact profile unavailable"
          message={error.message}
          onRetry={shared ? undefined : query.retry}
          compact
        />
      </div>
    )
  }
  const shown = effective ?? profile
  const legendCategories = [...shown.contacts.reduce(
    (categories, contact) => {
      const chemistry = contact.chemistry
      if (chemistry.colorKey) categories.set(chemistry.colorKey, chemistry)
      return categories
    },
    new Map<ResidueCategoryKey, ResidueChemistryView>(),
  ).values()]
  return (
    <div className="pose-contact-card">
      <header>
        <strong>
          {shown.target} · run {shown.runId}
          {shown.method ? ` · ${shown.method}` : ''}
        </strong>
        <small>
          {shown.available
            ? `${shown.label} · contacts within ${shown.cutoffAngstroms} Å`
            : `Unavailable: ${shown.unavailableReason ?? 'unknown reason'}`}
        </small>
      </header>
      {shown.available && (
        <div className="report-table-scroll pose-contact-scroll">
          <table className="report-residue-table">
            <thead>
              <tr>
                <th>Residue</th>
                <th>Chain</th>
                <th>Min distance</th>
                <th>Interactions</th>
              </tr>
            </thead>
            <tbody>
              {shown.contacts.map((contact) => {
                const category = contact.chemistry.colorKey
                return (
                  <tr key={`${contact.chain}:${contact.residueNumber}`}>
                    <td>
                      {category && contact.chemistry.primaryLabel && (
                        <CategoryDot
                          category={category}
                          label={contact.chemistry.primaryLabel}
                        />
                      )}
                      {contact.residueName ?? ''} {contact.residueNumber}
                    </td>
                    <td>{contact.chain}</td>
                    <td>{formatDistance(contact.minimumDistance)}</td>
                    <td>
                      <span className="interaction-list">
                        {contact.interactions.map((interaction) => (
                          <span
                            className={`interaction-badge ${interaction.type.toLowerCase()}`}
                            key={`${interaction.type}:${interaction.receptorAtom}:${interaction.ligandAtom}`}
                            title={`${interaction.receptorAtom}–${interaction.ligandAtom}`
                              + ` · ${formatDistance(interaction.distance)}`
                              + (interaction.angleDegrees == null
                                ? ''
                                : ` · ${interaction.angleDegrees.toFixed(1)}°`)
                              + ` · ${interaction.basis}`}
                          >
                            {interaction.label}
                          </span>
                        ))}
                        {contact.interactions.length === 0 && '—'}
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {shown.available && legendCategories.length > 0 && (
        <p className="category-legend">
          {legendCategories.map((chemistry) => (
            <span key={chemistry.colorKey}>
              {chemistry.colorKey && chemistry.primaryLabel && (
                <CategoryDot
                  category={chemistry.colorKey}
                  label={chemistry.primaryLabel}
                />
              )}
              {chemistry.primaryLabel}
            </span>
          ))}
        </p>
      )}
    </div>
  )
}

const ASSIGNMENT_STATUS_LABELS: Record<PocketAssignmentStatus, string> = {
  ASSIGNED: 'Assigned',
  AMBIGUOUS: 'Ambiguous',
  NOT_ASSIGNED: 'Not assigned',
}

const ASSIGNMENT_STATUS_CLASSES: Record<PocketAssignmentStatus, string> = {
  ASSIGNED: 'assigned',
  AMBIGUOUS: 'ambiguous',
  NOT_ASSIGNED: 'not-assigned',
}

function AssignmentStatusBadge({ status }: { status: PocketAssignmentStatus }) {
  return (
    <span className={`assignment-badge ${ASSIGNMENT_STATUS_CLASSES[status]}`}>
      {ASSIGNMENT_STATUS_LABELS[status]}
    </span>
  )
}

/**
 * Pocket assignment of the focused pose. Refetches whenever the pose
 * dropdown changes (the poseId prop feeds the request path). The Vina
 * score and the assignment score stay separate labeled fields.
 */
function PoseAssignmentPanel({ pose }: { pose: PoseView }) {
  const query = useApiQuery<PosePocketAssignmentView>(
    `/api/docking-poses/${pose.poseId}/pocket-assignment`,
  )
  const assignment = query.data
  const metrics = assignment?.metrics ?? null
  return (
    <section className="panel pose-section" aria-label="Pocket assignment">
      <div className="panel-heading">
        <h2>Pocket assignment</h2>
        {assignment?.available && (
          <AssignmentStatusBadge status={assignment.status} />
        )}
      </div>
      <div className="pose-assignment-body">
        {query.loading && (
          <AsyncState loading title="Loading pocket assignment" compact />
        )}
        {query.error && (
          <AsyncState
            title="Pocket assignment unavailable"
            message={query.error.message}
            onRetry={query.retry}
            compact
          />
        )}
        {assignment && !assignment.available && (
          <AsyncState
            title="Pocket assignment unavailable"
            message={assignment.unavailableReason ?? 'unknown reason'}
            compact
          />
        )}
        {assignment?.available && (
          <>
            <p className="pose-assignment-line">
              {assignment.status === 'ASSIGNED' && assignment.assignedPocket
                ? `${pose.label} occupies pocket ${assignment.assignedPocket.pocketNumber} (${assignment.assignedPocket.source})`
                : assignment.status === 'AMBIGUOUS'
                  ? `${pose.label} occupies an ambiguous pocket region`
                  : `${pose.label} is not assigned to any pocket`}
            </p>
            <dl className="pose-facts">
              <div>
                <dt>Vina score</dt>
                <dd>{formatScore(assignment.score ?? pose.score)}</dd>
              </div>
              <div>
                <dt>Assignment score</dt>
                <dd>{formatScore(assignment.assignmentScore)}</dd>
              </div>
              <div>
                <dt>Second-best pocket</dt>
                <dd>{formatPocket(assignment.secondBestPocket)}</dd>
              </div>
              <div>
                <dt>Score margin</dt>
                <dd>{formatScore(assignment.scoreMargin)}</dd>
              </div>
              <div>
                <dt>Centroid distance</dt>
                <dd>{formatDistance(metrics?.ligandCentroidDistance)}</dd>
              </div>
              <div>
                <dt>
                  Atom containment
                  {metrics?.containmentBasis
                    ? ` (${metrics.containmentBasis})`
                    : ''}
                </dt>
                <dd>{formatPercent(metrics?.atomContainmentFraction)}</dd>
              </div>
              <div>
                <dt>Within 2 Å of sphere</dt>
                <dd>{formatPercent(metrics?.atomWithin2AOfSphereFraction)}</dd>
              </div>
              <div>
                <dt>Contact-residue coverage</dt>
                <dd>{formatPercent(metrics?.contactResidueCoverage)}</dd>
              </div>
              <div>
                <dt>Mean nearest-sphere distance</dt>
                <dd>{formatDistance(metrics?.meanNearestSphereDistance)}</dd>
              </div>
            </dl>
            {assignment.status !== 'ASSIGNED' && assignment.reason && (
              <p className="muted-note">{assignment.reason}</p>
            )}
          </>
        )}
      </div>
    </section>
  )
}

/**
 * Per-pocket pose occupancy of the visible run: how the run's poses
 * distribute over the characterized pockets. Occupancy is a pose
 * frequency, not a binding probability.
 */
function PocketOccupancyPanel({ runId }: { runId: number }) {
  const query = useApiQuery<RunPocketOccupancyView>(
    `/api/docking-runs/${runId}/pocket-occupancy`,
  )
  const occupancy = query.data
  return (
    <section className="panel pose-section" aria-label="Pose occupancy">
      <div className="panel-heading">
        <h2>Pose occupancy across pockets</h2>
        {occupancy?.available && (
          <span className="count-badge">
            {occupancy.entries.length} pockets
          </span>
        )}
      </div>
      {query.loading && (
        <AsyncState loading title="Loading pose occupancy" compact />
      )}
      {query.error && (
        <AsyncState
          title="Pose occupancy unavailable"
          message={query.error.message}
          onRetry={query.retry}
          compact
        />
      )}
      {occupancy && !occupancy.available && (
        <AsyncState
          title="Pose occupancy unavailable"
          message={occupancy.unavailableReason ?? 'unknown reason'}
          compact
        />
      )}
      {occupancy?.available && (
        <>
          <div className="report-table-scroll pose-occupancy-scroll">
            <table className="report-residue-table">
              <thead>
                <tr>
                  <th>Pocket</th>
                  <th>Poses</th>
                  <th>Fraction</th>
                  <th>Best affinity</th>
                  <th>Median affinity</th>
                  <th>Mean assignment score</th>
                </tr>
              </thead>
              <tbody>
                {occupancy.entries.map((entry) => (
                  <Fragment key={entry.pocketId}>
                    <tr>
                      <td>
                        Pocket {entry.pocketNumber}
                        {' '}
                        <small>({entry.source})</small>
                      </td>
                      <td>{entry.poseCount}</td>
                      <td>{formatPercent(entry.fractionOfPoses)}</td>
                      <td>{formatScore(entry.bestAffinity)}</td>
                      <td>{formatScore(entry.medianAffinity)}</td>
                      <td>{formatScore(entry.meanAssignmentScore)}</td>
                    </tr>
                    {entry.poseLabels.length > 0 && (
                      <tr className="pose-occupancy-labels">
                        <td colSpan={6}>{entry.poseLabels.join(', ')}</td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
          <p className="muted-note pose-note">
            {occupancy.notAssignedCount} poses not assigned to any pocket
            · {occupancy.ambiguousCount} ambiguous. Occupancy counts how
            often poses land in a pocket; it is not a binding probability.
          </p>
        </>
      )}
    </section>
  )
}

/** Poses of the same ligand docked against one other target. */
function OtherTargetPoseOptions({
  target,
  ligandId,
}: {
  target: DockingTarget
  ligandId: string
}) {
  const query = useApiQuery<LigandAnalysis>(
    `/api/targets/${target.receptorId}/ligand-analysis?ligandId=${ligandId}`,
  )
  if (!query.data) return null
  return (
    <optgroup label={target.targetName}>
      {query.data.runs.flatMap((run) =>
        run.poses.map((pose) => (
          <option key={pose.poseId} value={pose.poseId}>
            {pose.label} · {run.target} ({formatScore(pose.score)})
          </option>
        )))}
    </optgroup>
  )
}

const RELATIONSHIP_LABELS: Record<CrossProteinRelationship, string> = {
  SAME_HOMOLOGOUS_SITE: 'Same homologous site',
  HOMOLOGOUS_SITE_DIFFERENT_POSE: 'Homologous site, different pose',
  DIFFERENT_SITE: 'Different site',
  AMBIGUOUS: 'Ambiguous',
}

const RELATIONSHIP_CLASSES: Record<CrossProteinRelationship, string> = {
  SAME_HOMOLOGOUS_SITE: 'assigned',
  HOMOLOGOUS_SITE_DIFFERENT_POSE: 'ambiguous',
  DIFFERENT_SITE: 'not-assigned',
  AMBIGUOUS: 'ambiguous',
}

function ComparisonSide({
  title,
  side,
}: {
  title: string
  side: CrossProteinComparisonView['query']
}) {
  return (
    <div>
      <dt>{title}</dt>
      <dd>
        {side.target}
        {side.uniProtId ? ` · ${side.uniProtId}` : ''}
        {' — '}
        {side.assignedPocket
          ? formatPocket(side.assignedPocket)
          : 'no pocket assigned'}
      </dd>
    </div>
  )
}

/**
 * Compares the focused pose with a pose of the same ligand docked
 * against another target: do both occupy the same (homologous) site?
 */
function CrossProteinComparisonPanel({
  otherTargets,
  ligandId,
  focusedPose,
}: {
  otherTargets: DockingTarget[]
  ligandId: string
  focusedPose: PoseView
}) {
  const [otherPoseId, setOtherPoseId] = useState<number | null>(null)
  const query = useApiQuery<CrossProteinComparisonView>(
    otherPoseId != null
      ? `/api/docking-poses/${focusedPose.poseId}/cross-protein-comparison`
        + `?otherPoseId=${otherPoseId}`
      : null,
  )
  const comparison = query.data
  return (
    <section className="panel pose-section" aria-label="Cross-protein comparison">
      <div className="panel-heading">
        <h2>Cross-protein comparison</h2>
        {comparison?.available && (
          <span
            className={
              `assignment-badge ${RELATIONSHIP_CLASSES[comparison.relationship]}`
            }
          >
            {RELATIONSHIP_LABELS[comparison.relationship]}
          </span>
        )}
      </div>
      <div className="pose-compare-body">
        <label htmlFor="pose-compare-select">Compare with</label>
        <select
          id="pose-compare-select"
          value={otherPoseId ?? ''}
          onChange={(event) => {
            const value = event.target.value
            setOtherPoseId(value === '' ? null : Number(value))
          }}
        >
          <option value="">Select a pose on another target…</option>
          {otherTargets.map((target) => (
            <OtherTargetPoseOptions
              key={target.receptorId}
              target={target}
              ligandId={ligandId}
            />
          ))}
        </select>
        {otherPoseId == null && (
          <p className="muted-note">
            Pick a pose of the same ligand docked against another target
            to compare where each pose sits.
          </p>
        )}
        {query.loading && (
          <AsyncState loading title="Comparing poses" compact />
        )}
        {query.error && (
          <AsyncState
            title="Comparison unavailable"
            message={query.error.message}
            onRetry={query.retry}
            compact
          />
        )}
        {comparison && !comparison.available && (
          <AsyncState
            title="Comparison unavailable"
            message={comparison.unavailableReason ?? 'unknown reason'}
            compact
          />
        )}
        {comparison?.available && (
          <>
            <dl className="pose-facts">
              <ComparisonSide title="This pose" side={comparison.query} />
              <ComparisonSide
                title="Compared pose"
                side={comparison.candidate}
              />
              <div>
                <dt>Pockets</dt>
                <dd>
                  {comparison.pocketsStructurallyHomologous == null
                    ? '—'
                    : comparison.pocketsStructurallyHomologous
                      ? 'homologous'
                      : 'not homologous'}
                </dd>
              </div>
              <div>
                <dt>Pocket similarity</dt>
                <dd>{formatScore(comparison.pocketSimilarity)}</dd>
              </div>
              <div>
                <dt>Aligned centroid distance</dt>
                <dd>
                  {formatDistance(comparison.alignedLigandCentroidDistance)}
                </dd>
              </div>
              <div>
                <dt>Aligned ligand RMSD</dt>
                <dd>{formatDistance(comparison.alignedLigandRmsd)}</dd>
              </div>
              <div>
                <dt>Shared aligned contact residues</dt>
                <dd>{comparison.sharedAlignedContactResidues ?? '—'}</dd>
              </div>
              <div>
                <dt>Contact-residue similarity</dt>
                <dd>{formatPercent(comparison.contactResidueSimilarity)}</dd>
              </div>
            </dl>
            {comparison.reason && (
              <p className="muted-note">{comparison.reason}</p>
            )}
          </>
        )}
      </div>
    </section>
  )
}

/**
 * Generic receptor-ligand analysis: pick a target receptor, then any
 * ligand docked against it, and inspect its runs, poses, contacts,
 * and clusters.
 */
export function LigandAnalysisPage() {
  const targetsQuery = useApiQuery<DockingTarget[]>('/api/docking-targets')
  const [selectedReceptorId, setSelectedReceptorId] =
    useState<number | null>(null)
  const [targetSearch, setTargetSearch] = useState('')
  const [selectedOption, setSelectedOption] =
    useState<LigandRunOption | null>(null)
  const [ligandSearch, setLigandSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [lastReceptorId, setLastReceptorId] = useState<number | null>(null)

  useEffect(() => {
    const handle = setTimeout(
      () => setDebouncedSearch(ligandSearch.trim()),
      300,
    )
    return () => clearTimeout(handle)
  }, [ligandSearch])

  const targets = useMemo(
    () => targetsQuery.data ?? [],
    [targetsQuery.data],
  )
  const filteredTargets = useMemo(() => {
    const needle = targetSearch.trim().toLowerCase()
    if (!needle) return targets
    return targets.filter((target) =>
      target.targetName.toLowerCase().includes(needle)
      || (target.uniProtId ?? '').toLowerCase().includes(needle))
  }, [targets, targetSearch])

  const receptorId = selectedReceptorId ?? targets[0]?.receptorId ?? null

  // Changing the target invalidates the ligand run selection.
  if (receptorId !== lastReceptorId) {
    setLastReceptorId(receptorId)
    setSelectedOption(null)
  }

  const runOptionsPath = receptorId != null
    ? `/api/targets/${receptorId}/docking-ligand-runs?limit=100`
      + (debouncedSearch
        ? `&query=${encodeURIComponent(debouncedSearch)}`
        : '')
    : null
  const runOptionsQuery = useApiQuery<LigandRunOption[]>(runOptionsPath)

  const runOptions = useMemo(
    () => runOptionsQuery.data ?? [],
    [runOptionsQuery.data],
  )
  // An explicit pick survives the search being cleared (which refetches
  // the unfiltered top-scoring runs); only an unpicked session falls
  // back to the best-scoring run.
  const effectiveOption = selectedOption ?? runOptions[0] ?? null

  const analysisQuery = useApiQuery<LigandAnalysis>(
    receptorId != null && effectiveOption
      ? `/api/targets/${receptorId}/ligand-analysis`
        + `?ligandId=${effectiveOption.ligandId}`
      : null,
  )

  // Page-level pose selection: the 3D dropdowns, pose cards, contact
  // tables and residue highlight all follow these two pieces of state.
  // Poses are scoped per run, so the selection is a runId -> poseId map.
  const [selectedPoseByRun, setSelectedPoseByRun] = useState<
    Record<number, number>
  >({})
  const [focusedRunId, setFocusedRunId] = useState<number | null>(null)
  const [lastAnalysisLigandId, setLastAnalysisLigandId] =
    useState<string | null>(null)

  const analysis = analysisQuery.data ?? null

  // Changing the ligand invalidates the pose selection.
  if (analysis && analysis.ligandId !== lastAnalysisLigandId) {
    setLastAnalysisLigandId(analysis.ligandId)
    setSelectedPoseByRun({})
    setFocusedRunId(null)
  }

  const handlePoseSelect = (runId: number, poseId: number) => {
    setFocusedRunId(runId)
    setSelectedPoseByRun((current) => ({ ...current, [runId]: poseId }))
  }

  const profileFor = (runId: number) =>
    analysis?.contactProfiles.find(
      (candidate) => candidate.runId === runId,
    ) ?? null

  // Explicit pick, else the run's best pose (identified by the embedded
  // best-pose contact profile so no extra request is needed for it).
  const selectedPoseIdFor = (run: PoseRunView) =>
    selectedPoseByRun[run.runId]
    ?? profileFor(run.runId)?.poseId
    ?? bestPose(run)?.poseId
    ?? null

  // Only the run picked in the ligand search is shown; the analysis
  // payload carries every run of the ligand.
  const visibleRuns = useMemo(
    () => analysis?.runs.filter(
      (run) => run.runId === effectiveOption?.runId,
    ) ?? [],
    [analysis, effectiveOption],
  )
  const visibleProfiles = useMemo(
    () => analysis?.contactProfiles.filter(
      (profile) => visibleRuns.some(
        (run) => run.runId === profile.runId,
      ),
    ) ?? [],
    [analysis, visibleRuns],
  )
  const focusedRun = visibleRuns.find(
    (run) => run.runId === focusedRunId,
  ) ?? visibleRuns[0] ?? null
  const focusedPoseId = focusedRun ? selectedPoseIdFor(focusedRun) : null
  const focusedEmbedded = focusedRun
    ? profileFor(focusedRun.runId)
    : null
  const focusedNeedsFetch = focusedPoseId != null
    && focusedEmbedded?.poseId !== focusedPoseId
  const focusedProfileQuery = useApiQuery<ContactProfileView>(
    focusedNeedsFetch
      ? `/api/docking-poses/${focusedPoseId}/contact-profile`
      : null,
  )
  const focusedProfile = focusedNeedsFetch
    ? focusedProfileQuery.data
    : focusedEmbedded
  const focusedPose = focusedRun?.poses.find(
    (pose) => pose.poseId === focusedPoseId,
  ) ?? null

  const target = targets.find(
    (candidate) => candidate.receptorId === receptorId,
  ) ?? null
  const structureId = target?.structureId ?? null
  const structureQuery = useApiQuery<Structure>(
    structureId != null ? `/api/structures/${structureId}` : null,
  )
  const chosenPocketQuery = useApiQuery<PocketDetails>(
    structureQuery.data?.chosenPocket?.id
      ? `/api/pockets/${structureQuery.data.chosenPocket.id}`
      : null,
  )
  const residueEvidence = useResidueEvidence(structureId)

  const chosenPocketResidueIds = useMemo(
    () => new Set(
      chosenPocketQuery.data?.residues.map((residue) => residue.id) ?? [],
    ),
    [chosenPocketQuery.data],
  )
  const directContactResidueIds = useMemo(
    () => new Set(
      chosenPocketQuery.data?.evidence?.directContactResidueIds ?? [],
    ),
    [chosenPocketQuery.data],
  )
  const ligandContactResidueIds = useMemo(() => {
    const residues = structureQuery.data?.residues ?? []
    const profile = focusedProfile?.available ? focusedProfile : null
    if (!profile) return new Set<number>()
    const ids = new Set<number>()
    for (const contact of profile.contacts) {
      const residue = residues.find(
        (candidate) =>
          candidate.chain === contact.chain
          && candidate.residueNumber === contact.residueNumber,
      )
      if (residue) ids.add(residue.id)
    }
    return ids
  }, [focusedProfile, structureQuery.data])

  if (targetsQuery.loading && !targetsQuery.data) {
    return <AsyncState loading title="Loading targets" />
  }
  if (targetsQuery.error || !targetsQuery.data) {
    return (
      <AsyncState
        title="Targets unavailable"
        message={targetsQuery.error?.message}
        onRetry={targetsQuery.retry}
      />
    )
  }

  return (
    <section className="workspace pose-page">
      <header className="selectivity-hero">
        <div>
          <p className="eyebrow">Receptor-ligand analysis</p>
          <h1>
            {target ? target.targetName : 'Docked ligands'}
            {' '}· docked ligands
          </h1>
          <p className="selectivity-intro">
            Pick a target receptor, then a ligand docked against it, to
            inspect runs, poses, per-residue contacts, and pose
            clustering.
          </p>
        </div>
        <div className="pose-ligand-picker">
          <label htmlFor="pose-target-search">Target</label>
          <div className="pose-autocomplete">
            <input
              id="pose-target-search"
              type="search"
              placeholder="Filter targets"
              value={targetSearch}
              onChange={(event) => setTargetSearch(event.target.value)}
            />
            {targetSearch.trim() !== '' && (
              <div className="pose-option-list">
                {filteredTargets.slice(0, 8).map((candidate) => (
                  <button
                    key={candidate.receptorId}
                    type="button"
                    className={candidate.receptorId === receptorId
                      ? 'pose-option selected'
                      : 'pose-option'}
                    aria-pressed={candidate.receptorId === receptorId}
                    onClick={() => {
                      setSelectedReceptorId(candidate.receptorId)
                      setTargetSearch('')
                    }}
                  >
                    {candidate.targetName}
                    {candidate.uniProtId ? ` · ${candidate.uniProtId}` : ''}
                    {' '}· {candidate.ligandCount} ligands
                  </button>
                ))}
                {filteredTargets.length === 0 && (
                  <small>No targets match.</small>
                )}
              </div>
            )}
          </div>
          <label htmlFor="pose-ligand-search">Ligand</label>
          <div className="pose-autocomplete">
            <input
              id="pose-ligand-search"
              type="search"
              placeholder="Search id, label or SMILES"
              value={ligandSearch}
              onChange={(event) => setLigandSearch(event.target.value)}
              disabled={receptorId == null}
            />
            {ligandSearch.trim() !== '' && (
              <div className="pose-option-list">
                {runOptions.slice(0, 8).map((option) => (
                  <button
                    key={optionKey(option)}
                    type="button"
                    className={option === effectiveOption
                      ? 'pose-option selected'
                      : 'pose-option'}
                    aria-pressed={option === effectiveOption}
                    onClick={() => {
                      setSelectedOption(option)
                      setLigandSearch('')
                    }}
                  >
                    {option.label} · {option.method} · best{' '}
                    {formatScore(option.bestScore)} · {option.poseCount} poses
                  </button>
                ))}
                {runOptions.length === 0 && !runOptionsQuery.loading && (
                  <small>No docked ligands match.</small>
                )}
              </div>
            )}
          </div>
          {effectiveOption == null && !runOptionsQuery.loading && (
            <small className="pose-current-option">
              No docked ligands for this target.
            </small>
          )}
        </div>
      </header>

      {analysis && structureId != null && (
        <div className="workspace-grid">
          <ResiduePanel
            key={structureId}
            structureId={structureId}
            residues={structureQuery.data?.residues ?? []}
            highlightedResidueIds={chosenPocketResidueIds}
            chosenPocketResidueIds={chosenPocketResidueIds}
            directContactResidueIds={directContactResidueIds}
            ligandContactResidueIds={ligandContactResidueIds}
            activePocket={chosenPocketQuery.data ?? null}
            pocketLoading={chosenPocketQuery.loading}
            dockingRuns={[]}
            selectedRunId={null}
            onRunSelect={() => {}}
            residueAnalysis={EMPTY_RESIDUE_ANALYSIS}
            analysisLoading={false}
            residueEvidence={residueEvidence.byResidueId}
            evidenceLoading={residueEvidence.loading}
            bare
            colorPocketByCategory
            contextNote={
              <span className="pose-context-depiction">
                <strong>{analysis.ligandLabel}</strong>
                {focusedRun && focusedPose && (
                  <small>
                    score {formatScore(focusedPose.score)} ·{' '}
                    {focusedRun.poseCount} poses
                  </small>
                )}
                {analysis.smiles ? (
                  <>
                    <code className="pose-preview-smiles">
                      {analysis.smiles}
                    </code>
                    <span className="pose-context-hover">
                      <LigandDepiction
                        smiles={analysis.smiles}
                        label={analysis.ligandLabel}
                      />
                    </span>
                  </>
                ) : (
                  <small>No SMILES recorded for this ligand</small>
                )}
                {focusedRun && focusedRun.poses.length > 1 && (
                  <select
                    aria-label="Pose"
                    className="pose-context-select"
                    value={focusedPose?.poseId ?? ''}
                    onChange={(event) =>
                      handlePoseSelect(
                        focusedRun.runId,
                        Number(event.target.value),
                      )}
                  >
                    {focusedRun.poses.map((candidate) => (
                      <option
                        key={candidate.poseId}
                        value={candidate.poseId}
                      >
                        {candidate.label} ({formatScore(candidate.score)})
                      </option>
                    ))}
                  </select>
                )}
              </span>
            }
          />
        </div>
      )}

      {analysisQuery.loading && (
        <AsyncState loading title="Loading ligand analysis" compact />
      )}
      {analysisQuery.error && (
        <AsyncState
          title="Ligand analysis unavailable"
          message={analysisQuery.error.message}
          onRetry={analysisQuery.retry}
          compact
        />
      )}

      {analysis && (
        <>
          <div className="selectivity-toolbar">
            <div className="selectivity-toolbar-actions">
              <span>
                {visibleRuns.reduce(
                  (total, run) => total + run.poseCount, 0)} poses
              </span>
              <button
                type="button"
                className="excel-download"
                onClick={() =>
                  downloadRunsCsv(analysis.ligandLabel, visibleRuns)}
              >
                <span aria-hidden="true">↓</span>
                Download CSV
              </button>
            </div>
          </div>

          {focusedPose && <PoseAssignmentPanel pose={focusedPose} />}

          <section className="panel pose-section">
            <div className="pose-viewer-grid">
              {visibleRuns.map((run) => (
                <PosePanel
                  key={run.runId}
                  run={run}
                  resetKey={`${analysis.ligandId}-${run.runId}`}
                  selectedPoseId={selectedPoseByRun[run.runId] ?? null}
                />
              ))}
            </div>
            <p className="muted-note pose-viewer-note">
              Receptor atoms and C-alpha trace in grey; the docked ligand
              as element-colored sticks. Drag to rotate, scroll to zoom;
              the dropdown switches poses within a run.
            </p>
          </section>

          <section className="panel pose-section">
            <div className="panel-heading">
              <h2>Selected-pose contact profiles</h2>
              <span className="count-badge">
                {visibleProfiles.length} runs
              </span>
            </div>
            <div className="pose-contact-grid">
              {visibleProfiles.map((profile) => {
                const run = analysis.runs.find(
                  (candidate) => candidate.runId === profile.runId,
                )
                const isFocused = profile.runId === focusedRun?.runId
                return (
                  <RunContactProfileCard
                    key={profile.runId}
                    profile={profile}
                    selectedPoseId={run ? selectedPoseIdFor(run) : null}
                    fetchedProfile={
                      isFocused ? focusedProfile : undefined
                    }
                    fetchedError={
                      isFocused ? focusedProfileQuery.error : undefined
                    }
                  />
                )
              })}
            </div>
          </section>

          {focusedRun && <PocketOccupancyPanel runId={focusedRun.runId} />}

          {focusedPose
            && targets.some(
              (candidate) => candidate.receptorId !== receptorId,
            ) && (
            <CrossProteinComparisonPanel
              key={analysis.ligandId}
              otherTargets={targets.filter(
                (candidate) => candidate.receptorId !== receptorId,
              )}
              ligandId={analysis.ligandId}
              focusedPose={focusedPose}
            />
          )}
        </>
      )}
    </section>
  )
}
