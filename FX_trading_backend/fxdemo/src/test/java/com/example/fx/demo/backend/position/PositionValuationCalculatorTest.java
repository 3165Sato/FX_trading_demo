package com.example.fx.demo.backend.position;

import com.example.fx.demo.backend.position.dto.PositionResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PositionValuationCalculatorTest {

    private static final CurrencyPairScale JPY_SCALE = new CurrencyPairScale("JPY", 3, 0);
    private final PositionValuationCalculator calculator = new PositionValuationCalculator();

    @Test
    void marksLongPositionWithBidPrice() {
        PositionResponse response = calculator.toResponse(
                snapshot("LONG", "10000", "155.100"),
                new BigDecimal("155.300"),
                new BigDecimal("155.303"),
                JPY_SCALE
        );

        assertThat(response.currentPrice()).isEqualByComparingTo(new BigDecimal("155.300"));
        assertThat(response.unrealizedPnl()).isEqualByComparingTo(new BigDecimal("2000"));
    }

    @Test
    void marksShortPositionWithAskPrice() {
        PositionResponse response = calculator.toResponse(
                snapshot("SHORT", "10000", "155.300"),
                new BigDecimal("155.097"),
                new BigDecimal("155.100"),
                JPY_SCALE
        );

        assertThat(response.currentPrice()).isEqualByComparingTo(new BigDecimal("155.100"));
        assertThat(response.unrealizedPnl()).isEqualByComparingTo(new BigDecimal("2000"));
    }

    @Test
    void returnsNullValuationWhenRateIsMissing() {
        PositionResponse response = calculator.toResponse(
                snapshot("LONG", "10000", "155.100"),
                null,
                null,
                JPY_SCALE
        );

        assertThat(response.currentPrice()).isNull();
        assertThat(response.unrealizedPnl()).isNull();
    }

    private PositionSnapshot snapshot(String side, String quantity, String averagePrice) {
        return new PositionSnapshot(
                "USD/JPY",
                side,
                new BigDecimal(quantity),
                new BigDecimal(averagePrice),
                "JPY",
                LocalDateTime.of(2026, 6, 21, 12, 0)
        );
    }
}
