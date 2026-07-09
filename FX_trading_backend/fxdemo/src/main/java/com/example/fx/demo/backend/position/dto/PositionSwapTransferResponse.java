package com.example.fx.demo.backend.position.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionSwapTransferResponse(
        Long positionId,
        BigDecimal transferredSwap,
        BigDecimal balanceAfter,
        LocalDateTime transferredAt
) {
}
