import { type FormEvent, useState } from 'react'
import type { PocketDetails, Structure } from '../../../api/types'
import { MolstarViewer } from './MolstarViewer'

interface Props {
  structure: Structure
  activePocket?: PocketDetails | null
  onStructureSubmit: (structureId: number) => void
  onCompare?: (structureId: number) => void
  onReportRequest?: () => void
}

export function StructureHero({
  structure,
  activePocket,
  onStructureSubmit,
  onCompare,
  onReportRequest,
}: Props) {
  const [input, setInput] = useState(String(structure.id))
  const [viewerOpen, setViewerOpen] = useState(false)
  const [mechanisticViewerOpen, setMechanisticViewerOpen] = useState(false)
  const receptor = structure.receptor
  const geneName = receptor.geneName ?? receptor.targetName
  const proteinName = receptor.proteinName ?? receptor.targetName

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const nextId = Number(input)
    if (Number.isSafeInteger(nextId) && nextId > 0) {
      onStructureSubmit(nextId)
    }
  }

  return (<>
    <section className="structure-hero">
      <div>
        <p className="eyebrow">
          Structure {structure.id}
          {receptor.organism ? ` · ${receptor.organism}` : ''}
        </p>
        <h1>{proteinName}</h1>
        <div className="identity-line">
          <span className="gene-symbol">{geneName}</span>
          {receptor.uniProtId && (
            <a
              href={`https://www.uniprot.org/uniprotkb/${receptor.uniProtId}/entry`}
              target="_blank"
              rel="noreferrer"
            >
              UniProt {receptor.uniProtId}
            </a>
          )}
          <span className="accession">
            {structure.sourceAccession ?? `${structure.source} model`}
          </span>
        </div>
      </div>
      <div className="structure-actions">
        <form className="structure-jump" onSubmit={handleSubmit}>
          <label htmlFor="structure-id">Structure ID</label>
          <div>
            <input
              id="structure-id"
              inputMode="numeric"
              value={input}
              onChange={(event) => setInput(event.target.value)}
            />
            <button type="submit">Open</button>
          </div>
        </form>
        <button
          className="report-button"
          type="button"
          onClick={() => setViewerOpen(true)}
        >
          3D view
          <small>Mol*</small>
        </button>
        <button
          className="report-button"
          type="button"
          disabled={!activePocket}
          title={activePocket
            ? 'Open the mechanistic pocket viewer'
            : 'Waiting for the selected pocket geometry'}
          onClick={() => setMechanisticViewerOpen(true)}
        >
          Mechanistic pocket
          <small>New panel</small>
        </button>
        {onCompare && (
          <button
            className="report-button"
            type="button"
            onClick={() => onCompare(structure.id === 2 ? 3 : 2)}
          >
            Compare
            <small>With structure {structure.id === 2 ? 3 : 2}</small>
          </button>
        )}
        <button
          className="report-button"
          type="button"
          disabled={!onReportRequest}
          title={
            onReportRequest
              ? 'Generate report for the selected pocket and docking run'
              : 'Select a pocket and docking run to generate a report'
          }
          onClick={onReportRequest}
        >
          Report
          <small>{onReportRequest ? 'Generate' : 'Select data'}</small>
        </button>
      </div>
      <dl className="metadata-strip">
        <div><dt>Source</dt><dd>{structure.source}</dd></div>
        <div><dt>Chain</dt><dd>{structure.chain ?? '—'}</dd></div>
        <div><dt>State</dt><dd>{structure.preparationState}</dd></div>
        <div><dt>Residues</dt><dd>{structure.residues?.length ?? 0}</dd></div>
        <div>
          <dt>Chosen pocket</dt>
          <dd>
            {structure.chosenPocket
              ? `${structure.chosenPocket.source} ${structure.chosenPocket.pocketNumber}`
              : 'Not selected'}
          </dd>
        </div>
      </dl>
    </section>
    {viewerOpen && (
      <MolstarViewer
        structureId={structure.id}
        structureName={proteinName}
        pocket={activePocket ?? null}
        onClose={() => setViewerOpen(false)}
      />
    )}
    {mechanisticViewerOpen && (
      <MolstarViewer
        structureId={structure.id}
        structureName={proteinName}
        pocket={activePocket ?? null}
        variant="mechanistic"
        onClose={() => setMechanisticViewerOpen(false)}
      />
    )}
  </>)
}
