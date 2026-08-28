package com.ffdash.league;

import java.util.List;

/** One league family's full multi-season history, newest season first. */
public record LeagueFamilyHistory(String key, String displayName, List<SeasonSummary> seasons) {
}
