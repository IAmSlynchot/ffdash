package com.ffdash.sleeper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of the fields returned by GET /league/{league_id}/rosters.
 * See https://docs.sleeper.com/#getting-rosters-in-a-league
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SleeperRoster(
        Integer roster_id,
        String owner_id,
        RosterSettings settings
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RosterSettings(
            Integer wins,
            Integer losses,
            Integer ties,
            Integer fpts,
            Integer fpts_decimal,
            Integer fpts_against,
            Integer fpts_against_decimal
    ) {
        public double pointsFor() {
            return combine(fpts, fpts_decimal);
        }

        public double pointsAgainst() {
            return combine(fpts_against, fpts_against_decimal);
        }

        private static double combine(Integer whole, Integer decimal) {
            double w = whole == null ? 0 : whole;
            double d = decimal == null ? 0 : decimal;
            return w + d / 100.0;
        }
    }
}
