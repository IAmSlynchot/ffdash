package com.ffdash.league;

/**
 * One team's standing within a season, ready for display.
 *
 * @param boughtIn Pick'em only: whether this owner paid that pool's optional
 *                 buy-in for this season (see PickemProperties) and is thus
 *                 eligible for prize money. Always false for FANTASY leagues,
 *                 where the concept doesn't apply.
 */
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
        double pointsAgainst,
        boolean boughtIn
) {
}
