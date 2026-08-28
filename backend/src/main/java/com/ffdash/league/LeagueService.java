package com.ffdash.league;

import com.ffdash.config.LeaguesProperties;
import com.ffdash.sleeper.SleeperClient;
import com.ffdash.sleeper.SleeperLeague;
import com.ffdash.sleeper.SleeperRoster;
import com.ffdash.sleeper.SleeperUser;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fetches league/roster/user data from Sleeper and joins it into a single
 * display-ready LeagueSummary per configured league.
 */
@Service
public class LeagueService {

    private final SleeperClient sleeperClient;
    private final LeaguesProperties leaguesProperties;

    public LeagueService(SleeperClient sleeperClient, LeaguesProperties leaguesProperties) {
        this.sleeperClient = sleeperClient;
        this.leaguesProperties = leaguesProperties;
    }

    public List<LeagueRef> listLeagues() {
        return leaguesProperties.getLeagues().stream()
                .map(l -> new LeagueRef(l.id(), l.displayName()))
                .toList();
    }

    public LeagueSummary getLeagueSummary(String leagueId) {
        leaguesProperties.getLeagues().stream()
                .filter(l -> l.id().equals(leagueId))
                .findFirst()
                .orElseThrow(() -> new UnknownLeagueException(leagueId));

        SleeperLeague league = sleeperClient.getLeague(leagueId);
        List<SleeperRoster> rosters = sleeperClient.getRosters(leagueId);
        List<SleeperUser> users = sleeperClient.getUsers(leagueId);

        Map<String, SleeperUser> usersById = users.stream()
                .collect(Collectors.toMap(SleeperUser::user_id, Function.identity()));

        List<TeamSummary> teams = rosters.stream()
                .map(roster -> toTeamSummary(roster, usersById.get(roster.owner_id())))
                .sorted(
                        Comparator.comparingInt(TeamSummary::wins).reversed()
                                .thenComparing(Comparator.comparingDouble(TeamSummary::pointsFor).reversed())
                )
                .toList();

        return new LeagueSummary(
                leagueId,
                league.name(),
                league.season(),
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
                teamName,
                avatarUrl,
                settings != null && settings.wins() != null ? settings.wins() : 0,
                settings != null && settings.losses() != null ? settings.losses() : 0,
                settings != null && settings.ties() != null ? settings.ties() : 0,
                settings != null ? settings.pointsFor() : 0,
                settings != null ? settings.pointsAgainst() : 0
        );
    }
}
