import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  computeHeadToHead,
  computeLeagueMemberships,
  computeScoringTrends,
  fetchFamilyHistory,
  fetchOwnerCareerSummaries,
  type EarnedBadge,
  type HeadToHeadRecord,
} from '../api/leagues'
import { useApiData } from '../hooks/useApiData'
import LoadingStatus from '../components/LoadingStatus'
import ScoringTrendChart from '../components/ScoringTrendChart'

const BADGE_GLYPH: Record<EarnedBadge['type'], string> = {
  CHAMPION: '🏆',
  TOP_SCORER: '🔥',
  FOUNDING_MEMBER: '🌱',
  TOP_3: '🥉',
  TOILET_CHAMP: '🚽',
  PICKINATOR: '🎯',
  MICRO_MANAGER: '🔬',
  ADVERSITY_SPECIALIST: '🛡️',
  OVERCONFIDENT: '😎',
  TOTAL_DEGENERATE: '🎰',
  MR_BOOMBASTIC: '💥',
  CHUMP_YEAR: '🤡',
}

// "Won the whole thing" badges get a gold/shimmer treatment (see .badge-item-legendary in
// App.css) to stand apart from the rest of the grid's standard styling.
const LEGENDARY_BADGES = new Set<EarnedBadge['type']>(['CHAMPION', 'PICKINATOR'])

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
    return { title: 'Stalemate', flavorText: 'Every meeting ends in a standoff — neither side can find an edge.' }
  }
  if (wins === 0 || losses === 0) {
    return { title: 'Undefeated', flavorText: 'One side has never tasted victory in this matchup.' }
  }
  if (Math.abs(wins - losses) <= 1) {
    return { title: 'Certified Rivalry', flavorText: 'A true coin flip — bragging rights change hands constantly.' }
  }
  const dominant = wins > losses ? ownerDisplayName : opponentTeamName
  return { title: `${dominant} Father`, flavorText: "This one isn't close — one side clearly has the other's number." }
}

