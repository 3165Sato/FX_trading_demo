package com.example.fx.demo.backend.position.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SwapTransferAllResponse(
        int transferredPositions,
        BigDecimal totalTransferredSwap,
        BigDecimal balanceAfter,
        LocalDateTime transferredAt
) {
}
