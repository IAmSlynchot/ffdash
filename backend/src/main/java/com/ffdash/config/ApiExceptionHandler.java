package com.ffdash.config;

import com.ffdash.league.UnknownLeagueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Gives unhandled exceptions a consistent JSON shape and, more importantly, a consistent log
 * level that actually distinguishes "an external call failed" from "our own code is broken" —
 * before this existed, both fell through to Spring Boot's default whitelabel/JSON error handling
 * identically, with no app-specific signal either way.
 *
 * com.ffdash.league.UnknownLeagueException IS handled explicitly here (not left to its own
 * {@code @ResponseStatus(NOT_FOUND)}), even though that annotation alone would be enough on its
 * own: once any {@code @RestControllerAdvice} in the app declares an {@code @ExceptionHandler(Exception.class)}
 * catch-all, Spring's ExceptionHandlerExceptionResolver resolves it first and never falls through
 * to ResponseStatusExceptionResolver, which is what actually reads {@code @ResponseStatus} — so
 * without this explicit, more-specific handler, UnknownLeagueException would get silently caught
 * by handleUnexpected() below and turned into a 500 instead of its intended 404. (Confirmed live —
 * this exact regression happened during development before this handler was added.)
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(UnknownLeagueException.class)
    public ResponseEntity<Map<String, String>> handleUnknownLeague(UnknownLeagueException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    /** Sleeper itself is unreachable/erroring — an expected, external failure mode, not a bug here. */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleSleeperFailure(RestClientException e) {
        log.warn("Sleeper API call failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Unable to reach Sleeper API"));
    }

    /** Anything else is, by elimination, a real bug in this app — logged loudly so it's visibly distinct from the case above. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("Unexpected error handling request", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}
