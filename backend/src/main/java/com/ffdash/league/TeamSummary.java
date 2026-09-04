package com.ffdash.league;

import java.util.List;

/**
 * One team's standing within a season, ready for display.
 *
 * @param pointsFor FANTASY: total points scored across the season, from Sleeper's roster
 *                  settings. PICKEM: the season-total Pick'em score instead (sum of
 *                  weeklyScores' non-null values) — reused rather than adding a separate field,
 *                  so ranking, the TOP_3/PICKINATOR badges, and cross-season aggregation all
 *                  work unchanged for both league types.
 * @param boughtIn Pick'em only: whether this owner paid that pool's optional
 *                 buy-in for this season (see PickemProperties) and is thus
 *                 eligible for prize money. Always false for FANTASY leagues,
 *                 where the concept doesn't apply.
 * @param playoffPlacement This team's final standing per that season's playoff bracket (1 =
 *                          champion), not the regular-season rank above. Null when no bracket
 *                          placement is known — no playoffs yet/at all for this league (e.g.
 *                          Pick'em, or a season still in progress).
 * @param toiletBowlChamp Whether this team won the "toilet bowl" (the playoff bracket's
 *                        consolation bracket) — a separate signal from playoffPlacement, since
 *                        "won the toilet bowl" doesn't correspond to any single number in that
 *                        ranking (it's a dubious-honor title for a team that was bad enough to
 *                        be in the consolation bracket at all, decided by that bracket's own
 *                        final game, independent of how the main bracket's placements are numbered).
 * @param weeklyScores PICKEM only: this team's score for each of that season's
 *                      SeasonSummary.pickemWeeks entries, same length/order — so index i here
 *                      corresponds to week pickemWeeks.get(i). A null element means no data for
 *                      that week (joined late, or not yet played), distinct from 0.0 (played,
 *                      scored zero). Empty for FANTASY.
 * @param coManagers Other Sleeper users with edit access to this same roster (Sleeper's
 *                    "co-owner" concept), in addition to the primary owner above. Usually
 *                    empty — most rosters have exactly one manager.
 * @param transactionCount FANTASY only: this team's total completed roster transactions
 *                          (waivers, free agent adds, trades) across every week fetched so far
 *                          this season — a trade counts for both sides. Always 0 for Pick'em,
 *                          where the concept doesn't apply, and only reflects weeks whose
 *                          matchup/transaction data has actually been fetched (see
 *                          SeasonDataService), so it only means "final" once the season is
 *                          complete.
 */
public record TeamSummary(
        String ownerUserId,
        String ownerDisplayName,
        String teamName,
        String avatarUrl,
        int rank,
        int wins,
        int losses,
        int ties,
        double pointsFor,
        double pointsAgainst,
        boolean boughtIn,
        Integer playoffPlacement,
        boolean toiletBowlChamp,
        List<Double> weeklyScores,
        List<CoManager> coManagers,
        int transactionCount
) {
    /** One co-manager of a TeamSummary's roster — same identity shape as the primary owner fields. */
    public record CoManager(String userId, String displayName, String avatarUrl) {
    }
}
