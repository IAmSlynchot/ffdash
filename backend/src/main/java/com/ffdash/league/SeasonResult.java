package com.ffdash.league;

/** One owner's placement in one season of one league family — the building block of OwnerCareerSummary. */
public record SeasonResult(
        String leagueFamilyKey,
        String leagueFamilyDisplayName,
        String season,
        String status,
        int rank
) {
}
