package com.ffdash.league.badge;

/** One BadgeType's eligibility rule — see BadgeEligibility for the full set. */
@FunctionalInterface
public interface BadgeEvaluator {
    boolean isEligible(BadgeContext ctx);
}
