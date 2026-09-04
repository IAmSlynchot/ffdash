package com.ffdash.league;

import com.ffdash.config.LeaguesProperties;
import com.ffdash.sleeper.SleeperBracketMatchup;
import com.ffdash.sleeper.SleeperClient;
import com.ffdash.sleeper.SleeperLeague;
import com.ffdash.sleeper.SleeperMatchup;
import com.ffdash.sleeper.SleeperRoster;
import com.ffdash.sleeper.SleeperTransaction;
import com.ffdash.sleeper.SleeperUser;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Fetches one Sleeper league id's league/rosters/users and joins them into a
 * single SeasonSummary, cached in memory. A completed season is immutable —
 * once fetched it's cached forever. An in-progress season is refetched once
 * its cache entry is older than ffdash.cache.live-season-ttl.
 *
 * This cache is a plain in-process map: fine for Render's single free-tier
 * instance, but it's lost on every cold start/restart (see README/CLAUDE.md
 * for why that's an accepted tradeoff rather than reaching for a database).
 */
@Service
public class SeasonDataService {

    private static final String COMPLETE_STATUS = "complete";

    private final SleeperClient sleeperClient;
    private final LeaguesProperties leaguesProperties;
    private final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();

    // Per-week caches, independent of the season-level cache above: a given (league, week) is
    // fetched at most once ever, since a week that's fully scored (see fetchWeeklyData) never
    // changes again. A failed fetch is deliberately left OUT of these maps rather than cached as
    // empty, so it's retried on the next call instead of being permanently blank — see
    // fetchWeekSafely.
    private final Map<WeekKey, List<SleeperMatchup>> matchupsCache = new ConcurrentHashMap<>();
    private final Map<WeekKey, List<SleeperTransaction>> transactionsCache = new ConcurrentHashMap<>();

    public SeasonDataService(SleeperClient sleeperClient, LeaguesProperties leaguesProperties) {
        this.sleeperClient = sleeperClient;
        this.leaguesProperties = leaguesProperties;
    }

    public SeasonSummary getSeasonSummary(String leagueId) {
        Duration ttl = leaguesProperties.getCache().liveSeasonTtl();
        CachedEntry cached = cache.get(leagueId);
        if (cached != null && cached.isFresh(ttl)) {
            return cached.summary();
        }

        SeasonSummary summary = fetchAndJoin(leagueId);
        cache.put(leagueId, new CachedEntry(summary, Instant.now()));
        return summary;
    }

    private SeasonSummary fetchAndJoin(String leagueId) {
        SleeperLeague league = sleeperClient.getLeague(leagueId);
        List<SleeperRoster> rosters = sleeperClient.getRosters(leagueId);
        List<SleeperUser> users = sleeperClient.getUsers(leagueId);

        List<SleeperBracketMatchup> winnersBracketRaw = fetchBracketSafely(() -> sleeperClient.getWinnersBracket(leagueId));
        List<SleeperBracketMatchup> losersBracketRaw = fetchBracketSafely(() -> sleeperClient.getLosersBracket(leagueId));
        Map<Integer, Integer> placementByRosterId = derivePlacements(winnersBracketRaw);
        Integer toiletBowlChampionRosterId = deriveToiletBowlChampion(losersBracketRaw);
        Map<Integer, String> pickemWeeks = detectPickemWeeks(league);

        Map<String, SleeperUser> usersById = users.stream()
                .collect(Collectors.toMap(SleeperUser::user_id, Function.identity()));
        Map<Integer, RosterIdentity> identityByRosterId = rosters.stream()
                .collect(Collectors.toMap(SleeperRoster::roster_id, r -> resolveIdentity(r, usersById.get(r.owner_id()))));

        WeeklyData weeklyData = fetchWeeklyData(leagueId, league, identityByRosterId, !pickemWeeks.isEmpty());

        List<TeamSummary> ranked = rosters.stream()
                .map(roster -> toTeamSummary(
                        roster, usersById.get(roster.owner_id()),
                        placementByRosterId.get(roster.roster_id()),
                        roster.roster_id().equals(toiletBowlChampionRosterId),
                        pickemWeeks,
                        resolveCoManagers(roster, usersById),
                        weeklyData.transactionCountByRosterId().getOrDefault(roster.roster_id(), 0)
                ))
                .sorted(
                        Comparator.comparingInt(TeamSummary::wins).reversed()
                                .thenComparing(Comparator.comparingDouble(TeamSummary::pointsFor).reversed())
                )
                .toList();

        // 1-based placement from the sort order above. For FANTASY this is wins-then-points, the
        // only ranking signal Sleeper's API gives us. For Pick'em, wins is always 0 (no such
        // concept there — see toTeamSummary), so this degenerates to a pure points-desc sort,
        // which is exactly right since pointsFor holds the season-total Pick'em score in that case.
        List<TeamSummary> teams = IntStream.range(0, ranked.size())
                .mapToObj(i -> withRank(ranked.get(i), i + 1))
                .toList();

        return new SeasonSummary(
                leagueId,
                league.season(),
                league.name(),
                league.status(),
                league.total_rosters() == null ? teams.size() : league.total_rosters(),
                teams,
                List.copyOf(pickemWeeks.keySet()),
                buildSeasonBracket(winnersBracketRaw, losersBracketRaw, identityByRosterId),
                weeklyData.weeklyMatchups()
        );
    }

