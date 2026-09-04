package com.ffdash.league;

import com.ffdash.sleeper.SleeperBracketMatchup;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Derives display-ready playoff brackets (and the per-roster placement/toilet-bowl-champion
 * flags used elsewhere for badges) from Sleeper's raw winners_bracket/losers_bracket matchup
 * lists. Extracted from SeasonDataService — every method here is a pure function of its
 * arguments (no field state, no Sleeper calls of its own), called from
 * SeasonDataService.fetchAndJoin.
 */
final class BracketAssembler {

    private BracketAssembler() {
    }

    /**
     * Fetches one bracket endpoint (winners_bracket or losers_bracket), tolerating any failure
     * (including a league type — Pick'em — that has no such data) by returning empty rather than
     * failing the whole season fetch: bracket data is a nice-to-have, both for the badges derived
     * from it below and for the full bracket display built from it further down.
     */
    static List<SleeperBracketMatchup> fetchBracketSafely(Supplier<List<SleeperBracketMatchup>> fetcher) {
        try {
            List<SleeperBracketMatchup> result = fetcher.get();
            return result != null ? result : List.of();
        } catch (RestClientException e) {
            return List.of();
        }
    }

    /**
     * Maps roster_id -> final standing in the main playoff bracket (1 = champion), derived from
     * the winners_bracket's placement-deciding matchups (those with a non-null {@code p}): a
     * matchup's winner finishes in place p, its loser in place p+1. Deliberately winners_bracket
     * only — the "toilet bowl" consolation bracket's own placement games use a locally-restarted
     * numbering that doesn't extend this same ranking (see deriveToiletBowlChampion). Empty (not
     * every roster_id present) for leagues with no playoffs yet/at all — e.g. Pick'em, or a
     * season still in progress.
     */
    static Map<Integer, Integer> derivePlacements(List<SleeperBracketMatchup> winnersBracket) {
        Map<Integer, Integer> placements = new HashMap<>();
        for (SleeperBracketMatchup matchup : winnersBracket) {
            if (matchup.p() == null) {
                continue;
            }
            if (matchup.w() != null) {
                placements.put(matchup.w(), matchup.p());
            }
            if (matchup.l() != null) {
                placements.put(matchup.l(), matchup.p() + 1);
            }
        }
        return placements;
    }

    /**
     * The roster_id that won the "toilet bowl" — the losers_bracket's own deciding (lowest-p)
     * matchup. A dubious-honor title for a team that was bad enough to be in the consolation
     * bracket at all; who "wins" it doesn't correspond to any single slot in the main bracket's
     * 1..N placement numbering above, so it's tracked separately rather than folded into that.
     * Null if there's no losers_bracket data (no playoffs yet/at all for this league).
     */
    static Integer deriveToiletBowlChampion(List<SleeperBracketMatchup> losersBracket) {
        return losersBracket.stream()
                .filter(matchup -> matchup.p() != null && matchup.w() != null)
                .min(Comparator.comparingInt(SleeperBracketMatchup::p))
                .map(SleeperBracketMatchup::w)
                .orElse(null);
    }

    /**
     * Builds both display-ready brackets from the raw Sleeper matchup lists, resolving each
     * team slot's roster_id via identityByRosterId. Deliberately empty (SeasonBracket.EMPTY)
     * unless at least one matchup in either bracket has actually been played (a non-null
     * winner) — Sleeper publishes a fully-seeded bracket from week 1 based on that moment's
     * standings, and showing that projection before playoffs have even started would be
     * misleading rather than informative.
     */
    static SeasonBracket buildSeasonBracket(List<SleeperBracketMatchup> winnersRaw,
                                             List<SleeperBracketMatchup> losersRaw,
                                             Map<Integer, RosterIdentity> identityByRosterId) {
        boolean playoffsStarted = Stream.concat(winnersRaw.stream(), losersRaw.stream())
                .anyMatch(matchup -> matchup.w() != null);
        if (!playoffsStarted) {
            return SeasonBracket.EMPTY;
        }
        // The toilet/losers bracket picks up numbering right where the winners bracket's real
        // placements leave off (e.g. a 6-team winners bracket settles 1st-6th, so the toilet
        // bracket starts at 7th) — see derivePlacementRanks for why its own placements need
        // recomputing rather than just offsetting Sleeper's raw p.
        int winnersBracketSize = winnersRaw.stream()
                .filter(m -> m.p() != null)
                .mapToInt(m -> m.p() + 1)
                .max()
                .orElse(0);
        return new SeasonBracket(
                resolveMatchups(winnersRaw, identityByRosterId, false, 0),
                resolveMatchups(losersRaw, identityByRosterId, true, winnersBracketSize)
        );
    }

