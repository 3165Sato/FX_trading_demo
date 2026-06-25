package com.example.fx.demo.backend.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeSummaryResponse(
        Long id,
        Long orderId,
        String currencyPair,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        LocalDateTime executedAt,
        String tradeKind,
        Long positionId,
        BigDecimal realizedPnl
) {
}
