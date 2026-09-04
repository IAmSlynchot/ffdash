package com.ffdash.league;

import com.ffdash.config.LeaguesProperties;
import com.ffdash.config.LeaguesProperties.LeagueFamilyConfig;
import com.ffdash.config.LeaguesProperties.LeagueType;
import com.ffdash.config.LeaguesProperties.SeasonConfig;
import com.ffdash.config.PickemProperties;
import com.ffdash.league.badge.BadgeContext;
import com.ffdash.league.badge.BadgeEarning;
import com.ffdash.league.badge.BadgeEligibility;
import com.ffdash.league.badge.BadgeType;
import com.ffdash.league.badge.EarnedBadge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Orchestrates league families: looks families up by their configured key,
 * assembles multi-season history, and computes the cross-league owner
 * aggregate. Per-season fetching/joining/caching lives in SeasonDataService;
 * badge eligibility rules live in the league.badge package (BadgeEligibility).
 */
@Service
public class LeagueService {

    private static final String COMPLETE_STATUS = "complete";
    private static final int TOP_FINISH_THRESHOLD = 3;

    private static final Logger log = LoggerFactory.getLogger(LeagueService.class);

    private final SeasonDataService seasonDataService;
    private final LeaguesProperties leaguesProperties;
    private final PickemProperties pickemProperties;
    private final BadgeEligibility badgeEligibility;

    public LeagueService(
            SeasonDataService seasonDataService, LeaguesProperties leaguesProperties,
            PickemProperties pickemProperties, BadgeEligibility badgeEligibility
    ) {
        this.seasonDataService = seasonDataService;
        this.leaguesProperties = leaguesProperties;
        this.pickemProperties = pickemProperties;
        this.badgeEligibility = badgeEligibility;
    }

    public List<LeagueFamilyRef> listLeagueFamilies() {
        return leaguesProperties.getLeagues().stream()
                .map(family -> new LeagueFamilyRef(family.key(), family.displayName()))
                .toList();
    }

    public LeagueFamilyHistory getFamilyHistory(String key) {
        LeagueFamilyConfig family = findFamily(key);

        List<SeasonSummary> seasons = family.seasons().stream()
                .flatMap(seasonConfig -> fetchSeason(family, seasonConfig).stream())
                // newest season first; Sleeper's own season field is authoritative, config order isn't relied on
                .sorted(Comparator.comparing(SeasonSummary::season).reversed())
                .toList();

        return new LeagueFamilyHistory(family.key(), family.displayName(), family.type(), seasons);
    }

    public List<OwnerCareerSummary> getOwnerCareerSummaries() {
        List<OwnerSeasonEntry> entries = leaguesProperties.getLeagues().stream()
                .flatMap(family -> family.seasons().stream()
                        .flatMap(seasonConfig -> fetchSeason(family, seasonConfig).stream())
                        .flatMap(season -> season.teams().stream()
                                .flatMap(team -> managersOf(team).stream()
                                        .map(manager -> new OwnerSeasonEntry(family, season, team, manager)))))
                .toList();

        Map<String, List<OwnerSeasonEntry>> byOwner = entries.stream()
                .collect(Collectors.groupingBy(e -> e.manager().userId()));

        return byOwner.values().stream()
                .map(this::toOwnerCareerSummary)
                .sorted(
                        Comparator.comparingInt(OwnerCareerSummary::combinedWins).reversed()
                                .thenComparing(Comparator.comparingDouble(OwnerCareerSummary::combinedPointsFor).reversed())
                )
                .toList();
    }

    private OwnerCareerSummary toOwnerCareerSummary(List<OwnerSeasonEntry> ownerEntries) {
        int wins = 0;
        int losses = 0;
        int ties = 0;
        double pointsFor = 0;
        double pointsAgainst = 0;
        int topThreeFinishes = 0;

        for (OwnerSeasonEntry e : ownerEntries) {
            if (e.family().type() == LeagueType.FANTASY) {
                wins += e.team().wins();
                losses += e.team().losses();
                ties += e.team().ties();
                pointsFor += e.team().pointsFor();
                pointsAgainst += e.team().pointsAgainst();
            }
            if (COMPLETE_STATUS.equals(e.season().status()) && e.team().rank() <= TOP_FINISH_THRESHOLD) {
                topThreeFinishes++;
            }
        }

        // Most recent season's manager info wins, so avatar stays current. displayName is
        // the person's stable Sleeper username (not a team nickname, which changes yearly
        // and per-league) — that's what identifies the person across the whole app. Sourced
        // from ManagerIdentity, not TeamSummary's owner fields — for a co-manager entry those
        // describe the team's primary owner, a different person.
        OwnerSeasonEntry mostRecent = ownerEntries.stream()
                .max(Comparator.comparing(e -> e.season().season()))
                .orElseThrow();

        List<SeasonResult> seasonResults = ownerEntries.stream()
                .map(e -> new SeasonResult(
                        e.family().key(),
                        e.family().displayName(),
                        e.season().season(),
                        e.season().status(),
                        e.team().rank(),
                        !e.manager().userId().equals(e.team().ownerUserId())
                ))
                .sorted(Comparator.comparing(SeasonResult::season).reversed())
                .toList();

        return new OwnerCareerSummary(
                mostRecent.manager().userId(),
                mostRecent.manager().displayName(),
                mostRecent.manager().avatarUrl(),
                wins,
                losses,
                ties,
                pointsFor,
                pointsAgainst,
                topThreeFinishes,
                seasonResults,
                computeBadges(ownerEntries)
        );
    }

