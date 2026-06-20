package com.example.fx.demo.backend.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SpreadStatsResponse(
        String currencyPair,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal spread,
        BigDecimal spreadPips,
        BigDecimal averageSpreadPips,
        BigDecimal minSpreadPips,
        BigDecimal maxSpreadPips,
        String status,
        int sampleCount,
        int limit,
        Integer pipScale,
        Instant quotedAt
) {
}
