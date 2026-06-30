package com.example.fx.demo.backend.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PendingOrderResponse(
        Long id,
        String currencyPair,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal triggerPrice,
        String status,
        LocalDateTime createdAt,
        LocalDateTime triggeredAt,
        Long resultingOrderId,
        String rejectionReason,
        String purpose,
        String exitType,
        Long targetPositionId,
        Long parentOrderId,
        String ocoGroupId
) {
}
