import { AppShell } from './AppShell'
import { StructureWorkspace } from '../features/structure/StructureWorkspace'
import { useStructureRoute } from './useStructureRoute'

export function App() {
  const route = useStructureRoute()
  return (
    <AppShell>
      <StructureWorkspace
        structureId={route.structureId}
        onNavigate={route.navigate}
      />
    </AppShell>
  )
}
