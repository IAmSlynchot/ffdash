package com.ffdash.league;

/**
 * One team's standing within a season, ready for display.
 *
 * @param boughtIn Pick'em only: whether this owner paid that pool's optional
 *                 buy-in for this season (see PickemProperties) and is thus
 *                 eligible for prize money. Always false for FANTASY leagues,
 *                 where the concept doesn't apply.
 * @param playoffPlacement This team's final standing per that season's playoff bracket (1 =
 *                          champion), not the regular-season rank above. Null when no bracket
 *                          placement is known — no playoffs yet/at all for this league (e.g.
 *                          Pick'em, or a season still in progress).
 * @param toiletBowlChamp Whether this team won the "toilet bowl" (the playoff bracket's
 *                        consolation bracket) — a separate signal from playoffPlacement, since
 *                        "won the toilet bowl" doesn't correspond to any single number in that
 *                        ranking (it's a dubious-honor title for a team that was bad enough to
 *                        be in the consolation bracket at all, decided by that bracket's own
 *                        final game, independent of how the main bracket's placements are numbered).
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
        Integer playoffPlacement,
        boolean toiletBowlChamp
) {
}
