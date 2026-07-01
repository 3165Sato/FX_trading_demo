package com.example.fx.demo.backend.margin.policy;

import java.math.BigDecimal;
import java.util.List;

public interface MarginAggregationPolicy {

    String name();

    BigDecimal aggregate(List<BigDecimal> requiredMargins);
}
