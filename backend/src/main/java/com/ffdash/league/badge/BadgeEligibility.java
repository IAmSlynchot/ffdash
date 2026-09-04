package com.ffdash.league.badge;

import com.ffdash.config.LeaguesProperties.SeasonConfig;
import com.ffdash.league.MatchupSide;
import com.ffdash.league.OwnerSeasonEntry;
import com.ffdash.league.SeasonSummary;
import com.ffdash.league.TeamSummary;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * Per-BadgeType eligibility rules, one BadgeEvaluator per type — extracted from
 * LeagueService (see LeagueService.computeBadges, which builds the BadgeContext this
 * consumes). Fantasy placement badges key off that season's playoff bracket rather than
 * the regular-season standings rank — CHAMPION and the fantasy half of TOP_3 use
 * TeamSummary.playoffPlacement (the main bracket's 1..N standing), while TOILET_CHAMP
 * uses the separate TeamSummary.toiletBowlChamp flag (the consolation bracket's own
 * winner, which doesn't correspond to any single placement number). Pick'em has no
 * playoffs, so its placement badges (TOP_3, PICKINATOR) use rank instead.
 *
 * Adding a new badge is one more evaluators.put(...) line in buildEvaluators(), not a
 * new switch case — this class exists specifically so that keeps being true as more
 * badge types get added.
 */
@Component
public class BadgeEligibility {

    private static final int TOP_FINISH_THRESHOLD = 3;

    private final Map<BadgeType, BadgeEvaluator> evaluators = buildEvaluators();

    public boolean isEligible(BadgeType type, BadgeContext ctx) {
        return evaluators.get(type).isEligible(ctx);
    }

    private static Map<BadgeType, BadgeEvaluator> buildEvaluators() {
        Map<BadgeType, BadgeEvaluator> evaluators = new EnumMap<>(BadgeType.class);

        // Founding Member is the only badge not gated on season completion — it's about
        // membership, not a performance placement.
        evaluators.put(BadgeType.FOUNDING_MEMBER, ctx -> isFoundingSeason(ctx.entry()));
        evaluators.put(BadgeType.CHAMPION, ctx -> ctx.seasonComplete() && isPlacement(ctx.entry(), 1));
        evaluators.put(BadgeType.TOP_3, ctx -> ctx.seasonComplete() && isTopThree(ctx.entry()));
        evaluators.put(BadgeType.TOILET_CHAMP, ctx -> ctx.seasonComplete() && ctx.entry().team().toiletBowlChamp());
        evaluators.put(BadgeType.PICKINATOR, ctx -> ctx.seasonComplete() && ctx.entry().team().rank() == 1);
        evaluators.put(BadgeType.TOP_SCORER, ctx -> ctx.seasonComplete()
                && ctx.entry().team().pointsFor() == maxAmong(ctx.entry(), TeamSummary::pointsFor));
        evaluators.put(BadgeType.ADVERSITY_SPECIALIST, ctx -> ctx.seasonComplete()
                && ctx.entry().team().pointsAgainst() == maxAmong(ctx.entry(), TeamSummary::pointsAgainst));
        evaluators.put(BadgeType.MICRO_MANAGER, ctx -> ctx.seasonComplete()
                && ctx.entry().team().transactionCount() > 0
                && ctx.entry().team().transactionCount() == maxAmong(ctx.entry(), t -> (double) t.transactionCount()));
        // The mirror of MICRO_MANAGER, but a genuine "did nothing all season" (0) has to be
        // allowed to win here — unlike MICRO_MANAGER, requiring > 0 would disqualify the most
        // impressive case. Guard on weeklyMatchups instead (same guard as
        // isSeasonSingleWeekExtreme below): if per-week data was actually fetched for this
        // season, an all-zero transactionCount is real; if it's empty (never fetched), the
        // 0-everywhere tie is just missing data and shouldn't award anyone.
        evaluators.put(BadgeType.OVERCONFIDENT, ctx -> ctx.seasonComplete()
                && !ctx.entry().season().weeklyMatchups().isEmpty()
                && ctx.entry().team().transactionCount() == minAmong(ctx.entry(), t -> (double) t.transactionCount()));
        // A lifetime-participation badge, not a per-season one: true once this owner has
        // appeared in every currently configured league family, regardless of which family/
        // season ctx.entry() itself is. Restricted to entry == mostRecentEntry so it's earned
        // exactly once (attached to their latest season) rather than once per season/family
        // they've ever played, which — since the underlying condition doesn't vary by season —
        // would otherwise repeat it on every single entry.
        evaluators.put(BadgeType.TOTAL_DEGENERATE, ctx -> ctx.entry() == ctx.mostRecentEntry() && playedEveryLeague(ctx));
        evaluators.put(BadgeType.MR_BOOMBASTIC, ctx -> ctx.seasonComplete() && isSeasonSingleWeekExtreme(ctx.entry(), true));
        evaluators.put(BadgeType.CHUMP_YEAR, ctx -> ctx.seasonComplete() && isSeasonSingleWeekExtreme(ctx.entry(), false));

        return evaluators;
    }

    /** Whether this owner has appeared in every currently configured league family at least once. */
    private static boolean playedEveryLeague(BadgeContext ctx) {
        long familiesPlayed = ctx.ownerEntries().stream().map(e -> e.family().key()).distinct().count();
        return familiesPlayed >= ctx.configuredLeagueCount();
    }

    /**
     * Whether this team had the single highest (highWeek) or lowest (!highWeek) individual weekly
     * score of any team in the season — one specific game, not a season total (that's TOP_SCORER/
     * ADVERSITY_SPECIALIST instead). Guards against SeasonSummary.weeklyMatchups being empty (no
     * per-week data fetched yet for this season — see SeasonDataService): without it, "this team's
     * extreme" and "the season's extreme" would both fall back to the same sentinel and compare
     * equal, awarding the badge to everyone.
     */
    private static boolean isSeasonSingleWeekExtreme(OwnerSeasonEntry e, boolean highWeek) {
        if (e.season().weeklyMatchups().isEmpty()) {
            return false;
        }
        String ownerUserId = e.team().ownerUserId();
        DoubleBinaryOperator combiner = highWeek ? Double::max : Double::min;
        double sentinel = highWeek ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

        double teamExtreme = weeklyScores(e.season())
                .filter(side -> ownerUserId != null && ownerUserId.equals(side.ownerUserId()))
                .mapToDouble(MatchupSide::score)
                .reduce(combiner)
                .orElse(sentinel);
        double seasonExtreme = weeklyScores(e.season())
                .mapToDouble(MatchupSide::score)
                .reduce(combiner)
                .orElse(sentinel);
        return teamExtreme == seasonExtreme;
    }

    private static Stream<MatchupSide> weeklyScores(SeasonSummary season) {
        return season.weeklyMatchups().stream().flatMap(m -> Stream.of(m.team1(), m.team2()));
    }

    private static boolean isFoundingSeason(OwnerSeasonEntry e) {
        String foundingSeason = e.family().seasons().stream()
                .map(SeasonConfig::season)
                .min(Comparator.naturalOrder())
                .orElse(null);
        return e.season().season().equals(foundingSeason);
    }

    private static boolean isPlacement(OwnerSeasonEntry e, int placement) {
        return e.team().playoffPlacement() != null && e.team().playoffPlacement() == placement;
    }

    private static boolean isTopThree(OwnerSeasonEntry e) {
        return switch (e.family().type()) {
            case FANTASY -> e.team().playoffPlacement() != null && e.team().playoffPlacement() <= TOP_FINISH_THRESHOLD;
            case PICKEM -> e.team().rank() <= TOP_FINISH_THRESHOLD;
        };
    }

    private static double maxAmong(OwnerSeasonEntry e, ToDoubleFunction<TeamSummary> metric) {
        return e.season().teams().stream()
                .mapToDouble(metric)
                .max()
                .orElse(Double.NEGATIVE_INFINITY);
    }

    private static double minAmong(OwnerSeasonEntry e, ToDoubleFunction<TeamSummary> metric) {
        return e.season().teams().stream()
                .mapToDouble(metric)
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }
}
