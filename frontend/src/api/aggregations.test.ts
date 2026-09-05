import { describe, expect, it } from 'vitest'
import { aggregateAllSeasons, computeHeadToHead, computeLeagueMemberships, computeScoringTrends } from './aggregations'
import type { LeagueFamilyHistory, MatchupSide, SeasonResult, SeasonSummary, TeamSummary, WeeklyMatchup } from './leagues'

// ---- fixture builders ----

function team(overrides: Partial<TeamSummary> & { ownerUserId: string; teamName: string }): TeamSummary {
  return {
    ownerDisplayName: overrides.ownerUserId,
    avatarUrl: null,
    rank: 1,
    wins: 0,
    losses: 0,
    ties: 0,
    pointsFor: 0,
    pointsAgainst: 0,
    boughtIn: false,
    weeklyScores: [],
    coManagers: [],
    transactionCount: 0,
    ...overrides,
  }
}

function season(overrides: Partial<SeasonSummary> & { season: string; teams: TeamSummary[] }): SeasonSummary {
  return {
    leagueId: `league-${overrides.season}`,
    name: 'Test League',
    status: 'complete',
    totalRosters: overrides.teams.length,
    pickemWeeks: [],
    bracket: { winnersBracket: [], toiletBowlBracket: [] },
    weeklyMatchups: [],
    currentWeek: null,
    ...overrides,
  }
}

function family(overrides: Partial<LeagueFamilyHistory> & { key: string; seasons: SeasonSummary[] }): LeagueFamilyHistory {
  return {
    displayName: overrides.key,
    type: 'FANTASY',
    ...overrides,
  }
}

function side(ownerUserId: string, teamName: string, score: number): MatchupSide {
  return { ownerUserId, teamName, avatarUrl: null, score }
}

function matchup(week: number, a: MatchupSide, b: MatchupSide): WeeklyMatchup {
  return { week, team1: a, team2: b }
}

// ---- aggregateAllSeasons ----

describe('aggregateAllSeasons', () => {
  it('sums wins/losses/points across seasons and uses the newest season for name/avatar', () => {
    const s2023 = season({
      season: '2023',
      teams: [team({ ownerUserId: 'u1', teamName: 'Old Name', wins: 5, losses: 5, pointsFor: 1000, pointsAgainst: 1100 })],
    })
    const s2024 = season({
      season: '2024',
      teams: [team({ ownerUserId: 'u1', teamName: 'New Name', wins: 8, losses: 2, pointsFor: 1300, pointsAgainst: 1200 })],
    })
    const history = family({ key: 'depot', seasons: [s2024, s2023] }) // newest first, matching real fetch order

    const result = aggregateAllSeasons(history)

    expect(result.teams).toHaveLength(1)
    const combined = result.teams[0]
    expect(combined.teamName).toBe('New Name') // newest season's name wins
    expect(combined.wins).toBe(13)
    expect(combined.losses).toBe(7)
    expect(combined.pointsFor).toBe(2300)
    expect(combined.pointsAgainst).toBe(2300)
  })

  it('ranks combined teams by wins then points, and marks the result as a synthetic "All" season', () => {
    const s = season({
      season: '2024',
      teams: [
        team({ ownerUserId: 'u1', teamName: 'Underdog', wins: 5, losses: 9, pointsFor: 1000 }),
        team({ ownerUserId: 'u2', teamName: 'TopDog', wins: 12, losses: 2, pointsFor: 1800 }),
      ],
    })
    const result = aggregateAllSeasons(family({ key: 'depot', seasons: [s] }))

    expect(result.season).toBe('All')
    expect(result.teams.map((t) => t.teamName)).toEqual(['TopDog', 'Underdog'])
    expect(result.teams[0].rank).toBe(1)
    expect(result.teams[1].rank).toBe(2)
    expect(result.bracket).toEqual({ winnersBracket: [], toiletBowlBracket: [] })
    expect(result.weeklyMatchups).toEqual([])
  })
})

// ---- computeScoringTrends ----

