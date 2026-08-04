import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { DEFAULT_STRUCTURE_ID } from './useAppRoute'

interface Props {
  children: ReactNode
  pathname: string
  onNavigate: (path: string) => void
  currentPocketId: number
}

interface NavigationItem {
  label: string
  description: string
  path: string
  active: (pathname: string) => boolean
}

const NAVIGATION: NavigationItem[] = [
  {
    label: 'Selectivity',
    description: '7B / 7A evidence',
    path: '/selectivity',
    active: (pathname) => pathname === '/selectivity',
  },
  {
    label: 'Structures',
    description: 'Browse structures',
    path: `/structures/${DEFAULT_STRUCTURE_ID}`,
    active: (pathname) => pathname.startsWith('/structures/'),
  },
]

function positiveInteger(value: string): number | null {
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

export function AppShell({ children, pathname, onNavigate, currentPocketId }: Props) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [structureId, setStructureId] = useState(String(DEFAULT_STRUCTURE_ID))
  const [jumpError, setJumpError] = useState<string | null>(null)

  const navigation = [
    ...NAVIGATION,
    {
      label: 'Similar pockets',
      description: 'Search and rank pockets',
      path: `/pockets/${currentPocketId}/similar`,
      active: (currentPath: string) =>
        currentPath.startsWith('/pockets/')
        && currentPath.endsWith('/similar'),
    },
  ]

  useEffect(() => {
    setMenuOpen(false)
    setJumpError(null)

    const structureMatch = /^\/structures\/(\d+)$/.exec(pathname)
    if (structureMatch) {
      setStructureId(structureMatch[1])
    }
  }, [pathname])

  const navigate = (path: string) => {
    onNavigate(path)
    setMenuOpen(false)
  }

  const openStructure = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const id = positiveInteger(structureId)
    if (id === null) {
      setJumpError('Enter a positive structure ID.')
      return
    }
    navigate(`/structures/${id}`)
  }

  const openPocketSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = event.currentTarget
    const value = form.elements.namedItem('pocket-id')
    const raw = value instanceof HTMLInputElement ? value.value : ''
    const id = positiveInteger(raw)
    if (id === null) {
      setJumpError('Enter a positive pocket ID.')
      return
    }
    navigate(`/pockets/${id}/similar`)
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <button
          type="button"
          className="brand brand-button"
          aria-label="Open default structure"
          onClick={() => navigate(`/structures/${DEFAULT_STRUCTURE_ID}`)}
        >
          <span className="brand-mark" aria-hidden="true">T</span>
          <span>
            <strong>Totah Lab</strong>
            <small>Pocket Atlas</small>
          </span>
        </button>

        <button
          type="button"
          className="menu-toggle"
          aria-expanded={menuOpen}
          aria-controls="primary-navigation"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span aria-hidden="true">☰</span>
          Menu
        </button>

        <nav
          id="primary-navigation"
          className={`primary-navigation${menuOpen ? ' open' : ''}`}
          aria-label="Primary navigation"
        >
          {navigation.map((item) => {
            const isActive = item.active(pathname)
            return (
              <button
                key={item.label}
                type="button"
                className={`navigation-item${isActive ? ' active' : ''}`}
                aria-current={isActive ? 'page' : undefined}
                onClick={() => navigate(item.path)}
              >
                <strong>{item.label}</strong>
                <small>{item.description}</small>
              </button>
            )
          })}
        </nav>

        <div className="topbar-actions">
          <div className="environment">
            <span className="status-dot" />
            Local workspace
          </div>
        </div>
      </header>

      <aside className={`navigation-drawer${menuOpen ? ' open' : ''}`}>
        <div className="quick-jump">
          <div>
            <p className="quick-jump-title">Quick navigation</p>
            <p className="quick-jump-copy">
              Open a structure or start a similarity search by database ID.
            </p>
          </div>

          <form onSubmit={openStructure}>
            <label htmlFor="menu-structure-id">Structure ID</label>
            <div>
              <input
                id="menu-structure-id"
                inputMode="numeric"
                value={structureId}
                onChange={(event) => setStructureId(event.target.value)}
              />
              <button type="submit">Open</button>
            </div>
          </form>

          <form onSubmit={openPocketSearch}>
            <label htmlFor="menu-pocket-id">Pocket ID</label>
            <div>
              <input
                id="menu-pocket-id"
                name="pocket-id"
                inputMode="numeric"
                key={currentPocketId}
                defaultValue={currentPocketId}
              />
              <button type="submit">Find similar</button>
            </div>
          </form>

          {jumpError && (
            <p className="quick-jump-error" role="alert">{jumpError}</p>
          )}
        </div>
      </aside>

      {menuOpen && (
        <button
          type="button"
          className="navigation-backdrop"
          aria-label="Close navigation menu"
          onClick={() => setMenuOpen(false)}
        />
      )}

      <main>{children}</main>
    </div>
  )
}