    private TeamSummary toTeamSummary(SleeperRoster roster, SleeperUser owner, Integer playoffPlacement,
                                       boolean toiletBowlChamp, Map<Integer, String> pickemWeeks,
                                       List<TeamSummary.CoManager> coManagers, int transactionCount) {
        var settings = roster.settings();
        String teamName = resolveTeamName(roster, owner);
        String avatarUrl = resolveAvatarUrl(owner);

        List<Double> weeklyScores = List.of();
        double pointsFor = settings != null ? settings.pointsFor() : 0;
        if (!pickemWeeks.isEmpty()) {
            Map<String, Double> pointsByLeg = roster.metadata() != null ? roster.metadata().points_by_leg() : null;
            List<Double> scores = new ArrayList<>(pickemWeeks.size());
            double total = 0;
            for (String rawKey : pickemWeeks.values()) {
                // A missing key (or metadata/points_by_leg entirely null) means no data for that
                // week yet — null, not 0 — distinct from a stored 0.0 (played, scored zero).
                Double weekScore = pointsByLeg != null ? pointsByLeg.get(rawKey) : null;
                scores.add(weekScore);
                if (weekScore != null) {
                    total += weekScore;
                }
            }
            // Collections.unmodifiableList, not List.copyOf/List.of — those reject null elements,
            // and a null element here is meaningful (see comment above), not an oversight.
            weeklyScores = Collections.unmodifiableList(scores);
            pointsFor = total;
        }

        return new TeamSummary(
                owner != null ? owner.user_id() : null,
                owner != null ? owner.display_name() : null,
                teamName,
                avatarUrl,
                0, // rank is filled in by withRank() once the full season is sorted
                settings != null && settings.wins() != null ? settings.wins() : 0,
                settings != null && settings.losses() != null ? settings.losses() : 0,
                settings != null && settings.ties() != null ? settings.ties() : 0,
                pointsFor,
                settings != null ? settings.pointsAgainst() : 0,
                false, // boughtIn is stamped in by LeagueService, which knows family type + season; this layer doesn't
                playoffPlacement,
                toiletBowlChamp,
                weeklyScores,
                coManagers,
                transactionCount
        );
    }

    private static TeamSummary withRank(TeamSummary team, int rank) {
        return new TeamSummary(
                team.ownerUserId(), team.ownerDisplayName(), team.teamName(), team.avatarUrl(), rank,
                team.wins(), team.losses(), team.ties(), team.pointsFor(), team.pointsAgainst(), team.boughtIn(),
                team.playoffPlacement(), team.toiletBowlChamp(), team.weeklyScores(), team.coManagers(),
                team.transactionCount()
        );
    }

    /**
     * Resolves a roster's co_owners (other Sleeper user ids with edit access to it, alongside
     * owner_id) against the same /users response already used for the primary owner — every
     * co-owner is a league member, so this should never miss, but a defensive filter drops any
     * id that somehow isn't (e.g. a co-owner removed from the league but not the roster).
     */
    private static List<TeamSummary.CoManager> resolveCoManagers(SleeperRoster roster, Map<String, SleeperUser> usersById) {
        if (roster.co_owners() == null || roster.co_owners().isEmpty()) {
            return List.of();
        }
        return roster.co_owners().stream()
                .filter(id -> !id.equals(roster.owner_id()))
                .distinct()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .map(user -> new TeamSummary.CoManager(
                        user.user_id(),
                        user.display_name(),
                        user.avatar() != null ? "https://sleepercdn.com/avatars/" + user.avatar() : null
                ))
                .toList();
    }

    private static final Pattern PICKEM_WEEK_KEY = Pattern.compile("^v1:regular:(\\d+)$");

