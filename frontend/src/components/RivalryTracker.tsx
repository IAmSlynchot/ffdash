import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import type { HeadToHeadRecord } from '../api/aggregations'
import LoadingStatus from './LoadingStatus'

interface RivalryTrackerProps {
  owner: { userId: string; displayName: string; avatarUrl: string | null }
  headToHead: HeadToHeadRecord[]
  loading: boolean
  error: string | null
  slow: boolean
  retry: () => void
}

interface RivalryDescriptor {
  title: string
  flavorText: string
}

/**
 * A personality-driven name for one rivalry, from `ownerDisplayName`'s perspective. Flavor text
 * is static per category (not unique per pairing) — see the Rivalry Tracker card. Exhaustive over
 * wins/losses/ties >= 0 given at least one game played (computeHeadToHead never creates a record
 * with zero games), so priority order matters: an all-ties record is a Stalemate before either
 * "zero wins" check can fire, then a literal shutout is Undefeated, then a close two-sided record
 * is a Certified Rivalry, and only a genuinely lopsided two-sided record gets the "Father" title.
 */
function describeRivalry(ownerDisplayName: string, opponentTeamName: string, record: HeadToHeadRecord): RivalryDescriptor {
  const { wins, losses } = record
  if (wins === 0 && losses === 0) {
    return { title: '❔Tense Mystery', flavorText: 'It\'s a classic will-they, won\'t-they. These two teams have yet to come head-to-head in the wild, but the rest of us wait with bated breath for the day that they do.' }
  }
  if (losses === 0) {
    return { title: '🏅Undefeated', flavorText: `Matchups between these two teams have been difficult for both sides. On the one hand, ${opponentTeamName} has never won. On the other hand, ${ownerDisplayName} has never gotten a competitive opponent.` }
  }
  if (wins === 0) {
    return { title: '🤡 Utterly Winless', flavorText: `Try, try, and try again. Every time ${ownerDisplayName} bravely picks up the Sisyphean pursuit of challenging ${opponentTeamName}, it ends the same way. But one must admire the dedication.` }
  }
  if (Math.abs(wins - losses) <= 1) {
    return { title: '🥵 Certified Heated Rivalry', flavorText: 'Each time these two teams come head-to-head, the raw tension is palpable. Who will end up on top? Only time and an HBO suscription will tell.' }
  }
  const dominant = wins > losses ? ownerDisplayName : opponentTeamName
  const submissive = wins > losses ? opponentTeamName : ownerDisplayName
  return { title: `👨‍🍼 ${dominant} is ${submissive} Father`, flavorText: `Some matchups are close, some are tense. Not this one. Whenever these two teams meet, ${submissive} can be seen nervously tweaking their lineup, grasping for hope, while ${dominant} nonchalantly chalks up a W.` }
}

/**
 * Head-to-head, elevated from a flat list into a dropdown-driven "vs" detail on one opponent at
 * a time — see ManagerProfilePage, which fetches headToHead (via computeHeadToHead) and just
 * passes it through here along with that fetch's own loading/error state.
 */
