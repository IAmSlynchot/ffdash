package com.ffdash.league;

import com.ffdash.config.LeaguesProperties.LeagueType;

/** Which league type(s) a BadgeType can be earned in — see BadgeType.scope(). */
public enum BadgeScope {
    FANTASY, PICKEM, ALL;

    public boolean appliesTo(LeagueType leagueType) {
        return this == ALL || name().equals(leagueType.name());
    }
}
