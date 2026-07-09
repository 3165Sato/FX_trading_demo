package com.example.fx.demo.backend.position.dto;

import com.example.fx.demo.backend.trade.dto.OrderResultResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionCloseResponse(
        Long positionId,
        String currencyPair,
        String side,
        BigDecimal quantity,
        BigDecimal closePrice,
        BigDecimal realizedPnl,
        BigDecimal realizedSwap,
        String realizedCurrency,
        LocalDateTime closedAt,
        OrderResultResponse execution
) {
}
