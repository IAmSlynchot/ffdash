package com.ffdash.league;

/** A person's identity as of one season — same shape as TeamSummary's owner fields, but always about the person this entry is for, not necessarily the team's primary owner. */
record ManagerIdentity(String userId, String displayName, String avatarUrl) {
}
