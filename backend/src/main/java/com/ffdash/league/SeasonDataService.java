package com.ffdash.league;

import com.ffdash.config.LeaguesProperties;
import com.ffdash.sleeper.SleeperBracketMatchup;
import com.ffdash.sleeper.SleeperClient;
import com.ffdash.sleeper.SleeperLeague;
import com.ffdash.sleeper.SleeperRoster;
import com.ffdash.sleeper.SleeperUser;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
        Map<Integer, Integer> placementByRosterId = fetchPlayoffPlacements(leagueId);
        Integer toiletBowlChampionRosterId = fetchToiletBowlChampion(leagueId);

        Map<String, SleeperUser> usersById = users.stream()
                .collect(Collectors.toMap(SleeperUser::user_id, Function.identity()));

        List<TeamSummary> ranked = rosters.stream()
                .map(roster -> toTeamSummary(
                        roster, usersById.get(roster.owner_id()),
                        placementByRosterId.get(roster.roster_id()),
                        roster.roster_id().equals(toiletBowlChampionRosterId)
                ))
                .sorted(
                        Comparator.comparingInt(TeamSummary::wins).reversed()
                                .thenComparing(Comparator.comparingDouble(TeamSummary::pointsFor).reversed())
                )
                .toList();

        // 1-based placement from the sort order above — the only ranking signal
        // Sleeper's API gives us today. Worth re-checking once real Pick'em
        // season data exists, since its ranking semantics may not be wins/points.
        List<TeamSummary> teams = IntStream.range(0, ranked.size())
                .mapToObj(i -> withRank(ranked.get(i), i + 1))
                .toList();

        return new SeasonSummary(
                leagueId,
                league.season(),
                league.name(),
                league.status(),
                league.total_rosters() == null ? teams.size() : league.total_rosters(),
                teams
        );
    }

    private TeamSummary toTeamSummary(SleeperRoster roster, SleeperUser owner, Integer playoffPlacement, boolean toiletBowlChamp) {
        var settings = roster.settings();
        String teamName = owner != null ? owner.teamName() : "Roster " + roster.roster_id();
        String avatarUrl = owner != null && owner.avatar() != null
                ? "https://sleepercdn.com/avatars/" + owner.avatar()
                : null;

        return new TeamSummary(
                owner != null ? owner.user_id() : null,
                owner != null ? owner.display_name() : null,
                teamName,
                avatarUrl,
                0, // rank is filled in by withRank() once the full season is sorted
                settings != null && settings.wins() != null ? settings.wins() : 0,
                settings != null && settings.losses() != null ? settings.losses() : 0,
                settings != null && settings.ties() != null ? settings.ties() : 0,
                settings != null ? settings.pointsFor() : 0,
                settings != null ? settings.pointsAgainst() : 0,
                false, // boughtIn is stamped in by LeagueService, which knows family type + season; this layer doesn't
                playoffPlacement,
                toiletBowlChamp
        );
    }

    private static TeamSummary withRank(TeamSummary team, int rank) {
        return new TeamSummary(
                team.ownerUserId(), team.ownerDisplayName(), team.teamName(), team.avatarUrl(), rank,
                team.wins(), team.losses(), team.ties(), team.pointsFor(), team.pointsAgainst(), team.boughtIn(),
                team.playoffPlacement(), team.toiletBowlChamp()
        );
    }

    /**
     * Maps roster_id -> final standing in the main playoff bracket (1 = champion), derived from
     * the winners_bracket's placement-deciding matchups (those with a non-null {@code p}): a
     * matchup's winner finishes in place p, its loser in place p+1. Deliberately winners_bracket
     * only — the "toilet bowl" consolation bracket's own placement games use a locally-restarted
     * numbering that doesn't extend this same ranking (see fetchToiletBowlChampion). Empty (not
     * every roster_id present) for leagues with no playoffs yet/at all — e.g. Pick'em, or a
     * season still in progress.
     */
    private Map<Integer, Integer> fetchPlayoffPlacements(String leagueId) {
        try {
            Map<Integer, Integer> placements = new HashMap<>();
            for (SleeperBracketMatchup matchup : sleeperClient.getWinnersBracket(leagueId)) {
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
        } catch (RuntimeException e) {
            // Bracket placement is a nice-to-have for badges — don't fail the whole season fetch over it.
            return Map.of();
        }
    }

    /**
     * The roster_id that won the "toilet bowl" — the losers_bracket's own deciding (lowest-p)
     * matchup. A dubious-honor title for a team that was bad enough to be in the consolation
     * bracket at all; who "wins" it doesn't correspond to any single slot in the main bracket's
     * 1..N placement numbering above, so it's tracked separately rather than folded into that.
     * Null if there's no losers_bracket data (no playoffs yet/at all for this league).
     */
    private Integer fetchToiletBowlChampion(String leagueId) {
        try {
            return sleeperClient.getLosersBracket(leagueId).stream()
                    .filter(matchup -> matchup.p() != null && matchup.w() != null)
                    .min(Comparator.comparingInt(SleeperBracketMatchup::p))
                    .map(SleeperBracketMatchup::w)
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private record CachedEntry(SeasonSummary summary, Instant fetchedAt) {
        boolean isFresh(Duration ttl) {
            return COMPLETE_STATUS.equals(summary.status()) || Instant.now().isBefore(fetchedAt.plus(ttl));
        }
    }
}
