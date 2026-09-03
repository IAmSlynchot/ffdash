package com.ffdash.league;

import com.ffdash.config.LeaguesProperties;
import com.ffdash.config.LeaguesProperties.LeagueFamilyConfig;
import com.ffdash.config.LeaguesProperties.LeagueType;
import com.ffdash.config.LeaguesProperties.SeasonConfig;
import com.ffdash.config.PickemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Orchestrates league families: looks families up by their configured key,
 * assembles multi-season history, and computes the cross-league owner
 * aggregate. Per-season fetching/joining/caching lives in SeasonDataService.
 */
@Service
public class LeagueService {

    private static final String COMPLETE_STATUS = "complete";
    private static final int TOP_FINISH_THRESHOLD = 3;

    private static final Logger log = LoggerFactory.getLogger(LeagueService.class);

    private final SeasonDataService seasonDataService;
    private final LeaguesProperties leaguesProperties;
    private final PickemProperties pickemProperties;

    public LeagueService(SeasonDataService seasonDataService, LeaguesProperties leaguesProperties, PickemProperties pickemProperties) {
        this.seasonDataService = seasonDataService;
        this.leaguesProperties = leaguesProperties;
        this.pickemProperties = pickemProperties;
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
                                .filter(team -> team.ownerUserId() != null)
                                .map(team -> new OwnerSeasonEntry(family, season, team))))
                .toList();

        Map<String, List<OwnerSeasonEntry>> byOwner = entries.stream()
                .collect(Collectors.groupingBy(e -> e.team().ownerUserId()));

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

        // Most recent season's owner info wins, so avatar stays current. displayName is
        // the owner's stable Sleeper username (not a team nickname, which changes yearly
        // and per-league) — that's what identifies the person across the whole app.
        OwnerSeasonEntry mostRecent = ownerEntries.stream()
                .max(Comparator.comparing(e -> e.season().season()))
                .orElseThrow();

        List<SeasonResult> seasonResults = ownerEntries.stream()
                .map(e -> new SeasonResult(
                        e.family().key(),
                        e.family().displayName(),
                        e.season().season(),
                        e.season().status(),
                        e.team().rank()
                ))
                .sorted(Comparator.comparing(SeasonResult::season).reversed())
                .toList();

        return new OwnerCareerSummary(
                mostRecent.team().ownerUserId(),
                mostRecent.team().ownerDisplayName(),
                mostRecent.team().avatarUrl(),
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
     * stays compact without losing any of that history.
     */
    private List<EarnedBadge> computeBadges(List<OwnerSeasonEntry> ownerEntries) {
        Map<BadgeType, List<BadgeEarning>> earningsByType = new EnumMap<>(BadgeType.class);

        for (OwnerSeasonEntry e : ownerEntries) {
            boolean seasonComplete = COMPLETE_STATUS.equals(e.season().status());
            for (BadgeType type : BadgeType.values()) {
                if (type.scope().appliesTo(e.family().type()) && isEligible(type, e, seasonComplete)) {
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

    /**
     * Per-type eligibility for one (owner, family, season) entry. Fantasy placement badges
     * (CHAMPION, TOP_3, TOILET_CHAMP) key off that season's playoff/toilet-bowl bracket
     * (TeamSummary.playoffPlacement) rather than the regular-season standings rank — Pick'em
     * has no playoffs, so its placement badges (TOP_3, PICKINATOR) use rank instead.
     */
    private static boolean isEligible(BadgeType type, OwnerSeasonEntry e, boolean seasonComplete) {
        return switch (type) {
            // Founding Member is the only badge not gated on season completion — it's about
            // membership, not a performance placement.
            case FOUNDING_MEMBER -> isFoundingSeason(e);
            case CHAMPION -> seasonComplete && isPlacement(e, 1);
            case TOP_3 -> seasonComplete && isTopThree(e);
            case TOILET_CHAMP -> seasonComplete && isLastPlace(e);
            case PICKINATOR -> seasonComplete && e.team().rank() == 1;
            case TOP_SCORER -> seasonComplete && e.team().pointsFor() == maxAmong(e, TeamSummary::pointsFor);
            case ADVERSITY_SPECIALIST -> seasonComplete && e.team().pointsAgainst() == maxAmong(e, TeamSummary::pointsAgainst);
            // Not yet computed — see BadgeType.MICRO_MANAGER's javadoc for why.
            case MICRO_MANAGER -> false;
        };
    }

    private static boolean isFoundingSeason(OwnerSeasonEntry e) {
        String foundingSeason = e.family().seasons().stream()
                .map(SeasonConfig::season)
                .min(Comparator.naturalOrder())
                .orElse(null);
        return e.season().season().equals(foundingSeason);
    }

    private static boolean isPlacement(OwnerSeasonEntry e, int placement) {
        return e.team().playoffPlacement() != null && e.team().playoffPlacement() == placement;
    }

    private static boolean isTopThree(OwnerSeasonEntry e) {
        return switch (e.family().type()) {
            case FANTASY -> e.team().playoffPlacement() != null && e.team().playoffPlacement() <= TOP_FINISH_THRESHOLD;
            case PICKEM -> e.team().rank() <= TOP_FINISH_THRESHOLD;
        };
    }

    /** Worst playoffPlacement among that season's teams — the "toilet bowl" bracket's own last place. */
    private static boolean isLastPlace(OwnerSeasonEntry e) {
        Integer placement = e.team().playoffPlacement();
        if (placement == null) {
            return false;
        }
        int worst = e.season().teams().stream()
                .map(TeamSummary::playoffPlacement)
                .filter(p -> p != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(Integer.MIN_VALUE);
        return placement == worst;
    }

    private static double maxAmong(OwnerSeasonEntry e, ToDoubleFunction<TeamSummary> metric) {
        return e.season().teams().stream()
                .mapToDouble(metric)
                .max()
                .orElse(Double.NEGATIVE_INFINITY);
    }

    private static void addEarning(Map<BadgeType, List<BadgeEarning>> earningsByType, BadgeType type, OwnerSeasonEntry e) {
        earningsByType.computeIfAbsent(type, t -> new ArrayList<>())
                .add(new BadgeEarning(e.family().key(), e.season().season(), e.family().displayName() + " " + e.season().season()));
    }

    private LeagueFamilyConfig findFamily(String key) {
        return leaguesProperties.getLeagues().stream()
                .filter(f -> f.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new UnknownLeagueException(key));
    }

    /** Empty if this season's data couldn't be fetched, so one bad/unreachable league id doesn't fail the whole response. */
    private Optional<SeasonSummary> fetchSeason(LeagueFamilyConfig family, SeasonConfig seasonConfig) {
        try {
            SeasonSummary summary = seasonDataService.getSeasonSummary(seasonConfig.leagueId());
            return Optional.of(withBuyIns(family, seasonConfig, summary));
        } catch (RuntimeException e) {
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
                        team.playoffPlacement()
                ))
                .toList();
        return new SeasonSummary(summary.leagueId(), summary.season(), summary.name(), summary.status(), summary.totalRosters(), teams);
    }

    private record OwnerSeasonEntry(LeagueFamilyConfig family, SeasonSummary season, TeamSummary team) {
    }
}
