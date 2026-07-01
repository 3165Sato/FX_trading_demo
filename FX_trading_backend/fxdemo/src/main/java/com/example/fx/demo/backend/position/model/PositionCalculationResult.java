package com.example.fx.demo.backend.position.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PositionCalculationResult(
        List<PositionSnapshot> openPositions,
        Map<String, BigDecimal> realizedByCurrency
) {
}
