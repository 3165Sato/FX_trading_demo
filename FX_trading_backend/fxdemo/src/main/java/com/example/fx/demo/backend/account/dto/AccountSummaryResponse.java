package com.example.fx.demo.backend.account.dto;

import java.math.BigDecimal;

public record AccountSummaryResponse(
        String accountId,
        String baseCurrency,
        BigDecimal balance,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedSwap,
        BigDecimal equity,
        BigDecimal usedMargin,
        BigDecimal freeMargin,
        BigDecimal marginRatio,
        BigDecimal lossCutThreshold,
        String status
) {
}
