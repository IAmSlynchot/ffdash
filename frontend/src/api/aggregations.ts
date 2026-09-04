// Client-side aggregation/combination logic over data already fetched via ./leagues — none of
// these functions touch the network. Split out from leagues.ts (which stays the HTTP boundary:
// DTO types + fetch functions only) since these have real algorithmic logic and their own
// edge-case handling worth keeping separate from the request/response plumbing.

import type { CoManager, LeagueFamilyHistory, LeagueType, SeasonResult, SeasonSummary, TeamSummary } from './leagues'

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
    currentWeek: null,
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

export interface HeadToHeadLastMeeting {
  leagueFamilyDisplayName: string
  season: string
  week: number
  myScore: number
  theirScore: number
}

export interface HeadToHeadRecord {
  opponentUserId: string
  /** The opponent's most-recently-seen team name — a per-season nickname, same convention standings tables already use as the primary label. */
  opponentTeamName: string
  opponentAvatarUrl: string | null
  wins: number
  losses: number
  ties: number
  /** The single most recent game against this opponent, across every FANTASY season/family passed in. */
  lastMeeting: HeadToHeadLastMeeting | null
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
          lastMeeting: null,
        }
        // Keep the latest name/avatar seen for this opponent so the label stays current.
        record.opponentTeamName = theirs.teamName
        record.opponentAvatarUrl = theirs.avatarUrl

        if (mine.score > theirs.score) record.wins++
        else if (mine.score < theirs.score) record.losses++
        else record.ties++

        // Iteration order alone can't be trusted for "most recent" — histories are
        // newest-season-first but different families interleave, so this needs an
        // explicit (season, week) comparison rather than just keeping the last one seen.
        const isNewer =
          !record.lastMeeting ||
          season.season > record.lastMeeting.season ||
          (season.season === record.lastMeeting.season && matchup.week > record.lastMeeting.week)
        if (isNewer) {
          record.lastMeeting = {
            leagueFamilyDisplayName: history.displayName,
            season: season.season,
            week: matchup.week,
            myScore: mine.score,
            theirScore: theirs.score,
          }
        }

        byOpponent.set(theirs.ownerUserId, record)
      }
    }
  }

  return Array.from(byOpponent.values()).sort((a, b) => b.wins - b.losses - (a.wins - a.losses))
}
