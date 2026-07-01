package com.example.fx.demo.backend.position.model;

import com.example.fx.demo.backend.common.enums.OrderSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionTradeInput(
        String currencyPair,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal executionPrice,
        LocalDateTime executedAt
) {
}
