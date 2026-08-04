import { useCallback, useEffect, useState } from 'react'

export const DEFAULT_STRUCTURE_ID = 2
export const DEFAULT_POCKET_ID = 1

const DEFAULT_PATH = `/structures/${DEFAULT_STRUCTURE_ID}`
const STRUCTURE_PATH = /^\/structures\/([1-9]\d*)$/
const SIMILAR_PATH = /^\/pockets\/([1-9]\d*)\/similar$/
const COMPARE_PATH = /^\/pockets\/([1-9]\d*)\/compare\/([1-9]\d*)$/

function normalizePath(pathname: string): string {
  if (pathname === '/' || pathname === '') {
    return DEFAULT_PATH
  }

  if (pathname === '/selectivity') {
    return pathname
  }

  if (
    STRUCTURE_PATH.test(pathname)
    || SIMILAR_PATH.test(pathname)
    || COMPARE_PATH.test(pathname)
  ) {
    return pathname
  }

  return DEFAULT_PATH
}

export function useAppRoute() {
  const [pathname, setPathname] = useState(() =>
    normalizePath(window.location.pathname),
  )

  useEffect(() => {
    const normalized = normalizePath(window.location.pathname)

    if (normalized !== window.location.pathname) {
      window.history.replaceState(null, '', normalized)
    }
    setPathname(normalized)

    const handlePopState = () => {
      const nextPath = normalizePath(window.location.pathname)
      if (nextPath !== window.location.pathname) {
        window.history.replaceState(null, '', nextPath)
      }
      setPathname(nextPath)
    }

    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const navigate = useCallback((path: string) => {
    const normalized = normalizePath(path)

    if (normalized === window.location.pathname) {
      setPathname(normalized)
      return
    }

    window.history.pushState(null, '', normalized)
    setPathname(normalized)
  }, [])

  return { pathname, navigate }
}
