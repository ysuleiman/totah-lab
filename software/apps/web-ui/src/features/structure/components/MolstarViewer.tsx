import { useEffect, useMemo, useRef, useState } from 'react'
import { Viewer } from 'molstar/lib/apps/viewer/app'
import { StructureElement } from 'molstar/lib/mol-model/structure'
import type {
  Point3D,
  PocketDetails,
  PocketComparisonDetails,
  PocketGeometryView,
  RigidTransformView,
  Structure as StructureView,
} from '../../../api/types'
import { useApiQuery } from '../../../api/hooks'
import { getText } from '../../../api/client'
import {
  PointCloudViewer,
  type ViewerCamera,
} from '../../compare/PointCloudViewer'
import { buildPocketSurface } from '../pocketSurface'
import { detectPocketOpenings, pdbHeavyAtoms } from '../pocketOpenings'
import {
  reconstructStaticChannels,
  type StaticStructureAtom,
} from '../staticChannelAnalysis'
import {
  buildChannelPhaseDiagram,
  type ChannelPhaseDiagram,
} from '../channelPhaseDiagram'
import {
  bestRouteCorrespondence,
  buildRouteFingerprint,
  compareRouteFingerprints,
  type RouteFingerprint,
  type RoutePairEvidence,
} from '../routeTopologyAnalysis'
import 'molstar/build/viewer/molstar.css'

type Representation = 'cartoon' | 'sticks' | 'surface' | 'spacefill'
type ViewMode = 'protein' | 'pocket'

interface Props {
  structureId: number
  structureName: string
  pocket: PocketDetails | null
  onClose: () => void
  variant?: 'classic' | 'mechanistic'
}

const PRESETS: Record<Representation, string> = {
  cartoon: 'polymer-cartoon',
  sticks: 'atomic-detail',
  surface: 'molecular-surface',
  spacefill: 'illustrative',
}

