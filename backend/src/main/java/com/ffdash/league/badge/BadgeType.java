package com.ffdash.league.badge;

/**
 * Statically defined achievement types — titles/descriptions never vary per
 * season. The per-earning context (which league-year it was earned in) lives
 * on EarnedBadge, not here; see BadgeEligibility for eligibility rules.
 * scope() gates which league type(s) (see LeaguesProperties.LeagueType) a
 * badge can even be earned in.
 */
public enum BadgeType {
    CHAMPION("Ultimate Championator", BadgeScope.FANTASY, "Won the league's playoff bracket."),
    PICKINATOR("Ultimate Pickinator", BadgeScope.PICKEM, "Finished a Pick'em season in first place"),
    TOP_3("Top 3 Finish", BadgeScope.ALL, "Finished a season ranked in the top 3"),
    TOILET_CHAMP("Toilet Bowl Champ", BadgeScope.FANTASY, "\"Won\" the toilet bowl — came in dead last in a season"),
    TOP_SCORER("Top Scorer", BadgeScope.FANTASY, "Scored the most total points of any team in a completed season."),
    ADVERSITY_SPECIALIST("Adversity Specialist", BadgeScope.FANTASY, "Most scored-against team in a completed season"),
    FOUNDING_MEMBER("Founding Member", BadgeScope.ALL, "Played in a league's first-ever season."),
    MICRO_MANAGER("Micro-manager", BadgeScope.FANTASY, "Finished a season with the most roster transactions"),
    OVERCONFIDENT("Overconfident", BadgeScope.FANTASY, "Finished a season with fewest total roster moves"),
    TOTAL_DEGENERATE("Total Degenerate", BadgeScope.ALL, "Played in all 3 leagues"),
    MR_BOOMBASTIC("Mr. Boombastic", BadgeScope.FANTASY, "Had the highest single-week score in a season"),
    CHUMP_YEAR("Chump of the Year", BadgeScope.FANTASY, "Had the lowest single-week score in a season");

    private final String title;
    private final BadgeScope scope;
    private final String description;

    BadgeType(String title, BadgeScope scope, String description) {
        this.title = title;
        this.scope = scope;
        this.description = description;
    }

    public String title() {
        return title;
    }

    public BadgeScope scope() {
        return scope;
    }

    public String description() {
        return description;
    }
}
