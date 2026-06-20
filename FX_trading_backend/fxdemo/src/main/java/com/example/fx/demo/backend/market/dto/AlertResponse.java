package com.example.fx.demo.backend.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AlertResponse(
        String id,
        String type,
        String currencyPair,
        String severity,
        String message,
        BigDecimal changePips,
        Instant raisedAt,
        Instant resolvedAt,
        boolean active
) {
}
