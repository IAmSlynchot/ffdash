package com.ffdash.league;

/** One team's standing within a league, ready for display. */
public record TeamSummary(
        String teamName,
        String avatarUrl,
        int wins,
        int losses,
        int ties,
        double pointsFor,
        double pointsAgainst
) {
}
