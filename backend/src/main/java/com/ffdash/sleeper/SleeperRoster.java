package com.ffdash.sleeper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Subset of the fields returned by GET /league/{league_id}/rosters.
 * See https://docs.sleeper.com/#getting-rosters-in-a-league
 *
 * @param settings Null for a Pick'em roster (confirmed live) — Pick'em has no wins/losses/points
 *                 concept here at all; its real per-week scores live in metadata instead.
 * @param metadata Null for a fantasy roster, and also null for a Pick'em roster in a season that
 *                 hasn't started yet (confirmed live: every roster's metadata is null before week
 *                 1 is played). Never assume present.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SleeperRoster(
        Integer roster_id,
        String owner_id,
        RosterSettings settings,
        RosterMetadata metadata
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

    /** @param points_by_leg Pick'em only: this roster's score for each played week, keyed by the
     *                        same "v1:regular:{week}" strings as SleeperLeague.scoring_settings.
     *                        A week missing from this map means no data for it (not yet played,
     *                        or this owner joined the pool late) — distinct from a stored 0.0,
     *                        which means they played that week and scored zero. Can itself be
     *                        null even when metadata isn't. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RosterMetadata(
            Map<String, Double> points_by_leg
    ) {
    }
}
