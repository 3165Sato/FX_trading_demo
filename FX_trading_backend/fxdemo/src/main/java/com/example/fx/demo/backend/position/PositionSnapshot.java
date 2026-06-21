package com.example.fx.demo.backend.position;

import java.math.BigDecimal;
import java.time.LocalDateTime;

record PositionSnapshot(
        String currencyPair,
        String side,
        BigDecimal quantity,
        BigDecimal averagePrice,
        String quoteCurrency,
        LocalDateTime updatedAt
) {
}
