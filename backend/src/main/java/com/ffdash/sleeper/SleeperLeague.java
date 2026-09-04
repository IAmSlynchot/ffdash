package com.ffdash.sleeper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Subset of the fields returned by GET /league/{league_id}.
 * See https://docs.sleeper.com/#get-a-single-league
 *
 * @param scoring_settings Generic numeric weights map — for a normal fantasy league, stat
 *                          abbreviations like "pass_td"; for a Pick'em pool, week keys like
 *                          "v1:regular:{week}" (present from day one of the season, even before
 *                          any week is played). SeasonDataService uses that key *pattern* to
 *                          detect a Pick'em-shaped season generically, without needing to know
 *                          about LeagueType — see SeasonDataService.detectPickemWeeks.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SleeperLeague(
        String league_id,
        String name,
        String season,
        String status,
        Integer total_rosters,
        Map<String, Double> scoring_settings,
        LeagueSettings settings
) {
    /**
     * @param last_scored_leg The last week whose games are fully final — confirmed live: absent
     *                        entirely (not zero) before any week has concluded, and equal to the
     *                        season's total week count once complete. SeasonDataService uses this
     *                        as the boundary for which weeks' matchup/transaction data are safe to
     *                        fetch and cache forever (immutable) versus not yet worth fetching at
     *                        all (still being played).
     * @param leg Sleeper's own notion of "the current week" — present (starting at 1) from
     *            preseason onward, distinct from last_scored_leg: it advances as soon as a new
     *            week starts, not only once one finishes. SeasonDataService surfaces this as
     *            SeasonSummary.currentWeek so the frontend can default a week picker to "now"
     *            on a live season even before that week has any scored results.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LeagueSettings(Integer last_scored_leg, Integer leg) {
    }
}
