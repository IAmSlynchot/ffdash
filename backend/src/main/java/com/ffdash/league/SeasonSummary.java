package com.ffdash.league;

import java.util.List;

/** Aggregated, display-ready snapshot of one Sleeper league id — i.e. one season of a league family. */
public record SeasonSummary(
        String leagueId,
        String season,
        String name,
        String status,
        int totalRosters,
        List<TeamSummary> teams
) {
}
