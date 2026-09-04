import { Link, useSearchParams } from 'react-router-dom'
import { aggregateAllSeasons } from '../api/aggregations'
import { fetchFamilyHistory, type SeasonSummary } from '../api/leagues'
import { useApiData } from '../hooks/useApiData'
import LoadingStatus from './LoadingStatus'
import PlayoffBrackets from './PlayoffBrackets'
import WeeklySchedule from './WeeklySchedule'

interface LeagueViewProps {
  leagueKey: string
}

export default function LeagueView({ leagueKey }: LeagueViewProps) {
  const { data: history, error, loading, slow, retry } = useApiData(() => fetchFamilyHistory(leagueKey), [leagueKey])
  const [searchParams, setSearchParams] = useSearchParams()

  if (loading || error || !history) {
    return <LoadingStatus loading={loading} slow={slow} error={error} retry={retry} subject="league" />
  }

  if (history.seasons.length === 0) {
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

  const isPickem = history.type === 'PICKEM'
  const isPickemWeekly = isPickem && season.pickemWeeks.length > 0

  return (
    <div className="league-view">
      <div className="league-view-controls">
        <header className="league-header">
          <h2>{season.name}</h2>
          <p>
            {season.season !== 'All' && `${season.season} season`}
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

      <div className="league-view-sections">
        {!isPickem && <WeeklySchedule weeklyMatchups={season.weeklyMatchups} status={season.status} currentWeek={season.currentWeek} />}

        <section className="card">
          <h3 className="card-title">Standings</h3>
          <div className="standings-table-scroll">
            <table className="standings-table">
              <thead>
                <tr>
                  <th>Rank</th>
                  <th className="team-col">Team</th>
                  {isPickem ? (
                    <>
                      <th className="total-col">Total</th>
                      {isPickemWeekly &&
                        season.pickemWeeks.map((week) => (
                          <th key={week} className="week-col">
                            Wk{week}
                          </th>
                        ))}
                    </>
                  ) : (
                    <>
                      <th>W</th>
                      <th>L</th>
                      <th>T</th>
                      <th>Points For</th>
                      <th>Points Against</th>
                    </>
                  )}
                </tr>
              </thead>
              <tbody>
                {season.teams.map((team) => (
                  <tr key={team.ownerUserId ?? team.teamName}>
                    <td className="rank-cell">{team.rank}</td>
                    <td className="team-cell">
                      {team.ownerUserId ? (
                        <Link to={`/managers/${team.ownerUserId}`} className="manager-link">
                          {team.avatarUrl && <img src={team.avatarUrl} alt="" className="avatar" />}
                          {team.teamName}
                        </Link>
                      ) : (
                        <>
                          {team.avatarUrl && <img src={team.avatarUrl} alt="" className="avatar" />}
                          {team.teamName}
                        </>
                      )}
                      {team.coManagers.length > 0 && (
                        <span className="co-managers">
                          w/{' '}
                          {team.coManagers.map((co, i) => (
                            <span key={co.userId}>
                              {i > 0 && ', '}
                              <Link to={`/managers/${co.userId}`}>{co.displayName}</Link>
                            </span>
                          ))}
                        </span>
                      )}
                      {isPickem && team.boughtIn && (
                        <span className="buyin-badge" title="Paid the buy-in — eligible for prize money">
                          Buy-in
                        </span>
                      )}
                    </td>
                    {isPickem ? (
                      <>
                        <td className="total-col">{team.pointsFor}</td>
                        {isPickemWeekly &&
                          team.weeklyScores.map((score, i) => (
                            <td key={season.pickemWeeks[i]} className="week-col">
                              {score === null ? '—' : score}
                            </td>
                          ))}
                      </>
                    ) : (
                      <>
                        <td>{team.wins}</td>
                        <td>{team.losses}</td>
                        <td>{team.ties}</td>
                        <td>{team.pointsFor.toFixed(2)}</td>
                        <td>{team.pointsAgainst.toFixed(2)}</td>
                      </>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        {!isPickem && <PlayoffBrackets bracket={season.bracket} />}
      </div>
    </div>
  )
}
