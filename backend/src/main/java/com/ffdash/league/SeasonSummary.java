package com.ffdash.league;

import java.util.List;

/**
 * Aggregated, display-ready snapshot of one Sleeper league id — i.e. one season of a league family.
 *
 * @param pickemWeeks Pick'em only: the week numbers this season has weeklyScores columns for,
 *                     ascending (e.g. [1..18]) — gives every team's TeamSummary.weeklyScores an
 *                     unambiguous index-to-week mapping. Empty for FANTASY seasons.
 * @param bracket FANTASY only: this season's playoff brackets. See SeasonBracket for when it's
 *                empty (Pick'em, or playoffs not yet started).
 * @param weeklyMatchups FANTASY only: every week-by-week head-to-head matchup fetched so far
 *                        this season, oldest week first. Empty for Pick'em (no head-to-head
 *                        concept there) and before any week has fully concluded — see
 *                        SeasonDataService for the fetch/cache mechanism.
 */
public record SeasonSummary(
        String leagueId,
        String season,
        String name,
        String status,
        int totalRosters,
        List<TeamSummary> teams,
        List<Integer> pickemWeeks,
        SeasonBracket bracket,
        List<WeeklyMatchup> weeklyMatchups
) {
}
