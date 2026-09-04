package com.ffdash.sleeper;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Thin wrapper around the public, unauthenticated Sleeper API
 * (https://docs.sleeper.com). No API key is required.
 */
@Component
public class SleeperClient {

    private static final String BASE_URL = "https://api.sleeper.app/v1";

    private final RestClient restClient = RestClient.create(BASE_URL);

    public SleeperLeague getLeague(String leagueId) {
        return restClient.get()
                .uri("/league/{leagueId}", leagueId)
                .retrieve()
                .body(SleeperLeague.class);
    }

    public List<SleeperRoster> getRosters(String leagueId) {
        return restClient.get()
                .uri("/league/{leagueId}/rosters", leagueId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<SleeperRoster>>() {
                });
    }

    public List<SleeperUser> getUsers(String leagueId) {
        return restClient.get()
                .uri("/league/{leagueId}/users", leagueId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<SleeperUser>>() {
                });
    }

    /** The playoff bracket. Empty (not an error) for a league with no playoffs yet/ever, e.g. Pick'em. */
    public List<SleeperBracketMatchup> getWinnersBracket(String leagueId) {
        return restClient.get()
                .uri("/league/{leagueId}/winners_bracket", leagueId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<SleeperBracketMatchup>>() {
                });
    }

    /** The "toilet bowl" consolation bracket among non-playoff teams. Empty just like getWinnersBracket. */
    public List<SleeperBracketMatchup> getLosersBracket(String leagueId) {
        return restClient.get()
                .uri("/league/{leagueId}/losers_bracket", leagueId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<SleeperBracketMatchup>>() {
                });
    }

    /** One week's matchups (every roster's score, paired up by matchup_id). Empty for a week not yet reached. */
    public List<SleeperMatchup> getMatchups(String leagueId, int week) {
        return restClient.get()
                .uri("/league/{leagueId}/matchups/{week}", leagueId, week)
                .retrieve()
                .body(new ParameterizedTypeReference<List<SleeperMatchup>>() {
                });
    }

    /** One week's roster transactions (waivers, free agent moves, trades). Empty for a week not yet reached. */
    public List<SleeperTransaction> getTransactions(String leagueId, int round) {
        return restClient.get()
                .uri("/league/{leagueId}/transactions/{round}", leagueId, round)
                .retrieve()
                .body(new ParameterizedTypeReference<List<SleeperTransaction>>() {
                });
    }
}
