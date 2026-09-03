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
        Map<String, Double> scoring_settings
) {
}
