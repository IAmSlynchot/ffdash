package com.ffdash.league;

/**
 * Statically defined achievement types — titles/descriptions never vary per
 * season. The per-earning context (which league-year it was earned in) lives
 * on EarnedBadge, not here; see LeagueService.computeBadges for eligibility.
 * scope() gates which league type(s) (see LeaguesProperties.LeagueType) a
 * badge can even be earned in.
 */
public enum BadgeType {
    // Fantasy placement badges are decided by that season's playoff/toilet-bowl bracket, not
    // regular-season standings (wins/points) — Pick'em has no playoffs, so its placement badges
    // (TOP_3 below, PICKINATOR) use the regular-season standings rank instead. See
    // LeagueService.computeBadges/isTopThree for exactly where that split is applied.
    CHAMPION("Ultimate Championator", BadgeScope.FANTASY, "Won the league's playoff bracket."),
    TOP_SCORER("Top Scorer", BadgeScope.FANTASY, "Scored the most total points of any team in a completed season."),
    FOUNDING_MEMBER("Founding Member", BadgeScope.ALL, "Played in a league's very first configured season."),
    TOP_3("Top 3 Finish", BadgeScope.ALL, "Finished a season ranked in the top 3"),
    TOILET_CHAMP("Toilet Bowl Champ", BadgeScope.FANTASY, "Won the toilet bowl — the playoff bracket's consolation bracket for non-playoff teams."),
    PICKINATOR("Ultimate Pickinator", BadgeScope.PICKEM, "Finished a Pick'em season in first place"),
    // Not yet earnable: needs a season's total roster-transaction count per team, which Sleeper only
    // exposes via a per-round transactions endpoint (~15-18 calls per completed season) — deferred
    // rather than adding that fetch/caching cost now. Wired into the type system so it already shows
    // up anywhere BadgeType is enumerated; LeagueService.computeBadges never awards it yet.
    MICRO_MANAGER("Micro-manager", BadgeScope.FANTASY, "Finished a season with the most roster transactions"),
    ADVERSITY_SPECIALIST("Adversity Specialist", BadgeScope.FANTASY, "Finished a season with the highest points against");

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
