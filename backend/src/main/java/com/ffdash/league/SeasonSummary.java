package com.ffdash.league;

import java.util.List;

/**
 * Aggregated, display-ready snapshot of one Sleeper league id — i.e. one season of a league family.
 *
 * @param pickemWeeks Pick'em only: the week numbers this season has weeklyScores columns for,
 *                     ascending (e.g. [1..18]) — gives every team's TeamSummary.weeklyScores an
 *                     unambiguous index-to-week mapping. Empty for FANTASY seasons.
 */
public record SeasonSummary(
        String leagueId,
        String season,
        String name,
        String status,
        int totalRosters,
        List<TeamSummary> teams,
        List<Integer> pickemWeeks
) {
}
