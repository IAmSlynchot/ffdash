import { useEffect, useState } from 'react'
import { fetchLeagueSummary, type LeagueSummary } from '../api/leagues'

interface LeagueViewProps {
  leagueId: string
}

export default function LeagueView({ leagueId }: LeagueViewProps) {
  const [summary, setSummary] = useState<LeagueSummary | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setSummary(null)
    setError(null)
    setLoading(true)

    fetchLeagueSummary(leagueId)
      .then((data) => {
        if (!cancelled) setSummary(data)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [leagueId])

  if (loading) {
    return <p className="status-message">Loading league…</p>
  }

  if (error) {
    return <p className="status-message error">Failed to load league: {error}</p>
  }

  if (!summary) {
    return null
  }

  return (
    <div className="league-view">
      <header className="league-header">
        <h2>{summary.name}</h2>
        <p>
          {summary.season} season · {summary.totalRosters} teams · {summary.status}
        </p>
      </header>

      <table className="standings-table">
        <thead>
          <tr>
            <th>Team</th>
            <th>W</th>
            <th>L</th>
            <th>T</th>
            <th>Points For</th>
            <th>Points Against</th>
          </tr>
        </thead>
        <tbody>
          {summary.teams.map((team) => (
            <tr key={team.teamName}>
              <td className="team-cell">
                {team.avatarUrl && <img src={team.avatarUrl} alt="" className="avatar" />}
                {team.teamName}
              </td>
              <td>{team.wins}</td>
              <td>{team.losses}</td>
              <td>{team.ties}</td>
              <td>{team.pointsFor.toFixed(2)}</td>
              <td>{team.pointsAgainst.toFixed(2)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
