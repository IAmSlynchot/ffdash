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
    transactionCount: number
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
        transactionCount: 0,
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
      acc.transactionCount += team.transactionCount

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
      transactionCount: acc.transactionCount,
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
    // Ditto for a week-by-week schedule — "All" stays season-total-only.
    weeklyMatchups: [],
  }
}

export interface ScoringTrendPoint {
  week: number
  score: number
}

export interface ScoringTrendSeries {
  leagueFamilyKey: string
  leagueFamilyDisplayName: string
  season: string
  points: ScoringTrendPoint[]
}

/**
 * This owner's own score per week, for the most recent season *with any weekly data* in each
 * FANTASY family they're in (histories are expected already scoped to just the families that
 * owner appears in — see ManagerProfilePage). Deliberately not just history.seasons[0] — the
 * newest season is often the current one still in progress with nothing played yet, in which
 * case this falls back to the newest season that actually has something to show. Matches purely
 * by MatchupSide.ownerUserId, the team's primary owner — Sleeper's matchup data is per-roster,
 * not per-manager, so a co-manager's own trend isn't separately attributed today (see
 * MatchupSide); a known gap, not a bug.
 */
export function computeScoringTrends(userId: string, histories: LeagueFamilyHistory[]): ScoringTrendSeries[] {
  const series: ScoringTrendSeries[] = []
  for (const history of histories) {
    if (history.type !== 'FANTASY') {
      continue
    }
    for (const season of history.seasons) {
      const points: ScoringTrendPoint[] = []
      for (const matchup of season.weeklyMatchups) {
        const side =
          matchup.team1.ownerUserId === userId ? matchup.team1 : matchup.team2.ownerUserId === userId ? matchup.team2 : null
        if (side) {
          points.push({ week: matchup.week, score: side.score })
        }
      }
      if (points.length > 0) {
        points.sort((a, b) => a.week - b.week)
        series.push({
          leagueFamilyKey: history.key,
          leagueFamilyDisplayName: history.displayName,
          season: season.season,
          points,
        })
        break // newest-with-data only, per this family — see javadoc above
      }
    }
  }
  return series
}

export interface LeagueMembership {
  key: string
  displayName: string
  type: LeagueType
  /** Whether this person held every one of their seasons in this league as a co-manager, never as primary owner. */
  coManagerOnly: boolean
  /** The season they first appeared in this league family, e.g. "2023". */
  since: string
  /**
   * This owner's team name as of their most recent season in this league — a per-season
   * nickname, so "most recent" is the only sensible single value to show here. Null for Pick'em
   * (no team names there, just usernames — see CLAUDE.md) or while `histories` doesn't have this
   * family loaded yet (see ManagerProfilePage, which fetches it separately from seasonResults).
   */
  teamName: string | null
}

/**
 * One row per league family this owner has ever played in, combining seasonResults (always
 * available, drives displayName/since/coManagerOnly) with each family's full history (fetched
 * separately by the caller — see ManagerProfilePage — only used here for teamName, so a family
 * missing from `histories` just leaves teamName null rather than failing).
 */
export function computeLeagueMemberships(
  userId: string,
  seasonResults: SeasonResult[],
  histories: LeagueFamilyHistory[],
): LeagueMembership[] {
  const historyByKey = new Map(histories.map((h) => [h.key, h]))
  const resultsByKey = new Map<string, SeasonResult[]>()
  for (const result of seasonResults) {
    const list = resultsByKey.get(result.leagueFamilyKey) ?? []
    list.push(result)
    resultsByKey.set(result.leagueFamilyKey, list)
  }

  return Array.from(resultsByKey.entries()).map(([key, results]) => {
    const since = results.reduce((earliest, r) => (r.season < earliest ? r.season : earliest), results[0].season)
    const coManagerOnly = results.every((r) => r.coManager)
    const history = historyByKey.get(key)

    let teamName: string | null = null
    if (history?.type === 'FANTASY') {
      for (const season of history.seasons) {
        const team = season.teams.find((t) => t.ownerUserId === userId || t.coManagers.some((c) => c.userId === userId))
        if (team) {
          teamName = team.teamName
          break // newest season this owner appears in, per history.seasons' newest-first order
        }
      }
    }

    return {
      key,
      displayName: results[0].leagueFamilyDisplayName,
      type: history?.type ?? 'FANTASY',
      coManagerOnly,
      since,
      teamName,
    }
  })
}

export interface HeadToHeadRecord {
  opponentUserId: string
  /** The opponent's most-recently-seen team name — a per-season nickname, same convention standings tables already use as the primary label. */
  opponentTeamName: string
  opponentAvatarUrl: string | null
  wins: number
  losses: number
  ties: number
}

/**
 * This owner's all-time head-to-head record against every specific opponent they've faced,
 * across every FANTASY season of every family passed in. Same primary-owner-only matching
 * caveat as computeScoringTrends.
 */
export function computeHeadToHead(userId: string, histories: LeagueFamilyHistory[]): HeadToHeadRecord[] {
  const byOpponent = new Map<string, HeadToHeadRecord>()

  for (const history of histories) {
    if (history.type !== 'FANTASY') {
      continue
    }
    for (const season of history.seasons) {
      for (const matchup of season.weeklyMatchups) {
        const mine =
          matchup.team1.ownerUserId === userId ? matchup.team1 : matchup.team2.ownerUserId === userId ? matchup.team2 : null
        if (!mine) {
          continue
        }
        const theirs = mine === matchup.team1 ? matchup.team2 : matchup.team1
        if (!theirs.ownerUserId) {
          continue
        }

        const record = byOpponent.get(theirs.ownerUserId) ?? {
          opponentUserId: theirs.ownerUserId,
          opponentTeamName: theirs.teamName,
          opponentAvatarUrl: theirs.avatarUrl,
          wins: 0,
          losses: 0,
          ties: 0,
        }
        // Keep the latest name/avatar seen for this opponent so the label stays current.
        record.opponentTeamName = theirs.teamName
        record.opponentAvatarUrl = theirs.avatarUrl

        if (mine.score > theirs.score) record.wins++
        else if (mine.score < theirs.score) record.losses++
        else record.ties++

        byOpponent.set(theirs.ownerUserId, record)
      }
    }
  }

  return Array.from(byOpponent.values()).sort((a, b) => b.wins - b.losses - (a.wins - a.losses))
}
