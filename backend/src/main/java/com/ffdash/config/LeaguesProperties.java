package com.ffdash.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Binds the statically configured league families from application.yml
 * (ffdash.leagues). Each family is a group of Sleeper league ids, one per
 * season, since Sleeper gives every season of a "league" its own id.
 * Edit application.yml to add/remove/rename leagues or seasons.
 */
@ConfigurationProperties(prefix = "ffdash")
public class LeaguesProperties {

    private List<LeagueFamilyConfig> leagues = List.of();
    private List<String> corsAllowedOrigins = List.of();
    private CacheConfig cache = new CacheConfig(Duration.ofMinutes(2));

    public List<LeagueFamilyConfig> getLeagues() {
        return leagues;
    }

    public void setLeagues(List<LeagueFamilyConfig> leagues) {
        this.leagues = leagues;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public CacheConfig getCache() {
        return cache;
    }

    public void setCache(CacheConfig cache) {
        this.cache = cache;
    }

    /** A logical league across years — a stable app-level {@code key} plus its per-season Sleeper league ids. */
    public record LeagueFamilyConfig(String key, String displayName, LeagueType type, List<SeasonConfig> seasons) {
    }

    public record SeasonConfig(String season, String leagueId) {
    }

    /** liveSeasonTtl: how long an in-progress season is served from cache before refetching from Sleeper. */
    public record CacheConfig(Duration liveSeasonTtl) {
    }

    /**
     * FANTASY: a normal head-to-head roster league (wins/losses are directly comparable).
     * PICKEM: a Sleeper confidence-pool pick'em — structurally different, excluded from
     * combined win/loss totals but still eligible for cross-format aggregates like top-3 finishes.
     */
    public enum LeagueType {
        FANTASY, PICKEM
    }
}
