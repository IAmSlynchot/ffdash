package com.ffdash.league;

/** A roster's display identity, resolved once per season fetch and reused for both TeamSummary and bracket team slots. */
record RosterIdentity(String ownerUserId, String teamName, String avatarUrl) {
}
