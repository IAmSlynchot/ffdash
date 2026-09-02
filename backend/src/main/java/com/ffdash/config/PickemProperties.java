package com.ffdash.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Who has paid the pick'em pool's optional buy-in, per season — only those
 * owners are eligible for prize money at season's end; everyone else can
 * still play, just not for the pot. Sleeper has no concept of this, so it's
 * hand-maintained here (ffdash.pickem.buy-ins in application.yml) rather than
 * fetched. Keyed by each owner's Sleeper display name rather than user_id
 * since that's what's practical to type by hand; matching is case-insensitive.
 */
@ConfigurationProperties(prefix = "ffdash.pickem")
public class PickemProperties {

    private Map<String, List<String>> buyIns = Map.of();

    public Map<String, List<String>> getBuyIns() {
        return buyIns;
    }

    public void setBuyIns(Map<String, List<String>> buyIns) {
        this.buyIns = buyIns;
    }

    /** Whether the given owner display name paid the given season's buy-in. Case-insensitive. */
    public boolean hasPaid(String season, String ownerDisplayName) {
        if (ownerDisplayName == null) {
            return false;
        }
        return buyIns.getOrDefault(season, List.of()).stream()
                .anyMatch(name -> name.equalsIgnoreCase(ownerDisplayName));
    }
}
