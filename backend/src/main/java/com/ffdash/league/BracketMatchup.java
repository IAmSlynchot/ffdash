package com.ffdash.league;

/**
 * One matchup within a playoff bracket (the main winners_bracket, or the "toilet bowl"
 * losers_bracket), resolved from Sleeper's raw SleeperBracketMatchup into display-ready teams.
 *
 * @param round Sleeper's 1-based playoff round number.
 * @param matchupId Sleeper's id for this matchup, unique within its bracket — stable ordering
 *                  key within a round, not meaningful beyond that.
 * @param placement When present, this matchup decides a final standing: its winner finishes in
 *                   place {@code placement}, its loser in {@code placement + 1} (e.g. 1 = the
 *                   championship game, 3 = the third-place game). Null for a matchup that just
 *                   advances winners/losers into a later round without settling a placement.
 * @param team1 Null when this slot isn't determined yet — it's fed by a later round of an
 *              earlier matchup that hasn't been played. Never null for a completed season.
 * @param team2 Same as team1.
 */
public record BracketMatchup(
        int round,
        int matchupId,
        Integer placement,
        BracketTeam team1,
        BracketTeam team2
) {
}
