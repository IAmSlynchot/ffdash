package com.ffdash.sleeper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of the fields returned by GET /league/{league_id}.
 * See https://docs.sleeper.com/#get-a-single-league
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SleeperLeague(
        String league_id,
        String name,
        String season,
        String status,
        Integer total_rosters
) {
}
