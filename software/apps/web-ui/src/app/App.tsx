import type { ReactNode } from 'react'
import { AppShell } from './AppShell'
import { useAppRoute } from './useAppRoute'
import { PocketComparisonPage } from '../features/compare/PocketComparisonPage'
import { SelectivityWorkspace } from '../features/selectivity/SelectivityWorkspace'
import { SimilarPocketsPage } from '../features/similar/SimilarPocketsPage'
import { StructureWorkspace } from '../features/structure/StructureWorkspace'

const STRUCTURE_PATH = /^\/structures\/([1-9]\d*)$/
const SIMILAR_PATH = /^\/pockets\/([1-9]\d*)\/similar$/
const COMPARE_PATH = /^\/pockets\/([1-9]\d*)\/compare\/([1-9]\d*)$/

export function App() {
  const { pathname, navigate } = useAppRoute()

  const structureMatch = STRUCTURE_PATH.exec(pathname)
  const similarMatch = SIMILAR_PATH.exec(pathname)
  const compareMatch = COMPARE_PATH.exec(pathname)

  let content: ReactNode

  if (compareMatch) {
    content = (
      <PocketComparisonPage
        queryPocketId={Number(compareMatch[1])}
        candidatePocketId={Number(compareMatch[2])}
        onNavigate={navigate}
      />
    )
  } else if (similarMatch) {
    content = (
      <SimilarPocketsPage
        pocketId={Number(similarMatch[1])}
        onNavigate={navigate}
      />
    )
  } else if (pathname === '/selectivity') {
    content = <SelectivityWorkspace />
  } else {
    const structureId = structureMatch
      ? Number(structureMatch[1])
      : 2

    content = (
      <StructureWorkspace
        structureId={structureId}
        onNavigate={(nextStructureId) =>
          navigate(`/structures/${nextStructureId}`)}
      />
    )
  }

  return (
    <AppShell pathname={pathname} onNavigate={navigate}>
      {content}
    </AppShell>
  )
}
