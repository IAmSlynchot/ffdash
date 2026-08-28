import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchOwnerCareerSummaries, type OwnerCareerSummary } from '../api/leagues'

export default function ManagerListPage() {
  const [owners, setOwners] = useState<OwnerCareerSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchOwnerCareerSummaries()
      .then(setOwners)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)))
  }, [])

  if (error) {
    return <p className="status-message error">Failed to load managers: {error}</p>
  }

  if (!owners) {
    return <p className="status-message">Loading managers…</p>
  }

  return (
    <div className="manager-list">
      <table className="standings-table">
        <thead>
          <tr>
            <th>Manager</th>
            <th>Leagues</th>
            <th>W</th>
            <th>L</th>
            <th>T</th>
            <th>Top 3 Finishes</th>
          </tr>
        </thead>
        <tbody>
          {owners.map((owner) => {
            const leagueCount = new Set(owner.seasonResults.map((r) => r.leagueFamilyKey)).size
            return (
              <tr key={owner.userId}>
                <td className="team-cell">
                  <Link to={`/managers/${owner.userId}`} className="manager-link">
                    {owner.avatarUrl && <img src={owner.avatarUrl} alt="" className="avatar" />}
                    {owner.displayName}
                  </Link>
                </td>
                <td>{leagueCount}</td>
                <td>{owner.combinedWins}</td>
                <td>{owner.combinedLosses}</td>
                <td>{owner.combinedTies}</td>
                <td>{owner.topThreeFinishes}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
