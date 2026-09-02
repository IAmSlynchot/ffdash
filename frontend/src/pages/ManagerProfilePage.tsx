import { Link, useParams } from 'react-router-dom'
import { fetchOwnerCareerSummaries } from '../api/leagues'
import { useApiData } from '../hooks/useApiData'
import LoadingStatus from '../components/LoadingStatus'

export default function ManagerProfilePage() {
  const { userId } = useParams<{ userId: string }>()

  // Re-fetches the full list (rather than passing data via router state) so a
  // direct link or page reload works on its own — cheap given the backend's
  // own caching.
  const { data: owners, error, loading, slow, retry } = useApiData(fetchOwnerCareerSummaries, [])

  if (loading || error || !owners) {
    return <LoadingStatus loading={loading} slow={slow} error={error} retry={retry} subject="manager" />
  }

  const owner = owners.find((o) => o.userId === userId)
  if (!owner) {
    return <p className="status-message">Manager not found.</p>
  }

  // Leagues this owner is part of, deduped from their per-season results.
  const leagues = Array.from(
    new Map(owner.seasonResults.map((r) => [r.leagueFamilyKey, r.leagueFamilyDisplayName])).entries(),
  )

  return (
    <div className="manager-profile">
      <header className="league-header manager-profile-header">
        {owner.avatarUrl && <img src={owner.avatarUrl} alt="" className="avatar avatar-lg" />}
        <h2>{owner.displayName}</h2>
      </header>

      <h3>Leagues</h3>
      <ul className="manager-league-list">
        {leagues.map(([key, displayName]) => (
          <li key={key}>
            <Link to={`/leagues/${key}`}>{displayName}</Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
