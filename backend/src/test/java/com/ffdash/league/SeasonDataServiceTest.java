package com.ffdash.league;

import com.ffdash.sleeper.SleeperMatchup;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers SeasonDataService.resolveWeeklyMatchups (made package-private, not private, for exactly
 * this) — the matchup_id pairing logic that turns one week's flat roster rows into head-to-head
 * WeeklyMatchups, skipping anything that can't be cleanly paired rather than guessing.
 */
class SeasonDataServiceTest {

    private static Map<Integer, RosterIdentity> identities(int... rosterIds) {
        Map<Integer, RosterIdentity> map = new HashMap<>();
        for (int id : rosterIds) {
            map.put(id, new RosterIdentity("user" + id, "Team " + id, null));
        }
        return map;
    }

    @Test
    void pairsTwoRostersSharingAMatchupId() {
        List<SleeperMatchup> matchups = List.of(
                new SleeperMatchup(10, 1, 120.5),
                new SleeperMatchup(11, 1, 98.25)
        );

        List<WeeklyMatchup> result = SeasonDataService.resolveWeeklyMatchups(3, matchups, identities(10, 11));

        assertThat(result).hasSize(1);
        WeeklyMatchup m = result.get(0);
        assertThat(m.week()).isEqualTo(3);
        assertThat(List.of(m.team1().ownerUserId(), m.team2().ownerUserId())).containsExactlyInAnyOrder("user10", "user11");
        assertThat(List.of(m.team1().score(), m.team2().score())).containsExactlyInAnyOrder(120.5, 98.25);
    }

    @Test
    void skipsAnUnpairedRosterInsteadOfGuessingAnOpponent() {
        // matchup_id 2 has only one roster in the response — a bye, or malformed data — either
        // way it can't be paired, so it's dropped rather than fabricating an opponent.
        List<SleeperMatchup> matchups = List.of(
                new SleeperMatchup(10, 1, 120.5),
                new SleeperMatchup(11, 1, 98.25),
                new SleeperMatchup(12, 2, 88.0)
        );

        List<WeeklyMatchup> result = SeasonDataService.resolveWeeklyMatchups(3, matchups, identities(10, 11, 12));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).team1().ownerUserId()).isNotEqualTo("user12");
        assertThat(result.get(0).team2().ownerUserId()).isNotEqualTo("user12");
    }

    @Test
    void skipsAMatchupIdSharedByMoreThanTwoRosters() {
        // Three rosters somehow sharing one matchup_id is malformed data, not a real matchup —
        // resolveWeeklyMatchups requires pair.size() == 2 exactly.
        List<SleeperMatchup> matchups = List.of(
                new SleeperMatchup(10, 1, 100.0),
                new SleeperMatchup(11, 1, 90.0),
                new SleeperMatchup(12, 1, 80.0)
        );

        assertThat(SeasonDataService.resolveWeeklyMatchups(1, matchups, identities(10, 11, 12))).isEmpty();
    }

    @Test
    void skipsARosterWithNoKnownIdentity() {
        // roster_id 99 isn't in identityByRosterId (shouldn't happen with real data, but
        // resolveMatchupSide guards against it rather than throwing/fabricating one).
        List<SleeperMatchup> matchups = List.of(
                new SleeperMatchup(10, 1, 100.0),
                new SleeperMatchup(99, 1, 90.0)
        );

        assertThat(SeasonDataService.resolveWeeklyMatchups(1, matchups, identities(10))).isEmpty();
    }

    @Test
    void treatsAMissingScoreAsZeroNotNull() {
        List<SleeperMatchup> matchups = List.of(
                new SleeperMatchup(10, 1, null),
                new SleeperMatchup(11, 1, 50.0)
        );

        WeeklyMatchup m = SeasonDataService.resolveWeeklyMatchups(1, matchups, identities(10, 11)).get(0);
        double scoreForRoster10 = m.team1().ownerUserId().equals("user10") ? m.team1().score() : m.team2().score();

        assertThat(scoreForRoster10).isZero();
    }
}
