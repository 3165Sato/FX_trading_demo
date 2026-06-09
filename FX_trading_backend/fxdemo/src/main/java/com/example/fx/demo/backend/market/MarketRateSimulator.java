package com.example.fx.demo.backend.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketRateSimulator {

    private static final BigDecimal TWO = new BigDecimal("2");

    private final MarketRateService marketRateService;
    private final MarketRateTickService marketRateTickService;

    public MarketRateSimulator(MarketRateService marketRateService, MarketRateTickService marketRateTickService) {
        this.marketRateService = marketRateService;
        this.marketRateTickService = marketRateTickService;
    }

    @Scheduled(fixedRate = 1000)
    public void updateRates() {
        marketRateService.getEnabledLatestRateEntities()
                .forEach(this::updateRate);
    }

    @Scheduled(fixedRate = 5000)
    public void saveRateTicks() {
        marketRateService.getEnabledLatestRateEntities()
                .forEach(marketRateTickService::saveTick);
        // TODO Keep roughly the latest 300 ticks per pair when retention rules are finalized.
    }

    private void updateRate(MarketRate marketRate) {
        CurrencyPair currencyPair = marketRate.getCurrencyPair();
        int priceScale = currencyPair.getPriceScale();
        BigDecimal spread = marketRate.getSpread();
        BigDecimal nextMidPrice = marketRate.getMidPrice()
                .add(randomDelta(currencyPair.getSymbol()))
                .setScale(priceScale, RoundingMode.HALF_UP);
        BigDecimal halfSpread = spread.divide(TWO);
        BigDecimal bid = nextMidPrice.subtract(halfSpread).setScale(priceScale, RoundingMode.HALF_UP);
        BigDecimal ask = nextMidPrice.add(halfSpread).setScale(priceScale, RoundingMode.HALF_UP);

        // This is a fictional learning rate; no external market API is used.
        marketRateService.updateLatestRate(marketRate, bid, ask, nextMidPrice, Instant.now());
    }

    private BigDecimal randomDelta(String symbol) {
        return switch (symbol) {
            case "USD/JPY" -> randomBetween("-0.010", "0.010");
            case "EUR/JPY" -> randomBetween("-0.012", "0.012");
            case "EUR/USD" -> randomBetween("-0.00100", "0.00100");
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal randomBetween(String min, String max) {
        double minValue = Double.parseDouble(min);
        double maxValue = Double.parseDouble(max);
        return BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(minValue, maxValue));
    }
}
