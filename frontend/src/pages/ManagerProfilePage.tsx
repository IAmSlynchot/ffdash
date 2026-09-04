import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  computeHeadToHead,
  computeLeagueMemberships,
  computeScoringTrends,
  fetchFamilyHistory,
  fetchOwnerCareerSummaries,
  type EarnedBadge,
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

export default function ManagerProfilePage() {
  const { userId } = useParams<{ userId: string }>()

  // Badge descriptions are shown in a small tooltip. Hover/keyboard-focus reveals it via CSS
  // alone (see .badge-info:hover/:focus-visible in App.css), but neither fires on tap — mobile
  // needs an explicit open/close toggle instead, tracked here by badge type. Click-outside and
  // Escape both dismiss it, matching normal tooltip/popover expectations.
  const [openBadgeInfo, setOpenBadgeInfo] = useState<string | null>(null)

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

  if (loading || error || !owners) {
    return <LoadingStatus loading={loading} slow={slow} error={error} retry={retry} subject="manager" />
  }

  if (!owner) {
    return <p className="status-message">Manager not found.</p>
  }

  // teamName stays null (and is simply not shown) until familyHistories loads — see
  // computeLeagueMemberships. displayName/since/coManagerOnly don't need it, so this card
  // doesn't wait on that fetch the way the Scoring Trend/Head-to-Head cards below do.
  const leagues = computeLeagueMemberships(owner.userId, owner.seasonResults, familyHistories ?? [])

  const scoringTrends = familyHistories ? computeScoringTrends(owner.userId, familyHistories) : []
  const headToHead = familyHistories ? computeHeadToHead(owner.userId, familyHistories) : []

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
          <h3 className="card-title">Head-to-Head</h3>
          {familiesLoading || familiesError ? (
            <LoadingStatus loading={familiesLoading} slow={familiesSlow} error={familiesError} retry={retryFamilies} subject="head-to-head history" />
          ) : headToHead.length === 0 ? (
            <p className="card-empty">No head-to-head matchups yet.</p>
          ) : (
            <ul className="head-to-head-list">
              {headToHead.map((record) => (
                <li key={record.opponentUserId} className="head-to-head-row">
                  <Link to={`/managers/${record.opponentUserId}`} className="manager-link">
                    {record.opponentAvatarUrl && <img src={record.opponentAvatarUrl} alt="" className="avatar" />}
                    {record.opponentTeamName}
                  </Link>
                  <span className="head-to-head-record">
                    {record.wins}-{record.losses}
                    {record.ties > 0 ? `-${record.ties}` : ''}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}
