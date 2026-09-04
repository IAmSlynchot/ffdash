package com.ffdash.league;

import com.ffdash.league.badge.EarnedBadge;

import java.util.List;

/**
 * One Sleeper user's aggregated standing across every configured league family/season.
 *
 * combined* fields sum only FANTASY-type families (any status — an in-progress season's
 * current record counts). topThreeFinishes counts rank <= 3 across ALL families, including
 * PICKEM, but only for seasons with status "complete" (a final placement, not a
 * mid-season snapshot).
 */
public record OwnerCareerSummary(
        String userId,
        String displayName,
        String avatarUrl,
        int combinedWins,
        int combinedLosses,
        int combinedTies,
        double combinedPointsFor,
        double combinedPointsAgainst,
        int topThreeFinishes,
        List<SeasonResult> seasonResults,
        /** Achievement badges this owner has earned, newest season first. See BadgeType/EarnedBadge. */
        List<EarnedBadge> badges
) {
}