    /**
     * Detects a Pick'em-shaped season generically from league.scoring_settings' key pattern
     * (v1:regular:N) rather than trusting family/LeagueType from config — this service
     * deliberately doesn't know about family, same as bracket placement above. Returns week
     * number -> the exact raw key string Sleeper used for it (never reconstructed, so lookups
     * into a roster's points_by_leg stay robust to any future key-format change), ordered
     * ascending by week via TreeMap. Empty for a normal fantasy season — its scoring_settings
     * keys are stat abbreviations (pass_td, rec, ...) that never match this pattern.
     */
    private static Map<Integer, String> detectPickemWeeks(SleeperLeague league) {
        if (league.scoring_settings() == null) {
            return Map.of();
        }
        Map<Integer, String> weeks = new TreeMap<>();
        for (String key : league.scoring_settings().keySet()) {
            Matcher matcher = PICKEM_WEEK_KEY.matcher(key);
            if (matcher.matches()) {
                weeks.put(Integer.parseInt(matcher.group(1)), key);
            }
        }
        return weeks;
    }

    /**
     * Fetches one bracket endpoint (winners_bracket or losers_bracket), tolerating any failure
     * (including a league type — Pick'em — that has no such data) by returning empty rather than
     * failing the whole season fetch: bracket data is a nice-to-have, both for the badges derived
     * from it below and for the full bracket display built from it further down.
     */
    private List<SleeperBracketMatchup> fetchBracketSafely(Supplier<List<SleeperBracketMatchup>> fetcher) {
        try {
            List<SleeperBracketMatchup> result = fetcher.get();
            return result != null ? result : List.of();
        } catch (RuntimeException e) {
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
    private static Map<Integer, Integer> derivePlacements(List<SleeperBracketMatchup> winnersBracket) {
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
    private static Integer deriveToiletBowlChampion(List<SleeperBracketMatchup> losersBracket) {
        return losersBracket.stream()
                .filter(matchup -> matchup.p() != null && matchup.w() != null)
                .min(Comparator.comparingInt(SleeperBracketMatchup::p))
                .map(SleeperBracketMatchup::w)
                .orElse(null);
    }

    private static String resolveTeamName(SleeperRoster roster, SleeperUser owner) {
        return owner != null ? owner.teamName() : "Roster " + roster.roster_id();
    }

    private static String resolveAvatarUrl(SleeperUser user) {
        return user != null && user.avatar() != null ? "https://sleepercdn.com/avatars/" + user.avatar() : null;
    }

    private static RosterIdentity resolveIdentity(SleeperRoster roster, SleeperUser owner) {
        return new RosterIdentity(
                owner != null ? owner.user_id() : null,
                resolveTeamName(roster, owner),
                resolveAvatarUrl(owner)
        );
    }

    /**
     * Builds both display-ready brackets from the raw Sleeper matchup lists, resolving each
     * team slot's roster_id via identityByRosterId. Deliberately empty (SeasonBracket.EMPTY)
     * unless at least one matchup in either bracket has actually been played (a non-null
     * winner) — Sleeper publishes a fully-seeded bracket from week 1 based on that moment's
     * standings, and showing that projection before playoffs have even started would be
     * misleading rather than informative.
     */
    private static SeasonBracket buildSeasonBracket(List<SleeperBracketMatchup> winnersRaw,
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

    /**
     * Fetches and resolves this season's week-by-week matchups and transaction counts. Empty
     * for Pick'em (isPickemSeason) and for a season with no fully-scored week yet — Sleeper
     * publishes league.settings but omits last_scored_leg entirely until at least one week has
     * concluded (confirmed live), which this treats the same as 0.
     *
     * Weeks already present in matchupsCache are never refetched (they're immutable once
     * final) — only genuinely new weeks trigger Sleeper calls, fetched in parallel via virtual
     * threads so the wall-clock cost of a first-time backfill stays close to one round trip
     * instead of one per week. A week whose fetch fails is left out of the cache (not cached as
     * empty), so it's retried the next time this runs rather than staying permanently blank.
     */
    private WeeklyData fetchWeeklyData(String leagueId, SleeperLeague league,
                                        Map<Integer, RosterIdentity> identityByRosterId, boolean isPickemSeason) {
        if (isPickemSeason) {
            return WeeklyData.EMPTY;
        }
        int lastScoredLeg = league.settings() != null && league.settings().last_scored_leg() != null
                ? league.settings().last_scored_leg() : 0;
        if (lastScoredLeg <= 0) {
            return WeeklyData.EMPTY;
        }

        List<Integer> weeksToFetch = IntStream.rangeClosed(1, lastScoredLeg)
                .filter(week -> !matchupsCache.containsKey(new WeekKey(leagueId, week)))
                .boxed()
                .toList();
        if (!weeksToFetch.isEmpty()) {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int week : weeksToFetch) {
                    executor.submit(() -> fetchWeekSafely(leagueId, week));
                }
            } // try-with-resources close() blocks until every submitted task finishes
        }

        List<WeeklyMatchup> weeklyMatchups = new ArrayList<>();
        Map<Integer, Integer> transactionCountByRosterId = new HashMap<>();
        for (int week = 1; week <= lastScoredLeg; week++) {
            WeekKey key = new WeekKey(leagueId, week);

            List<SleeperMatchup> matchups = matchupsCache.get(key);
            if (matchups != null) {
                weeklyMatchups.addAll(resolveWeeklyMatchups(week, matchups, identityByRosterId));
            }

            List<SleeperTransaction> transactions = transactionsCache.get(key);
            if (transactions != null) {
                for (SleeperTransaction transaction : transactions) {
                    if (!COMPLETE_STATUS.equals(transaction.status()) || transaction.roster_ids() == null) {
                        continue;
                    }
                    for (Integer rosterId : transaction.roster_ids()) {
                        transactionCountByRosterId.merge(rosterId, 1, Integer::sum);
                    }
                }
            }
        }
        return new WeeklyData(weeklyMatchups, transactionCountByRosterId);
    }

    private void fetchWeekSafely(String leagueId, int week) {
        WeekKey key = new WeekKey(leagueId, week);
        try {
            List<SleeperMatchup> matchups = sleeperClient.getMatchups(leagueId, week);
            matchupsCache.put(key, matchups != null ? matchups : List.of());
        } catch (RuntimeException ignored) {
            // Left uncached — see fetchWeeklyData's javadoc.
        }
        try {
            List<SleeperTransaction> transactions = sleeperClient.getTransactions(leagueId, week);
            transactionsCache.put(key, transactions != null ? transactions : List.of());
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Pairs one week's roster rows into head-to-head matchups by matchup_id — two rosters
     * sharing one played each other; anything else (a bye, or malformed data) can't be paired
     * and is skipped rather than guessed at.
     */
    private static List<WeeklyMatchup> resolveWeeklyMatchups(int week, List<SleeperMatchup> matchups,
                                                               Map<Integer, RosterIdentity> identityByRosterId) {
        Map<Integer, List<SleeperMatchup>> byMatchupId = matchups.stream()
                .filter(m -> m.matchup_id() != null)
                .collect(Collectors.groupingBy(SleeperMatchup::matchup_id, TreeMap::new, Collectors.toList()));

        List<WeeklyMatchup> result = new ArrayList<>();
        for (List<SleeperMatchup> pair : byMatchupId.values()) {
            if (pair.size() != 2) {
                continue;
            }
            MatchupSide side1 = resolveMatchupSide(pair.get(0), identityByRosterId);
            MatchupSide side2 = resolveMatchupSide(pair.get(1), identityByRosterId);
            if (side1 != null && side2 != null) {
                result.add(new WeeklyMatchup(week, side1, side2));
            }
        }
        return result;
    }

    private static MatchupSide resolveMatchupSide(SleeperMatchup matchup, Map<Integer, RosterIdentity> identityByRosterId) {
        if (matchup.roster_id() == null) {
            return null;
        }
        RosterIdentity identity = identityByRosterId.get(matchup.roster_id());
        if (identity == null) {
            return null;
        }
        return new MatchupSide(identity.ownerUserId(), identity.teamName(), identity.avatarUrl(),
                matchup.points() != null ? matchup.points() : 0.0);
    }

    private record WeekKey(String leagueId, int week) {
    }

    private record WeeklyData(List<WeeklyMatchup> weeklyMatchups, Map<Integer, Integer> transactionCountByRosterId) {
        static final WeeklyData EMPTY = new WeeklyData(List.of(), Map.of());
    }

    /** A roster's display identity, resolved once per season fetch and reused for both TeamSummary and bracket team slots. */
    private record RosterIdentity(String ownerUserId, String teamName, String avatarUrl) {
    }

    private record CachedEntry(SeasonSummary summary, Instant fetchedAt) {
        boolean isFresh(Duration ttl) {
            return COMPLETE_STATUS.equals(summary.status()) || Instant.now().isBefore(fetchedAt.plus(ttl));
        }
    }
}
