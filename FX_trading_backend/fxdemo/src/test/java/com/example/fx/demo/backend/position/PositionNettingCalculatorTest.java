package com.example.fx.demo.backend.position;

import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.position.dto.PositionResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PositionNettingCalculatorTest {

    private static final CurrencyPairScale USD_JPY_SCALE = new CurrencyPairScale(3, 0);
    private static final Map<String, CurrencyPairScale> SCALES = Map.of("USD/JPY", USD_JPY_SCALE);
    private final PositionNettingCalculator calculator = new PositionNettingCalculator();

    @Test
    void accumulatesSameSideTradesWithWeightedAveragePrice() {
        List<PositionResponse> positions = calculator.calculate(List.of(
                trade(OrderSide.BUY, "10000", "100.000", 1),
                trade(OrderSide.BUY, "10000", "102.000", 2)
        ), SCALES);

        assertThat(positions).hasSize(1);
        assertThat(positions.getFirst().side()).isEqualTo("LONG");
        assertThat(positions.getFirst().quantity()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(positions.getFirst().averagePrice()).isEqualByComparingTo(new BigDecimal("101.000"));
    }

    @Test
    void keepsAveragePriceWhenPartiallyClosed() {
        List<PositionResponse> positions = calculator.calculate(List.of(
                trade(OrderSide.BUY, "10000", "100.000", 1),
                trade(OrderSide.BUY, "10000", "102.000", 2),
                trade(OrderSide.SELL, "5000", "103.000", 3)
        ), SCALES);

        assertThat(positions).hasSize(1);
        assertThat(positions.getFirst().side()).isEqualTo("LONG");
        assertThat(positions.getFirst().quantity()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(positions.getFirst().averagePrice()).isEqualByComparingTo(new BigDecimal("101.000"));
    }

    @Test
    void removesPositionWhenFullyClosed() {
        List<PositionResponse> positions = calculator.calculate(List.of(
                trade(OrderSide.BUY, "10000", "100.000", 1),
                trade(OrderSide.SELL, "10000", "101.000", 2)
        ), SCALES);

        assertThat(positions).isEmpty();
    }

    @Test
    void reversesSideAtExecutionPriceWhenOverClosed() {
        List<PositionResponse> positions = calculator.calculate(List.of(
                trade(OrderSide.BUY, "10000", "100.000", 1),
                trade(OrderSide.SELL, "15000", "99.500", 2)
        ), SCALES);

        assertThat(positions).hasSize(1);
        assertThat(positions.getFirst().side()).isEqualTo("SHORT");
        assertThat(positions.getFirst().quantity()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(positions.getFirst().averagePrice()).isEqualByComparingTo(new BigDecimal("99.500"));
    }

    private PositionTradeInput trade(OrderSide side, String quantity, String price, int seconds) {
        return new PositionTradeInput(
                "USD/JPY",
                side,
                new BigDecimal(quantity),
                new BigDecimal(price),
                LocalDateTime.of(2026, 6, 21, 12, 0, seconds)
        );
    }
}
