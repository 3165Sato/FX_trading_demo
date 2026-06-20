package com.example.fx.demo.backend.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketRateSimulator {

    private static final BigDecimal TWO = new BigDecimal("2");

    private final MarketRateService marketRateService;
    private final MarketRateTickService marketRateTickService;
    private final MarketSimulatorProperties simulatorProperties;
    private final Random random = new Random();
    private final Map<String, BigDecimal> basePrices = new ConcurrentHashMap<>();

    public MarketRateSimulator(
            MarketRateService marketRateService,
            MarketRateTickService marketRateTickService,
            MarketSimulatorProperties simulatorProperties
    ) {
        this.marketRateService = marketRateService;
        this.marketRateTickService = marketRateTickService;
        this.simulatorProperties = simulatorProperties;
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
        BigDecimal nextMidPrice = nextMidPrice(marketRate, currencyPair);
        BigDecimal halfSpread = spread.divide(TWO, priceScale + 4, RoundingMode.HALF_UP);
        BigDecimal bid = nextMidPrice.subtract(halfSpread).setScale(priceScale, RoundingMode.HALF_UP);
        BigDecimal ask = nextMidPrice.add(halfSpread).setScale(priceScale, RoundingMode.HALF_UP);

        // This is a fictional learning rate; no external market API is used.
        marketRateService.updateLatestRate(marketRate, bid, ask, nextMidPrice, Instant.now());
    }

    private BigDecimal nextMidPrice(MarketRate marketRate, CurrencyPair currencyPair) {
        String symbol = currencyPair.getSymbol();
        int priceScale = currencyPair.getPriceScale();
        BigDecimal basePrice = resolveBasePrice(symbol, marketRate.getMidPrice());
        MarketSimulatorProperties.SimulatorTuning tuning = simulatorProperties.tuningFor(symbol);

        double prevMid = marketRate.getMidPrice().doubleValue();
        double base = basePrice.doubleValue();
        double deviation = prevMid - base;
        double reversion = -safeDouble(tuning.getReversionStrength()) * deviation;
        double shock = prevMid * (safeDouble(tuning.getVolatilityBps()) / 10000.0) * random.nextGaussian();
        double rawMid = prevMid + reversion + shock;
        double clampedMid = clamp(rawMid, base, tuning.getMaxDeviationBps());

        return BigDecimal.valueOf(clampedMid).setScale(priceScale, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveBasePrice(String symbol, BigDecimal currentMidPrice) {
        return basePrices.computeIfAbsent(
                symbol,
                key -> simulatorProperties.configuredBasePrice(key).orElse(currentMidPrice)
        );
    }

    private double clamp(double rawMid, double basePrice, Double maxDeviationBps) {
        double maxDeviation = safeDouble(maxDeviationBps);
        if (maxDeviation <= 0) {
            return rawMid;
        }

        double range = basePrice * maxDeviation / 10000.0;
        double lower = basePrice - range;
        double upper = basePrice + range;
        return Math.max(lower, Math.min(upper, rawMid));
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }
}
