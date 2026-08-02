import { useEffect, useState } from 'react'
import { AppShell } from './AppShell'
import { SelectivityWorkspace } from '../features/selectivity/SelectivityWorkspace'
import { StructureWorkspace } from '../features/structure/StructureWorkspace'
import { useStructureRoute } from './useStructureRoute'

export function App() {
  const [pathname, setPathname] = useState(window.location.pathname)
  const route = useStructureRoute()
  useEffect(() => {
    const updatePath = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', updatePath)
    return () => window.removeEventListener('popstate', updatePath)
  }, [])
  return (
    <AppShell>
      {pathname === '/selectivity'
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
