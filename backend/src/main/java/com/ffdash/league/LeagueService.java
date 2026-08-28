package com.ffdash.league;

import com.ffdash.config.LeaguesProperties;
import com.ffdash.config.LeaguesProperties.LeagueFamilyConfig;
import com.ffdash.config.LeaguesProperties.LeagueType;
import com.ffdash.config.LeaguesProperties.SeasonConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public LeagueService(SeasonDataService seasonDataService, LeaguesProperties leaguesProperties) {
        this.seasonDataService = seasonDataService;
        this.leaguesProperties = leaguesProperties;
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

        return new LeagueFamilyHistory(family.key(), family.displayName(), seasons);
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
                seasonResults
        );
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
            return Optional.of(seasonDataService.getSeasonSummary(seasonConfig.leagueId()));
        } catch (RuntimeException e) {
            log.warn("Skipping {} {} season (league id {}): {}",
                    family.key(), seasonConfig.season(), seasonConfig.leagueId(), e.getMessage());
            return Optional.empty();
        }
    }

    private record OwnerSeasonEntry(LeagueFamilyConfig family, SeasonSummary season, TeamSummary team) {
    }
}
