package com.example.fx.demo.backend.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketRateTickResponse(
        String currencyPair,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal midPrice,
        BigDecimal spread,
        Instant quotedAt
) {
}
