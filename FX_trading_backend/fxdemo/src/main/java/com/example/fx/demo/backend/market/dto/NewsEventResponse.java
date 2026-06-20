package com.example.fx.demo.backend.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record NewsEventResponse(
        String id,
        String currencyPair,
        String direction,
        BigDecimal magnitudeBps,
        double volatilityMultiplier,
        double spreadMultiplier,
        int durationSeconds,
        String headline,
        Instant startedAt,
        Instant endsAt,
        boolean active
) {
}