export function MolstarViewer({
  structureId,
  structureName,
  pocket,
  onClose,
  variant = 'classic',
}: Props) {
  const counterpartStructureId = structureId === 2 ? 3 : 2
  const [activeStructureId, setActiveStructureId] = useState(structureId)
  const counterpartStructureQuery = useApiQuery<StructureView>(
    variant === 'mechanistic'
      ? `/api/structures/${counterpartStructureId}`
      : null,
  )
  const counterpartPocketId = counterpartStructureQuery.data?.chosenPocket?.id
  const counterpartPocketQuery = useApiQuery<PocketDetails>(
    counterpartPocketId ? `/api/pockets/${counterpartPocketId}` : null,
  )
  const showingCounterpart = activeStructureId === counterpartStructureId
  const activePocket = showingCounterpart
    ? counterpartPocketQuery.data ?? null
    : pocket
  const activeStructureName = showingCounterpart
    ? counterpartStructureQuery.data?.receptor.proteinName
      ?? counterpartStructureQuery.data?.receptor.targetName
      ?? `Structure ${counterpartStructureId}`
    : structureName
  const pocketB = structureId === 2 ? pocket : counterpartPocketQuery.data ?? null
  const pocketA = structureId === 3 ? pocket : counterpartPocketQuery.data ?? null
  const comparisonQuery = useApiQuery<PocketComparisonDetails>(
    variant === 'mechanistic' && pocketB && pocketA
      ? `/api/pockets/${pocketB.id}/compare/${pocketA.id}` : null,
  )
  const hostRef = useRef<HTMLDivElement | null>(null)
  const viewerRef = useRef<Viewer | null>(null)
  const pocketRef = useRef(activePocket)
  const [representation, setRepresentation] = useState<Representation>('cartoon')
  const [viewMode, setViewMode] = useState<ViewMode>(
    variant === 'mechanistic' ? 'pocket' : 'protein',
  )
  const [showPocket, setShowPocket] = useState(true)
  const [surfaceOpacity, setSurfaceOpacity] = useState(0.42)
  const [showResidueMarkers, setShowResidueMarkers] = useState(true)
  const [residueMarkerPoints, setResidueMarkerPoints] = useState<Point3D[]>([])
  const [residueLabels, setResidueLabels] = useState<{
    point: Point3D
    label: string
  }[]>([])
  const [heavyAtomPoints, setHeavyAtomPoints] = useState<Point3D[]>([])
  const [structureAtoms, setStructureAtoms] = useState<StaticStructureAtom[]>([])
  const [samAtomPoints, setSamAtomPoints] = useState<Point3D[]>([])
  const [samBondPoints, setSamBondPoints] = useState<Point3D[]>([])
  const [showSam, setShowSam] = useState(true)
  const [mechanisticScene, setMechanisticScene] = useState<'mechanistic' | 'clean' | 'residues'>('mechanistic')
  const [showChannel, setShowChannel] = useState(true)
  const [showFpocketVolume, setShowFpocketVolume] = useState(true)
  const [showEntranceCandidates, setShowEntranceCandidates] = useState(true)
  const [showReactionPath, setShowReactionPath] = useState(true)
  const [showBottlenecks, setShowBottlenecks] = useState(true)
  const [showApproachVector, setShowApproachVector] = useState(true)
  const [showNacCorridor, setShowNacCorridor] = useState(false)
  const [showPathDetails, setShowPathDetails] = useState(false)
  const [showProvenance, setShowProvenance] = useState(true)
  const [samSulfur, setSamSulfur] = useState<Point3D | null>(null)
  const [samMethyl, setSamMethyl] = useState<Point3D | null>(null)
  const [probeRadius, setProbeRadius] = useState(1.2)
  const [selectedEntrance, setSelectedEntrance] = useState(0)
  const [paralogSnapshots, setParalogSnapshots] = useState<Record<number, ReturnType<typeof reconstructStaticChannels>>>({})
  const [sharedCamera, setSharedCamera] = useState<ViewerCamera>({
    yaw: 0.7, pitch: 0.45, zoom: 3.2,
  })
  const [comparisonSam, setComparisonSam] = useState<Record<number, string>>({})
  useEffect(() => {
    if (variant !== 'mechanistic') return
    const controller = new AbortController()
    for (const id of [2, 3]) {
      void getText(`/api/structures/${id}/sam-file`, controller.signal)
        .then((sam) => {
          if (!controller.signal.aborted) {
            setComparisonSam((current) => ({ ...current, [id]: sam }))
          }
        }).catch(() => undefined)
    }
    return () => controller.abort()
  }, [variant])
  const geometryQuery = useApiQuery<PocketGeometryView>(
    activePocket ? `/api/pockets/${activePocket.id}/geometry` : null,
  )
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const pocketSurface = useMemo(
    () => variant === 'classic'
      ? buildPocketSurface(geometryQuery.data?.alphaSpheres ?? [])
      : [],
    [geometryQuery.data?.alphaSpheres, variant],
  )
  const openings = useMemo(() => {
    const geometry = geometryQuery.data
    if (!geometry || geometry.alphaSpheres.length === 0 || heavyAtomPoints.length === 0) return []
    return detectPocketOpenings(
      geometry.alphaSpheres,
      geometry.centroid,
      heavyAtomPoints,
    )
  }, [geometryQuery.data, heavyAtomPoints])
  const mouth = openings[0] ?? null
  const staticChannels = useMemo(() => reconstructStaticChannels(
    geometryQuery.data?.alphaSpheres ?? [],
    openings,
    samMethyl,
    geometryQuery.data?.centroid ?? { x: 0, y: 0, z: 0 },
    probeRadius,
    structureAtoms,
  ), [geometryQuery.data, openings, probeRadius, samMethyl, structureAtoms])
  const selectedChannel = staticChannels[selectedEntrance] ?? null
  useEffect(() => {
    if (staticChannels.length === 0) return
    setParalogSnapshots((current) => ({
      ...current,
      [activeStructureId]: staticChannels,
    }))
  }, [activeStructureId, staticChannels])
  const bottleneckGeometry = useMemo(
    () => variant === 'mechanistic' && showChannel
      ? [
          ...(selectedChannel
            ? samplePolyline(selectedChannel.centerline)
            : []),
          ...(selectedChannel?.bottleneck && selectedChannel.centerline.length > 1
            ? openingRing(
                selectedChannel.bottleneck,
                localDirection(selectedChannel.centerline, selectedChannel.bottleneck),
                selectedChannel.minimumClearance ?? probeRadius,
              ) : []),
          ...(samSulfur && samMethyl ? attackAxis(samSulfur, samMethyl) : []),
        ]
      : [],
    [probeRadius, samMethyl, samSulfur, selectedChannel, showChannel, variant],
  )
  const samCenter = useMemo(
    () => centroid(samAtomPoints),
    [samAtomPoints],
  )
  const visibleResidueLabels = useMemo(() => {
    if (variant !== 'mechanistic') return residueLabels
    if (mechanisticScene === 'clean') return []
    if (mechanisticScene === 'residues') return residueLabels
    const keyResidues = new Set([98, 144, 146, 149, 150, 151, 198, 199, 200, 202, 203, 232])
    return residueLabels.filter(({ label }) => {
      const match = label.match(/\d+/)
      return match && keyResidues.has(Number(match[0]))
    })
  }, [mechanisticScene, residueLabels, variant])

  useEffect(() => {
    const controller = new AbortController()
    void getText(`/api/structures/${activeStructureId}/sam-file`, controller.signal)
      .then((pdb) => {
        const atoms = pdbAtoms(pdb)
        setSamAtomPoints(atoms.map((atom) => atom.point))
        setSamBondPoints(inferBondPoints(atoms))
        setSamSulfur(atoms.find((atom) => atom.name === 'SD')?.point ?? null)
        setSamMethyl(atoms.find((atom) => atom.name === 'CE')?.point ?? null)
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setSamAtomPoints([])
          setSamBondPoints([])
          setSamSulfur(null)
          setSamMethyl(null)
        }
      })
    return () => controller.abort()
  }, [activeStructureId])

  useEffect(() => {
    if (!activePocket) {
      setResidueMarkerPoints([])
      setResidueLabels([])
      setHeavyAtomPoints([])
      setStructureAtoms([])
      return
    }
    const controller = new AbortController()
    void getText(`/api/structures/${activeStructureId}/file`, controller.signal)
      .then((pdb) => {
        const markers = pocketResidueMarkers(pdb, activePocket)
        setResidueMarkerPoints(markers.map((marker) => marker.point))
        setResidueLabels(markers)
        setHeavyAtomPoints(pdbHeavyAtoms(pdb))
        setStructureAtoms(pdbStructureAtoms(pdb))
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setResidueMarkerPoints([])
          setResidueLabels([])
          setHeavyAtomPoints([])
          setStructureAtoms([])
        }
      })
    return () => controller.abort()
  }, [activeStructureId, activePocket])

  useEffect(() => {
    pocketRef.current = activePocket
    const viewer = viewerRef.current
    if (viewer && activePocket) {
      void applyRepresentation(viewer, representation, activePocket, showPocket)
    }
    // Representation and visibility changes are handled by their controls.
    // This effect only synchronizes a pocket that finishes loading later.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activePocket])

  useEffect(() => {
    let cancelled = false

    if (variant === 'mechanistic' && viewMode === 'pocket') {
      setLoading(false)
      return () => { cancelled = true }
    }

    async function createViewer() {
      if (!hostRef.current) return
      try {
        const viewer = await Viewer.create(hostRef.current, {
          layoutIsExpanded: false,
          layoutShowControls: false,
          layoutShowSequence: false,
          viewportShowExpand: false,
        })
        if (cancelled) {
          viewer.dispose()
          return
        }
        viewerRef.current = viewer
        await viewer.loadStructureFromUrl(
          `/api/structures/${activeStructureId}/file`,
          'pdb',
          false,
        )
        await applyRepresentation(viewer, 'cartoon', pocketRef.current, true)
        if (!cancelled) setLoading(false)
      } catch (cause) {
        if (!cancelled) {
          setError(cause instanceof Error ? cause.message : 'Could not load structure')
          setLoading(false)
        }
      }
    }

    // Deferring creation avoids mounting Mol* twice into the same host during
    // React StrictMode's development-only setup/cleanup probe.
    const timer = window.setTimeout(() => void createViewer(), 0)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
      viewerRef.current?.dispose()
      viewerRef.current = null
    }
  }, [activeStructureId, variant, viewMode])

  async function changeRepresentation(next: Representation) {
    const viewer = viewerRef.current
    if (!viewer || next === representation) return
    setRepresentation(next)
    setLoading(true)
    try {
      await viewer.plugin.clear()
      await viewer.loadStructureFromUrl(
        `/api/structures/${activeStructureId}/file`,
        'pdb',
        false,
        { representationParams: undefined },
      )
      await applyRepresentation(viewer, next, activePocket, showPocket)
      setLoading(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not change representation')
      setLoading(false)
    }
  }

  async function togglePocket() {
    const viewer = viewerRef.current
    if (!viewer || !activePocket) return
    const next = !showPocket
    setShowPocket(next)
    setLoading(true)
    try {
      await applyRepresentation(viewer, representation, activePocket, next)
      setLoading(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not update pocket view')
      setLoading(false)
    }
  }

  function focusPocket() {
    if (!activePocket) return
    viewerRef.current?.structureInteractivity({
      elements: pocketElements(activePocket),
      action: 'focus',
      focusOptions: { minRadius: 8, extraRadius: 4 },
    })
  }

  return (
    <div className="molstar-dialog-backdrop" role="presentation">
      <section className={`molstar-dialog${variant === 'mechanistic' ? ' mechanistic-dialog' : ''}`} role="dialog" aria-modal="true" aria-label={`3D view of ${activeStructureName}`}>
        <header className="molstar-dialog-header">
          <div>
            <p className="eyebrow">{variant === 'mechanistic' ? 'Mechanistic pocket viewer' : 'Mol* structure viewer'}</p>
            <h2>{activeStructureName}</h2>
          </div>
          <button type="button" onClick={onClose} aria-label="Close 3D view">Close</button>
        </header>
        {variant === 'mechanistic' && (
          <div className="mechanistic-panel-bar">
            <div className="paralog-switch" aria-label="METTL7 paralog">
              <button type="button" className={activeStructureId === 2 ? 'active' : ''} disabled={structureId !== 2 && (counterpartStructureQuery.loading || !counterpartPocketQuery.data)} onClick={() => setActiveStructureId(2)}>METTL7B</button>
              <button type="button" className={activeStructureId === 3 ? 'active' : ''} disabled={structureId !== 3 && (counterpartStructureQuery.loading || !counterpartPocketQuery.data)} onClick={() => setActiveStructureId(3)}>METTL7A</button>
              <small>Exact camera · shared probe/settings</small>
            </div>
            <div className="molstar-button-group" aria-label="Scene preset">
              {(['mechanistic', 'clean', 'residues'] as const).map((scene) => (
                <button key={scene} type="button" className={mechanisticScene === scene ? 'active' : ''} onClick={() => {
                  setMechanisticScene(scene)
                  setShowResidueMarkers(scene === 'residues')
                  setShowChannel(scene !== 'clean')
                }}>{scene === 'mechanistic' ? 'Mechanistic pocket' : scene === 'clean' ? 'Clean structural' : 'Residue architecture'}</button>
              ))}
            </div>
            <label className="molstar-marker-toggle"><input type="checkbox" checked={showChannel} onChange={(event) => setShowChannel(event.target.checked)} /> Candidate channels</label>
            <label className="molstar-marker-toggle"><input type="checkbox" checked={showFpocketVolume} onChange={(event) => setShowFpocketVolume(event.target.checked)} /> fpocket volume</label>
            <label className="molstar-marker-toggle"><input type="checkbox" checked={showEntranceCandidates} onChange={(event) => setShowEntranceCandidates(event.target.checked)} /> Entrances</label>
            <label className="molstar-marker-toggle"><input type="checkbox" checked={showReactionPath} onChange={(event) => setShowReactionPath(event.target.checked)} /> Reaction-center path</label>
            <label className="molstar-marker-toggle"><input type="checkbox" checked={showBottlenecks} onChange={(event) => setShowBottlenecks(event.target.checked)} /> Bottlenecks</label>
            <label className="molstar-marker-toggle"><input type="checkbox" checked={showApproachVector} onChange={(event) => setShowApproachVector(event.target.checked)} /> Approach vector</label>
            <label className="molstar-marker-toggle" title="Requires a validated Phase I substrate-sulfur attack vector"><input type="checkbox" checked={showNacCorridor} disabled onChange={(event) => setShowNacCorridor(event.target.checked)} /> NAC corridor unavailable</label>
            <label className="molstar-marker-toggle"><input type="checkbox" checked={showPathDetails} onChange={(event) => setShowPathDetails(event.target.checked)} /> Path details</label>
            <label className="molstar-marker-toggle"><input type="checkbox" checked={showProvenance} onChange={(event) => setShowProvenance(event.target.checked)} /> Provenance</label>
            <label className="mechanistic-probe-control">
              <span>Geometric probe {probeRadius.toFixed(1)} Å</span>
              <input type="range" min="0.5" max="2.5" step="0.1" value={probeRadius} onChange={(event) => setProbeRadius(Number(event.target.value))} />
            </label>
          </div>
        )}
        <div className="molstar-representation" aria-label="Molecular representation">
          <div className="molstar-mode-switch" aria-label="Viewer mode">
            <button
              type="button"
              className={viewMode === 'protein' ? 'active' : ''}
              aria-pressed={viewMode === 'protein'}
              onClick={() => setViewMode('protein')}
            >
              Protein + pocket
            </button>
            <button
              type="button"
              className={viewMode === 'pocket' ? 'active pocket-active' : ''}
              disabled={!activePocket}
              aria-pressed={viewMode === 'pocket'}
              onClick={() => setViewMode('pocket')}
            >
              Pocket only
            </button>
          </div>
          <div className="molstar-mode-controls">
            {viewMode === 'protein' ? <>
              <div className="molstar-button-group" aria-label="Protein style">
                {(Object.keys(PRESETS) as Representation[]).map((option) => (
                  <button key={option} type="button" className={representation === option ? 'active' : ''} aria-pressed={representation === option} onClick={() => void changeRepresentation(option)}>
                    {option[0].toUpperCase() + option.slice(1)}
                  </button>
                ))}
              </div>
              <button type="button" className={showPocket && activePocket ? 'active pocket-active' : ''} disabled={!activePocket} aria-pressed={showPocket && Boolean(activePocket)} onClick={() => void togglePocket()}>
                {showPocket && activePocket ? 'Hide pocket' : 'Show pocket'}
              </button>
              <button type="button" disabled={!activePocket} onClick={focusPocket}>Focus pocket</button>
            </> : <>
              <label className="molstar-sphere-scale">
                <span>Blanket opacity</span>
                <input type="range" min="0.2" max="0.95" step="0.1" value={surfaceOpacity} onChange={(event) => setSurfaceOpacity(Number(event.target.value))} />
              </label>
              <label className="molstar-marker-toggle">
                <input type="checkbox" checked={showResidueMarkers} onChange={(event) => setShowResidueMarkers(event.target.checked)} />
                Mark lining residues
              </label>
              <label className="molstar-marker-toggle sam-toggle">
                <input type="checkbox" checked={showSam} disabled={samAtomPoints.length === 0} onChange={(event) => setShowSam(event.target.checked)} />
                Show validated SAM
              </label>
            </>}
          </div>
          <small className="molstar-pocket-label">
            {activePocket
              ? `${activePocket.source} pocket ${activePocket.pocketNumber} · ${activePocket.residues.length} residues`
              : 'Select a pocket on the page first'}
          </small>
        </div>
        <div className="molstar-stage">
          <div
            ref={hostRef}
            className={`molstar-host${viewMode === 'pocket' ? ' hidden' : ''}`}
          />
          {viewMode === 'pocket' ? (
            geometryQuery.loading && !geometryQuery.data ? (
              <p className="molstar-status">Loading pocket geometry…</p>
            ) : geometryQuery.error || !geometryQuery.data ? (
              <p className="molstar-status error">
                Pocket geometry is unavailable for this pocket.
              </p>
            ) : (
              <>
              {variant === 'mechanistic' ? (
                comparisonQuery.data && pocketB && pocketA
                  ? <SynchronizedPocketGrid
                      comparison={comparisonQuery.data}
                      pocketB={pocketB}
                      pocketA={pocketA}
                      probeRadius={probeRadius}
                      selectedEntrance={selectedEntrance}
                      opacity={surfaceOpacity}
                      showFpocketVolume={showFpocketVolume}
                      showEntrances={showEntranceCandidates}
                      showReactionPath={showReactionPath}
                      showBottlenecks={showBottlenecks}
                      showApproachVector={showApproachVector}
                      showNacCorridor={showNacCorridor}
                      showPathDetails={showPathDetails}
                      showSam={showSam}
                      showResidues={showResidueMarkers}
                      camera={sharedCamera}
                      onCameraChange={setSharedCamera}
                      samFiles={comparisonSam}
                    />
                  : <div className="synchronized-pocket-grid loading-grid">
                      <div className="synchronized-pocket-half"><p>Loading METTL7B geometry…</p></div>
                      <div className="synchronized-pocket-half"><p>Loading METTL7A geometry…</p></div>
                    </div>
              ) : <PointCloudViewer
                queryPoints={
                  geometryQuery.data.alphaSpheres.length > 0
                    ? []
                    : geometryQuery.data.points
                }
                originalCandidatePoints={showSam ? samAtomPoints : []}
                alignedCandidatePoints={showReactionPath ? bottleneckGeometry : []}
                showQuery
                showOriginalCandidate={showSam}
                showAlignedCandidate={showReactionPath && bottleneckGeometry.length > 0}
                alignedPointSize={4}
                pointSize={12}
                opacity={surfaceOpacity}
                showCentroids={false}
                resetKey={viewerCameraResetKey(variant, activePocket?.id ?? 0)}
                querySpheres={geometryQuery.data.alphaSpheres}
                surfaceTriangles={pocketSurface}
                originalCandidateBonds={showSam ? samBondPoints : []}
                matchedQueryResiduePoints={showResidueMarkers ? residueMarkerPoints : []}
                matchedCandidateResiduePoints={mouth ? [mouth.center] : []}
                showMatchedResidues={showResidueMarkers || (showChannel && openings.length > 0)}
                labels={[
                  ...(showResidueMarkers ? visibleResidueLabels : []),
                  ...(mouth ? [{
                    point: mouth.center,
                    label: `MOUTH · ${mouth.clearance.toFixed(1)} Å clearance`,
                  }] : []),
                  ...(showSam && samCenter ? [{
                    point: samCenter,
                    label: 'SAM · validated canonical pose',
                  }] : []),
                ]}
                showLabels
              />}
              {variant === 'mechanistic' && showPathDetails && (
                <aside className="channel-analysis-panel" aria-label="Static channel analysis">
                  <div className="channel-candidate-tabs">
                    {staticChannels.map((path, index) => (
                      <button key={index} type="button" className={selectedEntrance === index ? 'active' : ''} onClick={() => setSelectedEntrance(index)}>
                        {index === 0 ? 'Primary' : index === 1 ? 'Alternative' : 'Local'}
                        <small>{path.continuity.replaceAll('_', ' ')}</small>
                      </button>
                    ))}
                  </div>
                  {selectedChannel && <ChannelProfile path={selectedChannel} />}
                  <ParalogPathSummary
                    selectedEntrance={selectedEntrance}
                    snapshots={paralogSnapshots}
                  />
                  <button type="button" className="channel-export" onClick={() => downloadStaticChannelArtifact({
                    structureId: activeStructureId,
                    pocketId: activePocket?.id ?? null,
                    pocketSource: activePocket?.source ?? null,
                    pocketNumber: activePocket?.pocketNumber ?? null,
                    samPlacement: 'canonical structural placement',
                    probeRadius,
                    algorithm: 'ALPHA_SPHERE_GRAPH_V1',
                    staticSnapshot: true,
                    paths: staticChannels,
                  })}>Export analysis JSON</button>
                </aside>
              )}
              </>
            )
          ) : (
            <>
              {loading && <p className="molstar-status">Loading 3D view…</p>}
              {error && <p className="molstar-status error">{error}</p>}
            </>
          )}
        </div>
        {variant === 'mechanistic' && showProvenance && (
          <footer className="mechanistic-provenance">
            <strong>Static snapshot provenance</strong>
            <span>Structure {activeStructureId} · {activePocket?.source ?? 'no pocket'} {activePocket?.pocketNumber ?? '—'}</span>
            <span>SAM: canonical structural placement · static transformed model</span>
            <span>Entrances: geometric candidates; clearance values are local measurements, not biological-pathway conclusions.</span>
          </footer>
        )}
      </section>
    </div>
  )
}

interface SynchronizedPocketGridProps {
  comparison: PocketComparisonDetails
  pocketB: PocketDetails
  pocketA: PocketDetails
  probeRadius: number
  selectedEntrance: number
  opacity: number
  showFpocketVolume: boolean
  showEntrances: boolean
  showReactionPath: boolean
  showBottlenecks: boolean
  showApproachVector: boolean
  showNacCorridor: boolean
  showPathDetails: boolean
  showSam: boolean
  showResidues: boolean
  camera: ViewerCamera
  onCameraChange: (camera: ViewerCamera) => void
  samFiles: Record<number, string>
}

function SynchronizedPocketGrid(props: SynchronizedPocketGridProps) {
  const [pdbFiles, setPdbFiles] = useState<Record<number, string>>({})
  const [phaseDiagrams, setPhaseDiagrams] = useState<{ b: ChannelPhaseDiagram | null; a: ChannelPhaseDiagram | null }>({ b: null, a: null })
  useEffect(() => {
    const controller = new AbortController()
    for (const id of [2, 3]) {
      void getText(`/api/structures/${id}/file`, controller.signal).then((pdb) => {
        if (!controller.signal.aborted) {
          setPdbFiles((current) => ({ ...current, [id]: pdb }))
        }
      }).catch(() => undefined)
    }
    return () => controller.abort()
  }, [])

  const geometryB = props.comparison.query
  const geometryA = useMemo(() => alignedGeometry(
    props.comparison.candidate,
    props.comparison.transform,
  ), [props.comparison.candidate, props.comparison.transform])
  const surfaceB = useMemo(
    () => buildPocketSurface(geometryB.alphaSpheres),
    [geometryB.alphaSpheres],
  )
  const surfaceA = useMemo(
    () => buildPocketSurface(geometryA.alphaSpheres),
    [geometryA.alphaSpheres],
  )
  const sceneB = useMemo(() => ({
    ...buildParalogScene(
      geometryB, props.pocketB, { pdb: pdbFiles[2], sam: props.samFiles[2] },
      props.probeRadius, undefined, surfaceB,
    ),
    residueLabels: comparisonResidueLabels(props.comparison, 'query'),
  }), [geometryB, pdbFiles, props.comparison, props.pocketB, props.probeRadius, props.samFiles, surfaceB])
  const sceneA = useMemo(() => ({
    ...buildParalogScene(
      geometryA, props.pocketA, { pdb: pdbFiles[3], sam: props.samFiles[3] },
      props.probeRadius, props.comparison.transform, surfaceA,
    ),
    residueLabels: comparisonResidueLabels(props.comparison, 'candidate'),
  }), [geometryA, pdbFiles, props.comparison, props.pocketA, props.probeRadius, props.samFiles, surfaceA])
  const sharedRadius = Math.max(
    sceneRadius(geometryB), sceneRadius(geometryA),
  )
  const selectedB = sceneB.routeFingerprints.find((route) => route.localLabel === localRouteLabel(props.selectedEntrance)) ?? null
  const selectedA = sceneA.routeFingerprints.find((route) => route.localLabel === localRouteLabel(props.selectedEntrance)) ?? null
  const correspondenceFromB = selectedB ? bestRouteCorrespondence(selectedB, sceneA.routeFingerprints) : null
  const correspondenceFromA = selectedA ? bestRouteCorrespondence(selectedA, sceneB.routeFingerprints) : null
  useEffect(() => {
    setPhaseDiagrams({ b: null, a: null })
    const timer = window.setTimeout(() => {
      const build = (scene: ParalogScene, geometry: PocketGeometryView) =>
        scene.openings.length > 0 && scene.methyl
          ? buildChannelPhaseDiagram(
              geometry.alphaSpheres, scene.openings, scene.methyl,
              geometry.centroid, scene.structureAtoms,
            )
          : null
      setPhaseDiagrams({ b: build(sceneB, geometryB), a: build(sceneA, geometryA) })
    }, 0)
    return () => window.clearTimeout(timer)
  }, [geometryA, geometryB, sceneA, sceneB])

  return <div className="synchronized-pocket-grid">
    <button className="route-artifact-export" type="button" onClick={() => void exportRouteArtifacts(sceneA, sceneB, phaseDiagrams)}>
      Export route CSVs
    </button>
    <SynchronizedPocketHalf
      title="METTL7B"
      scene={{ ...sceneB, phaseDiagram: phaseDiagrams.b }}
      geometry={geometryB}
      sharedRadius={sharedRadius}
      sharedCenter={geometryB.centroid}
      selectedFingerprint={selectedB}
      correspondence={correspondenceFromB}
      {...props}
    />
    <SynchronizedPocketHalf
      title="METTL7A"
      scene={{ ...sceneA, phaseDiagram: phaseDiagrams.a }}
      geometry={geometryA}
      sharedRadius={sharedRadius}
      sharedCenter={geometryB.centroid}
      selectedFingerprint={selectedA}
      correspondence={correspondenceFromA}
      {...props}
    />
  </div>
}

interface ParalogScene {
  surface: Point3D[]
  samPoints: Point3D[]
  samBonds: Point3D[]
  openings: ReturnType<typeof detectPocketOpenings>
  channels: ReturnType<typeof reconstructStaticChannels>
  sulfur: Point3D | null
  methyl: Point3D | null
  residueLabels: { point: Point3D; label: string }[]
  phaseDiagram: ChannelPhaseDiagram | null
  routeFingerprints: RouteFingerprint[]
  structureAtoms: StaticStructureAtom[]
}

function buildParalogScene(
  geometry: PocketGeometryView,
  pocket: PocketDetails,
  files: { pdb?: string; sam?: string } | undefined,
  probeRadius: number,
  transform?: RigidTransformView,
  surface: Point3D[] = [],
): ParalogScene {
  const samAtoms = pdbAtoms(files?.sam ?? '').map((atom) => ({
    ...atom,
    point: transform ? applyRigidTransform(atom.point, transform) : atom.point,
  }))
  const sulfur = samAtoms.find((atom) => atom.name === 'SD')?.point ?? null
  const methyl = samAtoms.find((atom) => atom.name === 'CE')?.point ?? null
  const heavyAtoms = pdbHeavyAtoms(files?.pdb ?? '').map((point) =>
    transform ? applyRigidTransform(point, transform) : point)
  const structureAtoms = pdbStructureAtoms(files?.pdb ?? '').map((atom) => ({
    ...atom,
    point: transform ? applyRigidTransform(atom.point, transform) : atom.point,
  }))
  const openings = heavyAtoms.length > 0 ? detectPocketOpenings(
    geometry.alphaSpheres, geometry.centroid, heavyAtoms,
  ) : []
  const residueLabels = pocketResidueMarkers(files?.pdb ?? '', pocket)
    .map((marker) => ({
      ...marker,
      point: transform
        ? applyRigidTransform(marker.point, transform)
        : marker.point,
    }))
  const channels = reconstructStaticChannels(
    geometry.alphaSpheres, openings, methyl, geometry.centroid,
    probeRadius, structureAtoms,
  )
  const routeFingerprints = methyl ? channels.flatMap((channel, index) => {
    const fingerprint = buildRouteFingerprint(
      `pocket-${pocket.id}-${localRouteLabel(index)}`,
      localRouteLabel(index), channel, structureAtoms, methyl, sulfur,
    )
    return fingerprint ? [fingerprint] : []
  }) : []
  return {
    surface,
    samPoints: samAtoms.map((atom) => atom.point),
    samBonds: inferBondPoints(samAtoms),
    openings,
    channels,
    sulfur,
    methyl,
    residueLabels,
    phaseDiagram: null,
    routeFingerprints,
    structureAtoms,
  }
}

function SynchronizedPocketHalf({
  title,
  scene,
  geometry,
  sharedRadius,
  sharedCenter,
  selectedEntrance,
  opacity,
  showFpocketVolume,
  showEntrances,
  showReactionPath,
  showBottlenecks,
  showApproachVector,
  showSam,
  showResidues,
  showPathDetails,
  camera,
  onCameraChange,
  selectedFingerprint,
  correspondence,
}: SynchronizedPocketGridProps & {
  title: string
  scene: ParalogScene
  geometry: PocketGeometryView
  sharedRadius: number
  sharedCenter: Point3D
  selectedFingerprint: RouteFingerprint | null
  correspondence: RoutePairEvidence | null
}) {
  const channel = scene.channels[selectedEntrance] ?? null
  const pathGeometry = channel ? [
    ...(showReactionPath ? samplePolyline(channel.centerline) : []),
    ...(showBottlenecks && channel.bottleneck && channel.centerline.length > 1
      ? openingRing(
          channel.bottleneck,
          localDirection(channel.centerline, channel.bottleneck),
          channel.minimumClearance ?? 1,
        ) : []),
    ...(showApproachVector && selectedFingerprint?.approachVector && scene.methyl
      ? vectorLine(scene.methyl, selectedFingerprint.approachVector, selectedFingerprint.approachTerminalLength)
      : []),
  ] : []
  return <section className="synchronized-pocket-half" aria-label={`${title} synchronized pocket`}>
    <header>
      <strong>{title}</strong>
      <small>{channel?.continuity.replaceAll('_', ' ') ?? 'LOADING'}</small>
      <span>r*ANY {formatCriticalRadius(scene.phaseDiagram, scene.phaseDiagram?.criticalAnyRadius ?? null)}</span>
    </header>
    <PointCloudViewer
      queryPoints={[]}
      originalCandidatePoints={showSam ? scene.samPoints : []}
      alignedCandidatePoints={pathGeometry}
      showQuery={showFpocketVolume}
      showOriginalCandidate={showSam}
      showAlignedCandidate={pathGeometry.length > 0}
      alignedPointSize={4}
      pointSize={10}
      opacity={opacity}
      showCentroids={false}
      resetKey={0}
      querySpheres={geometry.alphaSpheres}
      surfaceTriangles={scene.surface}
      originalCandidateBonds={showSam ? scene.samBonds : []}
      matchedQueryResiduePoints={scene.methyl && showEntrances
        ? scene.openings.map(() => scene.methyl!) : []}
      matchedCandidateResiduePoints={showEntrances
        ? scene.openings.map((opening) => opening.center) : []}
      showMatchedResidues={showEntrances}
      labels={[
        ...(showResidues ? scene.residueLabels.filter(({ label }) => {
          const residueNumber = Number(label.match(/\d+/)?.[0])
          return [98, 144, 146, 149, 150, 151, 198, 199, 200, 202, 203, 232]
            .includes(residueNumber)
        }) : []),
        ...(showEntrances ? scene.openings.map((opening, index) => ({
          point: opening.center,
          label: `${index === 0 ? 'PRIMARY' : index === 1 ? 'ALT' : 'LOCAL'} · ${shortContinuity(scene.channels[index]?.continuity)}`,
        })) : []),
        ...(scene.methyl ? [{ point: scene.methyl, label: 'SAM methyl carbon · reaction center' }] : []),
      ]}
      showLabels
      camera={camera}
      onCameraChange={onCameraChange}
      sceneCenter={sharedCenter}
      sceneRadius={sharedRadius}
    />
    {showPathDetails && scene.phaseDiagram && (
      <PhaseDiagramStrip
        diagram={scene.phaseDiagram}
        entranceIndex={selectedEntrance}
      />
    )}
    {showPathDetails && <RouteDetailPanel fingerprint={selectedFingerprint} correspondence={correspondence} />}
  </section>
}

function RouteDetailPanel({
  fingerprint,
  correspondence,
}: {
  fingerprint: RouteFingerprint | null
  correspondence: RoutePairEvidence | null
}) {
  if (!fingerprint) return <div className="route-detail-panel"><strong>Selected route</strong><span>Not connected to reaction center at this probe radius.</span></div>
  return <div className="route-detail-panel">
    <div><strong>{fingerprint.localLabel} route</strong><span>{fingerprint.length.toFixed(1)} Å · bottleneck {fingerprint.minimumBottleneck.toFixed(1)} Å</span></div>
    <span>Delivery residues: {fingerprint.finalDeliveryResidues.join(', ') || 'none within 4 Å'}</span>
    <span>SAM-axis angle: {fingerprint.angleToSamAxisDegrees?.toFixed(1) ?? '—'}° · NAC delivery: {fingerprint.nacDelivery.replaceAll('_', ' ')}</span>
    <span>{correspondence
      ? `${correspondence.status.replaceAll('_', ' ')} → ${correspondence.labelB} · RMS ${correspondence.centerlineRmsDistance.toFixed(1)} Å · approach Δ ${correspondence.approachVectorAngleDegrees?.toFixed(1) ?? '—'}°`
      : 'No confident A/B route correspondence; no match was forced.'}</span>
  </div>
}

function localRouteLabel(index: number): 'PRIMARY' | 'ALTERNATIVE' | 'LOCAL' {
  return index === 0 ? 'PRIMARY' : index === 1 ? 'ALTERNATIVE' : 'LOCAL'
}

function vectorLine(target: Point3D, direction: Point3D, length: number): Point3D[] {
  return Array.from({ length: 25 }, (_, index) => {
    const fraction = index / 24
    return {
      x: target.x - direction.x * length * (1 - fraction),
      y: target.y - direction.y * length * (1 - fraction),
      z: target.z - direction.z * length * (1 - fraction),
    }
  })
}

async function exportRouteArtifacts(
  sceneA: ParalogScene,
  sceneB: ParalogScene,
  phase: { a: ChannelPhaseDiagram | null; b: ChannelPhaseDiagram | null },
) {
  const routes = [...sceneA.routeFingerprints, ...sceneB.routeFingerprints]
  const correspondences = sceneA.routeFingerprints.flatMap((routeA) =>
    sceneB.routeFingerprints.map((routeB) => compareRouteFingerprints(routeA, routeB)))
  const files: Record<string, string> = {
    'route_fingerprints.csv': csv(
      ['route_id','local_label','length_A','tortuosity','minimum_bottleneck_A','bottleneck_path_distance_A','branch_topology'],
      routes.map((route) => [route.routeId, route.localLabel, route.length, route.tortuosity, route.minimumBottleneck, route.bottleneckPathDistance, route.branchTopology]),
    ),
    'ab_route_correspondence.csv': csv(
      ['route_a','route_b','label_a','label_b','status','centerline_overlap','centerline_rms_A','entrance_distance_A','bottleneck_distance_A','lining_overlap','topology_matches','approach_angle_deg','lateral_displacement_A','reason'],
      correspondences.map((pair) => [pair.routeA,pair.routeB,pair.labelA,pair.labelB,pair.status,pair.alignedCenterlineOverlap,pair.centerlineRmsDistance,pair.entranceDistance,pair.bottleneckDistance,pair.homologousLiningOverlap,pair.branchTopologyMatches,pair.approachVectorAngleDegrees,pair.lateralDisplacement,pair.reason]),
    ),
    'reaction_center_approach_vectors.csv': csv(
      ['route_id','terminal_length_A','vector_x','vector_y','vector_z','angle_to_sam_axis_deg','closest_approach_to_methyl_A','terminal_curvature_deg','robustness_angles_deg'],
      routes.map((route) => [route.routeId,route.approachTerminalLength,route.approachVector?.x,route.approachVector?.y,route.approachVector?.z,route.angleToSamAxisDegrees,route.closestApproachToMethyl,route.terminalCurvatureDegrees,route.approachRobustnessAngles.join(';')]),
    ),
    'nac_delivery_compatibility.csv': csv(
      ['route_id','classification','provenance'], routes.map((route) => [route.routeId,route.nacDelivery,route.nacProvenance]),
    ),
    'route_lining_residues.csv': csv(
      ['route_id','residue','segment','minimum_distance_A'], routes.flatMap((route) => route.liningResidues.map((residue) => [route.routeId,residue.identity,residue.segment,residue.minimumDistance])),
    ),
    'historical_residue_route_roles.csv': historicalResidueCsv(routes),
    'admission_vs_delivery_reconciliation.csv': admissionDeliveryCsv(sceneA, sceneB, phase),
  }
  const checksums: string[] = []
  for (const [name, contents] of Object.entries(files)) {
    checksums.push(`${await sha256(contents)}  ${name}`)
    downloadText(name, contents)
  }
  downloadText('checksums.sha256', `${checksums.join('\n')}\n`)
}

const HISTORICAL_RESIDUES = [43, 98, 144, 146, 149, 150, 151, 195, 198, 199, 200, 202, 203, 232]

function historicalResidueCsv(routes: RouteFingerprint[]) {
  return csv(['route_id','residue_number','observed_identity','route_role'], routes.flatMap((route) =>
    HISTORICAL_RESIDUES.map((number) => {
      const residue = route.liningResidues.find((candidate) => Number(candidate.identity.match(/\d+/)?.[0]) === number)
      return [route.routeId, number, residue?.identity ?? '', residue?.segment ?? 'OUTSIDE_CONNECTED_ROUTE']
    })))
}

function admissionDeliveryCsv(
  sceneA: ParalogScene,
  sceneB: ParalogScene,
  phase: { a: ChannelPhaseDiagram | null; b: ChannelPhaseDiagram | null },
) {
  return csv(['paralog','hypothesis','evidence','status','boundary'], [
    ['METTL7A','H1_ADMISSION',phase.a?.criticalAnyRadius,'STATIC_R_STAR_ANY','Static probe accessibility; not permeability'],
    ['METTL7B','H1_ADMISSION',phase.b?.criticalAnyRadius,'STATIC_R_STAR_ANY','Static probe accessibility; not permeability'],
    ['METTL7A','H2_CONNECTIVITY',sceneA.routeFingerprints.length,'REACTION_CENTER_CONNECTED_ROUTE_COUNT','Static route; not route usage'],
    ['METTL7B','H2_CONNECTIVITY',sceneB.routeFingerprints.length,'REACTION_CENTER_CONNECTED_ROUTE_COUNT','Static route; not route usage'],
    ['METTL7A','H3_DELIVERY','',sceneA.routeFingerprints.length ? 'NAC_UNRESOLVED' : 'NO_CONNECTED_ROUTE','Requires validated substrate sulfur attack vector'],
    ['METTL7B','H3_DELIVERY','',sceneB.routeFingerprints.length ? 'NAC_UNRESOLVED' : 'NO_CONNECTED_ROUTE','Requires validated substrate sulfur attack vector'],
  ])
}

function csv(headers: string[], rows: unknown[][]) {
  const field = (value: unknown) => `"${String(value ?? '').replaceAll('"', '""')}"`
  return `${[headers, ...rows].map((row) => row.map(field).join(',')).join('\n')}\n`
}

async function sha256(contents: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(contents))
  return [...new Uint8Array(digest)].map((value) => value.toString(16).padStart(2, '0')).join('')
}

function downloadText(name: string, contents: string) {
  const link = document.createElement('a')
  link.href = URL.createObjectURL(new Blob([contents], { type: 'text/csv;charset=utf-8' }))
  link.download = name
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(link.href), 1000)
}

