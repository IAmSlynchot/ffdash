package com.ffdash.sleeper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One roster move (waiver claim, free agent pickup, or trade), as returned by
 * GET /league/{league_id}/transactions/{round} (round == week). See
 * https://docs.sleeper.com/#getting-transactions
 *
 * @param status Only "complete" ever actually happened — a "failed" waiver claim (lost to a
 *               higher priority/bid) never took effect and shouldn't count as a move.
 * @param roster_ids Every roster involved — one for a waiver/free-agent move, two for a trade.
 *                    SeasonDataService credits each roster listed here with one transaction, so
 *                    a trade counts as a move for both sides.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SleeperTransaction(
        String status,
        String type,
        List<Integer> roster_ids
) {
}
