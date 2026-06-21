package com.example.fx.demo.backend.margin;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyConverterTest {

    private final CurrencyConverter converter = new CurrencyConverter();

    @Test
    void convertsUsdToJpyWithUsdJpyMidRate() {
        BigDecimal result = converter.toJpy(
                new BigDecimal("100"),
                "USD",
                Map.of("USD/JPY", new BigDecimal("155"))
        );

        assertThat(result).isEqualByComparingTo(new BigDecimal("15500"));
    }

    @Test
    void convertsChfToJpyWithUsdCrossRate() {
        BigDecimal result = converter.toJpy(
                new BigDecimal("100"),
                "CHF",
                Map.of(
                        "USD/JPY", new BigDecimal("155"),
                        "USD/CHF", new BigDecimal("0.9300")
                )
        );

        assertThat(result).isEqualByComparingTo(new BigDecimal("16667"));
    }

    @Test
    void returnsNullWhenCrossRateIsMissing() {
        BigDecimal result = converter.toJpy(
                new BigDecimal("100"),
                "CAD",
                Map.of("USD/JPY", new BigDecimal("155"))
        );

        assertThat(result).isNull();
    }
}
