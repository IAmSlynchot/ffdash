package com.ffdash.sleeper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Subset of the fields returned by GET /league/{league_id}/users.
 * See https://docs.sleeper.com/#getting-users-in-a-league
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SleeperUser(
        String user_id,
        String display_name,
        String avatar,
        Map<String, Object> metadata
) {
    /** The user's custom team name, if they set one; falls back to their display name. */
    public String teamName() {
        if (metadata != null && metadata.get("team_name") instanceof String teamName && !teamName.isBlank()) {
            return teamName;
        }
        return display_name;
    }
}
