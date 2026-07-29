import { type FormEvent, useState } from 'react'
import type { Structure } from '../../../api/types'

interface Props {
  structure: Structure
  onStructureSubmit: (structureId: number) => void
}

export function StructureHero({ structure, onStructureSubmit }: Props) {
  const [input, setInput] = useState(String(structure.id))
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

  return (
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
  )
}
