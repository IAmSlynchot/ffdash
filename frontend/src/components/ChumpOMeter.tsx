import { useEffect, useState } from 'react'
import type { CSSProperties } from 'react'
import type { MatchupSide, WeeklyMatchup } from '../api/leagues'

interface ChumpOMeterProps {
  matchups: WeeklyMatchup[]
}

/**
 * A racing-minimap-style trend bar for a live (in-progress or not-yet-started) week — every
 * team's avatar placed between "Chump" (red, left) and "Champ" (aquamarine, right) by where its
 * current score falls between this week's low and high. Only meaningful while the week's
 * outcome is still moving; see WeeklySchedule for the "is this the live week" gate.
 */
export default function ChumpOMeter({ matchups }: ChumpOMeterProps) {
  // Team-name tooltips work like BadgeGrid's: native `title` covers mouse hover for free, and
  // this open/close toggle (keyed per side) covers tap, since neither hover nor focus fires on
  // touch. Same outside-click/Escape dismissal pattern.
  const [openKey, setOpenKey] = useState<string | null>(null)

  useEffect(() => {
    if (!openKey) return

    function handlePointerDown(e: PointerEvent) {
      if (!(e.target instanceof Element) || !e.target.closest('.chump-o-meter-avatar')) {
        setOpenKey(null)
      }
    }
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpenKey(null)
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [openKey])

  const sides = matchups.flatMap((m) => [m.team1, m.team2])
  if (sides.length === 0) {
    return null
  }

  const scores = sides.map((s) => s.score)
  const min = Math.min(...scores)
  const max = Math.max(...scores)

  // Grouped by exact score — not proximity. Near-but-different scores are left to overlap
  // naturally (allowed per spec); only a genuine tie (every team at 0 before kickoff, most
  // commonly) gets the deliberate fan-out below.
  const byScore = new Map<number, MatchupSide[]>()
  for (const side of sides) {
    const group = byScore.get(side.score) ?? []
    group.push(side)
    byScore.set(side.score, group)
  }

  return (
    <div className="chump-o-meter">
      <h4 className="chump-o-meter-title">Chump-o-Meter</h4>
      <div className="chump-o-meter-track">
        {Array.from(byScore.entries()).flatMap(([score, group]) => {
          const position = max === min ? 50 : ((score - min) / (max - min)) * 100
          return group.map((side, i) => {
            // Fan tied teams out around their shared point in a small fixed-radius ring rather
            // than a stack that grows with group size — bounded regardless of how many teams
            // tie (worst case: everyone, at 0-0 before kickoff), so it always reads as one
            // compact cluster rather than a sprawling line. A lone side (the overwhelmingly
            // common case) gets no offset at all — it sits exactly on its real position.
            const angle = group.length > 1 ? (i / group.length) * 2 * Math.PI : 0
            const dx = group.length > 1 ? Math.cos(angle) * 9 : 0
            const dy = group.length > 1 ? Math.sin(angle) * 7 : 0
            const key = `${side.ownerUserId ?? side.teamName}-${score}`
            const isOpen = openKey === key
            const label = `${side.teamName} — ${side.score.toFixed(2)}`
            return (
              <button
                key={key}
                type="button"
                className="chump-o-meter-avatar"
                style={{
                  left: `${position}%`,
                  '--chump-o-meter-dx': `${dx}px`,
                  '--chump-o-meter-dy': `${dy}px`,
                  zIndex: i,
                } as CSSProperties}
                title={label}
                aria-label={label}
                aria-expanded={isOpen}
                onClick={() => setOpenKey((prev) => (prev === key ? null : key))}
              >
                {side.avatarUrl ? (
                  <img src={side.avatarUrl} alt="" />
                ) : (
                  <span className="chump-o-meter-avatar-fallback">{side.teamName.charAt(0)}</span>
                )}
                <span className={`chump-o-meter-tooltip${isOpen ? ' chump-o-meter-tooltip-open' : ''}`}>{label}</span>
              </button>
            )
          })
        })}
      </div>
      <div className="chump-o-meter-labels">
        <span className="chump-o-meter-label-chump">🚽 Chump</span>
        <span className="chump-o-meter-label-champ">Champ 🏆</span>
      </div>
    </div>
  )
}
