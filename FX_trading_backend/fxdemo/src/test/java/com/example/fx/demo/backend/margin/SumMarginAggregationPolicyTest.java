package com.example.fx.demo.backend.margin;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SumMarginAggregationPolicyTest {

    private final SumMarginAggregationPolicy policy = new SumMarginAggregationPolicy();

    @Test
    void aggregatesAllPositionMarginsWithoutOffsettingHedgedSides() {
        BigDecimal result = policy.aggregate(List.of(
                new BigDecimal("10000"),
                new BigDecimal("4000")
        ));

        assertThat(result).isEqualByComparingTo(new BigDecimal("14000"));
    }
}