function PhaseDiagramStrip({
  diagram,
  entranceIndex,
}: {
  diagram: ChannelPhaseDiagram
  entranceIndex: number
}) {
  const entrance = diagram.entrances[entranceIndex]
  if (!entrance) return null
  return <div className="phase-diagram-strip">
    <div><strong>Static probe sweep</strong><span>r* {formatCriticalRadius(diagram, entrance.criticalPassableRadius)}</span></div>
    <div className="phase-samples" aria-label="Probe radius connectivity phase diagram">
      {entrance.samples.map((sample) => (
        <i
          key={sample.probeRadius}
          className={sample.connectedToReactionCenter
            ? 'connected'
            : sample.continuity === 'CONNECTED_TO_CHAMBER_ONLY'
              ? 'chamber' : 'disconnected'}
          title={`${sample.probeRadius.toFixed(2)} Å · ${sample.continuity}`}
        />
      ))}
    </div>
    <small>0.50 Å → 3.00 Å · static geometric accessibility ≠ substrate permeability</small>
  </div>
}

function formatCriticalRadius(diagram: ChannelPhaseDiagram | null, radius: number | null) {
  if (!diagram || radius == null) return '—'
  const ceiling = diagram.radii.at(-1)
  return ceiling != null && Math.abs(radius - ceiling) < 1e-6
    ? `≥${radius.toFixed(2)} Å`
    : `${radius.toFixed(2)} ± ${diagram.radiusResolution.toFixed(2)} Å`
}

