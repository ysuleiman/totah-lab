import type { ReactNode } from 'react'

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/structures/2" aria-label="Totah Lab home">
          <span className="brand-mark" aria-hidden="true">T</span>
          <span>
            <strong>Totah Lab</strong>
            <small>Pocket Atlas</small>
          </span>
        </a>
        <div className="environment">
          <span className="status-dot" />
          Local workspace
        </div>
      </header>
      <main>{children}</main>
    </div>
  )
}
