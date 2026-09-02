package com.ffdash.league;

import com.ffdash.config.LeaguesProperties.LeagueType;

import java.util.List;

/** One league family's full multi-season history, newest season first. */
public record LeagueFamilyHistory(String key, String displayName, LeagueType type, List<SeasonSummary> seasons) {
}