describe('computeScoringTrends', () => {
  it('returns one entry per season that actually has weekly data, skipping seasons with none', () => {
    const inProgress2025 = season({ season: '2025', teams: [], weeklyMatchups: [] }) // nothing played yet
    const complete2024 = season({
      season: '2024',
      teams: [],
      weeklyMatchups: [matchup(1, side('u1', 'Me', 100), side('u2', 'Them', 90))],
    })
    const complete2023 = season({
      season: '2023',
      teams: [],
      weeklyMatchups: [matchup(1, side('u1', 'Me', 80), side('u2', 'Them', 70))],
    })
    const history = family({ key: 'depot', seasons: [inProgress2025, complete2024, complete2023] })

    const trends = computeScoringTrends('u1', [history])

    // Both seasons with data show up, not just the newest — the caller (ManagerProfilePage's
    // family tabs + season select) decides which one to display.
    expect(trends.map((t) => t.season)).toEqual(['2024', '2023'])
    expect(trends[0].points).toEqual([{ week: 1, score: 100 }])
  })

  it('includes PICKEM families, reading weeklyScores against pickemWeeks instead of weeklyMatchups', () => {
    const pickemSeason = season({
      season: '2024',
      teams: [team({ ownerUserId: 'u1', teamName: 'Me', weeklyScores: [10, null, 20] })],
      pickemWeeks: [1, 2, 3],
    })
    const pickemHistory = family({ key: 'pickem', type: 'PICKEM', seasons: [pickemSeason] })

    const trends = computeScoringTrends('u1', [pickemHistory])

    // Week 2 (index 1) is null — no data that week, distinct from a real 0 — so it's skipped.
    expect(trends).toHaveLength(1)
    expect(trends[0].points).toEqual([
      { week: 1, score: 10 },
      { week: 3, score: 20 },
    ])
  })

  it('sorts points by week ascending regardless of matchup order', () => {
    const s = season({
      season: '2024',
      teams: [],
      weeklyMatchups: [matchup(3, side('u1', 'Me', 130), side('u2', 'Them', 90)), matchup(1, side('u1', 'Me', 100), side('u2', 'Them', 90))],
    })

    const trends = computeScoringTrends('u1', [family({ key: 'depot', seasons: [s] })])

    expect(trends[0].points.map((p) => p.week)).toEqual([1, 3])
  })
})

// ---- computeLeagueMemberships ----

describe('computeLeagueMemberships', () => {
  const seasonResults: SeasonResult[] = [
    { leagueFamilyKey: 'depot', leagueFamilyDisplayName: 'The Depot League', season: '2025', status: 'in_season', rank: 4, coManager: false },
    { leagueFamilyKey: 'depot', leagueFamilyDisplayName: 'The Depot League', season: '2024', status: 'complete', rank: 6, coManager: false },
    { leagueFamilyKey: 'depot', leagueFamilyDisplayName: 'The Depot League', season: '2023', status: 'complete', rank: 8, coManager: false },
  ]

  it('derives "since" as the earliest season and teamName from the most recent season with this owner on a roster', () => {
    const s2025 = season({ season: '2025', teams: [team({ ownerUserId: 'u1', teamName: 'Newest Name' })] })
    const s2023 = season({ season: '2023', teams: [team({ ownerUserId: 'u1', teamName: 'Oldest Name' })] })
    const history = family({ key: 'depot', displayName: 'The Depot League', seasons: [s2025, s2023] })

    const [membership] = computeLeagueMemberships('u1', seasonResults, [history])

    expect(membership.since).toBe('2023')
    expect(membership.teamName).toBe('Newest Name')
    expect(membership.coManagerOnly).toBe(false)
  })

  it('leaves teamName null for Pick\'em and when the family history has not loaded yet', () => {
    const pickemResults: SeasonResult[] = [
      { leagueFamilyKey: 'pickem', leagueFamilyDisplayName: 'Pick Six(teen)', season: '2024', status: 'complete', rank: 2, coManager: false },
    ]
    const pickemHistory = family({
      key: 'pickem',
      type: 'PICKEM',
      seasons: [season({ season: '2024', teams: [team({ ownerUserId: 'u1', teamName: 'Irrelevant' })] })],
    })

    const [withHistory] = computeLeagueMemberships('u1', pickemResults, [pickemHistory])
    expect(withHistory.teamName).toBeNull()

    const [withoutHistory] = computeLeagueMemberships('u1', pickemResults, [])
    expect(withoutHistory.teamName).toBeNull()
    expect(withoutHistory.since).toBe('2024')
  })

  it('finds the team via coManagers when the owner is a co-manager, not the primary owner', () => {
    const coManagerResults: SeasonResult[] = [
      { leagueFamilyKey: 'depot', leagueFamilyDisplayName: 'The Depot League', season: '2024', status: 'complete', rank: 3, coManager: true },
    ]
    const s = season({
      season: '2024',
      teams: [
        team({
          ownerUserId: 'primary-owner',
          teamName: 'Shared Team',
          coManagers: [{ userId: 'u1', displayName: 'u1', avatarUrl: null }],
        }),
      ],
    })
    const history = family({ key: 'depot', seasons: [s] })

    const [membership] = computeLeagueMemberships('u1', coManagerResults, [history])

    expect(membership.teamName).toBe('Shared Team')
    expect(membership.coManagerOnly).toBe(true)
  })
})

