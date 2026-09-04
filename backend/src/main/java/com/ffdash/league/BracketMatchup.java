package com.ffdash.league;

/**
 * One matchup within a playoff bracket (the main winners_bracket, or the "toilet bowl"
 * losers_bracket), resolved from Sleeper's raw SleeperBracketMatchup into display-ready teams.
 *
 * @param round Sleeper's 1-based playoff round number.
 * @param matchupId Sleeper's id for this matchup, unique within its bracket — stable ordering
 *                  key within a round, not meaningful beyond that.
 * @param placement Sleeper's own raw placement marker for this matchup, present only on a
 *                   placement-deciding matchup. Structural only — use it to tell a placement
 *                   game apart from one that just advances teams onward (null), and to spot a
 *                   bracket's own final ({@code placement == 1}, always true in both bracket
 *                   types). Not the real final standing for the toilet/losers bracket — see
 *                   placementRank for that.
 * @param placementRank The real final standing this matchup's better-placed team achieves —
 *                       equal to {@code placement} for the winners bracket (Sleeper's own
 *                       numbering already ascends with real placement there), but recomputed for
 *                       the toilet/losers bracket, where advancing means finishing worse, not
 *                       better (see SeasonDataService.derivePlacementRanks). The other team
 *                       finishes one place worse. Meant for display ordering; null when
 *                       {@code placement} is null.
 * @param placementLabel Ready-to-display text for this matchup's placement pill ("Championship",
 *                        "3rd Place", "Toilet Bowl", ...), already accounting for the toilet
 *                        bracket's inverted numbering. Null when {@code placement} is null.
 * @param team1 Null when this slot isn't determined yet — it's fed by a later round of an
 *              earlier matchup that hasn't been played. Never null for a completed season.
 * @param team2 Same as team1.
 */
public record BracketMatchup(
        int round,
        int matchupId,
        Integer placement,
        Integer placementRank,
        String placementLabel,
        BracketTeam team1,
        BracketTeam team2
) {
}
