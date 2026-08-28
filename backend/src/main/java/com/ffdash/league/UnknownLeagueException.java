package com.ffdash.league;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UnknownLeagueException extends RuntimeException {
    public UnknownLeagueException(String leagueId) {
        super("Unknown league id: " + leagueId);
    }
}
