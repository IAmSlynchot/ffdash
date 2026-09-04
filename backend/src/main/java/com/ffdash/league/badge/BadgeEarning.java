package com.ffdash.league.badge;

/** One league-year in which an owner earned a particular EarnedBadge. */
public record BadgeEarning(
        String leagueFamilyKey,
        String season,
        /** Pre-formatted so the frontend does zero string assembly, e.g. "The Depot League 2024". */
        String subtitle
) {
}
