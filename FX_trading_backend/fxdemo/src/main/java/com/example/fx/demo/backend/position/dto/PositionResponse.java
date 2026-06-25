package com.example.fx.demo.backend.position.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionResponse(
        Long id,
        String currencyPair,
        String side,
        BigDecimal quantity,
        BigDecimal averagePrice,
        String quoteCurrency,
        BigDecimal currentPrice,
        BigDecimal unrealizedPnl,
        LocalDateTime updatedAt,
        BigDecimal requiredMargin,
        LocalDateTime openedAt
) {
}
