import { useState, type ReactNode } from 'react'
import { AppShell } from './AppShell'
import { DEFAULT_POCKET_ID, useAppRoute } from './useAppRoute'
import { PocketComparisonPage } from '../features/compare/PocketComparisonPage'
import { LigandAnalysisPage } from '../features/ligands/LigandAnalysisPage'
import { SelectivityWorkspace } from '../features/selectivity/SelectivityWorkspace'
import { SimilarPocketsPage } from '../features/similar/SimilarPocketsPage'
import { StructureWorkspace } from '../features/structure/StructureWorkspace'
import { DcmbReportPage } from '../features/report/DcmbReportPage'

const STRUCTURE_PATH = /^\/structures\/([1-9]\d*)$/
const SIMILAR_PATH = /^\/pockets\/([1-9]\d*)\/similar$/
const COMPARE_PATH = /^\/pockets\/([1-9]\d*)\/compare\/([1-9]\d*)$/
const POCKET_PATH = /^\/pockets\/([1-9]\d*)\//

export function App() {
  const { pathname, navigate } = useAppRoute()

  // The pocket the user is currently working with: the URL decides on
  // pocket pages; on structure pages the selected pocket card decides.
  // The top navigation's "Similar pockets" entry targets this pocket.
  const [cardPocketId, setCardPocketId] = useState<number | null>(null)

  const pocketMatch = POCKET_PATH.exec(pathname)
  const currentPocketId = pocketMatch
    ? Number(pocketMatch[1])
    : cardPocketId ?? DEFAULT_POCKET_ID

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
  } else if (pathname === '/ligands') {
    content = <LigandAnalysisPage />
  } else if (pathname === '/reports/dcmb') {
    content = <DcmbReportPage />
  } else {
    const structureId = structureMatch
      ? Number(structureMatch[1])
      : 2

    content = (
      <StructureWorkspace
        structureId={structureId}
        onNavigate={(nextStructureId) =>
          navigate(`/structures/${nextStructureId}`)}
        onPocketSelect={setCardPocketId}
      />
    )
  }

  return (
    <AppShell
      pathname={pathname}
      onNavigate={navigate}
      currentPocketId={currentPocketId}
    >
      {content}
    </AppShell>
  )
}
