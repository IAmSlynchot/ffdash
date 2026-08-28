// Types mirror the backend's DTOs (see backend/src/main/java/com/ffdash/league).
// A "league family" is a logical league across years (e.g. "The Depot League"),
// spanning multiple Sleeper league ids, one per season.

// In local dev this is unset, so calls go to a relative /api/... path that
// Vite's dev server proxies to the backend (see vite.config.ts). In
// production (a statically hosted build), set VITE_API_BASE_URL at build
// time to the backend's deployed origin, e.g. https://ffdash-backend.onrender.com
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export interface LeagueFamilyRef {
  key: string
  displayName: string
}

export interface TeamSummary {
  ownerUserId: string | null
  /** The owner's stable Sleeper username — distinct from teamName, which is a per-season nickname. */
  ownerDisplayName: string | null
  teamName: string
  avatarUrl: string | null
  rank: number
  wins: number
  losses: number
  ties: number
  pointsFor: number
  pointsAgainst: number
}

export interface SeasonSummary {
  leagueId: string
  season: string
  name: string
  status: string
  totalRosters: number
  teams: TeamSummary[]
}

export interface LeagueFamilyHistory {
  key: string
  displayName: string
  /** Newest season first. */
  seasons: SeasonSummary[]
}

export interface SeasonResult {
  leagueFamilyKey: string
  leagueFamilyDisplayName: string
  season: string
  status: string
  rank: number
}

export interface OwnerCareerSummary {
  userId: string
  displayName: string
  avatarUrl: string | null
  combinedWins: number
  combinedLosses: number
  combinedTies: number
  combinedPointsFor: number
  combinedPointsAgainst: number
  topThreeFinishes: number
  /** Newest season first. */
  seasonResults: SeasonResult[]
}

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`Request to ${url} failed: ${response.status} ${response.statusText}`)
  }
  return (await response.json()) as T
}

export function fetchLeagueFamilies(): Promise<LeagueFamilyRef[]> {
  return fetchJson(`${API_BASE_URL}/api/leagues`)
}

export function fetchFamilyHistory(key: string): Promise<LeagueFamilyHistory> {
  return fetchJson(`${API_BASE_URL}/api/leagues/${key}`)
}

export function fetchOwnerCareerSummaries(): Promise<OwnerCareerSummary[]> {
  return fetchJson(`${API_BASE_URL}/api/owners`)
}

/**
 * Combines a league family's own seasons into one all-time standings table,
 * grouped by owner. Mirrors the backend's OwnerCareerSummary combining logic
 * (see LeagueService.toOwnerCareerSummary), but scoped to just this one
 * family rather than every configured league — computed client-side since
 * fetchFamilyHistory already returns every season in one call.
 */
export function aggregateAllSeasons(history: LeagueFamilyHistory): SeasonSummary {
  interface Accumulator {
    ownerUserId: string | null
    ownerDisplayName: string | null
    teamName: string
    avatarUrl: string | null
    latestSeason: string
    wins: number
    losses: number
    ties: number
    pointsFor: number
    pointsAgainst: number
  }

  const byOwner = new Map<string, Accumulator>()

  for (const season of history.seasons) {
    for (const team of season.teams) {
      const ownerKey = team.ownerUserId ?? team.teamName
      const existing = byOwner.get(ownerKey)
      const isNewer = !existing || season.season > existing.latestSeason

      const acc: Accumulator = existing ?? {
        ownerUserId: team.ownerUserId,
        ownerDisplayName: team.ownerDisplayName,
        teamName: team.teamName,
        avatarUrl: team.avatarUrl,
        latestSeason: season.season,
        wins: 0,
        losses: 0,
        ties: 0,
        pointsFor: 0,
        pointsAgainst: 0,
      }

      if (isNewer) {
        acc.ownerDisplayName = team.ownerDisplayName
        acc.teamName = team.teamName
        acc.avatarUrl = team.avatarUrl
        acc.latestSeason = season.season
      }

      acc.wins += team.wins
      acc.losses += team.losses
      acc.ties += team.ties
      acc.pointsFor += team.pointsFor
      acc.pointsAgainst += team.pointsAgainst

      byOwner.set(ownerKey, acc)
    }
  }

  const teams: TeamSummary[] = Array.from(byOwner.values())
    .sort((a, b) => b.wins - a.wins || b.pointsFor - a.pointsFor)
    .map((acc, i) => ({
      ownerUserId: acc.ownerUserId,
      ownerDisplayName: acc.ownerDisplayName,
      teamName: acc.teamName,
      avatarUrl: acc.avatarUrl,
      rank: i + 1,
      wins: acc.wins,
      losses: acc.losses,
      ties: acc.ties,
      pointsFor: acc.pointsFor,
      pointsAgainst: acc.pointsAgainst,
    }))

  return {
    leagueId: 'all',
    season: 'All',
    name: history.displayName,
    status: 'combined',
    totalRosters: teams.length,
    teams,
  }
}
