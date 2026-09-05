package com.ffdash.league;

import com.ffdash.sleeper.SleeperBracketMatchup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers BracketAssembler (extracted from SeasonDataService — see there for why), in particular
 * the toilet/losers bracket's reversed placement-rank logic, which the class's own javadoc calls
 * out as the trickiest part: a toilet bowl's "winner" is whoever advances by losing more, so the
 * bracket's own final settles the WORST real standing, not the best.
 */
class BracketAssemblerTest {

    private static SleeperBracketMatchup matchup(int round, int id, int t1, int t2, Integer w, Integer l, Integer p) {
        return new SleeperBracketMatchup(round, id, t1, t2, w, l, p);
    }

    private static Map<Integer, RosterIdentity> identities(int... rosterIds) {
        Map<Integer, RosterIdentity> map = new java.util.HashMap<>();
        for (int id : rosterIds) {
            map.put(id, new RosterIdentity("user" + id, "Team " + id, null));
        }
        return map;
    }

    // ---- derivePlacements ----

    @Test
    void derivePlacementsMapsWinnerToPAndLoserToPPlusOne() {
        List<SleeperBracketMatchup> winnersBracket = List.of(
                matchup(3, 1, 10, 11, 10, 11, 1), // championship: winner 1st, loser 2nd
                matchup(3, 2, 12, 13, 12, 13, 3)  // 3rd place game: winner 3rd, loser 4th
        );

        Map<Integer, Integer> placements = BracketAssembler.derivePlacements(winnersBracket);

        assertThat(placements).containsExactlyInAnyOrderEntriesOf(Map.of(10, 1, 11, 2, 12, 3, 13, 4));
    }

    @Test
    void derivePlacementsIgnoresNonPlacementMatchups() {
        List<SleeperBracketMatchup> winnersBracket = List.of(matchup(1, 1, 10, 11, 10, 11, null));

        assertThat(BracketAssembler.derivePlacements(winnersBracket)).isEmpty();
    }

    // ---- deriveToiletBowlChampion ----

    @Test
    void deriveToiletBowlChampionIsTheLowestPPlacementGamesWinner() {
        List<SleeperBracketMatchup> losersBracket = List.of(
                matchup(2, 1, 10, 11, 11, 10, 3),
                matchup(2, 2, 12, 13, 13, 12, 1) // lowest p = the toilet bowl's own final
        );

        assertThat(BracketAssembler.deriveToiletBowlChampion(losersBracket)).isEqualTo(13);
    }

    @Test
    void deriveToiletBowlChampionIsNullWhenNoPlayoffsYet() {
        assertThat(BracketAssembler.deriveToiletBowlChampion(List.of())).isNull();
    }

    // ---- deriveFinalStandings ----

    @Test
    void deriveFinalStandingsCombinesBothBracketsWithToiletBracketFullyReversedIncludingItsOwnFinal() {
        // Winners bracket: settles 1st-4th (winnersBracketSize = 4), so the toilet bracket's real
        // standings should start at 5th.
        List<SleeperBracketMatchup> winnersBracket = List.of(
                matchup(3, 1, 1, 2, 1, 2, 1), // championship
                matchup(3, 2, 3, 4, 3, 4, 3)  // 3rd place game
        );
        // Toilet bracket, same shape as buildSeasonBracket's own toilet-bracket test: m=1 is a
        // non-final placement game (roster 10 escaped by losing, so gets the better standing),
        // m=2 is the toilet bowl's own final (roster 13 "won" it by losing/advancing the most,
        // so — unlike buildSeasonBracket's *display* highlight — gets the single WORST standing
        // of anyone, not a good one).
        List<SleeperBracketMatchup> losersBracket = List.of(
                matchup(1, 1, 10, 11, 11, 10, 3),
                matchup(2, 2, 12, 13, 13, 12, 1)
        );

        Map<Integer, Integer> standings = BracketAssembler.deriveFinalStandings(winnersBracket, losersBracket);

        assertThat(standings).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry(1, 1), Map.entry(2, 2), Map.entry(3, 3), Map.entry(4, 4),
                Map.entry(10, 5), Map.entry(11, 6), Map.entry(12, 7), Map.entry(13, 8)
        ));
    }

    @Test
    void deriveFinalStandingsIsEmptyWhenNeitherBracketHasPlacementGamesYet() {
        assertThat(BracketAssembler.deriveFinalStandings(List.of(), List.of())).isEmpty();
    }

    // ---- buildSeasonBracket ----

    @Test
    void buildSeasonBracketIsEmptyUntilAtLeastOneMatchupHasBeenPlayed() {
        List<SleeperBracketMatchup> notStarted = List.of(matchup(1, 1, 10, 11, null, null, null));

        SeasonBracket bracket = BracketAssembler.buildSeasonBracket(notStarted, notStarted, identities(10, 11));

        assertThat(bracket).isEqualTo(SeasonBracket.EMPTY);
    }

    @Test
    void buildSeasonBracketReversesToiletBracketTierOrderAndOffsetsPastTheWinnersBracket() {
        // A 6-team winners bracket (p=5 -> size 6), so the toilet bracket's own ranks start at 7.
        List<SleeperBracketMatchup> winnersRaw = List.of(matchup(3, 100, 1, 2, 1, 2, 5));

        // Two toilet-bracket placement games. Per Sleeper's convention here, the LOWER scorer is
        // recorded as the "winner" (w) because they advance further into the toilet bracket.
        //   m=1 (p=3): a non-final placement game — roster 10 (l) escaped early, so 10 should get
        //              the BETTER real standing (rank 7); roster 11 (w) is worse (rank 8, implied).
        //   m=2 (p=1): the toilet bowl's own final — roster 13 (w) advanced the furthest by losing
        //              the most, so 13 is the genuine, dubious "champion" (worst real standing).
        List<SleeperBracketMatchup> losersRaw = List.of(
                matchup(1, 1, 10, 11, 11, 10, 3),
                matchup(2, 2, 12, 13, 13, 12, 1)
        );
        Map<Integer, RosterIdentity> identities = identities(1, 2, 10, 11, 12, 13);

        SeasonBracket bracket = BracketAssembler.buildSeasonBracket(winnersRaw, losersRaw, identities);

        assertThat(bracket.toiletBowlBracket()).hasSize(2);
        BracketMatchup nonFinal = bracket.toiletBowlBracket().stream().filter(m -> m.matchupId() == 1).findFirst().orElseThrow();
        BracketMatchup theFinal = bracket.toiletBowlBracket().stream().filter(m -> m.matchupId() == 2).findFirst().orElseThrow();

        assertThat(nonFinal.placementRank()).isEqualTo(7);
        assertThat(nonFinal.placementLabel()).isEqualTo("7th Place");
        // Roster 10 (t1), the actual game's loser, is the one highlighted — they got the better
        // real standing by escaping the toilet bracket instead of advancing further into it.
        assertThat(nonFinal.team1().ownerUserId()).isEqualTo("user10");
        assertThat(nonFinal.team1().winner()).isTrue();
        assertThat(nonFinal.team2().winner()).isFalse();

        assertThat(theFinal.placementLabel()).isEqualTo("Toilet Bowl");
        // The final is left alone (not flipped) — Sleeper's own recorded winner (13) really is
        // the one "crowned", however dubious that prize is.
        assertThat(theFinal.team2().ownerUserId()).isEqualTo("user13");
        assertThat(theFinal.team2().winner()).isTrue();
        assertThat(theFinal.team1().winner()).isFalse();
    }
}
