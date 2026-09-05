import { useEffect, useState } from 'react'
import type { ScoringTrendPoint } from '../api/aggregations'

const CHART_WIDTH = 600
const CHART_HEIGHT = 100
const PADDING = 12

interface ScoringTrendChartProps {
  points: ScoringTrendPoint[]
}

/** A minimal SVG line chart of one season's week-by-week scores — no charting library, just a
 * hand-plotted polyline scaled to a fixed viewBox (CSS stretches it responsively). Each point
 * gets an invisible HTML hit-target overlaid at its exact plotted position (in percent, so it
 * tracks the SVG's own responsive stretch) rather than relying on the browser's native SVG
 * <title> tooltip — that shows only on hover, only after its own built-in delay, and never on
 * tap; this shows instantly on hover and supports tap too. */
export default function ScoringTrendChart({ points }: ScoringTrendChartProps) {
  // Tap-to-toggle for touch (mirrors BadgeGrid/ChumpOMeter's identical pattern); hover/focus show
  // it instantly via CSS alone, no JS involved for that path.
  const [openIndex, setOpenIndex] = useState<number | null>(null)

  useEffect(() => {
    if (openIndex === null) return

    function handlePointerDown(e: PointerEvent) {
      if (!(e.target instanceof Element) || !e.target.closest('.trend-chart-point')) {
        setOpenIndex(null)
      }
    }
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpenIndex(null)
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [openIndex])

  if (points.length === 0) {
    return null
  }

  const scores = points.map((p) => p.score)
  const min = Math.min(...scores)
  const max = Math.max(...scores)
  const range = max - min || 1
  const innerWidth = CHART_WIDTH - PADDING * 2
  const innerHeight = CHART_HEIGHT - PADDING * 2

  const coords = points.map((point, i) => ({
    x: PADDING + (points.length === 1 ? innerWidth / 2 : (i / (points.length - 1)) * innerWidth),
    y: PADDING + innerHeight - ((point.score - min) / range) * innerHeight,
    point,
  }))

  const linePath = coords.map((c, i) => `${i === 0 ? 'M' : 'L'} ${c.x} ${c.y}`).join(' ')

  return (
    <div className="trend-chart-wrap">
      <svg className="trend-chart" viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} preserveAspectRatio="none" role="img">
        <path className="trend-chart-line" d={linePath} />
        {coords.map((c, i) => (
          <circle key={i} className="trend-chart-dot" cx={c.x} cy={c.y} r={3} />
        ))}
      </svg>
      <div className="trend-chart-points">
        {coords.map((c, i) => {
          const isOpen = openIndex === i
          const label = `Week ${c.point.week}: ${c.point.score.toFixed(2)}`
          return (
            <button
              key={i}
              type="button"
              className="trend-chart-point"
              style={{ left: `${(c.x / CHART_WIDTH) * 100}%`, top: `${(c.y / CHART_HEIGHT) * 100}%` }}
              aria-label={label}
              aria-expanded={isOpen}
              onClick={() => setOpenIndex((prev) => (prev === i ? null : i))}
            >
              <span className={`trend-chart-tooltip${isOpen ? ' trend-chart-tooltip-open' : ''}`}>{label}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
