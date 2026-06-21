package com.example.fx.demo.backend.position.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionResponse(
        String currencyPair,
        String side,
        BigDecimal quantity,
        BigDecimal averagePrice,
        LocalDateTime updatedAt
) {
}
