package com.example.fx.demo.backend.market.simulation;

import com.example.fx.demo.backend.market.news.EventModifiers;
import com.example.fx.demo.backend.market.news.NewsEventService;
import com.example.fx.demo.backend.market.pair.CurrencyPair;
import com.example.fx.demo.backend.market.rate.MarketRate;
import com.example.fx.demo.backend.market.rate.MarketRateService;
import com.example.fx.demo.backend.market.rate.MarketRateTickService;
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
    private final NewsEventService newsEventService;
    private final Random random = new Random();
    private final Map<String, BigDecimal> basePrices = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> baseSpreads = new ConcurrentHashMap<>();

    public MarketRateSimulator(
            MarketRateService marketRateService,
            MarketRateTickService marketRateTickService,
            MarketSimulatorProperties simulatorProperties,
            NewsEventService newsEventService
    ) {
        this.marketRateService = marketRateService;
        this.marketRateTickService = marketRateTickService;
        this.simulatorProperties = simulatorProperties;
        this.newsEventService = newsEventService;
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
        Instant quotedAt = Instant.now();
        EventModifiers modifiers = newsEventService.consumeTick(currencyPair.getSymbol(), quotedAt);
        int priceScale = currencyPair.getPriceScale();
        BigDecimal spread = nextSpread(marketRate, currencyPair, modifiers);
        BigDecimal nextMidPrice = nextMidPrice(marketRate, currencyPair, modifiers);
        BigDecimal halfSpread = spread.divide(TWO, priceScale + 4, RoundingMode.HALF_UP);
        BigDecimal bid = nextMidPrice.subtract(halfSpread).setScale(priceScale, RoundingMode.HALF_UP);
        BigDecimal ask = nextMidPrice.add(halfSpread).setScale(priceScale, RoundingMode.HALF_UP);

        // This is a fictional learning rate; no external market API is used.
        marketRateService.updateLatestRate(marketRate, bid, ask, nextMidPrice, spread, quotedAt);
    }

    private BigDecimal nextMidPrice(MarketRate marketRate, CurrencyPair currencyPair, EventModifiers modifiers) {
        String symbol = currencyPair.getSymbol();
        int priceScale = currencyPair.getPriceScale();
        BigDecimal basePrice = resolveBasePrice(symbol, marketRate.getMidPrice());
        MarketSimulatorProperties.SimulatorTuning tuning = simulatorProperties.tuningFor(symbol);

        double prevMid = marketRate.getMidPrice().doubleValue();
        double base = basePrice.doubleValue();
        double deviation = prevMid - base;
        double reversion = -safeDouble(tuning.getReversionStrength()) * deviation;
        double shock = prevMid
                * (safeDouble(tuning.getVolatilityBps()) * modifiers.volatilityMultiplier() / 10000.0)
                * random.nextGaussian();
        double jump = prevMid * (modifiers.signedJumpBps().doubleValue() / 10000.0);
        double rawMid = prevMid + reversion + shock + jump;
        double clampedMid = modifiers.clampSuppressed() ? rawMid : clamp(rawMid, base, tuning.getMaxDeviationBps());

        return BigDecimal.valueOf(clampedMid).setScale(priceScale, RoundingMode.HALF_UP);
    }

    private BigDecimal nextSpread(MarketRate marketRate, CurrencyPair currencyPair, EventModifiers modifiers) {
        int priceScale = currencyPair.getPriceScale();
        BigDecimal baseSpread = resolveBaseSpread(currencyPair.getSymbol(), marketRate.getSpread());
        return baseSpread
                .multiply(BigDecimal.valueOf(modifiers.spreadMultiplier()))
                .setScale(priceScale, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveBasePrice(String symbol, BigDecimal currentMidPrice) {
        return basePrices.computeIfAbsent(
                symbol,
                key -> simulatorProperties.configuredBasePrice(key).orElse(currentMidPrice)
        );
    }

    private BigDecimal resolveBaseSpread(String symbol, BigDecimal currentSpread) {
        return baseSpreads.computeIfAbsent(symbol, key -> currentSpread);
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
