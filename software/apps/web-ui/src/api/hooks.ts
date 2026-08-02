import { useCallback, useEffect, useState } from 'react'
import { getJson } from './client'

interface QueryState<T> {
  key: string | null
  data: T | null
  error: Error | null
}

export function useApiQuery<T>(path: string | null) {
  const [revision, setRevision] = useState(0)
  const [state, setState] = useState<QueryState<T>>({
    key: null,
    data: null,
    error: null,
  })
  const requestKey = path ? `${path}:${revision}` : null

  useEffect(() => {
    if (!path) return

    const controller = new AbortController()
    getJson<T>(path, controller.signal)
      .then((data) => setState({ key: requestKey, data, error: null }))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setState({
          key: requestKey,
          data: null,
          error: error instanceof Error ? error : new Error('Unknown error'),
        })
      })

    return () => controller.abort()
  }, [path, requestKey])

  const retry = useCallback(() => setRevision((value) => value + 1), [])
  if (!path) {
    return { data: null, error: null, loading: false, retry }
  }
  const current = state.key === requestKey
  return {
    data: current ? state.data : null,
    error: current ? state.error : null,
    loading: !current,
    retry,
  }
}
