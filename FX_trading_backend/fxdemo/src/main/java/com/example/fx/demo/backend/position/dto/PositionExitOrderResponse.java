package com.example.fx.demo.backend.position.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionExitOrderResponse(
        Long id,
        String type,
        BigDecimal triggerPrice,
        String status,
        String ocoGroupId,
        LocalDateTime createdAt,
        LocalDateTime triggeredAt,
        Long parentOrderId
) {
}
