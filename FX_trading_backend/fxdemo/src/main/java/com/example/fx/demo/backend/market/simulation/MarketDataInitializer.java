package com.example.fx.demo.backend.market.simulation;

import com.example.fx.demo.backend.market.pair.CurrencyPair;
import com.example.fx.demo.backend.market.pair.CurrencyPairRepository;
import com.example.fx.demo.backend.market.rate.MarketRate;
import com.example.fx.demo.backend.market.rate.MarketRateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class MarketDataInitializer implements CommandLineRunner {

    private final CurrencyPairRepository currencyPairRepository;
    private final MarketRateRepository marketRateRepository;

    public MarketDataInitializer(
            CurrencyPairRepository currencyPairRepository,
            MarketRateRepository marketRateRepository
    ) {
        this.currencyPairRepository = currencyPairRepository;
        this.marketRateRepository = marketRateRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        CurrencyPair usdJpy = findOrCreateCurrencyPair("USD/JPY", "USD", "JPY", 3, 0, 2);
        CurrencyPair eurJpy = findOrCreateCurrencyPair("EUR/JPY", "EUR", "JPY", 3, 0, 2);
        CurrencyPair eurUsd = findOrCreateCurrencyPair("EUR/USD", "EUR", "USD", 5, 0, 4);
        CurrencyPair gbpUsd = findOrCreateCurrencyPair("GBP/USD", "GBP", "USD", 5, 0, 4);
        CurrencyPair gbpJpy = findOrCreateCurrencyPair("GBP/JPY", "GBP", "JPY", 3, 0, 2);
        CurrencyPair audUsd = findOrCreateCurrencyPair("AUD/USD", "AUD", "USD", 5, 0, 4);
        CurrencyPair audJpy = findOrCreateCurrencyPair("AUD/JPY", "AUD", "JPY", 3, 0, 2);
        CurrencyPair usdChf = findOrCreateCurrencyPair("USD/CHF", "USD", "CHF", 5, 0, 4);
        CurrencyPair usdCad = findOrCreateCurrencyPair("USD/CAD", "USD", "CAD", 5, 0, 4);

        createMarketRateIfAbsent(
                usdJpy,
                new BigDecimal("155.1215"),
                new BigDecimal("0.003")
        );
        createMarketRateIfAbsent(
                eurJpy,
                new BigDecimal("168.2525"),
                new BigDecimal("0.005")
        );
        createMarketRateIfAbsent(
                eurUsd,
                new BigDecimal("1.085015"),
                new BigDecimal("0.00003")
        );
        createMarketRateIfAbsent(gbpUsd, new BigDecimal("1.27000"), new BigDecimal("0.00004"));
        createMarketRateIfAbsent(gbpJpy, new BigDecimal("197.000"), new BigDecimal("0.008"));
        createMarketRateIfAbsent(audUsd, new BigDecimal("0.65000"), new BigDecimal("0.00004"));
        createMarketRateIfAbsent(audJpy, new BigDecimal("100.830"), new BigDecimal("0.007"));
        createMarketRateIfAbsent(usdChf, new BigDecimal("0.88000"), new BigDecimal("0.00004"));
        createMarketRateIfAbsent(usdCad, new BigDecimal("1.37000"), new BigDecimal("0.00005"));
    }

    private CurrencyPair findOrCreateCurrencyPair(
            String symbol,
            String baseCurrency,
            String quoteCurrency,
            Integer priceScale,
            Integer quantityScale,
            Integer pipScale
    ) {
        return currencyPairRepository.findBySymbol(symbol)
                .orElseGet(() -> {
                    CurrencyPair currencyPair = new CurrencyPair();
                    currencyPair.setSymbol(symbol);
                    currencyPair.setBaseCurrency(baseCurrency);
                    currencyPair.setQuoteCurrency(quoteCurrency);
                    currencyPair.setPriceScale(priceScale);
                    currencyPair.setQuantityScale(quantityScale);
                    currencyPair.setPipScale(pipScale);
                    currencyPair.setEnabled(true);
                    return currencyPairRepository.save(currencyPair);
                });
    }

    private void createMarketRateIfAbsent(
            CurrencyPair currencyPair,
            BigDecimal midPrice,
            BigDecimal spread
    ) {
        // 既に最新レートがある場合は、学習用の初期値で上書きしない。
        if (marketRateRepository.findByCurrencyPair(currencyPair).isPresent()) {
            return;
        }

        MarketRate marketRate = new MarketRate();
        int priceScale = currencyPair.getPriceScale();
        BigDecimal halfSpread = spread.divide(new BigDecimal("2"), priceScale + 4, java.math.RoundingMode.HALF_UP);
        marketRate.setCurrencyPair(currencyPair);
        marketRate.setBid(midPrice.subtract(halfSpread).setScale(priceScale, java.math.RoundingMode.HALF_UP));
        marketRate.setAsk(midPrice.add(halfSpread).setScale(priceScale, java.math.RoundingMode.HALF_UP));
        marketRate.setMidPrice(midPrice);
        marketRate.setSpread(spread);
        marketRate.setQuotedAt(Instant.now());
        marketRateRepository.save(marketRate);
    }
}
