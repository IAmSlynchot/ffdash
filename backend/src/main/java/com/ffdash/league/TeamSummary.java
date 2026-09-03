package com.ffdash.league;

/**
 * One team's standing within a season, ready for display.
 *
 * @param boughtIn Pick'em only: whether this owner paid that pool's optional
 *                 buy-in for this season (see PickemProperties) and is thus
 *                 eligible for prize money. Always false for FANTASY leagues,
 *                 where the concept doesn't apply.
 * @param playoffPlacement This team's final standing per that season's playoff/toilet-bowl
 *                          bracket (1 = champion), not the regular-season rank above. Null
 *                          when no bracket placement is known — no playoffs yet/at all for
 *                          this league (e.g. Pick'em, or a season still in progress).
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
        boolean boughtIn,
        Integer playoffPlacement
) {
}
