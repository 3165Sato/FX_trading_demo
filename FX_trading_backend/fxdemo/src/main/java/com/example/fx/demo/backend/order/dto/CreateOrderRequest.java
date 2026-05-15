package com.example.fx.demo.backend.order.dto;

import com.example.fx.demo.backend.common.enums.CurrencyPair;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderType;

import java.math.BigDecimal;

public record CreateOrderRequest(
        Long accountId,
        CurrencyPair currencyPair,
        OrderSide side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal price
) {
}
