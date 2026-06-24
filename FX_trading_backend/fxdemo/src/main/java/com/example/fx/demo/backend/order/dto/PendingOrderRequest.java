package com.example.fx.demo.backend.order.dto;

import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderType;

import java.math.BigDecimal;

public record PendingOrderRequest(
        String currencyPair,
        OrderSide side,
        OrderType orderType,
        BigDecimal quantity,
        BigDecimal triggerPrice
) {
}
