package com.ffdash.league;

import com.ffdash.config.LeaguesProperties.LeagueFamilyConfig;
import com.ffdash.config.LeaguesProperties.LeagueType;
import com.ffdash.config.LeaguesProperties.SeasonConfig;
import com.ffdash.league.badge.BadgeContext;
import com.ffdash.league.badge.BadgeEligibility;
import com.ffdash.league.badge.BadgeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers BadgeEligibility's per-BadgeType rules (extracted from LeagueService — see there for
 * why). Lives in com.ffdash.league (not league.badge) so it can construct OwnerSeasonEntry/
 * ManagerIdentity/TeamSummary/SeasonSummary fixtures directly — ManagerIdentity is deliberately
 * package-private (see its own javadoc), which league.badge classes never need to touch.
 */
class BadgeEligibilityTest {

    private final BadgeEligibility badgeEligibility = new BadgeEligibility();

    private static final LeagueFamilyConfig FANTASY_FAMILY = new LeagueFamilyConfig(
            "depot", "The Depot League", LeagueType.FANTASY,
            List.of(new SeasonConfig("2023", "id2023"), new SeasonConfig("2024", "id2024"))
    );
    private static final LeagueFamilyConfig PICKEM_FAMILY = new LeagueFamilyConfig(
            "pickem", "Pick Six(teen)", LeagueType.PICKEM,
            List.of(new SeasonConfig("2024", "pid2024"))
    );
    private static final LeagueFamilyConfig FUZZY_FAMILY = new LeagueFamilyConfig(
            "fuzzy", "Fuzzy Handcuffs", LeagueType.FANTASY,
            List.of(new SeasonConfig("2024", "fid2024"))
    );

    // ---- fixture builders ----

    private static TeamSummary team(String ownerUserId, int rank, int wins, int losses,
                                     double pointsFor, double pointsAgainst, Integer playoffPlacement,
                                     boolean toiletBowlChamp, int transactionCount) {
        return new TeamSummary(ownerUserId, ownerUserId, "Team " + ownerUserId, null, rank, wins, losses, 0,
                pointsFor, pointsAgainst, false, playoffPlacement, toiletBowlChamp, List.of(), List.of(), transactionCount);
    }

    private static SeasonSummary season(String season, String status, List<TeamSummary> teams) {
        return season(season, status, teams, List.of());
    }

    private static SeasonSummary season(String season, String status, List<TeamSummary> teams, List<WeeklyMatchup> weeklyMatchups) {
        return new SeasonSummary("league-" + season, season, "Test League", status, teams.size(), teams,
                List.of(), SeasonBracket.EMPTY, weeklyMatchups, null);
    }

    private static OwnerSeasonEntry entry(LeagueFamilyConfig family, SeasonSummary season, TeamSummary team) {
        return new OwnerSeasonEntry(family, season, team, new ManagerIdentity(team.ownerUserId(), team.ownerUserId(), null));
    }

    private static BadgeContext context(OwnerSeasonEntry entry, boolean seasonComplete) {
        return new BadgeContext(entry, seasonComplete, List.of(entry), entry, 3);
    }

    private static WeeklyMatchup matchup(int week, String owner1, double score1, String owner2, double score2) {
        return new WeeklyMatchup(week,
                new MatchupSide(owner1, "Team " + owner1, null, score1),
                new MatchupSide(owner2, "Team " + owner2, null, score2));
    }

    // ---- CHAMPION / TOP_3 / TOILET_CHAMP / PICKINATOR (placement-based) ----

    @Test
    void championAwardedOnlyForPlayoffPlacementOneInACompleteSeason() {
        TeamSummary champ = team("u1", 1, 10, 4, 1500, 1300, 1, false, 0);
        OwnerSeasonEntry e = entry(FANTASY_FAMILY, season("2024", "complete", List.of(champ)), champ);

        assertThat(badgeEligibility.isEligible(BadgeType.CHAMPION, context(e, true))).isTrue();
        assertThat(badgeEligibility.isEligible(BadgeType.CHAMPION, context(e, false))).isFalse();
    }

    @Test
    void championNotAwardedForRunnerUp() {
        TeamSummary runnerUp = team("u1", 2, 9, 5, 1400, 1350, 2, false, 0);
        OwnerSeasonEntry e = entry(FANTASY_FAMILY, season("2024", "complete", List.of(runnerUp)), runnerUp);

        assertThat(badgeEligibility.isEligible(BadgeType.CHAMPION, context(e, true))).isFalse();
    }