export default function ManagerProfilePage() {
  const { userId } = useParams<{ userId: string }>()

  // Badge descriptions are shown in a small tooltip. Hover/keyboard-focus reveals it via CSS
  // alone (see .badge-info:hover/:focus-visible in App.css), but neither fires on tap — mobile
  // needs an explicit open/close toggle instead, tracked here by badge type. Click-outside and
  // Escape both dismiss it, matching normal tooltip/popover expectations.
  const [openBadgeInfo, setOpenBadgeInfo] = useState<string | null>(null)

  // The Rivalry Tracker's selected opponent — see the derivation below, near headToHead.
  const [manualOpponentId, setManualOpponentId] = useState<string | null>(null)

  useEffect(() => {
    if (!openBadgeInfo) return

    function handlePointerDown(e: PointerEvent) {
      if (!(e.target instanceof Element) || !e.target.closest('.badge-title')) {
        setOpenBadgeInfo(null)
      }
    }
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpenBadgeInfo(null)
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [openBadgeInfo])

  // Re-fetches the full list (rather than passing data via router state) so a
  // direct link or page reload works on its own — cheap given the backend's
  // own caching.
  const { data: owners, error, loading, slow, retry } = useApiData(fetchOwnerCareerSummaries, [])

  const owner = owners?.find((o) => o.userId === userId) ?? null

  // OwnerCareerSummary above deliberately omits weekly matchup detail (it's a cross-league
  // aggregate) — the scoring trend and head-to-head cards need the full per-family history for
  // just the leagues this owner is in, fetched separately here. familyKeys is [] (so this
  // resolves to [] immediately, harmlessly) until `owner` above is known; the join(',') dep
  // keeps the effect from re-firing on every render's new array identity.
  const familyKeys = owner ? Array.from(new Set(owner.seasonResults.map((r) => r.leagueFamilyKey))) : []
  const {
    data: familyHistories,
    error: familiesError,
    loading: familiesLoading,
    slow: familiesSlow,
    retry: retryFamilies,
  } = useApiData(() => Promise.all(familyKeys.map((key) => fetchFamilyHistory(key))), [familyKeys.join(',')])

  // Computed here (rather than after the early returns below) so the useMemo right after it can
  // stay an unconditional hook call — react-hooks/rules-of-hooks requires every hook to run in
  // the same order on every render, which a hook placed after an early `return` would violate.
  // `owner`/`familyHistories` may still be null this early; both guards make that safely resolve
  // to an empty list rather than throwing, same as `familyKeys` above already does.
  const headToHead = owner && familyHistories ? computeHeadToHead(owner.userId, familyHistories) : []

  // manualOpponentId is only ever a user override (via the dropdown or the randomize button);
  // the actually-displayed selection is derived further down, same pattern as WeeklySchedule's
  // manualWeek. That means a pick made on a different manager's profile is automatically
  // invalidated (falls back to a fresh random pick) once `headToHead` no longer contains it —
  // no reset effect needed even though this page isn't remounted per userId (see App.tsx).
  const randomOpponentId = useMemo(() => {
    if (headToHead.length === 0) return null
    return headToHead[Math.floor(Math.random() * headToHead.length)].opponentUserId
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [owner?.userId, headToHead.length])

  if (loading || error || !owners) {
    return <LoadingStatus loading={loading} slow={slow} error={error} retry={retry} subject="manager" />
  }

  if (!owner) {
    return <p className="status-message">Manager not found.</p>
  }

  // teamName stays null (and is simply not shown) until familyHistories loads — see
  // computeLeagueMemberships. displayName/since/coManagerOnly don't need it, so this card
  // doesn't wait on that fetch the way the Scoring Trend/Rivalry Tracker cards below do.
  const leagues = computeLeagueMemberships(owner.userId, owner.seasonResults, familyHistories ?? [])

  const scoringTrends = familyHistories ? computeScoringTrends(owner.userId, familyHistories) : []
  const rivalsByGamesPlayed = [...headToHead].sort(
    (a, b) => b.wins + b.losses + b.ties - (a.wins + a.losses + a.ties),
  )
  const selectedOpponentId =
    manualOpponentId && headToHead.some((r) => r.opponentUserId === manualOpponentId) ? manualOpponentId : randomOpponentId
  const selectedRivalry = headToHead.find((r) => r.opponentUserId === selectedOpponentId) ?? null

  function randomizeOpponent() {
    if (headToHead.length === 0) return
    const candidates =
      headToHead.length > 1 ? headToHead.filter((r) => r.opponentUserId !== selectedOpponentId) : headToHead
    setManualOpponentId(candidates[Math.floor(Math.random() * candidates.length)].opponentUserId)
  }

  return (
    <div className="manager-profile">
      <header className="league-header manager-profile-header">
        {owner.avatarUrl && <img src={owner.avatarUrl} alt="" className="avatar avatar-lg" />}
        <h2>{owner.displayName}</h2>
      </header>

      <div className="profile-cards">
        <section className="card">
          <h3 className="card-title">As seen in...</h3>
          <ul className="league-card-grid">
            {leagues.map(({ key, displayName, teamName, since, coManagerOnly }) => (
              <li key={key}>
                <Link to={`/leagues/${key}`} className="league-card">
                  <span className="league-card-name">
                    {displayName}
                    {coManagerOnly && <span className="co-manager-tag"> (co-manager)</span>}
                  </span>
                  {teamName && <span className="league-card-team">{teamName}</span>}
                  <span className="league-card-since">Since {since}</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>

        <section className="card">
          <h3 className="card-title">Badges</h3>
          {owner.badges.length === 0 ? (
            <p className="card-empty">No badges earned yet.</p>
          ) : (
            <ul className="badge-grid">
              {owner.badges.map((badge) => {
                const [mostRecent, ...older] = badge.earnings
                const tooltipId = `badge-tooltip-${badge.type}`
                const legendary = LEGENDARY_BADGES.has(badge.type)
                return (
                  <li key={badge.type} className={`badge-item${legendary ? ' badge-item-legendary' : ''}`}>
                    {legendary && <span className="badge-shimmer" aria-hidden="true" />}
                    <span className="badge-glyph" aria-hidden="true">
                      {BADGE_GLYPH[badge.type]}
                    </span>
                    <div className="badge-text">
                      <span className="badge-title">
                        {badge.title}
                        {older.length > 0 && <span className="badge-count">×{badge.earnings.length}</span>}
                        <button
                          type="button"
                          className="badge-info"
                          title={badge.description}
                          aria-label={`About ${badge.title}`}
                          aria-describedby={tooltipId}
                          aria-expanded={openBadgeInfo === badge.type}
                          onClick={() => setOpenBadgeInfo((prev) => (prev === badge.type ? null : badge.type))}
                        >
                          ?
                        </button>
                        <span
                          id={tooltipId}
                          role="tooltip"
                          className={`badge-tooltip${openBadgeInfo === badge.type ? ' badge-tooltip-open' : ''}`}
                        >
                          {badge.description}
                        </span>
                      </span>
                      {older.length === 0 ? (
                        <span className="badge-subtitle">{mostRecent.subtitle}</span>
                      ) : (
                        <details className="badge-earnings">
                          <summary>
                            <span className="badge-subtitle">{mostRecent.subtitle}</span>
                            <span className="badge-more">+{older.length} more</span>
                          </summary>
                          <ul className="badge-earnings-list">
                            {older.map((earning) => (
                              <li key={`${earning.leagueFamilyKey}-${earning.season}`}>{earning.subtitle}</li>
                            ))}
                          </ul>
                        </details>
                      )}
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </section>

        <section className="card">
          <h3 className="card-title">Scoring Trend</h3>
          {familiesLoading || familiesError ? (
            <LoadingStatus loading={familiesLoading} slow={familiesSlow} error={familiesError} retry={retryFamilies} subject="scoring history" />
          ) : scoringTrends.length === 0 ? (
            <p className="card-empty">No weekly scoring data yet.</p>
          ) : (
            <div className="trend-list">
              {scoringTrends.map((series) => (
                <div key={series.leagueFamilyKey} className="trend-series">
                  <div className="trend-series-title">
                    {series.leagueFamilyDisplayName} <span className="trend-series-season">{series.season}</span>
                  </div>
                  <ScoringTrendChart points={series.points} />
                </div>
              ))}
            </div>
          )}
        </section>

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
          {familiesLoading || familiesError ? (
            <LoadingStatus loading={familiesLoading} slow={familiesSlow} error={familiesError} retry={retryFamilies} subject="head-to-head history" />
          ) : !selectedRivalry ? (
            <p className="card-empty">No head-to-head matchups yet.</p>
          ) : (
            <RivalryCard owner={owner} rivalry={selectedRivalry} />
          )}
        </section>
      </div>
    </div>
  )
}

interface RivalryCardProps {
  owner: { displayName: string; avatarUrl: string | null }
  rivalry: HeadToHeadRecord
}

/** The Rivalry Tracker's "vs" detail for one selected opponent — see ManagerProfilePage. */
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
      <div className="rivalry-vs">
        <div className="rivalry-side">
          {owner.avatarUrl && <img src={owner.avatarUrl} alt="" className="avatar" />}
          <span className="rivalry-side-name">{owner.displayName}</span>
        </div>
        <span className="rivalry-score">
          {rivalry.wins}-{rivalry.losses}
          {rivalry.ties > 0 ? `-${rivalry.ties}` : ''}
        </span>
        <div className="rivalry-side">
          {rivalry.opponentAvatarUrl && <img src={rivalry.opponentAvatarUrl} alt="" className="avatar" />}
          <Link to={`/managers/${rivalry.opponentUserId}`} className="rivalry-side-name">
            {rivalry.opponentTeamName}
          </Link>
        </div>
      </div>
      <div className="rivalry-title">{descriptor.title}</div>
      <p className="rivalry-flavor">{descriptor.flavorText}</p>
      <div className="rivalry-stats">
        <span>
          {gamesPlayed} game{gamesPlayed === 1 ? '' : 's'} played · {winPct}% win rate
        </span>
        {lastMeeting && (
          <span>
            Last meeting: {lastMeetingResult} {lastMeeting.myScore.toFixed(2)}-{lastMeeting.theirScore.toFixed(2)} ·{' '}
            {lastMeeting.leagueFamilyDisplayName}, Week {lastMeeting.week} {lastMeeting.season}
          </span>
        )}
      </div>
    </div>
  )
}
