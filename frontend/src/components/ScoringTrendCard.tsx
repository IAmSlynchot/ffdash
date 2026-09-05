import { useState } from 'react'
import type { ScoringTrendSeries } from '../api/aggregations'
import LoadingStatus from './LoadingStatus'
import ScoringTrendChart from './ScoringTrendChart'

interface ScoringTrendCardProps {
  scoringTrends: ScoringTrendSeries[]
  loading: boolean
  error: string | null
  slow: boolean
  retry: () => void
}

/**
 * One chart visible at a time — a league-family tab row (only leagues with any weekly data get
 * a tab) plus a season dropdown scoped to whichever family is selected. Both selections are
 * plain overrides re-derived every render rather than reset via an effect: switching family tabs
 * naturally falls back to that family's own newest season whenever the previous manual season
 * pick doesn't exist there (and keeps it when it happens to, e.g. comparing the same year across
 * leagues) — same "derive, don't sync" pattern as WeeklySchedule's week picker.
 */
export default function ScoringTrendCard({ scoringTrends, loading, error, slow, retry }: ScoringTrendCardProps) {
  const [manualFamilyKey, setManualFamilyKey] = useState<string | null>(null)
  const [manualSeason, setManualSeason] = useState<string | null>(null)

  const seriesByFamily = new Map<string, ScoringTrendSeries[]>()
  for (const series of scoringTrends) {
    const list = seriesByFamily.get(series.leagueFamilyKey) ?? []
    list.push(series)
    seriesByFamily.set(series.leagueFamilyKey, list)
  }
  // Only families with at least one graphable season get a tab, in first-appearance order
  // (which follows familyHistories' own order — see ManagerProfilePage).
  const familyKeys = Array.from(seriesByFamily.keys())

  const selectedFamilyKey = manualFamilyKey !== null && seriesByFamily.has(manualFamilyKey) ? manualFamilyKey : familyKeys[0]
  const familySeries = selectedFamilyKey ? (seriesByFamily.get(selectedFamilyKey) ?? []) : []
  const seasonsDescending = [...familySeries].sort((a, b) => (a.season < b.season ? 1 : a.season > b.season ? -1 : 0))
  const selectedSeries = (manualSeason !== null ? familySeries.find((s) => s.season === manualSeason) : undefined) ?? seasonsDescending[0]
  // A season that hasn't started yet still contributes one point — this owner's live/not-yet-
  // played current week, merged into weeklyMatchups at 0 (see SeasonDataService) — which would
  // otherwise plot as a single meaningless dot. Every real played week has a nonzero score
  // (score === 0 across every point on record is otherwise vanishingly unlikely), so that's the
  // signal used to tell "no real data yet" apart from "genuinely scored".
  const hasRealData = selectedSeries?.points.some((p) => p.score !== 0) ?? false

  return (
    <section className="card">
      <h3 className="card-title">Scoring Trend</h3>
      {loading || error ? (
        <LoadingStatus loading={loading} slow={slow} error={error} retry={retry} subject="scoring history" />
      ) : scoringTrends.length === 0 ? (
        <p className="card-empty">No weekly scoring data yet.</p>
      ) : (
        <>
          <div className="trend-controls">
            <div className="trend-family-tabs">
              {familyKeys.map((key) => (
                <button
                  key={key}
                  type="button"
                  className={`trend-family-tab${key === selectedFamilyKey ? ' active' : ''}`}
                  onClick={() => setManualFamilyKey(key)}
                >
                  {seriesByFamily.get(key)?.[0].leagueFamilyDisplayName}
                </button>
              ))}
            </div>
            <select
              className="week-select"
              value={selectedSeries?.season ?? ''}
              onChange={(e) => setManualSeason(e.target.value)}
              aria-label="Season"
            >
              {seasonsDescending.map((series) => (
                <option key={series.season} value={series.season}>
                  {series.season}
                </option>
              ))}
            </select>
          </div>
          {selectedSeries && (
            <div className="trend-series" key={`${selectedSeries.leagueFamilyKey}-${selectedSeries.season}`}>
              {hasRealData ? (
                <ScoringTrendChart points={selectedSeries.points} />
              ) : (
                <p className="card-empty">No data for this season yet.</p>
              )}
            </div>
          )}
        </>
      )}
    </section>
  )
}
