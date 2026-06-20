package com.example.fx.demo.backend.market.dto;

import com.example.fx.demo.backend.common.enums.NewsDirection;

import java.math.BigDecimal;

public record NewsEventRequest(
        String currencyPair,
        NewsDirection direction,
        BigDecimal magnitudeBps,
        Integer durationSeconds,
        Double volatilityMultiplier,
        Double spreadMultiplier,
        String headline
) {
}
