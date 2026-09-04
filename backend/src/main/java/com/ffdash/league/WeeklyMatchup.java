package com.ffdash.league;

/**
 * One head-to-head matchup between two teams in a single week of a FANTASY season. Absent
 * entirely for Pick'em (no head-to-head concept there) and for weeks not yet finished — see
 * SeasonSummary.weeklyMatchups.
 */
public record WeeklyMatchup(
        int week,
        MatchupSide team1,
        MatchupSide team2
) {
}
