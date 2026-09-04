package com.ffdash.league.badge;

import com.ffdash.league.OwnerSeasonEntry;

import java.util.List;

/**
 * Everything a BadgeEvaluator might need to decide eligibility for one (owner, family,
 * season) entry. Most evaluators only touch entry()/seasonComplete(); ownerEntries()/
 * mostRecentEntry()/configuredLeagueCount() exist solely for TOTAL_DEGENERATE, a
 * lifetime-participation badge rather than a per-season one (see BadgeEligibility).
 */
public record BadgeContext(
        OwnerSeasonEntry entry,
        boolean seasonComplete,
        List<OwnerSeasonEntry> ownerEntries,
        OwnerSeasonEntry mostRecentEntry,
        int configuredLeagueCount
) {
}
