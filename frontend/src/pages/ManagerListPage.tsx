import { Link } from 'react-router-dom'
import { fetchOwnerCareerSummaries } from '../api/leagues'
import { useApiData } from '../hooks/useApiData'
import LoadingStatus from '../components/LoadingStatus'

export default function ManagerListPage() {
  const { data: owners, error, loading, slow, retry } = useApiData(fetchOwnerCareerSummaries, [])

  if (loading || error || !owners) {
    return <LoadingStatus loading={loading} slow={slow} error={error} retry={retry} subject="managers" />
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