function shortContinuity(continuity: ReturnType<typeof reconstructStaticChannels>[number]['continuity'] | undefined) {
  return continuity === 'CONNECTED_TO_REACTION_CENTER' ? 'CONNECTED'
    : continuity === 'CONNECTED_TO_CHAMBER_ONLY' ? 'CHAMBER'
      : continuity === 'DEAD_END' ? 'DEAD END' : 'UNRESOLVED'
}

function sceneRadius(geometry: PocketGeometryView) {
  return Math.max(1, ...geometry.alphaSpheres.map((sphere) => Math.hypot(
    sphere.center.x - geometry.centroid.x,
    sphere.center.y - geometry.centroid.y,
    sphere.center.z - geometry.centroid.z,
  ) + sphere.radius))
}

function alignedGeometry(
  geometry: PocketGeometryView,
  transform: RigidTransformView,
): PocketGeometryView {
  return {
    ...geometry,
    centroid: applyRigidTransform(geometry.centroid, transform),
    alphaSpheres: geometry.alphaSpheres.map((sphere) => ({
      ...sphere,
      center: applyRigidTransform(sphere.center, transform),
    })),
    points: geometry.points.map((point) => applyRigidTransform(point, transform)),
  }
}

function applyRigidTransform(point: Point3D, transform: RigidTransformView): Point3D {
  const [first, second, third] = transform.rotation
  return {
    x: first[0]*point.x + first[1]*point.y + first[2]*point.z + transform.translation.x,
    y: second[0]*point.x + second[1]*point.y + second[2]*point.z + transform.translation.y,
    z: third[0]*point.x + third[1]*point.y + third[2]*point.z + transform.translation.z,
  }
}

