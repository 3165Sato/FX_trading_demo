package com.example.fx.demo.backend.margin;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Component
public class CurrencyConverter {

    private static final int INTERNAL_SCALE = 8;
    private static final int JPY_SCALE = 0;

    // DemoFXでは学習用にJPYを基軸通貨として扱う。
    public BigDecimal toJpy(
            BigDecimal amount,
            String currency,
            Map<String, BigDecimal> midRates
    ) {
        if (amount == null || currency == null) {
            return null;
        }
        return switch (currency) {
            case "JPY" -> scaleJpy(amount);
            case "USD" -> multiply(amount, midRates.get("USD/JPY"));
            case "CHF" -> convertUsdCross(amount, midRates.get("USD/JPY"), midRates.get("USD/CHF"));
            case "CAD" -> convertUsdCross(amount, midRates.get("USD/JPY"), midRates.get("USD/CAD"));
            default -> null;
        };
    }

    private BigDecimal convertUsdCross(BigDecimal amount, BigDecimal usdJpy, BigDecimal usdQuote) {
        if (usdJpy == null || usdQuote == null || usdQuote.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return scaleJpy(amount.multiply(usdJpy).divide(usdQuote, INTERNAL_SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal multiply(BigDecimal amount, BigDecimal rate) {
        if (rate == null) {
            return null;
        }
        return scaleJpy(amount.multiply(rate));
    }

    private BigDecimal scaleJpy(BigDecimal amount) {
        return amount.setScale(JPY_SCALE, RoundingMode.HALF_UP);
    }
}
