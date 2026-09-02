interface LoadingStatusProps {
  loading: boolean
  slow: boolean
  error: string | null
  retry: () => void
  /** What's being loaded, for the error message, e.g. "leagues" -> "Failed to load leagues: ...". */
  subject: string
}

/**
 * Shared loading/error UI for every page that fetches from the backend. The
 * backend is hosted on Render's free tier and can take up to ~a minute to
 * wake up from being idle (see CLAUDE.md) — `slow` (from useApiData) flags
 * when a request has been pending/retrying longer than a normal response
 * should take, so that wait gets an explanation instead of a blank screen.
 */
export default function LoadingStatus({ loading, slow, error, retry, subject }: LoadingStatusProps) {
  if (loading) {
    return (
      <p className="status-message">
        {slow ? "Waking up the server — this can take up to a minute if it's been idle…" : 'Loading…'}
      </p>
    )
  }

  return (
    <p className="status-message error">
      Failed to load {subject}: {error}{' '}
      <button className="retry-button" onClick={retry}>
        Try again
      </button>
    </p>
  )
}
