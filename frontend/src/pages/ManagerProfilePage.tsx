import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { computeHeadToHead, computeLeagueMemberships, computeScoringTrends } from '../api/aggregations'
import { fetchFamilyHistory, fetchOwnerCareerSummaries } from '../api/leagues'
import { useApiData } from '../hooks/useApiData'
import BadgeGrid from '../components/BadgeGrid'
import LoadingStatus from '../components/LoadingStatus'
import RivalryTracker from '../components/RivalryTracker'
import ScoringTrendCard from '../components/ScoringTrendCard'

export default function ManagerProfilePage() {
  const { userId } = useParams<{ userId: string }>()

  // An ever-incrementing/decrementing counter, not a bounds-checked index — currentIndex below
  // always derives the real, in-range position from it via modulo, so switching to a manager
  // with fewer leagues (this page isn't remounted per-manager) can't leave a stale out-of-range
  // index; same "derive, don't sync" pattern as WeeklySchedule's week picker.
  const [leagueCardStep, setLeagueCardStep] = useState(0)

  // Re-fetches the full list (rather than passing data via router state) so a
  // direct link or page reload works on its own — cheap given the backend's
  // own caching.
  const { data: owners, error, loading, slow, retry } = useApiData(fetchOwnerCareerSummaries, [])

  const owner = owners?.find((o) => o.userId === userId) ?? null

  // OwnerCareerSummary above deliberately omits weekly matchup detail (it's a cross-league
  // aggregate) — the scoring trend and rivalry tracker cards need the full per-family history for
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

  // displayName/since/coManagerOnly all come from seasonResults alone, so this card doesn't wait
  // on familyHistories the way the Scoring Trend/Rivalry Tracker cards below do.
  const leagues = computeLeagueMemberships(owner.userId, owner.seasonResults, familyHistories ?? [])
  const currentLeagueIndex = leagues.length === 0 ? 0 : ((leagueCardStep % leagues.length) + leagues.length) % leagues.length

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
          <div className="league-card-header">
            <h3 className="card-title">As seen in...</h3>
            {leagues.length > 1 && (
              <div className="league-card-nav">
                <button
                  type="button"
                  className="league-card-nav-button"
                  onClick={() => setLeagueCardStep((step) => step - 1)}
                  aria-label="Previous league"
                >
                  ‹
                </button>
                <span className="league-card-position">
                  {currentLeagueIndex + 1} / {leagues.length}
                </span>
                <button
                  type="button"
                  className="league-card-nav-button"
                  onClick={() => setLeagueCardStep((step) => step + 1)}
                  aria-label="Next league"
                >
                  ›
                </button>
              </div>
            )}
          </div>
          {leagues.length === 0 ? (
            <p className="card-empty">No leagues yet.</p>
          ) : (
            // All of them are always in the DOM — CSS decides how many show at once: only the
            // current one on narrow screens (the carousel), every one in a centered row once
            // there's enough width to not need the carousel at all (see .league-card-current /
            // the min-width: 640px block in App.css).
            <div className="league-card-list">
              {leagues.map((league, i) => (
                <Link
                  key={league.key}
                  to={`/leagues/${league.key}`}
                  className={`league-card${i === currentLeagueIndex ? ' league-card-current' : ''}`}
                >
                  <span className="league-card-name-row">
                    <span className="league-card-name">
                      {league.displayName}
                      {league.coManagerOnly && <span className="co-manager-tag"> (co-manager)</span>}
                    </span>
                    <span className="league-card-since">Since {league.since}</span>
                  </span>
                </Link>
              ))}
            </div>
          )}
        </section>

        <BadgeGrid badges={owner.badges} />

        <ScoringTrendCard
          scoringTrends={scoringTrends}
          loading={familiesLoading}
          error={familiesError}
          slow={familiesSlow}
          retry={retryFamilies}
        />

        <RivalryTracker
          owner={owner}
          headToHead={headToHead}
          loading={familiesLoading}
          error={familiesError}
          slow={familiesSlow}
          retry={retryFamilies}
        />
      </div>
    </div>
  )
}
