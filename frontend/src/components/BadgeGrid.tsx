import { useEffect, useState } from 'react'
import type { EarnedBadge } from '../api/leagues'

interface BadgeGridProps {
  badges: EarnedBadge[]
}

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

export default function BadgeGrid({ badges }: BadgeGridProps) {
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

  return (
    <section className="card">
      <h3 className="card-title">Badges</h3>
      {badges.length === 0 ? (
        <p className="card-empty">No badges earned yet.</p>
      ) : (
        <ul className="badge-grid">
          {badges.map((badge) => {
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
  )
}
