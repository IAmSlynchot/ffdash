import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { MatchupSide, WeeklyMatchup } from '../api/leagues'

interface WeeklyScheduleProps {
  weeklyMatchups: WeeklyMatchup[]
}

/** A season's own boxed "weekly view" area — a week picker plus that week's results today, with
 * room for more per-week content (e.g. transactions) to join it later. Empty (Pick'em, the "All"
 * view, or a season with no concluded week yet) means nothing to show at all. */
export default function WeeklySchedule({ weeklyMatchups }: WeeklyScheduleProps) {
  // Not synced via an effect: `manualWeek` is only ever a user override, and re-deriving
  // `selectedWeek` from it plus the current `weeks` list every render means a season switch
  // (weeklyMatchups changes without this component remounting) can't leave a stale/out-of-range
  // week selected — it just falls back to the latest week for whatever season is showing now.
  const [manualWeek, setManualWeek] = useState<number | null>(null)

  if (weeklyMatchups.length === 0) {
    return null
  }

  const byWeek = groupByWeek(weeklyMatchups)
  const weeks = byWeek.map(([week]) => week)
  const latestWeek = weeks[weeks.length - 1]
  const selectedWeek = manualWeek !== null && weeks.includes(manualWeek) ? manualWeek : latestWeek
  const matchups = byWeek.find(([week]) => week === selectedWeek)?.[1] ?? []
  const { champ, chump } = findWeekExtremes(matchups)

  return (
    <section className="card weekly-view">
      <div className="weekly-view-header">
        <h3 className="card-title">Weekly Results</h3>
        <select
          className="week-select"
          value={selectedWeek}
          onChange={(e) => setManualWeek(Number(e.target.value))}
          aria-label="Week"
        >
          {weeks.map((week) => (
            <option key={week} value={week}>
              Week {week}
            </option>
          ))}
        </select>
      </div>
      <div className="week-highlight-grid">
        <WeekHighlightCard label="Champ of the Week" glyph="🏆" side={champ} tone="champ" />
        <WeekHighlightCard label="Chump of the Week" glyph="🚽" side={chump} tone="chump" />
      </div>
      <div className="week-matchup-grid">
        {matchups.map((matchup, i) => (
          <div key={i} className="week-matchup">
            <MatchupRow side={matchup.team1} won={matchup.team1.score > matchup.team2.score} />
            <MatchupRow side={matchup.team2} won={matchup.team2.score > matchup.team1.score} />
          </div>
        ))}
      </div>
    </section>
  )
}

function findWeekExtremes(matchups: WeeklyMatchup[]): { champ: MatchupSide | null; chump: MatchupSide | null } {
  const sides = matchups.flatMap((m) => [m.team1, m.team2])
  if (sides.length === 0) {
    return { champ: null, chump: null }
  }
  let champ = sides[0]
  let chump = sides[0]
  for (const side of sides) {
    if (side.score > champ.score) champ = side
    if (side.score < chump.score) chump = side
  }
  return { champ, chump }
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

function WeekHighlightCard({
  label,
  glyph,
  side,
  tone,
}: {
  label: string
  glyph: string
  side: MatchupSide | null
  tone: 'champ' | 'chump'
}) {
  const nameContent = side && (
    <>
      {side.avatarUrl && <img src={side.avatarUrl} alt="" className="avatar" />}
      <span className="week-highlight-name">{side.teamName}</span>
    </>
  )

  return (
    <div className={`week-highlight-card week-highlight-${tone}`}>
      <span className="week-highlight-label">
        <span aria-hidden="true">{glyph}</span> {label}
      </span>
      {side ? (
        <div className="week-highlight-body">
          {side.ownerUserId ? (
            <Link to={`/managers/${side.ownerUserId}`} className="week-highlight-team">
              {nameContent}
            </Link>
          ) : (
            <span className="week-highlight-team">{nameContent}</span>
          )}
          <span className="week-highlight-score">{side.score.toFixed(2)}</span>
        </div>
      ) : (
        <p className="card-empty">No data yet.</p>
      )}
    </div>
  )
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
