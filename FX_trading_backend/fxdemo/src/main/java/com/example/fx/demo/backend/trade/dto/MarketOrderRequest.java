package com.example.fx.demo.backend.trade.dto;

import com.example.fx.demo.backend.common.enums.OrderSide;

import java.math.BigDecimal;

public record MarketOrderRequest(
        String currencyPair,
        OrderSide side,
        BigDecimal quantity
) {
}
