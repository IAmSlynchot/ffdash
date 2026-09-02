import { Navigate, useParams } from 'react-router-dom'
import { fetchLeagueFamilies } from '../api/leagues'
import { useApiData } from '../hooks/useApiData'
import LeagueNav from '../components/LeagueNav'
import LeagueView from '../components/LeagueView'
import LoadingStatus from '../components/LoadingStatus'

export default function LeaguesPage() {
  const { key } = useParams<{ key: string }>()
  const { data: leagues, error, loading, slow, retry } = useApiData(fetchLeagueFamilies, [])

  if (loading || error || !leagues) {
    return <LoadingStatus loading={loading} slow={slow} error={error} retry={retry} subject="leagues" />
  }

  if (leagues.length === 0) {
    return <p className="status-message">No leagues configured.</p>
  }

  // No league selected yet (e.g. landed on /leagues directly) — default to the first one.
  if (!key) {
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
