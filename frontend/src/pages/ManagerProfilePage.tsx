import { Link, useParams } from 'react-router-dom'
import { fetchOwnerCareerSummaries, type EarnedBadge } from '../api/leagues'
import { useApiData } from '../hooks/useApiData'
import LoadingStatus from '../components/LoadingStatus'

const BADGE_GLYPH: Record<EarnedBadge['type'], string> = {
  CHAMPION: '🏆',
  TOP_SCORER: '🔥',
  FOUNDING_MEMBER: '🌱',
  TOP_3: '🥉',
  TOILET_CHAMP: '🚽',
  PICKINATOR: '🎯',
  MICRO_MANAGER: '🔬',
  ADVERSITY_SPECIALIST: '🛡️',
}

export default function ManagerProfilePage() {
  const { userId } = useParams<{ userId: string }>()

  // Re-fetches the full list (rather than passing data via router state) so a
  // direct link or page reload works on its own — cheap given the backend's
  // own caching.
  const { data: owners, error, loading, slow, retry } = useApiData(fetchOwnerCareerSummaries, [])

  if (loading || error || !owners) {
    return <LoadingStatus loading={loading} slow={slow} error={error} retry={retry} subject="manager" />
  }

  const owner = owners.find((o) => o.userId === userId)
  if (!owner) {
    return <p className="status-message">Manager not found.</p>
  }

  // Leagues this owner is part of, deduped from their per-season results. Flagged as
  // co-managed only when every season they've had in that league was as a co-manager
  // (never the team's primary owner) — otherwise it's their own team, full stop.
  const leagues = Array.from(
    new Map(
      owner.seasonResults.map((r) => [r.leagueFamilyKey, r.leagueFamilyDisplayName] as const),
    ).entries(),
  ).map(([key, displayName]) => ({
    key,
    displayName,
    coManagerOnly: owner.seasonResults.filter((r) => r.leagueFamilyKey === key).every((r) => r.coManager),
  }))

  return (
    <div className="manager-profile">
      <header className="league-header manager-profile-header">
        {owner.avatarUrl && <img src={owner.avatarUrl} alt="" className="avatar avatar-lg" />}
        <h2>{owner.displayName}</h2>
      </header>

      <div className="profile-cards">
        <section className="card">
          <h3 className="card-title">Leagues</h3>
          <ul className="manager-league-list">
            {leagues.map(({ key, displayName, coManagerOnly }) => (
              <li key={key}>
                <Link to={`/leagues/${key}`}>{displayName}</Link>
                {coManagerOnly && <span className="co-manager-tag"> (co-manager)</span>}
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
                return (
                  <li key={badge.type} className="badge-item">
                    <span className="badge-glyph" aria-hidden="true">
                      {BADGE_GLYPH[badge.type]}
                    </span>
                    <div className="badge-text">
                      <span className="badge-title">
                        {badge.title}
                        {older.length > 0 && <span className="badge-count">×{badge.earnings.length}</span>}
                        <button type="button" className="badge-info" title={badge.description} aria-label={`About ${badge.title}`}>
                          ?
                        </button>
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
      </div>
    </div>
  )
}