    /**
     * Achievement badges this owner has earned (see BadgeType for eligibility rules and
     * BadgeScope for which league type(s) each one can apply to), computed from the same
     * per-owner entries used above. An owner can earn the same badge type in more than one
     * league-year; rather than repeating the badge once per year, each BadgeType appears at
     * most once, carrying every year it was earned (EarnedBadge.earnings) so the profile
     * stays compact without losing any of that history. Eligibility itself is delegated to
     * BadgeEligibility (league.badge) — this method just builds each entry's BadgeContext
     * and loops.
     */
    private List<EarnedBadge> computeBadges(List<OwnerSeasonEntry> ownerEntries) {
        Map<BadgeType, List<BadgeEarning>> earningsByType = new EnumMap<>(BadgeType.class);
        // Only used by TOTAL_DEGENERATE, a lifetime-participation badge rather than a per-season
        // performance one — it needs a single entry to attach its one earning to (see BadgeEligibility).
        OwnerSeasonEntry mostRecentEntry = ownerEntries.stream()
                .max(Comparator.comparing(e -> e.season().season()))
                .orElseThrow();
        int configuredLeagueCount = leaguesProperties.getLeagues().size();

        for (OwnerSeasonEntry e : ownerEntries) {
            boolean seasonComplete = COMPLETE_STATUS.equals(e.season().status());
            BadgeContext ctx = new BadgeContext(e, seasonComplete, ownerEntries, mostRecentEntry, configuredLeagueCount);
            for (BadgeType type : BadgeType.values()) {
                if (type.scope().appliesTo(e.family().type()) && badgeEligibility.isEligible(type, ctx)) {
                    addEarning(earningsByType, type, e);
                }
            }
        }

        // Badge types with the most recent earning show first.
        return earningsByType.entrySet().stream()
                .map(entry -> {
                    BadgeType type = entry.getKey();
                    List<BadgeEarning> earnings = entry.getValue().stream()
                            .sorted(Comparator.comparing(BadgeEarning::season).reversed())
                            .toList();
                    return new EarnedBadge(type, type.title(), type.description(), earnings);
                })
                .sorted(Comparator.comparing((EarnedBadge b) -> b.earnings().get(0).season()).reversed())
                .toList();
    }

    private static void addEarning(Map<BadgeType, List<BadgeEarning>> earningsByType, BadgeType type, OwnerSeasonEntry e) {
        earningsByType.computeIfAbsent(type, t -> new ArrayList<>())
                .add(new BadgeEarning(e.family().key(), e.season().season(), e.family().displayName() + " " + e.season().season()));
    }

    /**
     * Every person who managed this team — its primary owner (if any; a Pick'em/fantasy roster
     * can be ownerless, see TeamSummary), plus each of its co-managers. Used to fan a single
     * TeamSummary out into one OwnerSeasonEntry per person, so a co-manager gets full credit
     * (career totals, badges, this season in their Leagues list) alongside the primary owner
     * rather than only the owner being tracked.
     */
    private static List<ManagerIdentity> managersOf(TeamSummary team) {
        List<ManagerIdentity> managers = new ArrayList<>();
        if (team.ownerUserId() != null) {
            managers.add(new ManagerIdentity(team.ownerUserId(), team.ownerDisplayName(), team.avatarUrl()));
        }
        for (TeamSummary.CoManager coManager : team.coManagers()) {
            managers.add(new ManagerIdentity(coManager.userId(), coManager.displayName(), coManager.avatarUrl()));
        }
        return managers;
    }

    private LeagueFamilyConfig findFamily(String key) {
        return leaguesProperties.getLeagues().stream()
                .filter(f -> f.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new UnknownLeagueException(key));
    }

    /**
     * Empty if this season's data couldn't be fetched, so one bad/unreachable league id doesn't
     * fail the whole response. Deliberately narrowed to RestClientException (Sleeper itself
     * failed — a known, expected failure mode) rather than a bare RuntimeException: a genuine bug
     * in the joining logic (e.g. a null-handling mistake in SeasonDataService) now propagates to
     * ApiExceptionHandler instead of being silently WARN-logged and that season just dropped,
     * indistinguishable from an ordinary Sleeper outage.
     */
    private Optional<SeasonSummary> fetchSeason(LeagueFamilyConfig family, SeasonConfig seasonConfig) {
        try {
            SeasonSummary summary = seasonDataService.getSeasonSummary(seasonConfig.leagueId());
            return Optional.of(withBuyIns(family, seasonConfig, summary));
        } catch (RestClientException e) {
            log.warn("Skipping {} {} season (league id {}): {}",
                    family.key(), seasonConfig.season(), seasonConfig.leagueId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Pick'em only: stamps each team with whether its owner paid that season's
     * buy-in (see PickemProperties), so League View can show who's actually
     * competing for the pot. No-op for FANTASY leagues, where it doesn't apply.
     */
    private SeasonSummary withBuyIns(LeagueFamilyConfig family, SeasonConfig seasonConfig, SeasonSummary summary) {
        if (family.type() != LeagueType.PICKEM) {
            return summary;
        }
        List<TeamSummary> teams = summary.teams().stream()
                .map(team -> new TeamSummary(
                        team.ownerUserId(), team.ownerDisplayName(), team.teamName(), team.avatarUrl(), team.rank(),
                        team.wins(), team.losses(), team.ties(), team.pointsFor(), team.pointsAgainst(),
                        pickemProperties.hasPaid(seasonConfig.season(), team.ownerDisplayName()),
                        team.playoffPlacement(), team.toiletBowlChamp(), team.weeklyScores(), team.coManagers(),
                        team.transactionCount()
                ))
                .toList();
        return new SeasonSummary(summary.leagueId(), summary.season(), summary.name(), summary.status(),
                summary.totalRosters(), teams, summary.pickemWeeks(), summary.bracket(), summary.weeklyMatchups(),
                summary.currentWeek());
    }
}
