package com.example.fx.demo.backend.position;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

record PositionCalculationResult(
        List<PositionSnapshot> openPositions,
        Map<String, BigDecimal> realizedByCurrency
) {
}
