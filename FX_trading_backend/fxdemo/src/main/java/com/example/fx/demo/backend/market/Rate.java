package com.example.fx.demo.backend.market;

import com.example.fx.demo.backend.common.enums.CurrencyPair;

import java.math.BigDecimal;
import java.time.Instant;

public record Rate(
        CurrencyPair currencyPair,
        BigDecimal bid,
        BigDecimal ask,
        Instant quotedAt
) {
}
