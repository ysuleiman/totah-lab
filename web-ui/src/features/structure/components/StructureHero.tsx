import { type FormEvent, useState } from 'react'
import type { Structure } from '../../../api/types'

interface Props {
  structure: Structure
  onStructureSubmit: (structureId: number) => void
}

export function StructureHero({ structure, onStructureSubmit }: Props) {
  const [input, setInput] = useState(String(structure.id))

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
        <p className="eyebrow">Structure investigation</p>
        <h1>{structure.receptor.targetName}</h1>
        <p className="accession">
          {structure.sourceAccession ?? `Structure ${structure.id}`}
        </p>
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
