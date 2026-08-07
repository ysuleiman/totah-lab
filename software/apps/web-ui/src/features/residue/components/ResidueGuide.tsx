interface Props {
  showChosenPocket: boolean
  showBiohub: boolean
  showDocking: boolean
  showConstraint: boolean
  showNeighbors: boolean
}

export function ResidueGuide({
  showChosenPocket,
  showBiohub,
  showDocking,
  showConstraint,
  showNeighbors,
}: Props) {
  return (
    <aside className="residue-guide" aria-label="Residue color guide">
      <span className="residue-guide-title">Guide</span>
      <GuideItem signal="neutral" label="Structure residue" />
      {showChosenPocket && (
        <GuideItem signal="chosen" label="Chosen fpocket" />
      )}
      {showBiohub && (
        <>
          <GuideItem
            signal="biohub-inside"
            label="BioHub contact in pocket"
          />
          <GuideItem signal="biohub-direct" label="BioHub direct contact" />
          <GuideItem
            signal="biohub-outside"
            label="BioHub contact outside fpocket"
          />
        </>
      )}
      {showDocking && (
        <GuideItem signal="docking" label="Docking contact frequency" />
      )}
      {showConstraint && (
        <GuideItem signal="constraint" label="ESMC sequence constraint" />
      )}
      {showNeighbors && (
        <GuideItem signal="neighbor" label="Spatial neighbor" />
      )}
    </aside>
  )
}

interface GuideItemProps {
  signal: string
  label: string
}

function GuideItem({ signal, label }: GuideItemProps) {
  return (
    <span className="residue-guide-item">
      <i className={`residue-guide-swatch ${signal}`} aria-hidden="true" />
      {label}
    </span>
  )
}
