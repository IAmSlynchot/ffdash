import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { MatchupSide, WeeklyMatchup } from '../api/leagues'
import ChumpOMeter from './ChumpOMeter'

interface WeeklyScheduleProps {
  weeklyMatchups: WeeklyMatchup[]
  /** SeasonSummary.status — anything other than 'complete' is treated as "live" for the
   * purposes of defaulting to currentWeek and staying visible with no results yet. */
  status: string
  /** SeasonSummary.currentWeek — Sleeper's live current week, null for Pick'em/the "All" view. */
  currentWeek: number | null
}

/** A season's own boxed "weekly view" area — a week picker plus that week's results, with room
 * for more per-week content (e.g. transactions) to join it later. On a live season this stays
 * visible and defaults to `currentWeek` even before it has any results (an empty-state message
 * shows in place of the grid); on a complete season (or the "All" view, or Pick'em, both of
 * which never carry weeklyMatchups/currentWeek) it defaults to the last real week, or renders
 * nothing at all if there's truly no week to show. */
export default function WeeklySchedule({ weeklyMatchups, status, currentWeek }: WeeklyScheduleProps) {
  // Not synced via an effect: `manualWeek` is only ever a user override, and re-deriving
  // `selectedWeek` from it plus the current `weeks` list every render means a season switch
  // (weeklyMatchups changes without this component remounting) can't leave a stale/out-of-range
  // week selected — it just falls back to the current/latest week for whatever season is showing now.
  const [manualWeek, setManualWeek] = useState<number | null>(null)

  const isLive = status !== 'complete'
  const byWeek = groupByWeek(weeklyMatchups)
  const weeksWithData = byWeek.map(([week]) => week)
  const weeks =
    isLive && currentWeek !== null && !weeksWithData.includes(currentWeek)
      ? [...weeksWithData, currentWeek].sort((a, b) => a - b)
      : weeksWithData

  if (weeks.length === 0) {
    return null
  }

  const latestWeek = weeks[weeks.length - 1]
  const defaultWeek = isLive && currentWeek !== null ? currentWeek : latestWeek
  const selectedWeek = manualWeek !== null && weeks.includes(manualWeek) ? manualWeek : defaultWeek
  const matchups = byWeek.find(([week]) => week === selectedWeek)?.[1] ?? []
  const { champ, chump } = findWeekExtremes(matchups)
  const weekNotStarted = matchups.length > 0 && matchups.every((m) => m.team1.score === 0 && m.team2.score === 0)
  // True while the *selected* week is the season's live current week. Note this can't also
  // check "not already in weeksWithData" the way it used to read — SeasonDataService now always
  // merges the live week's matchups into the same weeklyMatchups list (that's what makes the
  // matchup grid above show its pairings before it's scored), so weeksWithData includes
  // currentWeek as soon as it has any pairings at all, live or final. The one edge case this
  // misses: the brief window right after currentWeek is fully scored but before Sleeper's own
  // settings.leg has advanced to the next week — the Meter would still show for a technically-
  // decided week there, which is harmless (it just shows the final positions) rather than wrong.
  const isViewingLiveWeek = isLive && currentWeek !== null && selectedWeek === currentWeek

  return (
    <section className="card">
      <div className="weekly-view-header">
        <h3 className="card-title">Weekly Breakdown</h3>
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
      {matchups.length === 0 ? (
        <p className="card-empty">No matchups scheduled yet this week.</p>
      ) : (
        <>
          {isViewingLiveWeek && <ChumpOMeter matchups={matchups} />}
          {/* A week whose games haven't kicked off yet still has real pairings (Sleeper sets the
              full schedule upfront) but every score is a real 0 — not "0-0 tie", just "hasn't
              played". Crowning a Champ/Chump off that would be meaningless, so skip the
              highlight cards until at least one side has a nonzero score. */}
          {weekNotStarted ? (
            <p className="card-empty">Games haven't kicked off yet — check back once scores start coming in.</p>
          ) : (
            <div className="week-highlight-grid">
              <WeekHighlightCard label="Champ of the Week" glyph="🏆" side={champ} tone="champ" />
              <WeekHighlightCard label="Chump of the Week" glyph="🚽" side={chump} tone="chump" />
            </div>
          )}
          <div className="week-matchup-grid">
            {matchups.map((matchup, i) => (
              <div key={i} className="week-matchup">
                <MatchupRow side={matchup.team1} won={matchup.team1.score > matchup.team2.score} />
                <MatchupRow side={matchup.team2} won={matchup.team2.score > matchup.team1.score} />
              </div>
            ))}
          </div>
        </>
      )}
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
