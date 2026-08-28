package com.ffdash.league;

/** One team's standing within a season, ready for display. */
public record TeamSummary(
        String ownerUserId,
        String ownerDisplayName,
        String teamName,
        String avatarUrl,
        int rank,
        int wins,
        int losses,
        int ties,
        double pointsFor,
        double pointsAgainst
) {
}
