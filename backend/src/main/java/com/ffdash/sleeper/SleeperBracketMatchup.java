package com.ffdash.sleeper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One matchup in a playoff bracket, as returned by
 * GET /league/{league_id}/winners_bracket and .../losers_bracket.
 * See https://docs.sleeper.com/#getting-the-playoff-bracket
 *
 * {@code p}, when present, marks this as a placement-deciding matchup: the
 * winner (w) finishes in place p, the loser (l) in place p+1. Most matchups
 * (earlier playoff rounds) have no {@code p} — only the games that actually
 * settle a final standing do.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SleeperBracketMatchup(
        Integer r,
        Integer m,
        Integer t1,
        Integer t2,
        Integer w,
        Integer l,
        Integer p
) {
}