function comparisonResidueLabels(
  comparison: PocketComparisonDetails,
  side: 'query' | 'candidate',
) {
  const correspondence = comparison.residueCorrespondence
  if (!correspondence) return []
  const matched = correspondence.matches.map((match) => match[side])
  const unmatched = side === 'query'
    ? correspondence.unmatchedQuery
    : correspondence.unmatchedCandidate
  return [...matched, ...unmatched].map((residue) => ({
    point: residue.position,
    label: `${residue.residueName}${residue.residueNumber} · ${residue.chainId}`,
  }))
}

async function applyRepresentation(
  viewer: Viewer,
  representation: Representation,
  pocket: PocketDetails | null,
  showPocket: boolean,
) {
  const structures = viewer.plugin.managers.structure.hierarchy.current.structures
  if (!structures[0]) return
  await viewer.plugin.managers.structure.component.clear(structures)
  await viewer.plugin.builders.structure.representation.applyPreset(
    structures[0].cell,
    PRESETS[representation],
  )
  if (pocket && showPocket) {
    const component = await viewer.plugin.builders.structure
      .tryCreateComponentFromExpression(
        structures[0].cell,
        StructureElement.Schema.toExpression(pocketElements(pocket)),
        'selected-pocket',
        { label: `${pocket.source} pocket ${pocket.pocketNumber}` },
      )
    if (component) {
      await viewer.plugin.builders.structure.representation.addRepresentation(
        component,
        { type: 'ball-and-stick', color: 'residue-name' },
      )
    }
  }
}

