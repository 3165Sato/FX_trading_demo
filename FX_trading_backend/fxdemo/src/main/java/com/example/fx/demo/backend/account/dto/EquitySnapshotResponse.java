package com.example.fx.demo.backend.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record EquitySnapshotResponse(
        Instant recordedAt,
        BigDecimal balance,
        BigDecimal equity,
        BigDecimal usedMargin,
        BigDecimal marginRatio
) {
}
