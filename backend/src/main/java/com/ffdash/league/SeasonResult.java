package com.ffdash.league;

/**
 * One owner's placement in one season of one league family — the building block of OwnerCareerSummary.
 *
 * @param coManager Whether this owner held this season's team as a co-manager (see
 *                   TeamSummary.coManagers) rather than as its primary owner. The rank/status
 *                   above still describe the team's actual result — a co-manager shares it.
 */
public record SeasonResult(
        String leagueFamilyKey,
        String leagueFamilyDisplayName,
        String season,
        String status,
        int rank,
        boolean coManager
) {
}
