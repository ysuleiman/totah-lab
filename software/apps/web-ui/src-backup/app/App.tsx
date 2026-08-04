import { useCallback, useEffect, useState } from 'react'
import { AppShell } from './AppShell'
import { SelectivityWorkspace } from '../features/selectivity/SelectivityWorkspace'
import { StructureWorkspace } from '../features/structure/StructureWorkspace'
import { SimilarPocketsPage } from '../features/similar/SimilarPocketsPage'
import { PocketComparisonPage } from '../features/compare/PocketComparisonPage'
import { useStructureRoute } from './useStructureRoute'

const SIMILAR_PATH = /^\/pockets\/(\d+)\/similar$/
const COMPARE_PATH = /^\/pockets\/(\d+)\/compare\/(\d+)$/

export function App() {
  const [pathname, setPathname] = useState(window.location.pathname)
  const route = useStructureRoute()
  useEffect(() => {
    const updatePath = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', updatePath)
    return () => window.removeEventListener('popstate', updatePath)
  }, [])

  const navigateTo = useCallback((path: string) => {
    window.history.pushState(null, '', path)
    setPathname(path)
  }, [])

  const compareMatch = COMPARE_PATH.exec(pathname)
  const similarMatch = SIMILAR_PATH.exec(pathname)

  return (
    <AppShell>
      {compareMatch
        ? (
          <PocketComparisonPage
            queryPocketId={Number(compareMatch[1])}
            candidatePocketId={Number(compareMatch[2])}
            onNavigate={navigateTo}
          />
        )
        : similarMatch
          ? (
            <SimilarPocketsPage
              pocketId={Number(similarMatch[1])}
              onNavigate={navigateTo}
            />
          )
          : pathname === '/selectivity'
            ? <SelectivityWorkspace />
            : (
              <StructureWorkspace
                structureId={route.structureId}
                onNavigate={route.navigate}
              />
            )}
    </AppShell>
  )
}
