package com.ffdash.league;

/**
 * One team's slot within a BracketMatchup, resolved from Sleeper's roster_id to display-ready
 * identity — same fields TeamSummary's owner section carries.
 *
 * @param winner Whether this team should be shown as the one who came out ahead in this specific
 *               matchup. Only ever true for one of a matchup's two teams, and only once that
 *               matchup has actually been played — false for both teams (not just unknown)
 *               while it's still pending. For almost every matchup this is literally who Sleeper
 *               recorded as the winner — except a toilet/losers bracket's own non-final
 *               placement games, where advancing (Sleeper's "winner") means finishing worse, so
 *               this is flipped to the team that actually earned the better real standing (see
 *               SeasonDataService). The bracket's own final is deliberately NOT flipped — the
 *               team Sleeper recorded as winning it is the one actually crowned, however dubious
 *               that "prize" is.
 */
public record BracketTeam(
        String ownerUserId,
        String teamName,
        String avatarUrl,
        boolean winner
) {
}
