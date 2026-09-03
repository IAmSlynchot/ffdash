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

export interface CoManager {
  userId: string
  displayName: string
  avatarUrl: string | null
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
  /** FANTASY: total points scored. PICKEM: the season-total Pick'em score (sum of weeklyScores' non-null values). */
  pointsFor: number
  pointsAgainst: number
  /** Pick'em only: whether this owner paid that season's buy-in and is eligible for prize money. Always false for FANTASY leagues. */
  boughtIn: boolean
  /**
   * PICKEM only: this team's score for each of SeasonSummary.pickemWeeks, same length/order —
   * index i here is week pickemWeeks[i]. null means no data for that week (joined late, or not
   * yet played), distinct from 0 (played, scored zero). Empty for FANTASY.
   */
  weeklyScores: (number | null)[]
  /** Other Sleeper users with edit access to this same roster, in addition to the primary owner. Usually empty. */
  coManagers: CoManager[]
}

export interface BracketTeam {
  ownerUserId: string | null
  teamName: string
  avatarUrl: string | null
  /** Whether this team won this specific matchup. Only meaningful once the matchup's been played. */
  winner: boolean
}

export interface BracketMatchup {
  round: number
  matchupId: number
  /** When set, this matchup decides a final standing: winner finishes here, loser finishes here + 1. */
  placement: number | null
  /** null when this slot isn't determined yet — fed by a later round of a matchup not yet played. */
  team1: BracketTeam | null
  team2: BracketTeam | null
}

export interface SeasonBracket {
  winnersBracket: BracketMatchup[]
  toiletBowlBracket: BracketMatchup[]
}

export interface SeasonSummary {
  leagueId: string
  season: string
  name: string
  status: string
  totalRosters: number
  teams: TeamSummary[]
  /** Pick'em only: the week numbers this season has weeklyScores columns for, ascending. Empty for FANTASY. */
  pickemWeeks: number[]
  /** FANTASY only: both brackets empty when there's nothing to show (Pick'em, or playoffs not yet started). */
  bracket: SeasonBracket
}

export type LeagueType = 'FANTASY' | 'PICKEM'

export interface LeagueFamilyHistory {
  key: string
  displayName: string
  type: LeagueType
  /** Newest season first. */
  seasons: SeasonSummary[]
}

export interface SeasonResult {
  leagueFamilyKey: string
  leagueFamilyDisplayName: string
  season: string
  status: string
  rank: number
  /** Whether this person held this season's team as a co-manager rather than as its primary owner. */
  coManager: boolean
}

export type BadgeType =
  | 'CHAMPION'
  | 'TOP_SCORER'
  | 'FOUNDING_MEMBER'
  | 'TOP_3'
  | 'TOILET_CHAMP'
  | 'PICKINATOR'
  | 'MICRO_MANAGER'
  | 'ADVERSITY_SPECIALIST'

export interface BadgeEarning {
  leagueFamilyKey: string
  season: string
  /** Pre-formatted, e.g. "The Depot League 2024". */
  subtitle: string
}

/** One badge type an owner has earned, consolidated: appears once even if earned in multiple league-years. */
export interface EarnedBadge {
  type: BadgeType
  title: string
  description: string
  /** Every league-year this badge was earned in, newest season first. */
  earnings: BadgeEarning[]
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
  /** Newest season first. An owner can earn the same badge type more than once, for different league-years. */
  badges: EarnedBadge[]
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
    coManagers: CoManager[]
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
        coManagers: team.coManagers,
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
        acc.coManagers = team.coManagers
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
      coManagers: acc.coManagers,
      rank: i + 1,
      wins: acc.wins,
      losses: acc.losses,
      ties: acc.ties,
      pointsFor: acc.pointsFor,
      pointsAgainst: acc.pointsAgainst,
      // Buy-ins are paid per season (see PickemProperties) — "competing for the pot" is inherently
      // a this-year question, so it's never shown on the combined "All" view.
      boughtIn: false,
      // A given week number means a different week in different years, so per-week scores can't
      // be meaningfully combined across seasons — "All" always falls back to Total-only display.
      weeklyScores: [],
    }))

  return {
    leagueId: 'all',
    season: 'All',
    name: history.displayName,
    status: 'combined',
    totalRosters: teams.length,
    teams,
    pickemWeeks: [],
    // A combined "All" view has no single season's playoffs to show a bracket for.
    bracket: { winnersBracket: [], toiletBowlBracket: [] },
  }
}
