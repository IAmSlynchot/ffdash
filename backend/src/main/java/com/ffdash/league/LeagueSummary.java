package com.ffdash.league;

import java.util.List;

/** Aggregated, display-ready snapshot of one Sleeper league. */
public record LeagueSummary(
        String id,
        String name,
        String season,
        String status,
        int totalRosters,
        List<TeamSummary> teams
) {
}
