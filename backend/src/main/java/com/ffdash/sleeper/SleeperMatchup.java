package com.ffdash.sleeper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One roster's row within a single week, as returned by GET /league/{league_id}/matchups/{week}.
 * See https://docs.sleeper.com/#getting-user-s-matchups-in-a-league
 *
 * @param matchup_id Pairs two rosters that played each other that week — every roster that
 *                    played has one, shared with exactly one other roster. A roster on a bye
 *                    that week is either absent from the response or carries a matchup_id no
 *                    other roster shares; either way it can't be paired, so SeasonDataService
 *                    skips it rather than guessing an opponent.
 * @param points Null (not the more common 0.0) when this roster hasn't been scored for the week
 *               yet — Sleeper docs note this can happen briefly during live scoring.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SleeperMatchup(
        Integer roster_id,
        Integer matchup_id,
        Double points
) {
}