export default function RivalryTracker({ owner, headToHead, loading, error, slow, retry }: RivalryTrackerProps) {
  const [manualOpponentId, setManualOpponentId] = useState<string | null>(null)

  // Recomputed only when the owner or their opponent count changes — not on every re-render —
  // so the "random" pick doesn't jump around on unrelated state updates. True Math.random()
  // picked once per {owner, opponent count} via useMemo, an established pattern in this codebase
  // for exactly this reason.
  const randomOpponentId = useMemo(() => {
    if (headToHead.length === 0) return null
    return headToHead[Math.floor(Math.random() * headToHead.length)].opponentUserId
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [owner.userId, headToHead.length])

  // manualOpponentId is only ever a user override (via the dropdown or the randomize button);
  // the actually-displayed selection is derived here, same pattern as WeeklySchedule's
  // manualWeek. That means a pick made for a different manager is automatically invalidated
  // (falls back to a fresh random pick) once `headToHead` no longer contains it — no reset
  // effect needed even though this component isn't remounted per owner (see App.tsx).
  const rivalsByGamesPlayed = [...headToHead].sort((a, b) => b.wins + b.losses + b.ties - (a.wins + a.losses + a.ties))
  const selectedOpponentId =
    manualOpponentId && headToHead.some((r) => r.opponentUserId === manualOpponentId) ? manualOpponentId : randomOpponentId
  const selectedRivalry = headToHead.find((r) => r.opponentUserId === selectedOpponentId) ?? null

  function randomizeOpponent() {
    if (headToHead.length === 0) return
    const candidates = headToHead.length > 1 ? headToHead.filter((r) => r.opponentUserId !== selectedOpponentId) : headToHead
    setManualOpponentId(candidates[Math.floor(Math.random() * candidates.length)].opponentUserId)
  }

  return (
    <section className="card">
      <div className="weekly-view-header">
        <h3 className="card-title">Rivalry Tracker</h3>
        {headToHead.length > 0 && (
          <div className="rivalry-controls">
            <select
              className="week-select"
              value={selectedOpponentId ?? ''}
              onChange={(e) => setManualOpponentId(e.target.value)}
              aria-label="Opponent"
            >
              {rivalsByGamesPlayed.map((r) => (
                <option key={r.opponentUserId} value={r.opponentUserId}>
                  {r.opponentTeamName} ({r.wins}-{r.losses}
                  {r.ties > 0 ? `-${r.ties}` : ''})
                </option>
              ))}
            </select>
            <button
              type="button"
              className="rivalry-randomize"
              onClick={randomizeOpponent}
              disabled={headToHead.length <= 1}
              aria-label="Pick a random opponent"
              title="Pick a random opponent"
            >
              🎲
            </button>
          </div>
        )}
      </div>
      {loading || error ? (
        <LoadingStatus loading={loading} slow={slow} error={error} retry={retry} subject="head-to-head history" />
      ) : !selectedRivalry ? (
        <p className="card-empty">No head-to-head matchups yet.</p>
      ) : (
        <RivalryCard owner={owner} rivalry={selectedRivalry} />
      )}
    </section>
  )
}

interface RivalryCardProps {
  owner: { displayName: string; avatarUrl: string | null }
  rivalry: HeadToHeadRecord
}

/** The Rivalry Tracker's "vs" detail for one selected opponent. */
function RivalryCard({ owner, rivalry }: RivalryCardProps) {
  const descriptor = describeRivalry(owner.displayName, rivalry.opponentTeamName, rivalry)
  const gamesPlayed = rivalry.wins + rivalry.losses + rivalry.ties
  const winPct = gamesPlayed > 0 ? Math.round((rivalry.wins / gamesPlayed) * 100) : 0
  const lastMeeting = rivalry.lastMeeting
  const lastMeetingResult = lastMeeting
    ? lastMeeting.myScore > lastMeeting.theirScore
      ? 'Won'
      : lastMeeting.myScore < lastMeeting.theirScore
        ? 'Lost'
        : 'Tied'
    : null

  return (
    <div className="rivalry-card">
      <div className="rivalry-card-header">
        <div className="rivalry-vs">
          <div className="rivalry-side">
            {owner.avatarUrl && <img src={owner.avatarUrl} alt="" className="avatar" />}
            <span className="rivalry-side-name">{owner.displayName}</span>
          </div>
          <div className="rivalry-score-block">
            <span className="rivalry-score">
              {rivalry.wins}-{rivalry.losses}
              {rivalry.ties > 0 ? `-${rivalry.ties}` : ''}
            </span>
            <div className="rivalry-winrate-bar" title={`${winPct}% win rate`}>
              <div className="rivalry-winrate-bar-fill" style={{ width: `${winPct}%` }} />
            </div>
          </div>
          <div className="rivalry-side">
            {rivalry.opponentAvatarUrl && <img src={rivalry.opponentAvatarUrl} alt="" className="avatar" />}
            <Link to={`/managers/${rivalry.opponentUserId}`} className="rivalry-side-name">
              {rivalry.opponentTeamName}
            </Link>
          </div>
        </div>
      </div>
      <div className="rivalry-card-body">
        <div className="rivalry-main">
          <div className="rivalry-title">{descriptor.title}</div>
          <p className="rivalry-flavor">{descriptor.flavorText}</p>
        </div>
        <div className="rivalry-last-meeting">
          <span className="rivalry-last-meeting-label">Last Meeting</span>
          {lastMeeting && lastMeetingResult ? (
            <>
              <span className={`rivalry-last-meeting-result rivalry-last-meeting-result-${lastMeetingResult.toLowerCase()}`}>
                {lastMeetingResult}
              </span>
              <span className="rivalry-last-meeting-score">
                {lastMeeting.myScore.toFixed(2)}-{lastMeeting.theirScore.toFixed(2)}
              </span>
              <span className="rivalry-last-meeting-context">
                {lastMeeting.leagueFamilyDisplayName} · Week {lastMeeting.week}, {lastMeeting.season}
              </span>
            </>
          ) : (
            <span className="rivalry-last-meeting-context">—</span>
          )}
        </div>
      </div>
    </div>
  )
}