function pocketElements(pocket: PocketDetails): StructureElement.Schema {
  return {
    items: pocket.residues.map((residue) => ({
      auth_asym_id: residue.chain,
      auth_seq_id: residue.residueNumber,
      pdbx_PDB_ins_code: residue.insertionCode.trim() || undefined,
    })),
  }
}

export function pocketResidueAlphaCarbons(
  pdb: string,
  pocket: PocketDetails,
): Point3D[] {
  return pocketResidueMarkers(pdb, pocket).map((marker) => marker.point)
}

function pocketResidueMarkers(pdb: string, pocket: PocketDetails) {
  const residues = new Map(pocket.residues.map((residue) => [
    `${residue.chain}:${residue.residueNumber}:${residue.insertionCode.trim()}`,
    residue,
  ]))
  const markers: { point: Point3D; label: string }[] = []
  for (const line of pdb.split(/\r?\n/)) {
    if (!line.startsWith('ATOM') || line.slice(12, 16).trim() !== 'CA') continue
    const key = `${line.slice(21, 22).trim()}:${Number(line.slice(22, 26))}:${line.slice(26, 27).trim()}`
    const residue = residues.get(key)
    if (!residue) continue
    const x = Number(line.slice(30, 38))
    const y = Number(line.slice(38, 46))
    const z = Number(line.slice(46, 54))
    if ([x, y, z].every(Number.isFinite)) markers.push({
      point: { x, y, z },
      label: `${residue.residueName}${residue.residueNumber} · ${residue.chain}`,
    })
  }
  return markers
}

