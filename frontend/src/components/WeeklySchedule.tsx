import { Link } from 'react-router-dom'
import type { MatchupSide, WeeklyMatchup } from '../api/leagues'

interface WeeklyScheduleProps {
  weeklyMatchups: WeeklyMatchup[]
}

/** Renders a season's week-by-week head-to-head results. Empty (Pick'em, the "All" view, or a
 * season with no concluded week yet) means nothing to show at all. */
export default function WeeklySchedule({ weeklyMatchups }: WeeklyScheduleProps) {
  if (weeklyMatchups.length === 0) {
    return null
  }

  const byWeek = groupByWeek(weeklyMatchups)

  return (
    <section className="weekly-schedule">
      <h3 className="card-title">Weekly Results</h3>
      {byWeek.map(([week, matchups]) => (
        <div key={week} className="week-section">
          <h4 className="week-section-title">Week {week}</h4>
          <div className="week-matchup-grid">
            {matchups.map((matchup, i) => (
              <div key={i} className="week-matchup">
                <MatchupRow side={matchup.team1} won={matchup.team1.score > matchup.team2.score} />
                <MatchupRow side={matchup.team2} won={matchup.team2.score > matchup.team1.score} />
              </div>
            ))}
          </div>
        </div>
      ))}
    </section>
  )
}

function groupByWeek(weeklyMatchups: WeeklyMatchup[]): [number, WeeklyMatchup[]][] {
  const byWeek = new Map<number, WeeklyMatchup[]>()
  for (const matchup of weeklyMatchups) {
    const list = byWeek.get(matchup.week) ?? []
    list.push(matchup)
    byWeek.set(matchup.week, list)
  }
  return Array.from(byWeek.entries()).sort(([a], [b]) => a - b)
}

function MatchupRow({ side, won }: { side: MatchupSide; won: boolean }) {
  const nameContent = (
    <>
      {side.avatarUrl && <img src={side.avatarUrl} alt="" className="avatar" />}
      <span className="week-matchup-name">{side.teamName}</span>
    </>
  )
  return (
    <div className={`week-matchup-side${won ? ' week-matchup-side-winner' : ''}`}>
      {side.ownerUserId ? (
        <Link to={`/managers/${side.ownerUserId}`} className="week-matchup-team">
          {nameContent}
        </Link>
      ) : (
        <span className="week-matchup-team">{nameContent}</span>
      )}
      <span className="week-matchup-score">{side.score.toFixed(2)}</span>
    </div>
  )
}