// ---- computeHeadToHead ----

describe('computeHeadToHead', () => {
  it('tallies wins/losses/ties from the viewed owner\'s perspective', () => {
    const s = season({
      season: '2024',
      teams: [],
      weeklyMatchups: [
        matchup(1, side('u1', 'Me', 120), side('u2', 'Them', 100)), // win
        matchup(2, side('u2', 'Them', 110), side('u1', 'Me', 90)), // loss (order flipped)
        matchup(3, side('u1', 'Me', 100), side('u2', 'Them', 100)), // tie
      ],
    })

    const [record] = computeHeadToHead('u1', [family({ key: 'depot', seasons: [s] })])

    expect(record.wins).toBe(1)
    expect(record.losses).toBe(1)
    expect(record.ties).toBe(1)
  })

  it('tracks lastMeeting via explicit (season, week) comparison, not iteration/insertion order', () => {
    // Newest-season-first order (as real fetches return), so naive "last one wins" would
    // incorrectly pick 2023's week 1 over 2024's actual most recent game.
    const s2024 = season({
      season: '2024',
      teams: [],
      weeklyMatchups: [matchup(5, side('u1', 'Me', 150), side('u2', 'Them', 140))],
    })
    const s2023 = season({
      season: '2023',
      teams: [],
      weeklyMatchups: [matchup(1, side('u1', 'Me', 80), side('u2', 'Them', 200))],
    })
    const history = family({ key: 'depot', seasons: [s2024, s2023] })

    const [record] = computeHeadToHead('u1', [history])

    expect(record.lastMeeting).toEqual({
      leagueFamilyDisplayName: 'depot',
      season: '2024',
      week: 5,
      myScore: 150,
      theirScore: 140,
    })
  })

  it('picks the later week within the same season correctly', () => {
    const s = season({
      season: '2024',
      teams: [],
      weeklyMatchups: [
        matchup(10, side('u1', 'Me', 100), side('u2', 'Them', 90)),
        matchup(3, side('u1', 'Me', 80), side('u2', 'Them', 70)),
      ],
    })

    const [record] = computeHeadToHead('u1', [family({ key: 'depot', seasons: [s] })])

    expect(record.lastMeeting?.week).toBe(10)
  })

  it('sorts by win differential (wins - losses) descending', () => {
    const s = season({
      season: '2024',
      teams: [],
      weeklyMatchups: [
        matchup(1, side('u1', 'Me', 100), side('rival-close', 'Close Rival', 90)),
        matchup(2, side('u1', 'Me', 100), side('rival-close', 'Close Rival', 110)),
        matchup(3, side('u1', 'Me', 120), side('rival-dominated', 'Dominated', 80)),
        matchup(4, side('u1', 'Me', 120), side('rival-dominated', 'Dominated', 80)),
      ],
    })

    const records = computeHeadToHead('u1', [family({ key: 'depot', seasons: [s] })])

    expect(records.map((r) => r.opponentUserId)).toEqual(['rival-dominated', 'rival-close'])
  })
})
