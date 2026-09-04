import type { ScoringTrendPoint } from '../api/aggregations'

const CHART_WIDTH = 600
const CHART_HEIGHT = 100
const PADDING = 12

interface ScoringTrendChartProps {
  points: ScoringTrendPoint[]
}

/** A minimal SVG line chart of one season's week-by-week scores — no charting library, just a
 * hand-plotted polyline scaled to a fixed viewBox (CSS stretches it responsively). */
export default function ScoringTrendChart({ points }: ScoringTrendChartProps) {
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
    <svg className="trend-chart" viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} preserveAspectRatio="none" role="img">
      <path className="trend-chart-line" d={linePath} />
      {coords.map((c, i) => (
        <circle key={i} className="trend-chart-dot" cx={c.x} cy={c.y} r={3}>
          <title>{`Week ${c.point.week}: ${c.point.score.toFixed(2)}`}</title>
        </circle>
      ))}
    </svg>
  )
}
