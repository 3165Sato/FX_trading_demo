package com.example.fx.demo.backend.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long id,
        String currencyPair,
        String side,
        String orderType,
        BigDecimal quantity,
        String status,
        String source,
        LocalDateTime requestedAt
) {
}