interface PdbAtomPoint {
  point: Point3D
  element: string
  name: string
}

function pdbAtoms(pdb: string): PdbAtomPoint[] {
  return pdb.split(/\r?\n/)
    .filter((line) => line.startsWith('ATOM') || line.startsWith('HETATM'))
    .map((line) => ({
      point: {
        x: Number(line.slice(30, 38)),
        y: Number(line.slice(38, 46)),
        z: Number(line.slice(46, 54)),
      },
      element: line.slice(76, 78).trim().toUpperCase()
        || line.slice(12, 16).trim()[0]?.toUpperCase()
        || 'C',
      name: line.slice(12, 16).trim().toUpperCase(),
    }))
    .filter((atom) => [atom.point.x, atom.point.y, atom.point.z]
      .every(Number.isFinite))
}

function pdbStructureAtoms(pdb: string): StaticStructureAtom[] {
  return pdb.split(/\r?\n/)
    .filter((line) => line.startsWith('ATOM') || line.startsWith('HETATM'))
    .map((line) => ({
      point: {
        x: Number(line.slice(30, 38)),
        y: Number(line.slice(38, 46)),
        z: Number(line.slice(46, 54)),
      },
      chain: line.slice(21, 22).trim(),
      residueNumber: Number(line.slice(22, 26)),
      residueName: line.slice(17, 20).trim(),
    }))
    .filter((atom) => [atom.point.x, atom.point.y, atom.point.z]
      .every(Number.isFinite))
}

const COVALENT_RADII: Record<string, number> = {
  C: 0.76, N: 0.71, O: 0.66, P: 1.07, S: 1.05,
}

