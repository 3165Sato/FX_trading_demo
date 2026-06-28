package com.example.fx.demo.backend.position.dto;

import com.example.fx.demo.backend.common.enums.ExitOrderType;

import java.math.BigDecimal;

public record PositionExitOrderRequest(
        ExitOrderType type,
        BigDecimal triggerPrice
) {
}
