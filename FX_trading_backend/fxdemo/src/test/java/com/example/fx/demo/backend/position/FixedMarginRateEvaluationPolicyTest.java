package com.example.fx.demo.backend.position;

import com.example.fx.demo.backend.position.domain.Position;
import com.example.fx.demo.backend.margin.policy.FixedMarginRateEvaluationPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FixedMarginRateEvaluationPolicyTest {

    private final FixedMarginRateEvaluationPolicy policy = new FixedMarginRateEvaluationPolicy();

    @Test
    void usesPositionOpenPriceAsFixedMarginRate() {
        Position position = new Position();
        position.setOpenPrice(new BigDecimal("155.100"));
        position.setAvgPrice(new BigDecimal("154.900"));

        assertThat(policy.evaluateRate(position, null)).isEqualByComparingTo(new BigDecimal("155.100"));
    }
}