    private static List<BracketMatchup> resolveMatchups(List<SleeperBracketMatchup> raw,
                                                          Map<Integer, RosterIdentity> identityByRosterId,
                                                          boolean inverted, int rankOffset) {
        Map<Integer, Integer> rankByMatchupId = derivePlacementRanks(raw, inverted, rankOffset);

        return raw.stream()
                .sorted(Comparator.comparingInt(SleeperBracketMatchup::r).thenComparingInt(SleeperBracketMatchup::m))
                .map(matchup -> {
                    boolean isFinal = matchup.p() != null && matchup.p() == 1;
                    // Sleeper always records the winner as whoever advances — for a normal
                    // bracket that's also who finished better, but a toilet bracket's non-final
                    // placement games run the opposite direction (see class note on
                    // derivePlacementRanks), so the highlighted team is flipped there to match
                    // who actually earned the better real standing. The bracket's own final is
                    // deliberately left alone — Sleeper's recorded winner there really is the one
                    // crowned, however dubious that "prize" is (see BracketTeam.winner).
                    boolean flipHighlight = inverted && matchup.p() != null && !isFinal;
                    Integer highlightedRosterId = flipHighlight ? matchup.l() : matchup.w();
                    Integer rank = rankByMatchupId.get(matchup.m());

                    return new BracketMatchup(
                            matchup.r(),
                            matchup.m(),
                            matchup.p(),
                            rank,
                            placementLabel(isFinal, inverted, rank),
                            resolveSlot(matchup.t1(), highlightedRosterId, identityByRosterId),
                            resolveSlot(matchup.t2(), highlightedRosterId, identityByRosterId)
                    );
                })
                .toList();
    }

    /**
     * Maps a bracket's own placement-deciding matchupId -> the real final standing its
     * better-placed team achieves. For the winners bracket (inverted = false, rankOffset = 0)
     * this just reproduces Sleeper's own p (its numbering already ascends with real placement:
     * p=1 is 1st, p=3 is 3rd, ...). The toilet/losers bracket runs backwards, though: within
     * each of its games the LOWER scorer is recorded as the Sleeper "winner" and advances
     * further into the bracket, and the matchup that most stubbornly keeps advancing (Sleeper's
     * own p=1, "the final") is the one that settles the WORST standing, not the best — so real
     * placement there needs the reverse tier order (largest p first) counted up from where the
     * winners bracket left off, rather than trusting p's absolute value the way the winners
     * bracket can.
     */
    private static Map<Integer, Integer> derivePlacementRanks(List<SleeperBracketMatchup> bracket, boolean inverted, int rankOffset) {
        Comparator<SleeperBracketMatchup> tierOrder = inverted
                ? Comparator.comparingInt(SleeperBracketMatchup::p).reversed()
                : Comparator.comparingInt(SleeperBracketMatchup::p);
        List<SleeperBracketMatchup> placementGames = bracket.stream()
                .filter(matchup -> matchup.p() != null)
                .sorted(tierOrder)
                .toList();

        Map<Integer, Integer> rankByMatchupId = new HashMap<>();
        int rank = rankOffset + 1;
        for (SleeperBracketMatchup matchup : placementGames) {
            rankByMatchupId.put(matchup.m(), rank);
            rank += 2;
        }
        return rankByMatchupId;
    }

    private static String placementLabel(boolean isFinal, boolean inverted, Integer rank) {
        if (rank == null) {
            return null;
        }
        if (isFinal) {
            return inverted ? "Toilet Bowl" : "Championship";
        }
        return ordinal(rank) + " Place";
    }

    private static String ordinal(int n) {
        if (n % 100 >= 11 && n % 100 <= 13) {
            return n + "th";
        }
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }

    private static BracketTeam resolveSlot(Integer rosterId, Integer highlightedRosterId, Map<Integer, RosterIdentity> identityByRosterId) {
        if (rosterId == null) {
            return null; // not yet determined — fed by a later round of a matchup not yet played
        }
        RosterIdentity identity = identityByRosterId.get(rosterId);
        if (identity == null) {
            return null;
        }
        return new BracketTeam(identity.ownerUserId(), identity.teamName(), identity.avatarUrl(), rosterId.equals(highlightedRosterId));
    }
}