    @Test
    void top3UsesPlayoffPlacementForFantasyAndRankForPickem() {
        TeamSummary fantasyThird = team("u1", 5, 7, 7, 1200, 1250, 3, false, 0);
        OwnerSeasonEntry fantasyEntry = entry(FANTASY_FAMILY, season("2024", "complete", List.of(fantasyThird)), fantasyThird);
        assertThat(badgeEligibility.isEligible(BadgeType.TOP_3, context(fantasyEntry, true))).isTrue();

        // Regular-season rank 3 but no playoff placement yet (still in_season) shouldn't count for FANTASY.
        TeamSummary fantasyRankThreeNoPlacement = team("u2", 3, 8, 6, 1300, 1200, null, false, 0);
        OwnerSeasonEntry noPlacementEntry =
                entry(FANTASY_FAMILY, season("2024", "complete", List.of(fantasyRankThreeNoPlacement)), fantasyRankThreeNoPlacement);
        assertThat(badgeEligibility.isEligible(BadgeType.TOP_3, context(noPlacementEntry, true))).isFalse();

        // Pick'em has no playoffs — uses rank instead, playoffPlacement always null.
        TeamSummary pickemThird = team("u3", 3, 0, 0, 900, 0, null, false, 0);
        OwnerSeasonEntry pickemEntry = entry(PICKEM_FAMILY, season("2024", "complete", List.of(pickemThird)), pickemThird);
        assertThat(badgeEligibility.isEligible(BadgeType.TOP_3, context(pickemEntry, true))).isTrue();
    }

    @Test
    void toiletChampUsesTheDedicatedFlagNotPlacement() {
        TeamSummary lastPlace = team("u1", 10, 2, 12, 900, 1600, null, true, 0);
        OwnerSeasonEntry e = entry(FANTASY_FAMILY, season("2024", "complete", List.of(lastPlace)), lastPlace);

        assertThat(badgeEligibility.isEligible(BadgeType.TOILET_CHAMP, context(e, true))).isTrue();
    }

    @Test
    void pickinatorRequiresRankOneInPickem() {
        TeamSummary first = team("u1", 1, 0, 0, 1000, 0, null, false, 0);
        OwnerSeasonEntry e = entry(PICKEM_FAMILY, season("2024", "complete", List.of(first)), first);

        assertThat(badgeEligibility.isEligible(BadgeType.PICKINATOR, context(e, true))).isTrue();
    }

    // ---- TOP_SCORER / ADVERSITY_SPECIALIST (maxAmong across the season's teams) ----

    @Test
    void topScorerAwardedToTheSeasonsHighestPointsFor() {
        TeamSummary high = team("u1", 1, 10, 4, 1600, 1300, null, false, 0);
        TeamSummary low = team("u2", 2, 9, 5, 1500, 1200, null, false, 0);
        SeasonSummary s = season("2024", "complete", List.of(high, low));

        assertThat(badgeEligibility.isEligible(BadgeType.TOP_SCORER, context(entry(FANTASY_FAMILY, s, high), true))).isTrue();
        assertThat(badgeEligibility.isEligible(BadgeType.TOP_SCORER, context(entry(FANTASY_FAMILY, s, low), true))).isFalse();
    }

    @Test
    void adversitySpecialistAwardedToTheSeasonsMostScoredAgainst() {
        TeamSummary mostScoredAgainst = team("u1", 8, 4, 10, 1300, 1700, null, false, 0);
        TeamSummary other = team("u2", 5, 7, 7, 1400, 1400, null, false, 0);
        SeasonSummary s = season("2024", "complete", List.of(mostScoredAgainst, other));

        assertThat(badgeEligibility.isEligible(BadgeType.ADVERSITY_SPECIALIST, context(entry(FANTASY_FAMILY, s, mostScoredAgainst), true)))
                .isTrue();
        assertThat(badgeEligibility.isEligible(BadgeType.ADVERSITY_SPECIALIST, context(entry(FANTASY_FAMILY, s, other), true))).isFalse();
    }

    @Test
    void maxAmongFallsBackToNegativeInfinitySentinelWhenSeasonHasNoTeams() {
        // A degenerate/empty teams list can't happen via real data, but the sentinel behavior a
        // badge silently depends on for correctness is worth pinning down directly.
        TeamSummary lonely = team("u1", 1, 0, 0, 0, 0, null, false, 0);
        SeasonSummary emptyTeamsSeason = new SeasonSummary("league-2024", "2024", "Test League", "complete", 0,
                List.of(), List.of(), SeasonBracket.EMPTY, List.of(), null);
        OwnerSeasonEntry e = entry(FANTASY_FAMILY, emptyTeamsSeason, lonely);

        // pointsFor (0) can never equal Double.NEGATIVE_INFINITY, so this must be false, not throw.
        assertThat(badgeEligibility.isEligible(BadgeType.TOP_SCORER, context(e, true))).isFalse();
    }

    // ---- MICRO_MANAGER / OVERCONFIDENT (transaction count max/min) ----

