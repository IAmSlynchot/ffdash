import { useCallback, useEffect, useState } from 'react'

// The backend is hosted on Render's free tier, which spins the service down
// after ~15m idle and takes up to ~a minute to wake back up on the next
// request — see CLAUDE.md. These tuned so a normal fast response never shows
// the "waking up" message, but a cold start gets an honest explanation
// instead of an indefinite blank/loading screen, and recovers on its own.
const SLOW_THRESHOLD_MS = 4_000
const RETRY_DELAY_MS = 5_000
const MAX_AUTO_RETRIES = 10 // ~50s of auto-retry after the first failed attempt

interface ApiDataState<T> {
  data: T | null
  error: string | null
  loading: boolean
  /** True once the request has been pending/retrying longer than a normal response should take. */
  slow: boolean
  /** Re-runs the fetch from scratch (e.g. from a "Try again" button after retries are exhausted). */
  retry: () => void
}

/**
 * Fetches data with automatic retry-with-delay on failure, and reports back
 * when a request is taking unusually long — the two things every fetch-driven
 * page in this app needs, so `LeaguesPage`/`LeagueView`/`ManagerListPage`/
 * `ManagerProfilePage` all fetch through this instead of duplicating
 * loading/error/retry state by hand.
 */
export function useApiData<T>(fetcher: () => Promise<T>, deps: unknown[]): ApiDataState<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [slow, setSlow] = useState(false)
  const [retryToken, setRetryToken] = useState(0)

  useEffect(() => {
    let cancelled = false
    let retries = 0

    setData(null)
    setError(null)
    setLoading(true)
    setSlow(false)

    const slowTimer = setTimeout(() => {
      if (!cancelled) setSlow(true)
    }, SLOW_THRESHOLD_MS)

    function attempt() {
      fetcher()
        .then((result) => {
          if (cancelled) return
          setData(result)
          setLoading(false)
        })
        .catch((err: unknown) => {
          if (cancelled) return
          if (retries < MAX_AUTO_RETRIES) {
            retries++
            setSlow(true)
            setTimeout(attempt, RETRY_DELAY_MS)
          } else {
            setError(err instanceof Error ? err.message : String(err))
            setLoading(false)
          }
        })
    }

    attempt()

    return () => {
      cancelled = true
      clearTimeout(slowTimer)
    }
    // deps is caller-supplied and intentionally the sole trigger for re-fetching;
    // fetcher itself is read fresh on each call but not tracked as a dependency.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, retryToken])

  const retry = useCallback(() => setRetryToken((t) => t + 1), [])

  return { data, error, loading, slow, retry }
}
