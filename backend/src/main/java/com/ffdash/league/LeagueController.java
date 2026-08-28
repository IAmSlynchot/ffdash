package com.ffdash.league;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leagues")
public class LeagueController {

    private final LeagueService leagueService;

    public LeagueController(LeagueService leagueService) {
        this.leagueService = leagueService;
    }

    /** The configured leagues, for building navigation. */
    @GetMapping
    public List<LeagueRef> listLeagues() {
        return leagueService.listLeagues();
    }

    /** Full standings snapshot for one configured league. */
    @GetMapping("/{leagueId}")
    public LeagueSummary getLeague(@PathVariable String leagueId) {
        return leagueService.getLeagueSummary(leagueId);
    }
}