function inferBondPoints(atoms: PdbAtomPoint[]): Point3D[] {
  const bonds: Point3D[] = []
  for (let first = 0; first < atoms.length; first++) {
    for (let second = first + 1; second < atoms.length; second++) {
      const a = atoms[first]
      const b = atoms[second]
      const dx = a.point.x - b.point.x
      const dy = a.point.y - b.point.y
      const dz = a.point.z - b.point.z
      const distance = Math.sqrt(dx * dx + dy * dy + dz * dz)
      const cutoff = (COVALENT_RADII[a.element] ?? 0.77)
        + (COVALENT_RADII[b.element] ?? 0.77) + 0.45
      if (distance > 0.4 && distance <= cutoff) {
        const steps = Math.max(2, Math.ceil(distance / 0.12))
        for (let step = 0; step <= steps; step++) {
          const fraction = step / steps
          bonds.push({
            x: a.point.x + (b.point.x - a.point.x) * fraction,
            y: a.point.y + (b.point.y - a.point.y) * fraction,
            z: a.point.z + (b.point.z - a.point.z) * fraction,
          })
        }
      }
    }
  }
  return bonds
}

export function entranceCandidateLabel(index: number, clearance: number) {
  const status = index === 0
    ? 'PRIMARY_CANDIDATE'
    : index === 1 ? 'ALTERNATIVE_CANDIDATE' : 'LOCAL_OPENING'
  return `${status} · local bottleneck ${clearance.toFixed(1)} Å`
}

export function viewerCameraResetKey(
  variant: 'classic' | 'mechanistic',
  pocketId: number,
) {
  return variant === 'mechanistic' ? 0 : pocketId
}

function openingRing(
  center: Point3D,
  direction: Point3D,
  radius: number,
): Point3D[] {
  const reference = Math.abs(direction.z) < 0.9
    ? { x: 0, y: 0, z: 1 }
    : { x: 0, y: 1, z: 0 }
  const first = normalize(cross(direction, reference))
  const second = normalize(cross(direction, first))
  return Array.from({ length: 72 }, (_, index) => {
    const angle = index / 72 * Math.PI * 2
    return {
      x: center.x + radius * (first.x * Math.cos(angle) + second.x * Math.sin(angle)),
      y: center.y + radius * (first.y * Math.cos(angle) + second.y * Math.sin(angle)),
      z: center.z + radius * (first.z * Math.cos(angle) + second.z * Math.sin(angle)),
    }
  })
}

function attackAxis(sulfur: Point3D, methyl: Point3D): Point3D[] {
  const direction = normalize({
    x: methyl.x - sulfur.x,
    y: methyl.y - sulfur.y,
    z: methyl.z - sulfur.z,
  })
  return Array.from({ length: 45 }, (_, index) => {
    const distance = index / 44 * 3.5
    return {
      x: methyl.x + direction.x * distance,
      y: methyl.y + direction.y * distance,
      z: methyl.z + direction.z * distance,
    }
  })
}

const cross = (a: Point3D, b: Point3D): Point3D => ({
  x: a.y * b.z - a.z * b.y,
  y: a.z * b.x - a.x * b.z,
  z: a.x * b.y - a.y * b.x,
})

function normalize(point: Point3D): Point3D {
  const length = Math.hypot(point.x, point.y, point.z) || 1
  return { x: point.x / length, y: point.y / length, z: point.z / length }
}

function samplePolyline(points: Point3D[]): Point3D[] {
  const sampled: Point3D[] = []
  for (let index = 1; index < points.length; index++) {
    const first = points[index - 1]
    const second = points[index]
    const length = Math.hypot(second.x - first.x, second.y - first.y, second.z - first.z)
    const steps = Math.max(1, Math.ceil(length / 0.18))
    for (let step = 0; step <= steps; step++) {
      const fraction = step / steps
      sampled.push({
        x: first.x + (second.x - first.x) * fraction,
        y: first.y + (second.y - first.y) * fraction,
        z: first.z + (second.z - first.z) * fraction,
      })
    }
  }
  return sampled
}

function localDirection(points: Point3D[], target: Point3D): Point3D {
  let nearest = 0
  points.forEach((point, index) => {
    if (Math.hypot(point.x-target.x, point.y-target.y, point.z-target.z)
      < Math.hypot(points[nearest].x-target.x, points[nearest].y-target.y, points[nearest].z-target.z)) nearest = index
  })
  const first = points[Math.max(0, nearest - 1)]
  const second = points[Math.min(points.length - 1, nearest + 1)]
  return normalize({ x: second.x-first.x, y: second.y-first.y, z: second.z-first.z })
}

function ChannelProfile({ path }: { path: ReturnType<typeof reconstructStaticChannels>[number] }) {
  const width = 280
  const height = 82
  const maximumDistance = Math.max(1, path.length ?? 1)
  const maximumClearance = Math.max(1, ...path.profile.map((point) => point.clearance))
  const polyline = path.profile.map((point) =>
    `${point.distance / maximumDistance * width},${height - point.clearance / maximumClearance * (height - 8)}`,
  ).join(' ')
  return <div className="channel-profile">
    <div className="channel-metrics">
      <span><b>Length</b> {path.length?.toFixed(1) ?? '—'} Å</span>
      <span><b>Tortuosity</b> {path.tortuosity?.toFixed(2) ?? '—'}</span>
      <span><b>Minimum</b> {path.minimumClearance?.toFixed(1) ?? '—'} Å</span>
      <span><b>Bottleneck position</b> {path.bottleneckPathDistance?.toFixed(1) ?? '—'} Å</span>
      <span><b>To methyl C</b> {path.bottleneckDistanceToTarget?.toFixed(1) ?? '—'} Å</span>
      <span className={`path-survival ${path.probeSurvival}`}>{path.probeSurvival}</span>
    </div>
    <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Distance along static path versus local clearance">
      <polyline points={polyline} fill="none" stroke="currentColor" strokeWidth="2" />
    </svg>
    <small>Bottleneck walls: {path.bottleneckResidues.join(', ') || 'unresolved'}</small>
  </div>
}

function downloadStaticChannelArtifact(value: object) {
  const blob = new Blob([JSON.stringify(value, null, 2)], {
    type: 'application/json',
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'static-channel-analysis.json'
  link.click()
  URL.revokeObjectURL(url)
}

function ParalogPathSummary({
  selectedEntrance,
  snapshots,
}: {
  selectedEntrance: number
  snapshots: Record<number, ReturnType<typeof reconstructStaticChannels>>
}) {
  const pathB = snapshots[2]?.[selectedEntrance]
  const pathA = snapshots[3]?.[selectedEntrance]
  if (!pathA || !pathB) return <p className="ab-path-note">
    Visit both paralogs to populate the static A/B summary.
  </p>
  return <div className="ab-path-summary">
    <strong>A/B static profile — candidate rank {selectedEntrance + 1}</strong>
    <span>7B: {pathB.continuity.replaceAll('_', ' ')} · min {pathB.minimumClearance?.toFixed(1) ?? '—'} Å · {pathB.length?.toFixed(1) ?? '—'} Å</span>
    <span>7A: {pathA.continuity.replaceAll('_', ' ')} · min {pathA.minimumClearance?.toFixed(1) ?? '—'} Å · {pathA.length?.toFixed(1) ?? '—'} Å</span>
    <small>Entrance correspondence: UNRESOLVED; rows share candidate rank only.</small>
  </div>
}

function centroid(points: Point3D[]): Point3D | null {
  if (points.length === 0) return null
  const sum = points.reduce((current, point) => ({
    x: current.x + point.x,
    y: current.y + point.y,
    z: current.z + point.z,
  }), { x: 0, y: 0, z: 0 })
  return {
    x: sum.x / points.length,
    y: sum.y / points.length,
    z: sum.z / points.length,
  }
}
