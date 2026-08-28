import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { fetchOwnerCareerSummaries, type OwnerCareerSummary } from '../api/leagues'

export default function ManagerProfilePage() {
  const { userId } = useParams<{ userId: string }>()
  const [owner, setOwner] = useState<OwnerCareerSummary | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!userId) return

    // Re-fetched (rather than passed via route state) so a direct link or
    // page reload works on its own — cheap given the backend's own caching.
    fetchOwnerCareerSummaries()
      .then((owners) => {
        const match = owners.find((o) => o.userId === userId)
        if (match) {
          setOwner(match)
        } else {
          setNotFound(true)
        }
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)))
  }, [userId])

  if (error) {
    return <p className="status-message error">Failed to load manager: {error}</p>
  }

  if (notFound) {
    return <p className="status-message">Manager not found.</p>
  }

  if (!owner) {
    return <p className="status-message">Loading manager…</p>
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
