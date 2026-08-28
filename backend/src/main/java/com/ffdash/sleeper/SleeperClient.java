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
}
