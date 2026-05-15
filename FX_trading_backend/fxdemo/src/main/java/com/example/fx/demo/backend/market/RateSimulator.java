package com.example.fx.demo.backend.market;

import com.example.fx.demo.backend.common.enums.CurrencyPair;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RateSimulator {

    public Rate currentRate(CurrencyPair currencyPair) {
        BigDecimal mid = baseRate(currencyPair).add(randomNoise());
        BigDecimal spread = new BigDecimal("0.003");
        return new Rate(
                currencyPair,
                mid.subtract(spread).setScale(3, RoundingMode.HALF_UP),
                mid.add(spread).setScale(3, RoundingMode.HALF_UP),
                Instant.now()
        );
    }

    private BigDecimal baseRate(CurrencyPair currencyPair) {
        return switch (currencyPair) {
            case USD_JPY -> new BigDecimal("155.000");
            case EUR_JPY -> new BigDecimal("168.000");
            case EUR_USD -> new BigDecimal("1.085");
            case GBP_JPY -> new BigDecimal("195.000");
        };
    }

    private BigDecimal randomNoise() {
        // 実際の市場レートではなく、画面確認用の小さな揺らぎ。
        double noise = ThreadLocalRandom.current().nextDouble(-0.050, 0.050);
        return BigDecimal.valueOf(noise).setScale(3, RoundingMode.HALF_UP);
    }
}
