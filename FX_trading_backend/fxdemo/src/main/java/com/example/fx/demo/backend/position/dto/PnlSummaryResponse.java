package com.example.fx.demo.backend.position.dto;

import java.math.BigDecimal;
import java.util.Map;

public record PnlSummaryResponse(
        Map<String, BigDecimal> unrealizedByCurrency,
        Map<String, BigDecimal> realizedByCurrency
) {
}
