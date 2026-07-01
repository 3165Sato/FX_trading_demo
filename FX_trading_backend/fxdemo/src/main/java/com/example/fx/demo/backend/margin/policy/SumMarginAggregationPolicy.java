package com.example.fx.demo.backend.margin.policy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class SumMarginAggregationPolicy implements MarginAggregationPolicy {

    @Override
    public String name() {
        return "SUM";
    }

    @Override
    public BigDecimal aggregate(List<BigDecimal> requiredMargins) {
        return requiredMargins.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
