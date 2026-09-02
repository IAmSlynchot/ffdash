package com.ffdash.league;

import com.ffdash.config.LeaguesProperties;
import com.ffdash.sleeper.SleeperClient;
import com.ffdash.sleeper.SleeperLeague;
import com.ffdash.sleeper.SleeperRoster;
import com.ffdash.sleeper.SleeperUser;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
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

        Map<String, SleeperUser> usersById = users.stream()
                .collect(Collectors.toMap(SleeperUser::user_id, Function.identity()));

        List<TeamSummary> ranked = rosters.stream()
                .map(roster -> toTeamSummary(roster, usersById.get(roster.owner_id())))
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

    private TeamSummary toTeamSummary(SleeperRoster roster, SleeperUser owner) {
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
                false // boughtIn is stamped in by LeagueService, which knows family type + season; this layer doesn't
        );
    }

    private static TeamSummary withRank(TeamSummary team, int rank) {
        return new TeamSummary(
                team.ownerUserId(), team.ownerDisplayName(), team.teamName(), team.avatarUrl(), rank,
                team.wins(), team.losses(), team.ties(), team.pointsFor(), team.pointsAgainst(), team.boughtIn()
        );
    }

    private record CachedEntry(SeasonSummary summary, Instant fetchedAt) {
        boolean isFresh(Duration ttl) {
            return COMPLETE_STATUS.equals(summary.status()) || Instant.now().isBefore(fetchedAt.plus(ttl));
        }
    }
}
