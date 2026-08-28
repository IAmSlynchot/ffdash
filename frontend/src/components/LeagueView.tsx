import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  aggregateAllSeasons,
  fetchFamilyHistory,
  type LeagueFamilyHistory,
  type SeasonSummary,
} from '../api/leagues'

interface LeagueViewProps {
  leagueKey: string
}

export default function LeagueView({ leagueKey }: LeagueViewProps) {
  const [history, setHistory] = useState<LeagueFamilyHistory | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [searchParams, setSearchParams] = useSearchParams()

  useEffect(() => {
    let cancelled = false

    fetchFamilyHistory(leagueKey)
      .then((data) => {
        if (!cancelled) setHistory(data)
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
  }, [leagueKey])

  if (loading) {
    return <p className="status-message">Loading league…</p>
  }

  if (error) {
    return <p className="status-message error">Failed to load league: {error}</p>
  }

  if (!history || history.seasons.length === 0) {
    return null
  }

  const currentSeason = history.seasons[0].season
  const requestedSeason = searchParams.get('season') ?? currentSeason
  const season: SeasonSummary =
    requestedSeason === 'all'
      ? aggregateAllSeasons(history)
      : (history.seasons.find((s) => s.season === requestedSeason) ?? history.seasons[0])

  function handleSeasonChange(value: string) {
    setSearchParams(value === currentSeason ? {} : { season: value })
  }

  return (
    <div className="league-view">
      <div className="league-view-controls">
        <header className="league-header">
          <h2>{season.name}</h2>
          <p>
            {season.season !== 'All' && `${season.season} season · `}
            {season.totalRosters} teams
            {season.status !== 'combined' && ` · ${season.status}`}
          </p>
        </header>

        <select
          className="season-select"
          value={requestedSeason}
          onChange={(e) => handleSeasonChange(e.target.value)}
          aria-label="Season"
        >
          {history.seasons.map((s) => (
            <option key={s.season} value={s.season}>
              {s.season}
            </option>
          ))}
          <option value="all">All</option>
        </select>
      </div>

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
          {season.teams.map((team) => (
            <tr key={team.ownerUserId ?? team.teamName}>
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
