package com.example.fx.demo.backend.position;

import com.example.fx.demo.backend.common.enums.OrderSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;

record PositionTradeInput(
        String currencyPair,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal executionPrice,
        LocalDateTime executedAt
) {
}
