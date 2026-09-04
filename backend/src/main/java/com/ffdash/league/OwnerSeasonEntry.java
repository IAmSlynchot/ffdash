package com.ffdash.league;

import com.ffdash.config.LeaguesProperties.LeagueFamilyConfig;

/**
 * One (family, season, team, person) tuple — team/season carry that team's actual result,
 * manager identifies which person this entry is for. A team with a co-manager produces two
 * entries, one per person, both pointing at the same team/season so both get credit for its
 * result; manager is what tells them apart (and is who owns this entry once grouped by
 * userId in LeagueService.getOwnerCareerSummaries). Public (rather than nested/private in
 * LeagueService, as it originally was) so league.badge's BadgeEligibility can evaluate it.
 */
public record OwnerSeasonEntry(LeagueFamilyConfig family, SeasonSummary season, TeamSummary team, ManagerIdentity manager) {
}
