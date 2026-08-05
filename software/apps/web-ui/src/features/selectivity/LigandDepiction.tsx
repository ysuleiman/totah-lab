import { useEffect, useRef, useState } from 'react'
import SmilesDrawer from 'smiles-drawer'

export function LigandDepiction({
  smiles,
  label,
}: {
  smiles: string
  label: string
}) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    setFailed(false)
    const drawer = new SmilesDrawer.SvgDrawer({ width: 260, height: 220 })
    SmilesDrawer.parse(
      smiles,
      (tree) => {
        try {
          const svg = drawer.draw(tree, null, 'light')
          container.replaceChildren(svg)
        } catch {
          setFailed(true)
        }
      },
      () => setFailed(true),
    )
    return () => container.replaceChildren()
  }, [smiles])

  if (failed) {
    return <code className="ligand-depiction-fallback">{smiles}</code>
  }
  return (
    <div
      ref={containerRef}
      className="ligand-depiction"
      role="img"
      aria-label={`2D structure of ${label}`}
    />
  )
}
