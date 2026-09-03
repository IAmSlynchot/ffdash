package com.ffdash.league;

import java.util.List;

/**
 * One BadgeType this owner has earned, consolidated: appears once per profile
 * even if earned in multiple league-years, tying every year it was earned in
 * to that single instance (see BadgeEarning) rather than repeating the badge
 * itself per year. title/description are copied in from the BadgeType so the
 * frontend needs no separate lookup, mirroring how SeasonResult denormalizes
 * its league family's key/name.
 */
public record EarnedBadge(
        BadgeType type,
        String title,
        String description,
        /** Every league-year this badge was earned in, newest season first. */
        List<BadgeEarning> earnings
) {
}
