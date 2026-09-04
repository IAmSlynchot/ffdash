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
import org.springframework.web.client.RestClientException;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

        List<SleeperBracketMatchup> winnersBracketRaw = BracketAssembler.fetchBracketSafely(() -> sleeperClient.getWinnersBracket(leagueId));
        List<SleeperBracketMatchup> losersBracketRaw = BracketAssembler.fetchBracketSafely(() -> sleeperClient.getLosersBracket(leagueId));
        Map<Integer, Integer> placementByRosterId = BracketAssembler.derivePlacements(winnersBracketRaw);
        Integer toiletBowlChampionRosterId = BracketAssembler.deriveToiletBowlChampion(losersBracketRaw);
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
                BracketAssembler.buildSeasonBracket(winnersBracketRaw, losersBracketRaw, identityByRosterId),
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

    // Narrowed to RestClientException (Sleeper itself failed), not a bare RuntimeException, so a
    // genuine bug elsewhere doesn't get silently absorbed the same way an expected Sleeper hiccup
    // does — same reasoning as LeagueService.fetchSeason.
    private void fetchWeekSafely(String leagueId, int week) {
        WeekKey key = new WeekKey(leagueId, week);
        try {
            List<SleeperMatchup> matchups = sleeperClient.getMatchups(leagueId, week);
            matchupsCache.put(key, matchups != null ? matchups : List.of());
        } catch (RestClientException ignored) {
            // Left uncached — see fetchWeeklyData's javadoc.
        }
        try {
            List<SleeperTransaction> transactions = sleeperClient.getTransactions(leagueId, week);
            transactionsCache.put(key, transactions != null ? transactions : List.of());
        } catch (RestClientException ignored) {
        }
    }

    /**
     * Pairs one week's roster rows into head-to-head matchups by matchup_id — two rosters
     * sharing one played each other; anything else (a bye, or malformed data) can't be paired
     * and is skipped rather than guessed at.
     */
    // Package-private (not private) so SeasonDataServiceTest can exercise this pure pairing logic
    // directly, without needing a real Sleeper fetch.
    static List<WeeklyMatchup> resolveWeeklyMatchups(int week, List<SleeperMatchup> matchups,
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

    private record CachedEntry(SeasonSummary summary, Instant fetchedAt) {
        boolean isFresh(Duration ttl) {
            return COMPLETE_STATUS.equals(summary.status()) || Instant.now().isBefore(fetchedAt.plus(ttl));
        }
    }
}
