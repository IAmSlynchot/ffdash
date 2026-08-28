package com.ffdash.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the statically configured list of fantasy football leagues from
 * application.yml (ffdash.leagues). Edit that file to add/remove/rename leagues.
 */
@ConfigurationProperties(prefix = "ffdash")
public class LeaguesProperties {

    private List<LeagueConfig> leagues = List.of();
    private String corsAllowedOrigin;

    public List<LeagueConfig> getLeagues() {
        return leagues;
    }

    public void setLeagues(List<LeagueConfig> leagues) {
        this.leagues = leagues;
    }

    public String getCorsAllowedOrigin() {
        return corsAllowedOrigin;
    }

    public void setCorsAllowedOrigin(String corsAllowedOrigin) {
        this.corsAllowedOrigin = corsAllowedOrigin;
    }

    public record LeagueConfig(String id, String displayName) {
    }
}
