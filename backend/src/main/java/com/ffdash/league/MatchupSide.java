package com.ffdash.league;

/** One team's side of a WeeklyMatchup — same identity shape used elsewhere (TeamSummary/BracketTeam), plus that week's score. */
public record MatchupSide(
        String ownerUserId,
        String teamName,
        String avatarUrl,
        double score
) {
}
