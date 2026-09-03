package com.ffdash.league;

/**
 * One team's slot within a BracketMatchup, resolved from Sleeper's roster_id to display-ready
 * identity — same fields TeamSummary's owner section carries.
 *
 * @param winner Whether this team won this specific matchup. Only ever true for one of a
 *               matchup's two teams, and only once that matchup has actually been played —
 *               false for both teams (not just unknown) while it's still pending.
 */
public record BracketTeam(
        String ownerUserId,
        String teamName,
        String avatarUrl,
        boolean winner
) {
}
