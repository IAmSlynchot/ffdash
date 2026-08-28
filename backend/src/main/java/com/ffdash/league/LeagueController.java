package com.ffdash.league;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LeagueController {

    private final LeagueService leagueService;

    public LeagueController(LeagueService leagueService) {
        this.leagueService = leagueService;
    }

    /** The configured league families, for building navigation. */
    @GetMapping("/leagues")
    public List<LeagueFamilyRef> listLeagueFamilies() {
        return leagueService.listLeagueFamilies();
    }

    /** Full multi-season history for one league family. */
    @GetMapping("/leagues/{key}")
    public LeagueFamilyHistory getFamilyHistory(@PathVariable String key) {
        return leagueService.getFamilyHistory(key);
    }

    /** Cross-league aggregate standings, one entry per Sleeper user. */
    @GetMapping("/owners")
    public List<OwnerCareerSummary> getOwnerCareerSummaries() {
        return leagueService.getOwnerCareerSummaries();
    }
}
