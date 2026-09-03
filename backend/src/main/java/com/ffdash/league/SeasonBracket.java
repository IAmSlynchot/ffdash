package com.ffdash.league;

import java.util.List;

/**
 * A season's playoff brackets, ready for display. Both lists are empty together whenever
 * there's nothing to show: no playoffs yet for this league (Pick'em), or this season's
 * playoffs haven't started (no matchup anywhere in either bracket has been played) — an early,
 * all-projected-seeding bracket isn't worth rendering. Once non-empty, a bracket can still be
 * partway through — see BracketMatchup.team1/team2 for how a not-yet-reached slot reads.
 */
public record SeasonBracket(
        List<BracketMatchup> winnersBracket,
        List<BracketMatchup> toiletBowlBracket
) {
    public static final SeasonBracket EMPTY = new SeasonBracket(List.of(), List.of());
}
