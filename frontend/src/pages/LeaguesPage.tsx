import { useEffect, useState } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { fetchLeagueFamilies, type LeagueFamilyRef } from '../api/leagues'
import LeagueNav from '../components/LeagueNav'
import LeagueView from '../components/LeagueView'

export default function LeaguesPage() {
  const { key } = useParams<{ key: string }>()
  const [leagues, setLeagues] = useState<LeagueFamilyRef[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchLeagueFamilies()
      .then(setLeagues)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)))
  }, [])

  if (error) {
    return <p className="status-message error">Failed to load leagues: {error}</p>
  }

  // No league selected yet (e.g. landed on /leagues directly) — default to the first one.
  if (!key) {
    if (leagues.length === 0) {
      return null
    }
    return <Navigate to={`/leagues/${leagues[0].key}`} replace />
  }

  return (
    <div>
      <LeagueNav leagues={leagues} />
      {/* Keyed by leagueKey so the view remounts (and its loading/error state resets
          naturally) on every league switch, instead of resetting state by hand. */}
      <LeagueView key={key} leagueKey={key} />
    </div>
  )
}