    @Test
    void microManagerRequiresBothTheSeasonMaxAndAtLeastOneTransaction() {
        TeamSummary most = team("u1", 1, 10, 4, 1500, 1300, null, false, 20);
        TeamSummary none = team("u2", 8, 4, 10, 1200, 1500, null, false, 0);
        SeasonSummary allZero = season("2024", "complete", List.of(none, none));
        SeasonSummary real = season("2024", "complete", List.of(most, none));

        assertThat(badgeEligibility.isEligible(BadgeType.MICRO_MANAGER, context(entry(FANTASY_FAMILY, real, most), true))).isTrue();
        // Nobody made a single move all season — the max is 0, but MICRO_MANAGER requires > 0.
        assertThat(badgeEligibility.isEligible(BadgeType.MICRO_MANAGER, context(entry(FANTASY_FAMILY, allZero, none), true))).isFalse();
    }

    @Test
    void overconfidentAwardsAGenuineZeroTransactionSeasonWhenWeeklyDataWasActuallyFetched() {
        TeamSummary didNothing = team("u1", 5, 7, 7, 1300, 1300, null, false, 0);
        TeamSummary madeMoves = team("u2", 3, 8, 6, 1350, 1250, null, false, 12);
        List<WeeklyMatchup> realWeeklyData = List.of(matchup(1, "u1", 100, "u2", 90));
        SeasonSummary fetched = season("2024", "complete", List.of(didNothing, madeMoves), realWeeklyData);

        assertThat(badgeEligibility.isEligible(BadgeType.OVERCONFIDENT, context(entry(FANTASY_FAMILY, fetched, didNothing), true)))
                .as("a real 0-transaction season, with per-week data actually fetched, should win Overconfident")
                .isTrue();
        assertThat(badgeEligibility.isEligible(BadgeType.OVERCONFIDENT, context(entry(FANTASY_FAMILY, fetched, madeMoves), true)))
                .isFalse();
    }

    @Test
    void overconfidentDoesNotAwardAnUnfetchedSeasonWhereEveryoneShowsZero() {
        // Same all-zero transactionCount shape as the real case above, but weeklyMatchups is
        // empty — meaning no per-week data was ever fetched, so the 0s aren't meaningful.
        TeamSummary a = team("u1", 5, 7, 7, 1300, 1300, null, false, 0);
        TeamSummary b = team("u2", 3, 8, 6, 1350, 1250, null, false, 0);
        SeasonSummary neverFetched = season("2024", "complete", List.of(a, b)); // weeklyMatchups = List.of()

        assertThat(badgeEligibility.isEligible(BadgeType.OVERCONFIDENT, context(entry(FANTASY_FAMILY, neverFetched, a), true))).isFalse();
        assertThat(badgeEligibility.isEligible(BadgeType.OVERCONFIDENT, context(entry(FANTASY_FAMILY, neverFetched, b), true))).isFalse();
    }

    // ---- MR_BOOMBASTIC / CHUMP_YEAR (single-week extremes, and their tie behavior) ----

    @Test
    void mrBoombasticAwardsTheSeasonsSingleHighestWeeklyScore() {
        TeamSummary a = team("u1", 1, 10, 4, 1500, 1300, null, false, 0);
        TeamSummary b = team("u2", 2, 9, 5, 1400, 1200, null, false, 0);
        List<WeeklyMatchup> weeks = List.of(
                matchup(1, "u1", 120, "u2", 100),
                matchup(2, "u1", 90, "u2", 200) // u2's week-2 score is the season's real high
        );
        SeasonSummary s = season("2024", "complete", List.of(a, b), weeks);

        assertThat(badgeEligibility.isEligible(BadgeType.MR_BOOMBASTIC, context(entry(FANTASY_FAMILY, s, a), true))).isFalse();
        assertThat(badgeEligibility.isEligible(BadgeType.MR_BOOMBASTIC, context(entry(FANTASY_FAMILY, s, b), true))).isTrue();
    }

    @Test
    void chumpYearAwardsTheSeasonsSingleLowestWeeklyScore() {
        TeamSummary a = team("u1", 1, 10, 4, 1500, 1300, null, false, 0);
        TeamSummary b = team("u2", 2, 9, 5, 1400, 1200, null, false, 0);
        List<WeeklyMatchup> weeks = List.of(matchup(1, "u1", 55, "u2", 130));
        SeasonSummary s = season("2024", "complete", List.of(a, b), weeks);

        assertThat(badgeEligibility.isEligible(BadgeType.CHUMP_YEAR, context(entry(FANTASY_FAMILY, s, a), true))).isTrue();
        assertThat(badgeEligibility.isEligible(BadgeType.CHUMP_YEAR, context(entry(FANTASY_FAMILY, s, b), true))).isFalse();
    }

