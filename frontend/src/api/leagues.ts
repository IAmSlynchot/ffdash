// Types mirror the backend's DTOs (see backend/src/main/java/com/ffdash/league).
// A "league family" is a logical league across years (e.g. "The Depot League"),
// spanning multiple Sleeper league ids, one per season.
//
// This file is the HTTP boundary only — DTO types plus the fetch functions that call the
// backend. Client-side aggregation/combination logic (aggregateAllSeasons, computeScoringTrends,
// computeLeagueMemberships, computeHeadToHead) lives in ./aggregations instead — see there.

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
  /** FANTASY only: total completed roster transactions (waivers, free agent adds, trades) across every week fetched so far. Always 0 for Pick'em. */
  transactionCount: number
}

export interface MatchupSide {
  ownerUserId: string | null
  teamName: string
  avatarUrl: string | null
  score: number
}

export interface WeeklyMatchup {
  week: number
  team1: MatchupSide
  team2: MatchupSide
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
  /** Structural only — non-null marks a placement-deciding matchup, and 1 always marks a bracket's own final. Not a real standing for the toilet bracket; see placementRank/placementLabel for that. */
  placement: number | null
  /** The real final standing this matchup's better-placed team achieves (the other team finishes one worse). For display ordering. Null when placement is null. */
  placementRank: number | null
  /** Ready-to-display pill text ("Championship", "3rd Place", "Toilet Bowl", ...). Null when placement is null. */
  placementLabel: string | null
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
  /** FANTASY only: every week-by-week matchup fetched so far this season, oldest week first. Empty for Pick'em or before any week has concluded. */
  weeklyMatchups: WeeklyMatchup[]
  /**
   * FANTASY only: Sleeper's live "current week", null for Pick'em. Distinct from the last week
   * in weeklyMatchups — this can point at a week with no scored results yet, which is what lets
   * WeeklySchedule default a live season's week picker to "now" rather than the last *completed*
   * week.
   */
  currentWeek: number | null
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
  | 'OVERCONFIDENT'
  | 'TOTAL_DEGENERATE'
  | 'MR_BOOMBASTIC'
  | 'CHUMP_YEAR'

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
