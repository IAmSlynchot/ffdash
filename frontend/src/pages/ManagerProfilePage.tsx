import { Link, useParams } from 'react-router-dom'
import { computeHeadToHead, computeLeagueMemberships, computeScoringTrends } from '../api/aggregations'
import { fetchFamilyHistory, fetchOwnerCareerSummaries } from '../api/leagues'
import { useApiData } from '../hooks/useApiData'
import BadgeGrid from '../components/BadgeGrid'
import LoadingStatus from '../components/LoadingStatus'
import RivalryTracker from '../components/RivalryTracker'
import ScoringTrendChart from '../components/ScoringTrendChart'

export default function ManagerProfilePage() {
  const { userId } = useParams<{ userId: string }>()

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

  // teamName stays null (and is simply not shown) until familyHistories loads — see
  // computeLeagueMemberships. displayName/since/coManagerOnly don't need it, so this card
  // doesn't wait on that fetch the way the Scoring Trend/Rivalry Tracker cards below do.
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

        <BadgeGrid badges={owner.badges} />

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