    @Test
    void singleWeekExtremeAwardsBothSidesOnATie() {
        // Two teams sharing the season's single high score in different weeks — an == comparison,
        // not a unique max, so both should be eligible.
        TeamSummary a = team("u1", 1, 10, 4, 1500, 1300, null, false, 0);
        TeamSummary b = team("u2", 2, 9, 5, 1400, 1200, null, false, 0);
        List<WeeklyMatchup> weeks = List.of(
                matchup(1, "u1", 200, "u2", 100),
                matchup(2, "u2", 200, "u1", 90)
        );
        SeasonSummary s = season("2024", "complete", List.of(a, b), weeks);

        assertThat(badgeEligibility.isEligible(BadgeType.MR_BOOMBASTIC, context(entry(FANTASY_FAMILY, s, a), true))).isTrue();
        assertThat(badgeEligibility.isEligible(BadgeType.MR_BOOMBASTIC, context(entry(FANTASY_FAMILY, s, b), true))).isTrue();
    }

    @Test
    void singleWeekExtremeNeverAwardsAnUnfetchedSeason() {
        TeamSummary a = team("u1", 1, 10, 4, 1500, 1300, null, false, 0);
        SeasonSummary neverFetched = season("2024", "complete", List.of(a)); // weeklyMatchups = List.of()

        assertThat(badgeEligibility.isEligible(BadgeType.MR_BOOMBASTIC, context(entry(FANTASY_FAMILY, neverFetched, a), true))).isFalse();
        assertThat(badgeEligibility.isEligible(BadgeType.CHUMP_YEAR, context(entry(FANTASY_FAMILY, neverFetched, a), true))).isFalse();
    }

    // ---- FOUNDING_MEMBER (not gated on season completion) ----

    @Test
    void foundingMemberIsAwardedForTheFamilysFirstConfiguredSeasonRegardlessOfCompletion() {
        TeamSummary team = team("u1", 1, 0, 0, 0, 0, null, false, 0);
        OwnerSeasonEntry foundingEntry = entry(FANTASY_FAMILY, season("2023", "complete", List.of(team)), team);
        OwnerSeasonEntry laterEntry = entry(FANTASY_FAMILY, season("2024", "in_season", List.of(team)), team);

        assertThat(badgeEligibility.isEligible(BadgeType.FOUNDING_MEMBER, context(foundingEntry, false))).isTrue();
        assertThat(badgeEligibility.isEligible(BadgeType.FOUNDING_MEMBER, context(laterEntry, false))).isFalse();
    }

    // ---- TOTAL_DEGENERATE (lifetime participation, reference-equality gate) ----

    @Test
    void totalDegenerateRequiresAllConfiguredFamiliesAndOnlyAttachesToTheMostRecentEntry() {
        TeamSummary fantasyTeam = team("u1", 1, 0, 0, 0, 0, null, false, 0);
        TeamSummary pickemTeam = team("u1", 1, 0, 0, 0, 0, null, false, 0);
        TeamSummary fuzzyTeam = team("u1", 1, 0, 0, 0, 0, null, false, 0);

        OwnerSeasonEntry depot2023 = entry(FANTASY_FAMILY, season("2023", "complete", List.of(fantasyTeam)), fantasyTeam);
        OwnerSeasonEntry pickem2024 = entry(PICKEM_FAMILY, season("2024", "complete", List.of(pickemTeam)), pickemTeam);
        OwnerSeasonEntry fuzzy2024 = entry(FUZZY_FAMILY, season("2024", "complete", List.of(fuzzyTeam)), fuzzyTeam);
        List<OwnerSeasonEntry> allThreeFamilies = List.of(depot2023, pickem2024, fuzzy2024);

        BadgeContext mostRecentCtx = new BadgeContext(fuzzy2024, true, allThreeFamilies, fuzzy2024, 3);
        BadgeContext olderEntryCtx = new BadgeContext(depot2023, true, allThreeFamilies, fuzzy2024, 3);
        assertThat(badgeEligibility.isEligible(BadgeType.TOTAL_DEGENERATE, mostRecentCtx))
                .as("awarded on the most-recent entry once all 3 configured families are covered")
                .isTrue();
        assertThat(badgeEligibility.isEligible(BadgeType.TOTAL_DEGENERATE, olderEntryCtx))
                .as("not repeated on every entry — only the one == mostRecentEntry")
                .isFalse();

        // Only 2 of the 3 configured families played — should never be awarded, on any entry.
        List<OwnerSeasonEntry> onlyTwoFamilies = List.of(depot2023, pickem2024);
        BadgeContext incompleteCtx = new BadgeContext(pickem2024, true, onlyTwoFamilies, pickem2024, 3);
        assertThat(badgeEligibility.isEligible(BadgeType.TOTAL_DEGENERATE, incompleteCtx)).isFalse();
    }
}
