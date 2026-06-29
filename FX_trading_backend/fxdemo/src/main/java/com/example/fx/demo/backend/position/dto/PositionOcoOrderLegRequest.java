package com.example.fx.demo.backend.position.dto;

import java.math.BigDecimal;

public record PositionOcoOrderLegRequest(
        BigDecimal triggerPrice
) {
}
