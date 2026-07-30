import { useCallback, useEffect, useState } from 'react'

const DEFAULT_STRUCTURE_ID = 2
const STRUCTURE_PATH = /^\/structures\/(\d+)$/

function structureIdFromPath(pathname: string): number {
  const match = STRUCTURE_PATH.exec(pathname)
  if (!match) return DEFAULT_STRUCTURE_ID
  const value = Number(match[1])
  return Number.isSafeInteger(value) && value > 0
    ? value
    : DEFAULT_STRUCTURE_ID
}

export function useStructureRoute() {
  const [structureId, setStructureId] = useState(() =>
    structureIdFromPath(window.location.pathname),
  )

  useEffect(() => {
    const canonicalPath = `/structures/${structureId}`
    if (
      window.location.pathname !== '/selectivity'
      && window.location.pathname !== canonicalPath
    ) {
      window.history.replaceState(null, '', canonicalPath)
    }
    const handlePopState = () => {
      setStructureId(structureIdFromPath(window.location.pathname))
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [structureId])

  const navigate = useCallback((nextStructureId: number) => {
    window.history.pushState(null, '', `/structures/${nextStructureId}`)
    setStructureId(nextStructureId)
  }, [])

  return { structureId, navigate }
}
