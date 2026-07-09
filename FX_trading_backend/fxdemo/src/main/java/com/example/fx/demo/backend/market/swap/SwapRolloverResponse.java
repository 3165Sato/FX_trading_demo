package com.example.fx.demo.backend.market.swap;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SwapRolloverResponse(
        int days,
        int appliedPositions,
        BigDecimal totalAccruedSwap,
        LocalDateTime appliedAt
) {
}
